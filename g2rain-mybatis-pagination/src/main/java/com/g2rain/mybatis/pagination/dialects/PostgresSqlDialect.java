package com.g2rain.mybatis.pagination.dialects;

import com.g2rain.mybatis.pagination.Dialect;
import com.g2rain.mybatis.pagination.DialectModel;

/**
 * PostgreSQL 数据库分页方言实现类。
 * <p>
 * 负责根据 PostgreSQL 的语法特性组装分页 SQL 语句。
 * PostgreSQL 分页语法为：
 * <pre>
 *   SELECT ... FROM ... [WHERE ...] ORDER BY ... LIMIT ? OFFSET ?;
 * </pre>
 * 当 offset 为 0 时，可优化为仅使用 LIMIT ?。
 * <p>
 * <b>注意：</b>此类仅生成分页 SQL，不执行查询。
 * <p>
 * 示例用法：
 * <pre>
 * Dialect dialect = new PostgresSqlDialect();
 * DialectModel model = dialect.buildPaginationSql("SELECT * FROM user", 10, 20);
 * </pre>
 *
 * @author alpha
 * @since 2026/3/5
 */
public class PostgresSqlDialect implements Dialect {

    /**
     * 构建 PostgreSQL 分页 SQL 语句。
     *
     * @param originalSql 原始 SQL 语句
     * @param offset      分页偏移量（起始行）
     * @param limit       分页大小（每页条数）
     * @return {@link DialectModel} 分页模型对象，包含分页 SQL 与参数消费逻辑
     */
    @Override
    public DialectModel buildPaginationSql(String originalSql, long offset, long limit) {
        // 拼接基本的 LIMIT 占位符
        StringBuilder sql = new StringBuilder(originalSql).append(" LIMIT ").append("?");

        // 当 offset 为 0 时，仅需单个参数
        if (offset == 0L) {
            return new DialectModel(sql.toString(), limit).setConsumer(true);
        }

        // offset 非零时，PostgreSQL 语法为 LIMIT ? OFFSET ?
        sql.append(" OFFSET ").append("?");

        // 设置两个参数的消费逻辑链
        return new DialectModel(sql.toString(), limit, offset).setConsumerChain();
    }
}
