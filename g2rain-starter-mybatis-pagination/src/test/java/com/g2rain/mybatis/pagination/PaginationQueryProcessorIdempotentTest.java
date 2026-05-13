package com.g2rain.mybatis.pagination;

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

/**
 * 回归测试：当前版本仅在存在 PageContext 时参与拦截。
 */
class PaginationQueryProcessorIdempotentTest {

    @Test
    void should_intercept_when_page_context_is_present() throws Throwable {
        PaginationQueryProcessor processor = new PaginationQueryProcessor(20000);
        InvocationContextBundle bundle = buildQueryInvocationContext(
            "SELECT id, name, age, email FROM test_user ORDER BY id DESC"
        );

        PageContext.of(1, 20, false, (Runnable) () ->
            Assertions.assertTrue(processor.shouldIntercept(bundle.context))
        );
    }

    @Test
    void should_not_intercept_when_page_context_is_absent() throws Throwable {
        PaginationQueryProcessor processor = new PaginationQueryProcessor(20000);
        InvocationContextBundle bundle = buildQueryInvocationContext(
            "SELECT id, name, age, email FROM test_user ORDER BY id DESC"
        );
        Assertions.assertFalse(processor.shouldIntercept(bundle.context));
    }

    private static InvocationContextBundle buildQueryInvocationContext(String originalSql) throws Exception {
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
        Mockito.when(ms.getId()).thenReturn("mapper.TestUserMapper.selectAll");

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

    private record InvocationContextBundle(com.g2rain.mybatis.extension.InvocationContext context, BoundSql boundSql) {
    }
}
