package com.g2rain.mybatis.pagination;

import com.g2rain.mybatis.extension.InvocationContext;
import com.g2rain.mybatis.extension.QueryProcessor;
import com.g2rain.mybatis.extension.SqlHelper;
import com.g2rain.mybatis.extension.SqlParserDelegate;
import com.g2rain.mybatis.pagination.model.OrderItem;
import com.g2rain.mybatis.pagination.model.Page;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.Distinct;
import net.sf.jsqlparser.statement.select.GroupByElement;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;
import net.sf.jsqlparser.statement.select.SetOperationList;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * MyBatis 分页拦截器内部实现，用于自动拦截查询，执行分页逻辑和总数统计。
 * <p>
 * 该类在查询执行前，通过 {@link PageContext} 判断是否需要分页；若开启 count，
 * 在 {@link #shouldIntercept(InvocationContext)} 中构造并执行 count SQL。
 * 物理分页 SQL 的生成与参数注入在 {@link org.apache.ibatis.executor.statement.StatementHandler#prepare}
 * 阶段由 {@link PaginationPrepareProcessor} 完成，以避免 Executor 查询再入导致重复 LIMIT。
 * </p>
 * <p>
 * 分页查询完成后，结果会被填充到 {@link PageContext} 中，同时提供排序字段合并和优化 count SQL 的功能。
 * </p>
 * <p>
 * 典型流程：
 * <ol>
 *     <li>{@link #shouldIntercept(InvocationContext)}：分页上下文与幂等守卫；可选执行 count</li>
 *     <li>若开启 count：构建 count SQL 并执行统计查询</li>
 *     <li>prepare 阶段：{@link PaginationPrepareProcessor} 按方言生成分页 SQL 并注入参数</li>
 *     <li>{@link #onQuery}：本类不改写 SQL（保留扩展点）</li>
 *     <li>{@link #onResult}：将查询列表回填至 {@link PageContext}</li>
 * </ol>
 * </p>
 *
 * @author alpha
 * @since 2026/3/5
 */
public class PaginationQueryProcessor extends QueryProcessor {

    private static final System.Logger logger = System.getLogger(PaginationQueryProcessor.class.getName());
    private static final boolean CACHE_STATS_ENABLED = Boolean.parseBoolean(
        System.getProperty("g2rain.mybatis.pagination.cache.stats.enabled", "false")
    );
    private static final int CACHE_STATS_LOG_EVERY = Integer.parseInt(
        System.getProperty("g2rain.mybatis.pagination.cache.stats.logEvery", "1000")
    );
    private static final AtomicLong countSqlCacheCalls = new AtomicLong();
    private static final AtomicLong orderBySqlCacheCalls = new AtomicLong();

    /**
     * 拦截器顺序
     */
    private final int order;

    /**
     * Count 查询缓存，key 为 MappedStatement ID
     */
    protected static final Map<String, MappedStatement> countMsCache = new ConcurrentHashMap<>();

    /**
     * Count SQL 字符串缓存。
     * <p>
     * count SQL 的生成依赖 SQL AST 解析与改写，成本较高；即使 SqlParserDelegate 命中缓存，
     * 仍需要反序列化出副本。这里直接缓存最终 count SQL 字符串，减少重复解析与改写成本。
     */
    private static final Cache<String, String> COUNT_SQL_CACHE = Caffeine.newBuilder()
        .maximumSize(2048)
        .expireAfterAccess(2, TimeUnit.HOURS)
        .recordStats()
        .build();

    /**
     * ORDER BY 合并后的 SQL 缓存。
     * <p>
     * 对同一条 SQL + 同一排序规则的组合，缓存合并后的 SQL，避免重复 AST 解析与重建。
     */
    private static final Cache<String, String> ORDER_BY_SQL_CACHE = Caffeine.newBuilder()
        .maximumSize(2048)
        .expireAfterAccess(2, TimeUnit.HOURS)
        .recordStats()
        .build();

    /**
     * Count 查询默认 SelectItem
     */
    protected static final List<SelectItem<?>> COUNT_SELECT_ITEM = Collections.singletonList(
        new SelectItem<>(new Column().withColumnName("COUNT(*)")).withAlias(new Alias("total"))
    );

    /**
     * Count 查询结果 Map ID
     */
    protected static final String COUNT_RESULT_MAP_ID = "com.g2rain.pagination.CountMap";

    /**
     * 构造函数
     *
     * @param order 拦截器执行顺序
     */
    public PaginationQueryProcessor(int order) {
        this.order = order;
    }

    /**
     * 判断当前查询是否进入分页插件链；在此执行 count（若开启）并配合 {@link PaginationGuards} 做幂等。
     *
     * @param invocationContext MyBatis 执行上下文
     * @return true 表示继续执行分页逻辑，false 表示跳过
     */
    @Override
    public boolean shouldIntercept(InvocationContext invocationContext) {
        return Objects.nonNull(PageContext.peek());
    }

    /**
     * 查询前扩展点；分页 SQL 改写已在 {@link PaginationPrepareProcessor}（prepare 阶段）完成。
     *
     * @param executor      Executor
     * @param ms            MappedStatement
     * @param parameter     参数
     * @param rowBounds     行边界
     * @param resultHandler 结果处理器
     * @param boundSql      原始 BoundSql
     * @throws SQLException SQL 异常
     */
    @Override
    protected void onQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler<?> resultHandler, BoundSql boundSql) throws SQLException {
        Page<?> pageScope = PageContext.peek();
        // 守卫拦截, 如果没有分页功能, 不执行分页查询动作
        if (Objects.isNull(pageScope)) {
            return;
        }

        String buildSql = boundSql.getSql();

        Select parsedSelect;
        try {
            parsedSelect = (Select) SqlParserDelegate.parse(buildSql);
        } catch (JSQLParserException e) {
            throw new SQLException(e);
        }

        // 如果没有启动 count 查询, 直接返回
        long total = -1L;
        if (pageScope.isCount()) {
            // 执行 count 查询
            MappedStatement countMs = buildAutoCountMappedStatement(ms);
            String countSqlStr = autoCountSql(parsedSelect, buildSql);
            SqlHelper.SqlContext sqlContext = SqlHelper.sql(boundSql);
            BoundSql countSql = new BoundSql(countMs.getConfiguration(), countSqlStr, sqlContext.parameterMappings(), parameter);
            SqlHelper.applyAdditionalParams(countSql, sqlContext.additionalParameters());
            CacheKey cacheKey = executor.createCacheKey(countMs, parameter, rowBounds, countSql);
            List<Object> result = executor.query(countMs, parameter, rowBounds, resultHandler, cacheKey, countSql);
            total = 0L;
            if (Objects.nonNull(result) && !result.isEmpty()) {
                Object o = result.getFirst();
                if (Objects.nonNull(o)) {
                    total = Long.parseLong(o.toString());
                }
            }
        }

        pageScope.setTotal(total);
        if (total == 0L) {
            return;
        }

        // 执行分页查询
        List<OrderItem> orderBy = pageScope.getOrderBy();
        if (Objects.nonNull(orderBy) && !orderBy.isEmpty()) {
            buildSql = this.concatOrderBy(parsedSelect, orderBy);
        }

        Dialect dialect = DatabaseType.getDialect(ms);
        final Configuration configuration = ms.getConfiguration();

        DialectModel model = dialect.buildPaginationSql(
            buildSql, pageScope.getStartRow(), pageScope.getPageSize()
        );

        SqlHelper.SqlContext sqlContext = SqlHelper.sql(boundSql);
        List<ParameterMapping> mappings = sqlContext.parameterMappings();
        Map<String, Object> additionalParameter = sqlContext.additionalParameters();
        model.consumers(configuration, mappings, additionalParameter);
        sqlContext.sql(model.getDialectSql());
        sqlContext.parameterMappings(mappings);
    }

    /**
     * 填充分页查询结果到 PageContext
     *
     * @param ms        MappedStatement
     * @param parameter 参数
     * @param rowBounds 行边界
     * @param result    查询结果
     * @throws SQLException SQL 异常
     */
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void onResult(MappedStatement ms, Object parameter, RowBounds rowBounds, Object result) throws SQLException {
        Page<?> page = PageContext.peek();
        if (Objects.isNull(page)) {
            return;
        }

        if (!(result instanceof List list)) {
            return;
        }

        if (!page.isEmpty()) {
            page.clear();
        }

        page.addAll(list);
    }

    /**
     * 查询完成后的清理操作
     */
    @Override
    public void afterCompletion() {
        PageContext.clear();
    }

    /**
     * 获取拦截器执行顺序
     *
     * @return 顺序值
     */
    @Override
    public int order() {
        return this.order;
    }

    /**
     * 构建 count 查询的 MappedStatement，并缓存
     *
     * @param ms 原始 MappedStatement
     * @return count 查询 MappedStatement
     */
    protected MappedStatement buildAutoCountMappedStatement(MappedStatement ms) {
        final String countId = ms.getId() + PaginationConstants.COUNT_MAPPED_STATEMENT_SUFFIX;
        final Configuration configuration = ms.getConfiguration();
        return countMsCache.computeIfAbsent(countId, key -> {
            ResultMap resultMap = new ResultMap.Builder(
                configuration, COUNT_RESULT_MAP_ID,
                Long.class, Collections.emptyList()
            ).build();

            MappedStatement.Builder builder = new MappedStatement.Builder(
                configuration, key, ms.getSqlSource(), ms.getSqlCommandType()
            );

            builder.resource(ms.getResource());
            builder.fetchSize(ms.getFetchSize());
            builder.statementType(ms.getStatementType());
            builder.timeout(ms.getTimeout());
            builder.parameterMap(ms.getParameterMap());
            builder.resultMaps(Collections.singletonList(resultMap));
            builder.resultSetType(ms.getResultSetType());
            builder.cache(ms.getCache());
            builder.flushCacheRequired(ms.isFlushCacheRequired());
            builder.useCache(ms.isUseCache());
            return builder.build();
        });
    }

    /**
     * 自动生成 count SQL，优化 order by 和参数
     *
     * @param parsedSelect 原始 SQL
     * @param originalSql  原始 SQL
     * @return count SQL
     */
    public String autoCountSql(Select parsedSelect, String originalSql) {
        // set operation（UNION 等）直接降级
        if (parsedSelect instanceof SetOperationList) {
            return lowLevelCountSql(originalSql);
        }

        PlainSelect plainSelect = (PlainSelect) parsedSelect;

        // 保存现场
        List<OrderByElement> oldOrderBy = plainSelect.getOrderByElements();
        List<SelectItem<?>> oldSelectItems = plainSelect.getSelectItems();

        try {


            // 优化 order by 在非分组情况下
            List<OrderByElement> orderBy = plainSelect.getOrderByElements();
            if (Objects.nonNull(orderBy) && !orderBy.isEmpty()) {
                boolean canClean = true;
                for (OrderByElement order : orderBy) {
                    // order by 里带参数, 不去除order by
                    Expression expression = order.getExpression();
                    if (!(expression instanceof Column) && expression.toString().contains("?")) {
                        canClean = false;
                        break;
                    }
                }

                if (canClean) {
                    plainSelect.setOrderByElements(null);
                }
            }

            Distinct distinct = plainSelect.getDistinct();
            GroupByElement groupBy = plainSelect.getGroupBy();
            // 包含 distinct、groupBy 不优化
            if (Objects.nonNull(distinct) || Objects.nonNull(groupBy)) {
                return lowLevelCountSql(parsedSelect.toString());
            }

            for (SelectItem<?> item : plainSelect.getSelectItems()) {
                if (item.toString().contains("?")) {
                    return lowLevelCountSql(parsedSelect.toString());
                }
            }

            // 优化 SQL
            plainSelect.setSelectItems(COUNT_SELECT_ITEM);
            return parsedSelect.toString();

        } finally {
            // 恢复现场，避免污染后续分页 SQL
            plainSelect.setOrderByElements(oldOrderBy);
            plainSelect.setSelectItems(oldSelectItems);
        }
    }

    protected String cachedAutoCountSql(String originalSql) throws SQLException {
        if (CACHE_STATS_ENABLED) {
            long calls = countSqlCacheCalls.incrementAndGet();
            if (CACHE_STATS_LOG_EVERY > 0 && (calls % CACHE_STATS_LOG_EVERY == 0)) {
                var stats = COUNT_SQL_CACHE.stats();
                logger.log(System.Logger.Level.INFO,
                    "COUNT_SQL_CACHE stats: requests={0}, hitRate={1}, hits={2}, misses={3}, evictions={4}",
                    stats.requestCount(), stats.hitRate(), stats.hitCount(), stats.missCount(), stats.evictionCount());
            }
        }
        String cached = COUNT_SQL_CACHE.getIfPresent(originalSql);
        if (cached != null) {
            return cached;
        }

        String countSql = autoCountSql(originalSql);
        COUNT_SQL_CACHE.put(originalSql, countSql);
        return countSql;
    }

    /**
     * 当 SQL 复杂或包含参数时，使用低级方式生成 count SQL
     *
     * @param originalSql 原始 SQL
     * @return count SQL
     */
    protected String lowLevelCountSql(String originalSql) {
        return String.format("SELECT COUNT(*) FROM (%s) TOTAL", originalSql);
    }

    /**
     * 合并原始 SQL 的 order by 字段
     *
     * @param parsedSelect 原始 SQL
     * @param orderBy      order by 字段字符串，逗号分隔
     * @return 合并后的 SQL
     */
    public String concatOrderBy(Select parsedSelect, List<OrderItem> orderBy) {
        List<OrderByElement> additionalOrderBy = orderBy.stream().map(item -> {
            OrderByElement element = new OrderByElement();
            element.setExpression(new Column(item.getColumn()));
            element.setAsc(!"DESC".equalsIgnoreCase(item.getDirection()));
            element.setAscDescPresent(true);
            return element;
        }).collect(Collectors.toList());

        List<OrderByElement> orderByElements = parsedSelect.getOrderByElements();
        List<OrderByElement> merged;
        if (Objects.isNull(orderByElements) || orderByElements.isEmpty()) {
            merged = additionalOrderBy;
        } else {
            merged = new ArrayList<>(orderByElements);
            merged.addAll(additionalOrderBy);
        }

        parsedSelect.setOrderByElements(merged);
        return parsedSelect.toString();
    }

    protected String cachedConcatOrderBy(String originalSql, List<OrderItem> orderBy) throws SQLException {
        if (CACHE_STATS_ENABLED) {
            long calls = orderBySqlCacheCalls.incrementAndGet();
            if (CACHE_STATS_LOG_EVERY > 0 && (calls % CACHE_STATS_LOG_EVERY == 0)) {
                var stats = ORDER_BY_SQL_CACHE.stats();
                logger.log(System.Logger.Level.INFO,
                    "ORDER_BY_SQL_CACHE stats: requests={0}, hitRate={1}, hits={2}, misses={3}, evictions={4}",
                    stats.requestCount(), stats.hitRate(), stats.hitCount(), stats.missCount(), stats.evictionCount());
            }
        }
        String key = buildOrderByCacheKey(originalSql, orderBy);
        String cached = ORDER_BY_SQL_CACHE.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        String mergedSql = concatOrderBy(originalSql, orderBy);
        ORDER_BY_SQL_CACHE.put(key, mergedSql);
        return mergedSql;
    }

    private static String buildOrderByCacheKey(String originalSql, List<OrderItem> orderBy) {
        // orderBy 已在 PageContext 做过安全过滤，这里生成稳定签名以提高命中率
        String signature = orderBy.stream()
            .map(i -> i.getColumn() + " " + i.getDirection())
            .collect(Collectors.joining(","));
        return originalSql + "||" + signature;
    }

    private static String tryFastConcatOrderBy(String originalSql, List<OrderItem> orderBy) {
        if (originalSql == null || originalSql.isBlank()) {
            return null;
        }

        String lower = originalSql.toLowerCase(Locale.ROOT);
        // 只对“非常典型”的简单 SELECT 做快路径，避免误改写
        if (!lower.startsWith("select")) {
            return null;
        }
        if (lower.contains(" order by ")) {
            return null;
        }
        if (lower.contains(" union ") || lower.contains(" group by ") || lower.contains(" distinct ")) {
            return null;
        }
        if (lower.contains(" for update") || lower.contains(" lock in share mode")) {
            return null;
        }

        String orderByClause = orderBy.stream()
            .map(i -> i.getColumn() + ("desc".equalsIgnoreCase(i.getDirection()) ? " DESC" : " ASC"))
            .collect(Collectors.joining(", "));
        if (orderByClause.isBlank()) {
            return null;
        }

        return originalSql + " ORDER BY " + orderByClause;
    }

    static com.github.benmanes.caffeine.cache.stats.CacheStats countSqlCacheStats() {
        return COUNT_SQL_CACHE.stats();
    }

    static com.github.benmanes.caffeine.cache.stats.CacheStats orderBySqlCacheStats() {
        return ORDER_BY_SQL_CACHE.stats();
    }

    static void clearInternalCachesForTest() {
        COUNT_SQL_CACHE.invalidateAll();
        ORDER_BY_SQL_CACHE.invalidateAll();
        COUNT_SQL_CACHE.cleanUp();
        ORDER_BY_SQL_CACHE.cleanUp();
    }
}
