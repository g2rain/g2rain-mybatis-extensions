package com.g2rain.mybatis.extension;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.Objects;

/**
 * 拦截点枚举，用于标识 MyBatis {@link Executor} 的调用类型。
 * <p>
 * 枚举值用于 {@link ExecutorCompositeInterceptor} 中区分不同的拦截逻辑，
 * 并封装各类型调用的默认处理方式。
 * <p>
 * 枚举值说明：
 * <ul>
 *     <li>{@link #QUERY} - 查询操作，重写 {@link #handle(InvocationContext)} 来执行查询逻辑。</li>
 *     <li>{@link #UPDATE} - 更新操作，调用 {@link InvocationContext} 的 proceed 方法。</li>
 *     <li>{@link #UNKNOWN} - 未知操作类型，调用 {@link InvocationContext} 的 proceed 方法。</li>
 * </ul>
 *
 * @author alpha
 * @since 2026/3/4
 */
public enum InterceptPoint {

    /**
     * Executor 查询操作拦截点。
     * <p>
     * 重写 {@link #handle(InvocationContext)} 方法，调用
     * {@link Executor#query(MappedStatement, Object, RowBounds, ResultHandler, CacheKey, BoundSql)}
     * 执行查询操作。
     */
    QUERY {
        @Override
        public Object handle(InvocationContext invocationContext) throws Throwable {
            Executor executor = invocationContext.getExecutor();
            MappedStatement mappedStatement = invocationContext.getMappedStatement();
            Object parameter = invocationContext.getParameter();
            RowBounds rowBounds = invocationContext.getRowBounds();
            ResultHandler<?> resultHandler = invocationContext.getResultHandler();
            BoundSql boundSql = invocationContext.getBoundSql();
            CacheKey cacheKey = executor.createCacheKey(mappedStatement, parameter, rowBounds, boundSql);
            return executor.query(mappedStatement, parameter, rowBounds, resultHandler, cacheKey, boundSql);
        }
    },

    /**
     * Executor 更新操作拦截点（INSERT、UPDATE、DELETE）。
     * <p>
     * 调用 {@link InvocationContext} 的 {@code proceed()} 方法执行更新操作。
     */
    UPDATE,

    /**
     * StatementHandler 更新操作拦截点（SELECT、INSERT、UPDATE、DELETE）。
     * <p>
     * 调用 {@link InvocationContext} 的 {@code proceed()} 方法执行更新操作。
     */
    PREPARE,

    /**
     * 未知操作类型拦截点。
     * <p>
     * 调用 {@link InvocationContext} 的 {@code proceed()} 方法执行原始调用。
     */
    UNKNOWN;

    /**
     * 默认处理方法。
     * <p>
     * 对于未重写 {@code handle} 的枚举值（如 {@link #UPDATE}、{@link #UNKNOWN}），
     * 调用该方法将执行原始 {@link Invocation} 的 {@code proceed()}。
     *
     * @param invocationContext 当前拦截的调用上下文
     * @return 执行结果对象
     * @throws Throwable 执行原始方法时抛出的异常
     */
    public Object handle(InvocationContext invocationContext) throws Throwable {
        return invocationContext.getInvocation().proceed();
    }

    /**
     * 根据 {@link InvocationContext} 中的参数识别拦截点。
     *
     * @param invocationContext 当前拦截的调用上下文
     * @return 对应的 {@link InterceptPoint} 枚举值
     */
    public static InterceptPoint identify(InvocationContext invocationContext) {
        Object[] args = invocationContext.getInvocation().getArgs();
        if (Objects.nonNull(invocationContext.getExecutor())) {
            if (args.length == 2) {
                return UPDATE;
            }

            if (args.length == 4 || args.length == 6) {
                return QUERY;
            }
        } else if (Objects.nonNull(invocationContext.getStatementHandler())) {
            if (Objects.nonNull(args)) {
                return PREPARE;
            }
        }

        return UNKNOWN;
    }
}
