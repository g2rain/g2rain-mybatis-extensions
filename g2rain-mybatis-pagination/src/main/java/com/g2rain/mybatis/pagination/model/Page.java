package com.g2rain.mybatis.pagination.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 分页结果封装类。
 * <p>
 * 用于承载分页查询结果及分页信息，继承自 {@link ArrayList} 方便直接存放查询结果列表。
 * 支持分页参数计算、总数记录和总页数计算。
 * <p>
 * 示例用法：
 * <pre>{@code
 * Page<User> page = new Page<>(1, 10, true, "id desc");
 * // 执行查询后，将查询结果 addAll 到 page 中
 * page.setTotal(totalCount);
 * }</pre>
 *
 * @param <E> 泛型，表示结果集合的元素类型
 * @author alpha
 * @since 2026/3/6
 */
@Getter
public class Page<E> extends ArrayList<E> {

    /**
     * 起始行号（用于 SQL LIMIT/OFFSET）
     */
    private final long startRow;

    /**
     * 当前页码，从 1 开始
     */
    private final int pageNum;

    /**
     * 每页记录数
     */
    private final int pageSize;

    /**
     * 是否执行 count 查询
     */
    private final boolean count;

    /**
     * 排序字段，可为 null
     */
    private final List<OrderItem> orderBy;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 总页数
     */
    private int pages;

    /**
     * 构造分页对象。
     *
     * @param pageNum  当前页码（从 1 开始）
     * @param pageSize 每页记录数
     * @param count    是否进行 count 查询
     * @param orderBy  排序字段，支持 "id desc,name asc" 格式，可为 null
     */
    public Page(int pageNum, int pageSize, boolean count, List<OrderItem> orderBy) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.count = count;
        this.orderBy = orderBy;
        this.startRow = pageNum > 1 ? (long) (pageNum - 1) * pageSize : 0L;
    }

    /**
     * 设置总记录数，并计算总页数。
     * <p>
     * 当 total = -1 时，pages 默认 1，表示不进行 count 查询。
     * 当 pageSize &lt;= 0 时，总页数为 0。
     *
     * @param total 总记录数
     */
    public void setTotal(long total) {
        this.total = total;
        if (total == -1) {
            this.pages = 1;
            return;
        }

        // 向上取整计算总页数
        if (this.pageSize > 0) {
            this.pages = (int) (total + this.pageSize - 1) / this.pageSize;
        } else {
            this.pages = 0;
        }
    }

    public List<E> getResult() {
        return this;
    }
}
