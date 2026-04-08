package com.g2rain.mybatis.extension;

import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.Connection;
import java.util.Objects;

/**
 * 封装 MyBatis {@link Invocation} 调用的上下文信息。
 * <p>
 * 该类用于 {@link ExecutorCompositeInterceptor}、{@link StatementHandlerCompositeInterceptor} 中解析调用参数，
 * 并为 {@link PluginProcessor} 提供便捷访问。
 * <p>
 * 根据拦截目标类型，可解析 {@link Executor} 场景下的 {@link MappedStatement}、参数对象、
 * {@link RowBounds}、{@link ResultHandler}、{@link BoundSql}；或 {@link StatementHandler} 场景下的
 * {@link Connection}、事务超时等。
 * 可通过 InvocationContext#getParameter(), InvocationContext#setParameter(Object) 等方法访问或修改。
 *
 * @author alpha
 * @since 2026/3/5
 */
@Getter
public class InvocationContext {
    /**
     * 当前拦截的 {@link Invocation} 对象。
     */
    private final Invocation invocation;

    /**
     * 拦截目标 {@link Executor} 对象。
     */
    private Executor executor;

    /**
     * 当前执行的 {@link MappedStatement}。
     */
    private MappedStatement mappedStatement;

    /**
     * SQL 执行参数对象。
     */
    @Setter
    private Object parameter;

    /**
     * 分页参数。
     */
    @Setter
    private RowBounds rowBounds;

    /**
     * 查询结果处理器 {@link ResultHandler}。
     */
    private ResultHandler<?> resultHandler;

    /**
     * 当前 SQL 对应的 {@link BoundSql} 对象。
     */
    @Setter
    private BoundSql boundSql;

    private StatementHandler statementHandler;

    private Connection connection;

    private Integer transactionTimeout;

    /**
     * 构造器。
     * <p>
     * 根据 {@link Invocation#getTarget()} 类型和参数数量，解析
     * {@link Executor}、{@link MappedStatement}、参数对象、分页信息、结果处理器和 BoundSql；
     * 或 {@link StatementHandler}、{@link Connection}、事务超时等。
     *
     * @param invocation 当前拦截的 {@link Invocation} 对象
     */
    public InvocationContext(Invocation invocation) {
        this.invocation = invocation;
        Object[] args = invocation.getArgs();
        Object target = invocation.getTarget();
        if (target instanceof Executor exec) {
            this.executor = exec;
            this.mappedStatement = (MappedStatement) args[0];
            this.parameter = args[1];
            if (args.length != 2) {
                this.rowBounds = (RowBounds) args[2];
                this.resultHandler = (ResultHandler<?>) args[3];
                if (args.length == 4) {
                    this.boundSql = this.mappedStatement.getBoundSql(this.parameter);
                } else {
                    this.boundSql = (BoundSql) args[5];
                }
            }
        } else if (target instanceof StatementHandler sh) {
            this.statementHandler = sh;
            if (Objects.nonNull(args)) {
                this.connection = (Connection) args[0];
                this.transactionTimeout = (Integer) args[1];
            }
        }
    }
}
