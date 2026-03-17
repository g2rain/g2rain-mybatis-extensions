package com.g2rain.mybatis.pagination;

import org.apache.ibatis.mapping.BoundSql;

/**
 * 数据库方言接口，用于组装不同数据库的分页 SQL。
 * <p>
 * 各数据库分页语法存在差异，例如 MySQL 使用 "LIMIT offset, count"，PostgreSQL 使用 "LIMIT count OFFSET offset"。
 * 实现该接口可以针对不同数据库生成对应的分页 SQL {@link DialectModel}。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * Dialect dialect = DatabaseType.getDialect(mappedStatement);
 * DialectModel model = dialect.buildPaginationSql(originalSql, offset, limit);
 * String paginationSql = model.getDialectSql();
 * }</pre>
 * </p>
 *
 * @author alpha
 * @since 2026/3/5
 */
public interface Dialect {

    /**
     * 根据原始 SQL 及分页参数生成分页 SQL 模型。
     * <p>
     * 返回的 {@link DialectModel} 中不仅包含分页后的 SQL 字符串，还包含分页参数消费逻辑，
     * 可直接应用到 MyBatis {@link BoundSql} 参数映射中。
     * </p>
     *
     * @param originalSql 原始 SQL 查询语句
     * @param offset      分页偏移量（从第几条记录开始）
     * @param limit       每页记录数限制
     * @return 分页模型 {@link DialectModel}，包含 SQL 与参数映射消费者
     */
    DialectModel buildPaginationSql(String originalSql, long offset, long limit);
}
