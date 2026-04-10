package com.g2rain.mybatis.extension;

import java.sql.SQLException;

/**
 * 插件处理器接口，用于在 {@link ExecutorCompositeInterceptor} 中按序扩展 MyBatis 执行逻辑。
 * <p>
 * 该接口定义了一个完整的插件生命周期：
 * <ol>
 *   <li><b>静态过滤</b> ({@link #supports})：确定作用点。</li>
 *   <li><b>动态准入</b> ({@link #shouldIntercept})：判定执行条件。</li>
 *   <li><b>前置增强</b> ({@link #preHandle})：改写 SQL 或绑定参数。</li>
 *   <li><b>后置处理</b> ({@link #postHandle})：回填结果或包装对象。</li>
 *   <li><b>资源清理</b> ({@link #afterCompletion})：防止参数污染。</li>
 * </ol>
 * <p>
 * 实现类通过 {@link #order()} 定义在插件链中的执行优先级。
 *
 * @author alpha
 * @since 2026/3/4
 */
public interface PluginProcessor {

    /**
     * 匹配插件支持的拦截点类型（如查询、更新等）。
     * <p>
     * 只有当拦截点匹配时，后续的判断和执行逻辑才会触发。
     *
     * @return {@link InterceptPoint} 拦截点枚举
     */
    InterceptPoint supports();

    /**
     * 运行时准入判断，决定当前调用上下文是否触发插件逻辑。
     * <p>
     * 场景示例：分页插件在此处判断当前 SQL 环境是否携带分页参数，若无则返回 {@code false} 跳过后续处理。
     *
     * @param context 当前调用上下文
     * @return {@code true} 表示执行插件逻辑，{@code false} 表示跳过本次拦截
     * @throws SQLException 执行判断过程中可能抛出的数据库异常
     */
    default boolean shouldIntercept(InvocationContext context) throws SQLException {
        return true;
    }

    /**
     * 前置处理回调（SQL 执行前）。
     * <p>
     * 触发时机：在 MyBatis 构建出 BoundSql 后，真实 SQL 执行前。
     * 主要职责：执行参数校验、自动 Count 查询、物理分页 SQL 改写及动态参数绑定。
     * 约束：在此处对 BoundSql 或入参的修改将直接作用于后续的数据库交互。
     *
     * @param context 当前调用上下文，包含 Executor、MappedStatement、BoundSql 等核心对象
     * @throws SQLException 执行过程中可能抛出的异常
     */
    default void preHandle(InvocationContext context) throws SQLException {
    }

    /**
     * 后置处理回调（SQL 执行后）。
     * <p>
     * 触发时机：在 Executor 成功执行 SQL 并获得返回结果后。
     * 主要职责：处理查询结果的回填（如将 List 注入 Page 对象）、结果集包装或插件特有的资源处理。
     * 约束：此处的处理结果将决定最终返回给 Mapper 调用者的对象内容。
     *
     * @param context 当前调用上下文
     * @param result  MyBatis 执行后返回的原始结果对象
     * @throws SQLException 执行过程中可能抛出的异常
     */
    default void postHandle(InvocationContext context, Object result) throws SQLException {
    }

    /**
     * 最终收尾回调。
     * <p>
     * 触发时机：无论执行成功还是发生异常，在整个拦截器逻辑结束时触发。
     * 主要职责：清理 ScopedValue、ThreadLocal 等上下文资源，防止分页参数污染或内存泄漏。
     */
    default void afterCompletion() {

    }

    /**
     * 插件在拦截器链中的执行顺序。
     *
     * @return 顺序值，值越小优先级越高（通常分页插件优先级较高）
     */
    int order();
}
