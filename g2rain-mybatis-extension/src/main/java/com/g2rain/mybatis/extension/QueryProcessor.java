package com.g2rain.mybatis.extension;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.SQLException;

/**
 * {@link Executor} 查询处理器抽象基类，预置了对 {@link InterceptPoint#QUERY} 拦截点的支持。
 * <p>
 * 该类通过对 {@link InvocationContext} 的自动解包，将拦截逻辑拆分为两个核心阶段：
 * <ul>
 *   <li><b>前置干预</b> ({@link #onQuery})：负责 SQL 改写、物理分页参数绑定等。</li>
 *   <li><b>后置处理</b> ({@link #onResult})：负责结果回填（如 Page 对象赋值）或结果集包装。</li>
 * </ul>
 *
 * @author alpha
 * @since 2026/3/4
 */
public abstract class QueryProcessor implements PluginProcessor {

    /**
     * 返回支持的拦截点类型，固定为 {@link InterceptPoint#QUERY}。
     *
     * @return {@link InterceptPoint#QUERY}
     */
    @Override
    public final InterceptPoint supports() {
        return InterceptPoint.QUERY;
    }

    /**
     * 前置处理委派。
     * <p>
     * 自动从上下文中提取 MyBatis 查询核心组件并委派给 {@link #onQuery}。
     *
     * @param context 当前调用上下文
     * @throws SQLException 执行查询处理时可能抛出的异常
     */
    @Override
    public final void preHandle(InvocationContext context) throws SQLException {
        onQuery(
                context.getExecutor(),
                context.getMappedStatement(),
                context.getParameter(),
                context.getRowBounds(),
                context.getResultHandler(),
                context.getBoundSql()
        );
    }

    /**
     * 子类实现：处理查询前的 SQL 改写与参数准备。
     *
     * @param executor      MyBatis 执行器
     * @param ms            映射语句对象
     * @param parameter     原始查询参数
     * @param rowBounds     逻辑分页参数
     * @param resultHandler 结果处理器
     * @param boundSql      包含 SQL 语句及参数映射的 BoundSql 对象
     * @throws SQLException 逻辑处理中可能抛出的数据库异常
     */
    protected abstract void onQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler<?> resultHandler, BoundSql boundSql) throws SQLException;

    /**
     * 后置处理委派。
     * <p>
     * 自动从上下文中提取核心参数并委派给 {@link #onResult}。
     *
     * @param context 当前调用上下文
     * @param result  MyBatis 执行后返回的原始结果对象
     * @throws SQLException 执行过程中可能抛出的异常
     */
    public final void postHandle(InvocationContext context, Object result) throws SQLException {
        onResult(
                context.getMappedStatement(),
                context.getParameter(),
                context.getRowBounds(),
                result
        );
    }

    /**
     * 子类实现：处理查询后的结果回填与数据包装。
     * <p>
     * 常用场景：将 {@code result} 列表注入到 {@code parameter} 中的分页对象内。
     *
     * @param ms        映射语句对象，用于识别具体业务方法
     * @param parameter 原始查询参数，通常分页插件会从中提取 Page 对象进行结果回填
     * @param rowBounds 逻辑分页信息
     * @param result    MyBatis 执行后返回的原始结果集（通常为 List）
     * @throws SQLException 处理结果过程中可能抛出的异常
     */
    protected void onResult(MappedStatement ms, Object parameter, RowBounds rowBounds, Object result) throws SQLException {

    }
}