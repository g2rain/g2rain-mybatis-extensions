package com.g2rain.mybatis.pagination;

import com.g2rain.mybatis.extension.PrepareProcessor;
import com.g2rain.mybatis.extension.SqlHelper;
import com.g2rain.mybatis.pagination.model.OrderItem;
import com.g2rain.mybatis.pagination.model.Page;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 在 {@link StatementHandler#prepare} 阶段执行分页 SQL 改写，避免 Executor.query 再入导致重复 LIMIT。
 *
 * @author alpha
 * @since 2026/3/18
 */
public class PaginationPrepareProcessor extends PrepareProcessor {

    private final int order;
    private final PaginationQueryProcessor paginationQueryProcessor;

    public PaginationPrepareProcessor(int order, PaginationQueryProcessor paginationQueryProcessor) {
        this.order = order;
        this.paginationQueryProcessor = paginationQueryProcessor;
    }

    @Override
    protected void onPrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) throws SQLException {
        Page<?> pageScope = PageContext.peek();
        if (Objects.isNull(pageScope)) {
            return;
        }

        SqlHelper.StatementContext statementContext = SqlHelper.statement(sh);
        MappedStatement ms = statementContext.mappedStatement();
        BoundSql boundSql = statementContext.boundSql();
        if (PaginationGuards.isCountMappedStatement(ms) || PaginationGuards.isPaginationAlreadyApplied(boundSql)) {
            return;
        }

        String buildSql = boundSql.getSql();
        List<OrderItem> orderBy = pageScope.getOrderBy();
        if (Objects.nonNull(orderBy) && !orderBy.isEmpty()) {
            buildSql = this.paginationQueryProcessor.cachedConcatOrderBy(buildSql, orderBy);
        }

        Dialect dialect = DatabaseType.getDialect(ms);
        final Configuration configuration = statementContext.configuration();
        DialectModel model = dialect.buildPaginationSql(
            buildSql, pageScope.getStartRow(), pageScope.getPageSize()
        );

        SqlHelper.SqlContext sqlContext = statementContext.sqlContext();
        List<ParameterMapping> mappings = sqlContext.parameterMappings();
        Map<String, Object> additionalParameter = sqlContext.additionalParameters();
        model.consumers(configuration, mappings, additionalParameter);
        sqlContext.sql(model.getDialectSql());
        sqlContext.parameterMappings(mappings);
    }

    @Override
    public int order() {
        return this.order;
    }
}
