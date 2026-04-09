package com.g2rain.mybatis.extension;

import org.apache.ibatis.executor.statement.StatementHandler;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * {@link StatementHandler} 操作处理器抽象基类，预置了对 {@link InterceptPoint#PREPARE} 拦截点的支持。
 * <p>
 * 该类专用于处理 SELECT、INSERT、UPDATE、DELETE 类型的拦截逻辑：
 * <ul>
 *   <li>自动将 {@link #supports()} 固定为更新类型。</li>
 *   <li>在 {@link #preHandle} 阶段自动解包并回调 {@link #onPrepare}。</li>
 *   <li>子类只需专注实现写入前的增强逻辑。</li>
 * </ul>
 *
 * @author alpha
 * @since 2026/3/4
 */
public abstract class PrepareProcessor implements PluginProcessor {

    /**
     * 返回支持的拦截点类型，固定为 {@link InterceptPoint#UPDATE}。
     *
     * @return {@link InterceptPoint#UPDATE}
     */
    @Override
    public final InterceptPoint supports() {
        return InterceptPoint.PREPARE;
    }

    /**
     * 前置处理委派。
     * <p>
     * 从上下文中提取核心组件并委派给 {@link #onPrepare}。由于更新操作主要关注写入前的参数干预，
     * 故该处理器不提供后置处理（onResult）的回调。
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