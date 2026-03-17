package com.g2rain.mybatis.extension.cache;

import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;

/**
 * SQL 语句缓存接口。
 * <p>
 * 该接口用于缓存解析后的 SQL {@link Statement} 和 {@link Statements} 对象，
 * 避免每次执行时重复解析，提高性能。支持单条 SQL 与多条 SQL 的缓存。
 * <p>
 * 设计目标：
 * <ul>
 *     <li>通过 SQL 字符串作为 key，缓存解析后的 {@link Statement}/{@link Statements}。</li>
 *     <li>可结合 {@link KryoCaffeineSqlStatementCache} 实现高性能的序列化与复制。</li>
 * </ul>
 * <p>
 *
 * @author alpha
 * @since 2026/3/5
 */
public interface SqlStatementCache {

    /**
     * 缓存单条 SQL {@link Statement}。
     *
     * @param sql   原始 SQL 字符串
     * @param value 解析后的 {@link Statement} 对象
     */
    void putStatement(String sql, Statement value);

    /**
     * 缓存多条 SQL {@link Statements}。
     *
     * @param sql   原始 SQL 字符串
     * @param value 解析后的 {@link Statements} 对象
     */
    void putStatements(String sql, Statements value);

    /**
     * 获取缓存的单条 {@link Statement}。
     *
     * @param sql 原始 SQL 字符串
     * @return 如果缓存存在，返回 {@link Statement}；否则返回 {@code null}
     */
    Statement getStatement(String sql);

    /**
     * 获取缓存的多条 {@link Statements}。
     *
     * @param sql 原始 SQL 字符串
     * @return 如果缓存存在，返回 {@link Statements}；否则返回 {@code null}
     */
    Statements getStatements(String sql);
}
