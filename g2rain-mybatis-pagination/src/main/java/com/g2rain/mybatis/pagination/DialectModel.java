package com.g2rain.mybatis.pagination;

import lombok.Getter;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 数据库方言分页模型，用于封装分页 SQL 及分页参数消费逻辑。
 * <p>
 * 该类主要用于 MyBatis 分页插件中，封装了分页 SQL 字符串及对应的分页参数，
 * 并提供参数消费接口，支持将分页参数注入 {@link ParameterMapping} 或
 * {@link Map} 中，方便 MyBatis 执行。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * DialectModel model = dialect.buildPaginationSql(originalSql, offset, limit);
 * model.consumers(configuration, boundSql.getParameterMappings(), boundSql.getAdditionalParameters());
 * String paginationSql = model.getDialectSql();
 * }</pre>
 * </p>
 *
 * @author alpha
 * @since 2026/3/5
 */
public class DialectModel {

    /**
     * 分页后的 SQL
     */
    @Getter
    private final String dialectSql;

    /**
     * 当前 Configuration，用于构建 ParameterMapping
     */
    private Configuration configuration;

    /**
     * 第一个分页参数值（如 limit 或 offset）
     */
    private final long firstParam;

    /**
     * 第二个分页参数值（如 offset 或 limit）
     */
    private final long secondParam;

    /**
     * 消费 List<ParameterMapping> 的第一个值逻辑
     */
    private Consumer<List<ParameterMapping>> firstParamConsumer = i -> {
    };

    /**
     * 消费 Map<String,Object> 的第一个值逻辑
     */
    private Consumer<Map<String, Object>> firstParamMapConsumer = i -> {
    };

    /**
     * 消费 List<ParameterMapping> 的第二个值逻辑
     */
    private Consumer<List<ParameterMapping>> secondParamConsumer = i -> {
    };

    /**
     * 消费 Map<String,Object> 的第二个值逻辑
     */
    private Consumer<Map<String, Object>> secondParamMapConsumer = i -> {
    };

    /**
     * 构造函数，只传入 SQL
     *
     * @param dialectSql 分页 SQL
     */
    public DialectModel(String dialectSql) {
        this(dialectSql, 0, 0);
    }

    /**
     * 构造函数，传入 SQL 和第一个分页参数
     *
     * @param dialectSql 分页 SQL
     * @param firstParam 第一个分页参数
     */
    public DialectModel(String dialectSql, long firstParam) {
        this(dialectSql, firstParam, 0);
    }

    /**
     * 构造函数，传入 SQL 和两个分页参数
     *
     * @param dialectSql  分页 SQL
     * @param firstParam  第一个分页参数
     * @param secondParam 第二个分页参数
     */
    public DialectModel(String dialectSql, long firstParam, long secondParam) {
        this.dialectSql = dialectSql;
        this.firstParam = firstParam;
        this.secondParam = secondParam;
    }

    /**
     * 设置分页参数消费逻辑
     * <p>
     * 将第一个或第二个参数注入 {@link ParameterMapping} 和 {@link Map} 中。
     * </p>
     *
     * @param isFirstParam 是否为第一个参数
     * @return 当前 DialectModel
     */
    public DialectModel setConsumer(boolean isFirstParam) {
        if (isFirstParam) {
            this.firstParamConsumer = i -> i.add(new ParameterMapping.Builder(configuration, PaginationConstants.FIRST_PARAM_NAME, long.class).build());
            this.firstParamMapConsumer = i -> i.put(PaginationConstants.FIRST_PARAM_NAME, firstParam);
        } else {
            this.secondParamConsumer = i -> i.add(new ParameterMapping.Builder(configuration, PaginationConstants.SECOND_PARAM_NAME, long.class).build());
            this.secondParamMapConsumer = i -> i.put(PaginationConstants.SECOND_PARAM_NAME, secondParam);
        }

        return this;
    }

    /**
     * 设置两个分页参数都消费
     *
     * @return 当前 DialectModel
     */
    public DialectModel setConsumerChain() {
        return setConsumer(true).setConsumer(false);
    }

    /**
     * 执行分页参数消费，将内部参数注入到 MyBatis 的 ParameterMapping 与附加参数 Map。
     *
     * @param configuration        当前 MyBatis Configuration
     * @param parameterMappings    ParameterMapping 列表
     * @param additionalParameters BoundSql 附加参数 Map
     */
    public void consumers(Configuration configuration, List<ParameterMapping> parameterMappings, Map<String, Object> additionalParameters) {
        this.configuration = configuration;
        this.firstParamConsumer.accept(parameterMappings);
        this.secondParamConsumer.accept(parameterMappings);
        this.firstParamMapConsumer.accept(additionalParameters);
        this.secondParamMapConsumer.accept(additionalParameters);
    }
}
