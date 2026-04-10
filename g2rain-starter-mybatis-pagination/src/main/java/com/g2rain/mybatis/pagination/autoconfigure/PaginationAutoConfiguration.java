package com.g2rain.mybatis.pagination.autoconfigure;

import com.g2rain.mybatis.extension.ExecutorCompositeInterceptor;
import com.g2rain.mybatis.pagination.PaginationQueryProcessor;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 分页 Starter 自动配置。
 * <p>
 * 在存在 MyBatis {@link SqlSessionFactory} 且启用分页时，注册 {@link PaginationQueryProcessor}
 * 与 {@link ExecutorCompositeInterceptor}，并通过 {@link ConfigurationCustomizer}
 * 将拦截器加入 MyBatis {@link Configuration}，实现基于
 * {@link com.g2rain.mybatis.pagination.PageContext} 的自动分页与 count 查询。
 * </p>
 * <p>
 * 与 {@code g2rain-starter-mybatis-extensions} 同时存在时，若已存在 {@link ExecutorCompositeInterceptor}，
 * 本 Starter 不再注册拦截器与 Customizer，由扩展 Starter 统一装配分页与数据隔离。
 * </p>
 *
 * @author alpha
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnBean(SqlSessionFactory.class)
@ConditionalOnClass(SqlSessionFactory.class)
@AutoConfigureAfter(MybatisAutoConfiguration.class)
@EnableConfigurationProperties(PaginationProperties.class)
public class PaginationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PaginationQueryProcessor.class)
    public PaginationQueryProcessor paginationQueryProcessor(PaginationProperties properties) {
        return new PaginationQueryProcessor(properties.getOrder());
    }

    @Bean(name = "paginationExecutorCompositeInterceptor")
    @ConditionalOnMissingBean(ExecutorCompositeInterceptor.class)
    public ExecutorCompositeInterceptor paginationExecutorCompositeInterceptor(PaginationQueryProcessor paginationQueryProcessor) {
        ExecutorCompositeInterceptor interceptor = new ExecutorCompositeInterceptor();
        interceptor.addPluginProcessor(paginationQueryProcessor);
        return interceptor;
    }
}
