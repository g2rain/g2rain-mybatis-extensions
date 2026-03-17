package com.g2rain.mybatis.pagination.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 分页 Starter 配置属性。
 * <p>
 * 配置前缀：{@code g2rain.mybatis.pagination}。
 * </p>
 *
 * @author alpha
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "g2rain.mybatis.pagination")
public class PaginationProperties {
    /**
     * 分页处理器在插件链中的执行顺序，数值越小越先执行，默认 {@code 20000}。
     */
    private int order = 20000;

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
