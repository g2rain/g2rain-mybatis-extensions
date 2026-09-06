# 公共 API

## 扩展基础层

- `PluginProcessor`：插件处理器基础契约。
- `QueryProcessor`、`UpdateProcessor`、`PrepareProcessor`：按 MyBatis 拦截点扩展行为。
- `CompositeInterceptor`、`ExecutorCompositeInterceptor`、`StatementHandlerCompositeInterceptor`：组合并调度处理器。
- `InvocationContext`、`InterceptPoint`：描述调用与拦截点。
- `SqlParserDelegate`、`SqlHelper`：SQL 解析和 `BoundSql` 操作入口。
- `SqlStatementCache` 及 Caffeine/Kryo 实现：SQL AST 缓存扩展点。

## 分页层

- `PageContext`：在回调作用域内发起分页；优先使用 `of(...)` 系列方法。
- `Page<E>`：携带页码、页大小、总数、排序和查询结果。
- `OrderItem`：排序列与方向。
- `PagingEscape`：在指定作用域跳过分页处理。
- `Dialect`、`DialectModel`、`DatabaseType`：分页方言扩展与数据库识别。
- `PaginationQueryProcessor`：查询分页和 count 处理器。

## Starter 配置

依赖 `g2rain-starter-mybatis-pagination` 后自动装配。当前公开配置只有：

```properties
g2rain.mybatis.pagination.order=20000
```

可通过提供自定义 `PaginationQueryProcessor` 或 `ExecutorCompositeInterceptor` Bean 覆盖默认装配。扩展公共 API 时应保持二进制兼容，破坏性变化必须进入明确的主版本升级。
