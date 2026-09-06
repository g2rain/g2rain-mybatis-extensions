package com.g2rain.mybatis.extension;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SQL 助手工具类，提供 MyBatis 核心对象的解析、包装与参数操作功能。
 * <p>
 * 核心功能：
 * <ul>
 *     <li>解除 MyBatis 插件代理（{@link #unwrap(Object)}），获取真实对象。</li>
 *     <li>基于 {@link MetaObject} 封装核心对象，实现统一属性访问（{@link #wrap(Object)}）。</li>
 *     <li>提供 StatementHandler 和 BoundSql 的上下文封装，便于分页、动态 SQL 改写等操作。</li>
 *     <li>批量应用额外参数到 {@link BoundSql}（{@link #applyAdditionalParams(BoundSql, Map)})</li>
 * </ul>
 * <p>
 * 典型使用场景：
 * <ul>
 *     <li>分页插件对 BoundSql SQL 语句改写</li>
 *     <li>SQL 解析或参数增强操作</li>
 * </ul>
 *
 * <b>线程安全：</b>该类方法均为静态方法，内部状态仅局部使用 {@link MetaObject}，可安全在多线程环境下调用。
 *
 * @author alpha
 * @since 2026/3/5
 */
public abstract class SqlHelper {

    /**
     * 默认的 MyBatis 反射工厂，用于创建 {@link MetaObject}。
     */
    public static final DefaultReflectorFactory DEFAULT_REFLECTOR_FACTORY = new DefaultReflectorFactory();

    /**
     * 解除 MyBatis 插件代理，获取真实对象。
     * <p>
     * 如果对象是 {@link Proxy} 包装的插件对象，会循环解包直到得到真实目标。
     *
     * @param target 可能被插件代理包装的对象
     * @param <T>    对象类型
     * @return 真实对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T unwrap(Object target) {
        while (Objects.nonNull(target) && Proxy.isProxyClass(target.getClass())) {
            Object handler = Proxy.getInvocationHandler(target);
            if (!(handler instanceof Plugin h)) {
                break;
            }

            target = wrap(h).getValue("target");
        }

        return (T) target;
    }

    /**
     * 使用 {@link MetaObject} 包装对象，实现统一的属性访问。
     *
     * @param object 待包装对象
     * @return 对象的 {@link MetaObject} 封装
     */
    public static MetaObject wrap(Object object) {
        return MetaObject.forObject(object,
                SystemMetaObject.DEFAULT_OBJECT_FACTORY,
                SystemMetaObject.DEFAULT_OBJECT_WRAPPER_FACTORY,
                DEFAULT_REFLECTOR_FACTORY
        );
    }

    /**
     * 构造 {@link StatementContext}。
     * <p>
     * 自动解析代理并获取 delegate 对象。
     *
     * @param statementHandler MyBatis StatementHandler 对象
     * @return 封装后的 {@link StatementContext}
     */
    public static StatementContext statement(StatementHandler statementHandler) {
        return new StatementContext(wrap(wrap(unwrap(statementHandler)).getValue("delegate")));
    }

    /**
     * 构造 {@link SqlContext}。
     *
     * @param boundSql MyBatis {@link BoundSql} 对象
     * @return 封装后的 {@link SqlContext}
     */
    public static SqlContext sql(BoundSql boundSql) {
        return new SqlContext(boundSql);
    }

    /**
     * 批量将参数设置到 {@link BoundSql} 的 additionalParameters。
     *
     * @param target {@link BoundSql} 对象
     * @param params 额外参数集合
     */
    public static void applyAdditionalParams(BoundSql target, Map<String, Object> params) {
        if (Objects.isNull(params)) {
            return;
        }

        params.forEach(target::setAdditionalParameter);
    }

    /**
     * Statement 上下文封装类，提供对 StatementHandler 内部核心属性的访问。
     */
    public static class StatementContext {
        private final MetaObject metaObject;

        StatementContext(MetaObject metaObject) {
            this.metaObject = metaObject;
        }

        /**
         * 获取 {@link SqlContext} 封装
         */
        public SqlContext sqlContext() {
            return new SqlContext(boundSql());
        }

        /**
         * 获取 MyBatis Configuration
         */
        public Configuration configuration() {
            return get("configuration");
        }

        /**
         * 获取 Executor 对象
         */
        public Executor executor() {
            return get("executor");
        }

        /**
         * 获取当前 MappedStatement
         */
        public MappedStatement mappedStatement() {
            return get("mappedStatement");
        }

        /**
         * 获取 BoundSql 对象
         */
        public BoundSql boundSql() {
            return get("boundSql");
        }

        /**
         * 获取 ParameterHandler 对象
         */
        public ParameterHandler parameterHandler() {
            return get("parameterHandler");
        }

        @SuppressWarnings("unchecked")
        private <T> T get(String property) {
            return (T) metaObject.getValue(property);
        }
    }

    /**
     * BoundSql 上下文封装类，提供 SQL、参数映射和额外参数访问与修改接口。
     */
    public static class SqlContext {
        private final BoundSql delegate;
        private final MetaObject boundSql;

        SqlContext(BoundSql boundSql) {
            this.delegate = boundSql;
            this.boundSql = wrap(boundSql);
        }

        /**
         * 获取 SQL 字符串
         */
        public String sql() {
            return delegate.getSql();
        }

        /**
         * 设置 SQL 字符串
         */
        public void sql(String sql) {
            boundSql.setValue("sql", sql);
        }

        /**
         * 获取不可修改的参数映射列表
         */
        public List<ParameterMapping> parameterMappings() {
            return new ArrayList<>(delegate.getParameterMappings());
        }

        /**
         * 设置参数映射列表（不可修改）
         */
        public void parameterMappings(List<ParameterMapping> parameterMappings) {
            boundSql.setValue("parameterMappings", Collections.unmodifiableList(parameterMappings));
        }

        /**
         * 获取原始参数对象
         */
        public Object parameterObject() {
            return get("parameterObject");
        }

        /**
         * 获取额外参数 Map
         */
        public Map<String, Object> additionalParameters() {
            return get("additionalParameters");
        }

        @SuppressWarnings("unchecked")
        private <T> T get(String property) {
            return (T) boundSql.getValue(property);
        }
    }
}
