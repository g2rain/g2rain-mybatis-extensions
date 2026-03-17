package com.g2rain.mybatis.pagination;

import com.g2rain.mybatis.pagination.model.OrderItem;
import com.g2rain.mybatis.pagination.model.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * 分页上下文管理器。
 * <p>
 * 通过 {@link ScopedValue} 在同一线程中维护分页对象，支持在回调执行期间临时注入分页信息。
 * 主要用于分页拦截器获取当前线程的分页参数和结果。
 * <p>
 * 使用示例：
 * <pre>{@code
 * Page<User> page = PageContext.of(1, 10, () -> {
 *     userMapper.selectList(...); // 查询操作，分页信息在 ScopedValue 中生效
 * });
 * long total = page.getTotal();
 * List<User> results = page;
 * }</pre>
 * <p>
 * 注意：该类为最终类，禁止继承。
 *
 * @author alpha
 * @since 2026/3/6
 */
public final class PageContext {

    /**
     * 用于拆分排序字符串的逗号分隔符，允许列名前后有空白。
     * 例如 "name asc, age desc" 会拆分成 ["name asc", "age desc"]。
     */
    private static final Pattern COMMA_PATTERN = Pattern.compile("\\s*,\\s*");

    /**
     * 黑名单正则，用于检测 SQL 排序字段中的不安全字符。
     * 匹配空白符、分号、单行注释符号 (--, #) 以及多行注释开头 (/*)，忽略大小写。
     */
    private static final Pattern BLACKLIST = Pattern.compile("\\s|;|--|#|/\\*", Pattern.CASE_INSENSITIVE);

    /**
     * 分页作用域变量，使用 {@link ScopedValue} 保证线程安全和嵌套回调隔离。
     * 存储 {@link AtomicReference} 包裹的 {@link Page} 对象。
     */
    private static final ScopedValue<AtomicReference<Page<?>>> PAGE_SCOPE = ScopedValue.newInstance();

    /**
     * 私有构造方法，禁止实例化。
     */
    private PageContext() {
    }

    /**
     * 创建分页上下文，并执行回调。
     * <p>
     * 默认开启 count 查询，排序字段为空。
     *
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param callback 回调函数，分页信息在回调执行期间生效
     * @param <T>      查询结果类型
     * @return 分页对象
     */
    public static <T> Page<T> of(int pageNum, int pageSize, Runnable callback) {
        return of(pageNum, pageSize, true, null, callback);
    }

    /**
     * 创建分页上下文，并执行回调。
     *
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param count    是否启用 count 查询
     * @param callback 回调函数
     * @param <T>      查询结果类型
     * @return 分页对象
     */
    public static <T> Page<T> of(int pageNum, int pageSize, boolean count, Runnable callback) {
        return of(pageNum, pageSize, count, null, callback);
    }

    /**
     * 创建分页上下文，并执行回调。
     *
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param orderBy  排序字段，可为 null
     * @param callback 回调函数
     * @param <T>      查询结果类型
     * @return 分页对象
     */
    public static <T> Page<T> of(int pageNum, int pageSize, String orderBy, Runnable callback) {
        return of(pageNum, pageSize, true, parseOrderBy(orderBy), callback);
    }

    /**
     * 创建分页上下文，并执行回调。
     *
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param orderBy  排序字段，可为 null
     * @param callback 回调函数
     * @param <T>      查询结果类型
     * @return 分页对象
     */
    public static <T> Page<T> of(int pageNum, int pageSize, List<OrderItem> orderBy, Runnable callback) {
        return of(pageNum, pageSize, true, orderBy, callback);
    }

    /**
     * 核心方法，创建分页上下文并注入到 {@link ScopedValue} 中执行回调。
     *
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param count    是否启用 count 查询
     * @param orderBy  排序字段，可为 null
     * @param callback 回调函数
     * @param <T>      查询结果类型
     * @return 分页对象
     */
    public static <T> Page<T> of(int pageNum, int pageSize, boolean count, List<OrderItem> orderBy, Runnable callback) {
        Page<T> page = new Page<>(pageNum, pageSize, count, filterSafeOrderBy(orderBy));
        AtomicReference<Page<?>> holder = new AtomicReference<>(page);
        ScopedValue.where(PAGE_SCOPE, holder).run(callback);
        return page;
    }

    /**
     * 获取当前线程分页上下文。
     *
     * @return 当前线程的分页对象，如果未绑定则返回 null
     */
    public static Page<?> peek() {
        if (!PAGE_SCOPE.isBound()) {
            return null;
        }

        var holder = PAGE_SCOPE.get();
        if (Objects.isNull(holder)) {
            return null;
        }

        return holder.get();
    }

    /**
     * 清理当前线程分页上下文。
     * <p>
     * 回收 {@link AtomicReference} 引用，避免内存泄漏。
     */
    public static void clear() {
        if (!PAGE_SCOPE.isBound()) {
            return;
        }

        var holder = PAGE_SCOPE.get();
        if (Objects.isNull(holder)) {
            return;
        }

        holder.set(null);
    }

    /**
     * 将排序字符串解析为 {@link OrderItem} 列表。
     * <p>
     * 排序字符串可以包含多个列，使用逗号分隔，每个列可指定排序方向（asc/desc）。
     * 对不安全的列名（包含空白符、分号、注释符等）会自动过滤。
     *
     * @param orderBy 排序字符串，例如 "name asc, age desc"
     * @return 安全的 {@link OrderItem} 列表，如果没有有效列则返回 {@code null}
     */
    private static List<OrderItem> parseOrderBy(String orderBy) {
        if (Objects.isNull(orderBy) || orderBy.isBlank()) {
            return null;
        }

        String[] parts = COMMA_PATTERN.split(orderBy.trim());
        List<OrderItem> items = new ArrayList<>(parts.length);
        for (String s : parts) {
            String trimmed = s.trim();
            int space = trimmed.lastIndexOf(' ');

            String column;
            String direction;
            if (space > 0) {
                column = trimmed.substring(0, space).trim();
                direction = trimmed.substring(space + 1).trim();
            } else {
                column = trimmed;
                direction = "asc";
            }

            // 如果列名为空或不安全，直接跳过
            if (column.isEmpty()) {
                continue;
            }

            if (isValidSortOrder(direction)) {
                OrderItem item = new OrderItem();
                item.setColumn(column.trim());
                item.setDirection(direction.trim().toLowerCase());
                items.add(item);
            }
        }
        return items;
    }

    /**
     * 过滤不安全的排序字段。
     * <p>
     * 对于 {@link OrderItem} 列表，检查列名是否合法，并只保留排序方向为 asc/desc 的项。
     *
     * @param orderBy 排序字段列表
     * @return 过滤后的安全排序列表，如果无有效字段返回 {@code null}
     */
    private static List<OrderItem> filterSafeOrderBy(List<OrderItem> orderBy) {
        if (Objects.isNull(orderBy) || orderBy.isEmpty()) {
            return null;
        }

        return orderBy.stream().filter(item -> {
            if (isUnsafe(item.getColumn().trim())) {
                return false;
            }

            return isValidSortOrder(item.getDirection().trim());
        }).toList();
    }

    /**
     * 检查输入字符串是否存在安全风险。
     * <p>
     * 判断规则为是否包含空白符、分号、注释符等 SQL 注入敏感字符。
     *
     * @param input 待检查字符串
     * @return {@code true} 表示存在不安全字符，{@code false} 表示安全
     */
    private static boolean isUnsafe(String input) {
        if (Objects.isNull(input) || input.isBlank()) {
            return true;
        }

        return BLACKLIST.matcher(input).find();
    }

    /**
     * 检查排序方向字符串是否为有效的排序顺序（ASC 或 DESC）。
     *
     * @param direction 排序方向字符串，不区分大小写。
     * @return 如果方向是 "asc" 或 "desc"（不区分大小写）则返回 true，否则返回 false。
     */
    private static boolean isValidSortOrder(String direction) {
        return "asc".equalsIgnoreCase(direction) || "desc".equalsIgnoreCase(direction);
    }
}

