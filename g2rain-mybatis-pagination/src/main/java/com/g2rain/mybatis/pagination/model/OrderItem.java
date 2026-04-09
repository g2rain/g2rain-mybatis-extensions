package com.g2rain.mybatis.pagination.model;

import com.g2rain.mybatis.pagination.PageContext;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 表示单个排序项，用于分页查询的 ORDER BY 参数。
 * <p>
 * 包含列名和排序方向（升序或降序）。
 * 通常由 {@link PageContext} 的排序解析方法生成。
 * </p>
 *
 * @author alpha
 * @since 2026/3/17
 */
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    /**
     * 排序列名，对应数据库表字段。
     */
    private String column;

    /**
     * 排序方向，取值 "asc"（升序）或 "desc"（降序）。
     */
    private String direction;
}
