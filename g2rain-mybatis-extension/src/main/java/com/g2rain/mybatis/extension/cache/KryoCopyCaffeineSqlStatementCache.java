package com.g2rain.mybatis.extension.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 基于 Caffeine + Kryo copy 的 SQL 语句缓存实现。
 * <p>
 * 与 {@link KryoCaffeineSqlStatementCache} 的字节数组序列化方案不同，
 * 该实现直接缓存 AST 对象，并在读写两侧通过 Kryo copy 做副本隔离：
 * <ul>
 *     <li>写入缓存：缓存对象副本，避免外部后续修改影响缓存。</li>
 *     <li>读取缓存：返回对象副本，避免调用方修改污染缓存。</li>
 * </ul>
 * 这样可显著降低缓存命中时的反序列化成本。
 *
 * <p>支持通过系统属性调优缓存参数：</p>
 * <ul>
 *     <li>{@code g2rain.mybatis.sql.cache.max-size}（默认 1024）</li>
 *     <li>{@code g2rain.mybatis.sql.cache.expire-after-access-seconds}（默认 7200）</li>
 *     <li>{@code g2rain.mybatis.sql.cache.soft-values}（默认 false）</li>
 * </ul>
 *
 * @author alpha
 * @since 2026/3/18
 */
public class KryoCopyCaffeineSqlStatementCache implements SqlStatementCache {

    private static final long DEFAULT_MAX_SIZE = 1024L;
    private static final long DEFAULT_EXPIRE_AFTER_ACCESS_SECONDS = 2 * 60 * 60L;
    private static final boolean DEFAULT_SOFT_VALUES = false;

    private static final String PROP_MAX_SIZE = "g2rain.mybatis.sql.cache.max-size";
    private static final String PROP_EXPIRE_SECONDS = "g2rain.mybatis.sql.cache.expire-after-access-seconds";
    private static final String PROP_SOFT_VALUES = "g2rain.mybatis.sql.cache.soft-values";

    private final Cache<String, Object> cache;
    private final KryoFactory kryoFactory = KryoFactory.getDefaultFactory();

    public KryoCopyCaffeineSqlStatementCache() {
        this(null);
    }

    public KryoCopyCaffeineSqlStatementCache(Consumer<Caffeine<Object, Object>> consumer) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
            .maximumSize(longProperty(PROP_MAX_SIZE, DEFAULT_MAX_SIZE))
            .expireAfterAccess(longProperty(PROP_EXPIRE_SECONDS, DEFAULT_EXPIRE_AFTER_ACCESS_SECONDS), TimeUnit.SECONDS);

        if (booleanProperty(PROP_SOFT_VALUES, DEFAULT_SOFT_VALUES)) {
            builder.softValues();
        }

        if (Objects.nonNull(consumer)) {
            consumer.accept(builder);
        }

        this.cache = builder.build();
    }

    @Override
    public void putStatement(String sql, Statement value) {
        put(sql, value);
    }

    @Override
    public void putStatements(String sql, Statements value) {
        put(sql, value);
    }

    @Override
    public Statement getStatement(String sql) {
        return get(sql, Statement.class);
    }

    @Override
    public Statements getStatements(String sql) {
        return get(sql, Statements.class);
    }

    private void put(String sql, Object value) {
        if (sql == null || value == null) {
            return;
        }

        try {
            cache.put(sql, kryoFactory.copy(value));
        } catch (RuntimeException ex) {
            // copy 失败时回退到序列化路径，保证兼容性
            cache.put(sql, kryoFactory.deserialize(kryoFactory.serialize(value)));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T get(String sql, Class<T> type) {
        Object cached = cache.getIfPresent(sql);
        if (cached == null) {
            return null;
        }

        try {
            return (T) kryoFactory.copy(cached);
        } catch (RuntimeException ex) {
            try {
                return (T) kryoFactory.deserialize(kryoFactory.serialize(cached));
            } catch (RuntimeException fallbackEx) {
                cache.invalidate(sql);
                return null;
            }
        }
    }

    private static long longProperty(String key, long defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static boolean booleanProperty(String key, boolean defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return Boolean.parseBoolean(value);
    }
}
