package com.g2rain.mybatis.extension;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;

import java.sql.Connection;

/**
 * {@link StatementHandler} 复合拦截器，基于 {@link PluginProcessor} 插件链扩展 MyBatis 执行逻辑。
 * <p>
 * 该拦截器接管 {@link StatementHandler#prepare} 阶段（各类 SQL 语句准备）
 *
 * @author alpha
 * @since 2026/3/3
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}),
})
public class StatementHandlerCompositeInterceptor extends CompositeInterceptor {
    /**
     * 拦截 StatementHandler 方法。
     * <p>
     * 创建 {@link InvocationContext}，识别拦截点类型，
     * 并依次执行匹配的插件处理器，最后调用对应 {@link InterceptPoint#handle(InvocationContext)}。
     *
     * @param invocation 当前方法调用信息
     * @return 执行结果对象
     * @throws Throwable 执行方法或插件处理时抛出的异常
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        return executeChain(invocation);
    }

    /**
     * 包装目标对象。
     * <p>
     * 当目标对象为 {@link StatementHandler} 时，使用 {@link Plugin#wrap} 进行代理包装。
     *
     * @param target 目标对象
     * @return 代理对象或原对象
     */
    @Override
    public Object plugin(Object target) {
        if (target instanceof StatementHandler) {
            return Plugin.wrap(target, this);
        }

        return target;
    }
}
