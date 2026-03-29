package com.g2rain.mybatis.pagination;

/**
 * 分页插件与方言共用的常量（附加参数名、自动 count 的 MappedStatement 后缀等）。
 */
public final class PaginationConstants {

    /**
     * BoundSql 附加参数：第一个分页占位（如 limit / 组合中的首参）。
     */
    public static final String FIRST_PARAM_NAME = "__p_param_1__";

    /**
     * BoundSql 附加参数：第二个分页占位（如 offset）。
     */
    public static final String SECOND_PARAM_NAME = "__p_param_2__";

    /**
     * 自动生成的 count 查询 {@link org.apache.ibatis.mapping.MappedStatement#getId()} 后缀。
     */
    public static final String COUNT_MAPPED_STATEMENT_SUFFIX = "_COUNT";

    private PaginationConstants() {
    }
}
