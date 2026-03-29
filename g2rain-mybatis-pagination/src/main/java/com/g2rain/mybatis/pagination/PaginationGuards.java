package com.g2rain.mybatis.pagination;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;

import java.util.Locale;

/**
 * 分页幂等守卫：避免对自动 count 语句或已改写 SQL 重复注入分页 / count。
 */
public final class PaginationGuards {

    private PaginationGuards() {
    }

    /**
     * 是否为插件自动注册的 count 查询 MappedStatement（id 以 {@link PaginationConstants#COUNT_MAPPED_STATEMENT_SUFFIX} 结尾）。
     */
    public static boolean isCountMappedStatement(MappedStatement mappedStatement) {
        return mappedStatement != null
            && mappedStatement.getId() != null
            && mappedStatement.getId().endsWith(PaginationConstants.COUNT_MAPPED_STATEMENT_SUFFIX);
    }

    /**
     * 是否已应用分页改写（附加分页参数或 SQL 已含 {@code LIMIT ?}）。
     */
    public static boolean isPaginationAlreadyApplied(BoundSql boundSql) {
        if (boundSql == null) {
            return false;
        }
        if (boundSql.hasAdditionalParameter(PaginationConstants.FIRST_PARAM_NAME)
            || boundSql.hasAdditionalParameter(PaginationConstants.SECOND_PARAM_NAME)) {
            return true;
        }

        String sql = boundSql.getSql();
        if (sql == null || sql.isBlank()) {
            return false;
        }
        String lower = sql.toLowerCase(Locale.ROOT);
        return lower.contains(" limit ?");
    }
}
