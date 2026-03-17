package com.g2rain.mybatis.extension;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;

import java.sql.SQLException;

/**
 * {@link Executor} 更新处理器抽象基类，预置了对 {@link InterceptPoint#UPDATE} 拦截点的支持。
 * <p>
 * 该类专用于处理 INSERT、UPDATE、DELETE 类型的拦截逻辑：
 * <ul>
 *   <li>自动将 {@link #supports()} 固定为更新类型。</li>
 *   <li>在 {@link #preHandle} 阶段自动解包并回调 {@link #onUpdate}。</li>
 *   <li>子类只需专注实现写入前的增强逻辑。</li>
 * </ul>
 *
 * @author alpha
 * @since 2026/3/4
 */
public abstract class UpdateProcessor implements PluginProcessor {

    /**
     * 返回支持的拦截点类型，固定为 {@link InterceptPoint#UPDATE}。
     *
     * @return {@link InterceptPoint#UPDATE}
     */
    @Override
    public final InterceptPoint supports() {
        return InterceptPoint.UPDATE;
    }

    /**
     * 前置处理委派。
     * <p>
     * 从上下文中提取核心组件并委派给 {@link #onUpdate}。由于更新操作主要关注写入前的参数干预，
     * 故该处理器不提供后置处理（onResult）的回调。
     */
    @Override
    public final void preHandle(InvocationContext context) throws SQLException {
        onUpdate(
                context.getExecutor(),
                context.getMappedStatement(),
                context.getParameter()
        );
    }

    /**
     * 子类实现的方法，用于处理更新逻辑（INSERT、UPDATE、DELETE）。
     *
     * @param executor        当前 {@link Executor} 对象
     * @param mappedStatement 当前 {@link MappedStatement}
     * @param parameter       更新操作参数对象
     * @throws SQLException 更新处理过程中可能抛出的异常
     */
    protected abstract void onUpdate(Executor executor, MappedStatement mappedStatement, Object parameter) throws SQLException;
}