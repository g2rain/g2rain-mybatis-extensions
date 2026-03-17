package com.g2rain.mybatis.extension.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 基于 {@link Caffeine} 的 SQL 语句缓存抽象类。
 * <p>
 * 提供 {@link SqlStatementCache} 接口的基础实现，利用 Caffeine 高性能缓存存储
 * 解析后的 {@link Statement} 或 {@link Statements} 对象。缓存对象通过序列化
 * 存储为 {@code byte[]}，避免直接共享引用导致线程安全问题。
 * <p>
 * 设计要点：
 * <ul>
 *     <li>缓存最大容量默认 1024 条，访问 2 小时未使用则自动清理。</li>
 *     <li>缓存对象使用 {@link #serialize(Object)} / {@link #deserialize(String, byte[])} 管理，
 *         避免对象状态被外部修改。</li>
 *     <li>提供可选的 {@link Consumer<Caffeine>} 回调，允许自定义 Caffeine 构建配置。</li>
 * </ul>
 * <p>
 * 注意事项：
 * <ul>
 *     <li>子类必须实现 {@link #serialize(Object)} 和 {@link #deserialize(String, byte[])} 方法，
 *         定义具体序列化方式（如 Kryo、Java 序列化等）。</li>
 *     <li>高并发场景下，缓存访问是线程安全的，但序列化/反序列化的实现需确保线程安全。</li>
 * </ul>
 *
 * @author alpha
 * @since 2026/3/5
 */
public abstract class CaffeineSqlStatementCache implements SqlStatementCache {

    /**
     * 缓存存储对象，key 为 SQL 字符串，value 为序列化后的 {@code byte[]}。
     * <p>
     * 使用 Caffeine 提供的高性能缓存特性，包括最大容量控制、过期策略和软引用存储。
     */
    protected final Cache<String, byte[]> cache;

    /**
     * 默认构造器。
     * <p>
     * 使用默认配置：最大 1024 条缓存，2 小时未访问自动清理，值使用软引用。
     */
    public CaffeineSqlStatementCache() {
        this(null);
    }

    /**
     * 可定制化构造器。
     * <p>
     * 提供 {@link Consumer} 回调，可用于自定义 Caffeine 缓存配置。
     *
     * @param consumer Caffeine 构建器回调，可为 {@code null}
     */
    public CaffeineSqlStatementCache(Consumer<Caffeine<Object, Object>> consumer) {
        // 默认最多存 1024 条语法树, 默认 2 小时未访问则清理
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                .maximumSize(1024)
                .expireAfterAccess(2, TimeUnit.HOURS)
                .softValues();

        if (Objects.nonNull(consumer)) {
            consumer.accept(caffeine);
        }

        this.cache = caffeine.build();
    }

    /**
     * 缓存单条 {@link Statement}。
     *
     * @param sql   原始 SQL 字符串
     * @param value 解析后的 {@link Statement} 对象
     */
    @Override
    public void putStatement(String sql, Statement value) {
        this.put(sql, value);
    }

    /**
     * 缓存多条 {@link Statements}。
     *
     * @param sql   原始 SQL 字符串
     * @param value 解析后的 {@link Statements} 对象
     */
    @Override
    public void putStatements(String sql, Statements value) {
        this.put(sql, value);
    }

    /**
     * 从缓存中获取 {@link Statement}。
     *
     * @param sql 原始 SQL 字符串
     * @return 如果存在缓存返回 {@link Statement}，否则返回 {@code null}
     */
    @Override
    public Statement getStatement(String sql) {
        return this.get(sql);
    }

    /**
     * 从缓存中获取 {@link Statements}。
     *
     * @param sql 原始 SQL 字符串
     * @return 如果存在缓存返回 {@link Statements}，否则返回 {@code null}
     */
    @Override
    public Statements getStatements(String sql) {
        return this.get(sql);
    }

    /**
     * 通用获取缓存方法。
     * <p>
     * 从缓存中取出 {@code byte[]} 并反序列化为对应对象。若反序列化失败，则自动清理该缓存。
     *
     * @param sql 原始 SQL 字符串
     * @param <T> 返回对象类型
     * @return 反序列化后的对象，若缓存不存在或反序列化失败，返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    protected <T> T get(String sql) {
        byte[] bytes = cache.getIfPresent(sql);
        if (Objects.isNull(bytes)) {
            return null;
        }

        try {
            return (T) deserialize(sql, bytes);
        } catch (Exception e) {
            cache.invalidate(sql);
            return null;
        }
    }

    /**
     * 通用缓存写入方法。
     * <p>
     * 对象在存入缓存前会通过 {@link #serialize(Object)} 转换为 {@code byte[]}。
     *
     * @param sql   原始 SQL 字符串
     * @param value 待缓存对象
     */
    protected void put(String sql, Object value) {
        cache.put(sql, serialize(value));
    }

    /**
     * 对象序列化方法。
     * <p>
     * 子类必须实现此方法，定义对象转 {@code byte[]} 的具体逻辑。
     *
     * @param obj 待序列化对象
     * @return 序列化后的字节数组
     */
    protected abstract byte[] serialize(Object obj);

    /**
     * 对象反序列化方法。
     * <p>
     * 子类必须实现此方法，定义 {@code byte[]} 转对象的逻辑。
     *
     * @param sql   原始 SQL 字符串（用于异常提示或日志）
     * @param bytes 待反序列化字节数组
     * @return 反序列化后的对象
     */
    protected abstract Object deserialize(String sql, byte[] bytes);
}
