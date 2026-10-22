# 架构总览

项目将 MyBatis 拦截逻辑拆成“组合拦截器 + 有序处理器”。组合拦截器接入 MyBatis，处理器在查询、更新、语句准备等拦截点执行具体逻辑。分页是在该扩展模型之上的一个实现，Starter 负责在 Spring Boot 中完成缺省装配。

```text
业务 Mapper
    -> MyBatis Executor / StatementHandler
        -> CompositeInterceptor
            -> PluginProcessor（按 order 执行）
                -> SQL 解析与缓存
                -> count SQL / 排序 / 方言分页 SQL
    <- 查询结果写入 PageContext
```

SQL 解析由 JSqlParser 完成，解析结果可通过 Caffeine 和 Kryo 序列化缓存，避免共享可变 AST。分页当前内置 MySQL 与 PostgreSQL 方言。
