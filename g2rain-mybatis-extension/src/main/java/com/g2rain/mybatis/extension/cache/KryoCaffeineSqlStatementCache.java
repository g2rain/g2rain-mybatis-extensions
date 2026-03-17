package com.g2rain.mybatis.extension.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;

import java.util.function.Consumer;

/**
 * 基于 {@link CaffeineSqlStatementCache} 的 Kryo 序列化实现。
 * <p>
 * 使用 {@link KryoFactory} 提供的 Kryo 序列化工具对 {@link Statement} 和 {@link Statements}
 * 对象进行深拷贝和缓存存储。
 * <p>
 * 设计要点：
 * <ul>
 *     <li>继承自 {@link CaffeineSqlStatementCache}，继承了缓存容量、过期策略等基础功能。</li>
 *     <li>通过 Kryo 实现对象序列化/反序列化，保证缓存中对象状态不会被外部修改，从而避免线程安全问题。</li>
 * </ul>
 * <p>
 * 注意事项：
 * <ul>
 *     <li>Kryo 对象序列化线程安全依赖于 {@link KryoFactory#getDefaultFactory()} 的线程安全实现。</li>
 * </ul>
 *
 * @author alpha
 * @since 2026/3/5
 */
public class KryoCaffeineSqlStatementCache extends CaffeineSqlStatementCache {

    /**
     * 默认构造器。
     * <p>
     * 使用 {@link CaffeineSqlStatementCache} 默认缓存配置：
     * 最大 1024 条，访问 2 小时未访问自动清理，值使用软引用。
     */
    public KryoCaffeineSqlStatementCache() {
        super();
    }

    /**
     * 可定制化构造器。
     * <p>
     * 提供 {@link Consumer} 回调，可用于自定义 Caffeine 缓存配置。
     *
     * @param consumer Caffeine 构建器回调，可为 {@code null}
     */
    public KryoCaffeineSqlStatementCache(Consumer<Caffeine<Object, Object>> consumer) {
        super(consumer);
    }

    /**
     * 对象序列化为 {@code byte[]}。
     * <p>
     * 使用 Kryo 进行序列化，保证缓存中存储的是对象副本。
     *
     * @param obj 待序列化对象
     * @return 序列化后的字节数组
     */
    @Override
    public byte[] serialize(Object obj) {
        return KryoFactory.getDefaultFactory().serialize(obj);
    }

    /**
     * {@code byte[]} 反序列化为对象。
     * <p>
     * 使用 Kryo 进行反序列化，从缓存字节恢复对象副本。
     *
     * @param sql   原始 SQL 字符串（用于日志或异常提示）
     * @param bytes 待反序列化字节数组
     * @return 反序列化后的对象
     */
    @Override
    public Object deserialize(String sql, byte[] bytes) {
        return KryoFactory.getDefaultFactory().deserialize(bytes);
    }
}
