# 模块说明

## g2rain-mybatis-extension

基础扩展模块。包含插件处理器抽象、Executor/StatementHandler 组合拦截器、调用上下文、SQL 辅助与解析缓存。它不包含具体业务策略。

## g2rain-mybatis-pagination

分页实现模块。通过 `PageContext` 在回调作用域中绑定分页请求，生成 count SQL，合并排序并按数据库方言重写查询 SQL。支持 `PagingEscape` 跳过框架内部或显式豁免的分页处理。

## g2rain-starter-mybatis-pagination

Spring Boot 自动配置模块。存在 `SqlSessionFactory` 时注册 `PaginationQueryProcessor`；在没有其他 `ExecutorCompositeInterceptor` 时注册分页专用拦截器。配置前缀为 `g2rain.mybatis.pagination`。
