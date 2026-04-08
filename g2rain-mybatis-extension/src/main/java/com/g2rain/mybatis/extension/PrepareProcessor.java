package com.g2rain.mybatis.extension;

import org.apache.ibatis.executor.statement.StatementHandler;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * {@link StatementHandler} 操作处理器抽象基类，预置了对 {@link InterceptPoint#PREPARE} 拦截点的支持。
 * <p>
 * 该类专用于处理 SELECT、INSERT、UPDATE、DELETE 在 {@link StatementHandler#prepare} 阶段的拦截逻辑：
 * <ul>
 *   <li>自动将 {@link #supports()} 固定为 {@link InterceptPoint#PREPARE}。</li>
 *   <li>在 {@link #preHandle} 阶段自动解包并回调 {@link #onPrepare}。</li>
 *   <li>子类专注实现语句准备前的 SQL 或参数增强。</li>
 * </ul>
 *
 * @author alpha
 * @since 2026/3/4
 */
public abstract class PrepareProcessor implements PluginProcessor {

    /**
     * 返回支持的拦截点类型，固定为 {@link InterceptPoint#PREPARE}。
     *
     * @return {@link InterceptPoint#PREPARE}
     */
    @Override
    public final InterceptPoint supports() {
        return InterceptPoint.PREPARE;
    }

    /**
     * 前置处理委派。
     * <p>
     * 从上下文中提取核心组件并委派给 {@link #onPrepare}。该处理器仅提供前置回调，
     * 不提供与 {@link QueryProcessor} 类似的后置结果处理。
     */
    @Override
    public final void preHandle(InvocationContext context) throws SQLException {
        onPrepare(
                context.getStatementHandler(),
                context.getConnection(),
                context.getTransactionTimeout()
        );
    }

    protected abstract void onPrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) throws SQLException;
}