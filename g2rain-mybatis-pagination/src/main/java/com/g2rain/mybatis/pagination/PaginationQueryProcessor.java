package com.g2rain.mybatis.pagination;

import com.g2rain.mybatis.extension.InvocationContext;
import com.g2rain.mybatis.extension.QueryProcessor;
import com.g2rain.mybatis.extension.SqlHelper;
import com.g2rain.mybatis.extension.SqlParserDelegate;
import com.g2rain.mybatis.pagination.model.OrderItem;
import com.g2rain.mybatis.pagination.model.Page;
import com.g2rain.mybatis.pagination.PageContext;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MyBatis 分页拦截器内部实现，用于自动拦截查询，执行分页逻辑和总数统计。
 * <p>
 * 该类在查询执行前，通过 {@link PageContext} 判断是否需要分页；如果需要分页，
 * 会构造 count SQL 并执行统计查询，然后根据 {@link DatabaseType} 生成对应数据库的分页 SQL，
 * 并将参数注入到 {@link BoundSql}。
 * </p>
 * <p>
 * 分页查询完成后，结果会被填充到 {@link PageContext} 中，同时提供排序字段合并和优化 count SQL 的功能。
 * </p>
 * <p>
 * 典型流程：
 * <ol>
 *     <li>拦截执行 {@link #shouldIntercept(InvocationContext)} 判断是否分页</li>
 *     <li>构建 count 查询语句并执行统计</li>
 *     <li>拦截 {@link #onQuery}，根据数据库类型生成分页 SQL</li>
 *     <li>注入分页参数到 ParameterMapping 和 BoundSql 的额外参数</li>
 *     <li>拦截 {@link #onResult} 填充分页结果到 PageContext</li>
 * </ol>
 * </p>
 *
 * @author alpha
 * @since 2026/3/5
 */
public class PaginationQueryProcessor extends QueryProcessor {

    /**
     * 拦截器顺序
     */
    private final int order;

    /**
     * Count 查询缓存，key 为 MappedStatement ID
     */
    protected static final Map<String, MappedStatement> countMsCache = new ConcurrentHashMap<>();

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
     * 判断是否需要拦截 SQL 执行，主要用于分页和 count 查询
     *
     * @param invocationContext MyBatis 执行上下文
     * @return true 表示继续执行分页逻辑，false 表示跳过
     * @throws SQLException SQL 异常
     */
    @Override
    public boolean shouldIntercept(InvocationContext invocationContext) throws SQLException {
        Executor executor = invocationContext.getExecutor();
        MappedStatement mappedStatement = invocationContext.getMappedStatement();
        Object parameter = invocationContext.getParameter();
        RowBounds rowBounds = invocationContext.getRowBounds();
        ResultHandler<?> resultHandler = invocationContext.getResultHandler();
        BoundSql boundSql = invocationContext.getBoundSql();

        Page<?> pageScope = PageContext.peek();
        // 守卫拦截, 如果没有分页功能, 不执行分页查询动作
        if (Objects.isNull(pageScope)) {
            return false;
        }

        // 如果没有启动 count 查询, 直接返回
        if (!pageScope.isCount()) {
            pageScope.setTotal(-1);
            return true;
        }

        // 执行 count 查询
        MappedStatement countMs = buildAutoCountMappedStatement(mappedStatement);
        String countSqlStr = autoCountSql(boundSql.getSql());
        SqlHelper.SqlContext sqlContext = SqlHelper.sql(boundSql);
        BoundSql countSql = new BoundSql(countMs.getConfiguration(), countSqlStr, sqlContext.parameterMappings(), parameter);
        SqlHelper.applyAdditionalParams(countSql, sqlContext.additionalParameters());
        CacheKey cacheKey = executor.createCacheKey(countMs, parameter, rowBounds, countSql);
        List<Object> result = executor.query(countMs, parameter, rowBounds, resultHandler, cacheKey, countSql);

        long total = 0L;
        if (Objects.nonNull(result) && !result.isEmpty()) {
            Object o = result.getFirst();
            if (Objects.nonNull(o)) {
                total = Long.parseLong(o.toString());
            }
        }

        pageScope.setTotal(total);
        return total > 0;
    }

    /**
     * 执行分页查询 SQL，注入分页参数
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

        // 执行分页查询
        String buildSql = boundSql.getSql();
        List<OrderItem> orderBy = pageScope.getOrderBy();
        if (Objects.nonNull(orderBy) && !orderBy.isEmpty()) {
            buildSql = this.concatOrderBy(buildSql, orderBy);
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
        if (Objects.nonNull(page) && result instanceof List list) {
            if (!page.isEmpty()) {
                page.clear();
            }

            page.addAll(list);
        }
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
        final String countId = String.format("%s_COUNT", ms.getId());
        final Configuration configuration = ms.getConfiguration();
        return countMsCache.computeIfAbsent(countId, key -> {
            MappedStatement.Builder builder = new MappedStatement.Builder(configuration, key, ms.getSqlSource(), ms.getSqlCommandType());
            builder.resource(ms.getResource());
            builder.fetchSize(ms.getFetchSize());
            builder.statementType(ms.getStatementType());
            builder.timeout(ms.getTimeout());
            builder.parameterMap(ms.getParameterMap());
            builder.resultMaps(Collections.singletonList(new ResultMap.Builder(configuration, COUNT_RESULT_MAP_ID, Long.class, Collections.emptyList()).build()));
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
     * @param sql 原始 SQL
     * @return count SQL
     * @throws SQLException SQL 异常
     */
    public String autoCountSql(String sql) throws SQLException {
        try {
            Select select = (Select) SqlParserDelegate.parse(sql);
            if (select instanceof SetOperationList) {
                return lowLevelCountSql(sql);
            }

            PlainSelect plainSelect = (PlainSelect) select;
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
                return lowLevelCountSql(select.toString());
            }

            for (SelectItem<?> item : plainSelect.getSelectItems()) {
                if (item.toString().contains("?")) {
                    return lowLevelCountSql(select.toString());
                }
            }

            // 优化 SQL
            plainSelect.setSelectItems(COUNT_SELECT_ITEM);
            return select.toString();
        } catch (JSQLParserException e) {
            throw new SQLException(e);
        }
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
     * @param originalSql 原始 SQL
     * @param orderBy     order by 字段字符串，逗号分隔
     * @return 合并后的 SQL
     * @throws SQLException SQL 异常
     */
    public String concatOrderBy(String originalSql, List<OrderItem> orderBy) throws SQLException {
        try {
            Statement statement = SqlParserDelegate.parse(originalSql);
            if (!(statement instanceof Select select)) {
                return originalSql;
            }

            List<OrderByElement> additionalOrderBy = orderBy.stream().map(item -> {
                OrderByElement element = new OrderByElement();
                element.setExpression(new Column(item.getColumn()));
                element.setAsc(!"DESC".equalsIgnoreCase(item.getDirection()));
                element.setAscDescPresent(true);
                return element;
            }).collect(Collectors.toList());

            List<OrderByElement> orderByElements = select.getOrderByElements();
            List<OrderByElement> merged;
            if (Objects.isNull(orderByElements) || orderByElements.isEmpty()) {
                merged = additionalOrderBy;
            } else {
                merged = new ArrayList<>(orderByElements);
                merged.addAll(additionalOrderBy);
            }

            select.setOrderByElements(merged);
            return select.toString();
        } catch (JSQLParserException e) {
            throw new SQLException(e);
        }
    }
}
