package com.g2rain.mybatis.pagination;

import com.g2rain.mybatis.pagination.dialects.MySqlDialect;
import com.g2rain.mybatis.pagination.dialects.PostgresSqlDialect;
import lombok.Getter;
import org.apache.ibatis.mapping.MappedStatement;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 数据库类型枚举。
 * <p>
 * 提供 MySQL、PostgreSQL 以及 UNKNOWN 类型，并关联对应的 {@link Dialect} 实现。
 * 主要用于根据 {@link MappedStatement} 中的数据源自动识别数据库类型并生成对应分页 SQL。
 * <p>
 * 缓存策略：
 * <ul>
 *     <li>TYPE_CACHE: 将 {@link DataSource} 与 {@link DatabaseType} 对应，避免每次通过 JDBC URL 解析。</li>
 *     <li>DIALECT_ENUM_MAP: 将 {@link DatabaseType} 与 {@link Dialect} 对应，避免重复创建方言对象。</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 * Dialect dialect = DatabaseType.getDialect(mappedStatement);
 * String paginationSql = dialect.buildPaginationSql(originalSql, offset, limit).getDialectSql();
 * }</pre>
 *
 * @author alpha
 * @since 2026/3/5
 */
@Getter
public enum DatabaseType {

    /**
     * MySQL 数据库类型
     */
    MYSQL("mysql", ":mysql:", MySqlDialect::new, "MySql 数据库"),

    /**
     * PostgreSQL 数据库类型
     */
    POSTGRES_SQL("postgresSql", ":postgresql:", PostgresSqlDialect::new, "postgresSql 数据库"),

    /**
     * 未知数据库类型
     */
    UNKNOWN("unknown", "", () -> null, "未知数据库");

    /**
     * 枚举缓存数组，用于遍历匹配
     */
    private static final DatabaseType[] VALUES = values();

    /**
     * 数据源到数据库类型的缓存
     */
    private static final Map<DataSource, DatabaseType> TYPE_CACHE = new ConcurrentHashMap<>();

    /**
     * 数据库类型到方言对象的缓存
     */
    private static final Map<DatabaseType, Dialect> DIALECT_ENUM_MAP = new EnumMap<>(DatabaseType.class);

    /**
     * 数据库标识字符串，例如 mysql、PostgreSQL
     */
    private final String database;

    /**
     * JDBC URL 匹配模式
     */
    private final String pattern;

    /**
     * 数据库方言构造器
     */
    private final Supplier<Dialect> dialectSupplier;

    /**
     * 数据库描述信息
     */
    private final String description;

    /**
     * 编译后的正则模式（仅在 pattern 包含转义符时使用）
     */
    private final Pattern compiledPattern;

    DatabaseType(String database, String pattern, Supplier<Dialect> dialectSupplier, String description) {
        this.database = database;
        this.pattern = pattern;
        this.dialectSupplier = dialectSupplier;
        this.description = description;
        this.compiledPattern = pattern.contains("\\") ? Pattern.compile(pattern) : null;
    }

    /**
     * 根据 {@link MappedStatement} 获取对应数据库的 {@link Dialect} 对象。
     * <p>
     * 首先从 TYPE_CACHE 获取，如果缓存不存在则通过 JDBC URL 解析数据库类型。
     * 如果数据库类型为 UNKNOWN，则抛出 {@link UnsupportedOperationException}。
     *
     * @param ms MyBatis MappedStatement
     * @return 对应数据库的方言对象
     */
    public static Dialect getDialect(MappedStatement ms) {
        DataSource ds = ms.getConfiguration().getEnvironment().getDataSource();
        DatabaseType databaseType = TYPE_CACHE.computeIfAbsent(ds, key -> {
            try (Connection conn = key.getConnection()) {
                return fromJdbcUrl(conn.getMetaData().getURL());
            } catch (SQLException e) {
                return UNKNOWN;
            }
        });

        if (databaseType == DatabaseType.UNKNOWN) {
            throw new UnsupportedOperationException(String.format(
                    "%s database not supported.",
                    databaseType.getDatabase()
            ));
        }

        return DIALECT_ENUM_MAP.computeIfAbsent(databaseType, key ->
                key.getDialectSupplier().get()
        );
    }

    /**
     * 从 JDBC URL 中解析数据库类型。
     *
     * @param jdbcUrl JDBC 连接 URL
     * @return 匹配到的 {@link DatabaseType}，未匹配返回 {@link #UNKNOWN}
     */
    private static DatabaseType fromJdbcUrl(String jdbcUrl) {
        if (Objects.isNull(jdbcUrl) || jdbcUrl.isBlank()) {
            return UNKNOWN;
        }

        String lowerUrl = jdbcUrl.toLowerCase();
        return Arrays.stream(VALUES)
                .filter(o -> o.matches(lowerUrl))
                .findFirst()
                .orElse(UNKNOWN);
    }

    /**
     * 判断 URL 是否匹配当前数据库类型。
     *
     * @param url JDBC URL
     * @return true 如果匹配，false 否则
     */
    private boolean matches(String url) {
        if (this == UNKNOWN || pattern.isEmpty()) {
            return false;
        }

        if (Objects.isNull(compiledPattern)) {
            return url.contains(pattern);
        }

        return compiledPattern.matcher(url).find();
    }
}
