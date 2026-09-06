# 依赖关系

```text
g2rain-starter-mybatis-pagination
    -> g2rain-mybatis-pagination
        -> g2rain-mybatis-extension
```

核心第三方依赖由根 POM 管理：MyBatis 3.5.19、JSqlParser 4.7、Caffeine 3.2.3、Kryo 5.6.2。Starter 导入 Spring Boot 4.0.3 依赖管理并使用 MyBatis Spring Boot Starter 4.0.1。

升级 JSqlParser 需要重点回归 AST 复制、count SQL 优化、排序合并和两种方言；升级 MyBatis 需要回归拦截方法签名、`BoundSql` 参数与缓存键；升级 Spring Boot/MyBatis Starter 需要回归自动配置顺序和条件装配。
