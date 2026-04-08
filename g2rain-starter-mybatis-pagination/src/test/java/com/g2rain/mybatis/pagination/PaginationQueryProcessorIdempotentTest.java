package com.g2rain.mybatis.pagination;

import com.g2rain.mybatis.pagination.model.Page;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 回归测试：同一查询链路多次进入拦截时，分页应保持幂等，最终 SQL 仅出现一次 LIMIT。
 */
class PaginationQueryProcessorIdempotentTest {

    @Test
    void should_apply_limit_only_once_when_query_chain_reentered() throws Throwable {
        PaginationQueryProcessor processor = new PaginationQueryProcessor(20000);
        AtomicReference<String> sqlRef = new AtomicReference<>();

        PageContext.of(1, 20, false, (Runnable) () -> {
            try {
                InvocationContextBundle bundle = buildQueryInvocationContext(
                    "SELECT id, name, age, email FROM test_user ORDER BY id DESC"
                );

                boolean firstIntercept = processor.shouldIntercept(bundle.context);
                Assertions.assertTrue(firstIntercept, "first intercept should pass");

                // 模拟 prepare 阶段完成了一次分页改写与参数注入
                bundle.boundSql.setAdditionalParameter(PaginationConstants.FIRST_PARAM_NAME, 20L);
                bundle.boundSql.setAdditionalParameter(PaginationConstants.SECOND_PARAM_NAME, 0L);
                String oncePagedSql = bundle.boundSql.getSql() + " LIMIT ?";
                setBoundSqlSql(bundle.boundSql, oncePagedSql);

                // 再次进入同一链路，应被幂等保护拦截
                boolean secondIntercept = processor.shouldIntercept(bundle.context);
                Assertions.assertFalse(secondIntercept, "second intercept should be skipped by idempotent guard");

                sqlRef.set(bundle.boundSql.getSql());
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });

        String finalSql = sqlRef.get();
        Assertions.assertNotNull(finalSql);
        Assertions.assertEquals(1, countToken(finalSql, "limit"), "final SQL should contain exactly one LIMIT");
    }

    @Test
    void should_skip_intercept_for_count_mapped_statement_when_count_enabled() throws Throwable {
        PaginationQueryProcessor processor = new PaginationQueryProcessor(20000);
        AtomicReference<Boolean> intercepted = new AtomicReference<>(true);

        PageContext.of(1, 20, true, (Runnable) () -> {
            try {
                InvocationContextBundle bundle = buildQueryInvocationContext(
                    "SELECT COUNT(*) FROM test_user",
                    "mapper.TestUserMapper.selectAll" + PaginationConstants.COUNT_MAPPED_STATEMENT_SUFFIX
                );

                boolean shouldIntercept = processor.shouldIntercept(bundle.context);
                intercepted.set(shouldIntercept);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });

        Assertions.assertFalse(
            intercepted.get(),
            "count mapped statement should be skipped to avoid re-triggering pagination"
        );
    }

    private static int countToken(String sql, String token) {
        String lowerSql = sql.toLowerCase(Locale.ROOT);
        String lowerToken = token.toLowerCase(Locale.ROOT);
        int from = 0;
        int count = 0;
        while (true) {
            int idx = lowerSql.indexOf(lowerToken, from);
            if (idx < 0) {
                return count;
            }
            count++;
            from = idx + lowerToken.length();
        }
    }

    private static InvocationContextBundle buildQueryInvocationContext(String originalSql) throws Exception {
        return buildQueryInvocationContext(originalSql, "mapper.TestUserMapper.selectAll");
    }

    private static InvocationContextBundle buildQueryInvocationContext(String originalSql, String mappedStatementId) throws Exception {
        Executor executor = Mockito.mock(Executor.class);
        MappedStatement ms = Mockito.mock(MappedStatement.class);
        @SuppressWarnings("unchecked")
        ResultHandler<Object> resultHandler = Mockito.mock(ResultHandler.class);

        BoundSql boundSql = new BoundSql(
            new org.apache.ibatis.session.Configuration(),
            originalSql,
            new ArrayList<>(),
            new Object()
        );
        Mockito.when(ms.getBoundSql(Mockito.any())).thenReturn(boundSql);
        Mockito.when(ms.getId()).thenReturn(mappedStatementId);

        Method queryMethod = Executor.class.getMethod(
            "query",
            MappedStatement.class,
            Object.class,
            RowBounds.class,
            ResultHandler.class
        );
        Object parameter = new Object();
        Object[] args = new Object[]{ms, parameter, RowBounds.DEFAULT, resultHandler};
        Invocation invocation = new Invocation(executor, queryMethod, args);
        com.g2rain.mybatis.extension.InvocationContext context = new com.g2rain.mybatis.extension.InvocationContext(invocation);
        return new InvocationContextBundle(context, boundSql);
    }

    private static void setBoundSqlSql(BoundSql boundSql, String sql) {
        org.apache.ibatis.reflection.MetaObject metaObject = org.apache.ibatis.reflection.SystemMetaObject.forObject(boundSql);
        metaObject.setValue("sql", sql);
    }

    private record InvocationContextBundle(com.g2rain.mybatis.extension.InvocationContext context, BoundSql boundSql) {
    }
}
