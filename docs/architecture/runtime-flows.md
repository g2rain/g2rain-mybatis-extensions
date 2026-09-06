# 运行流程

## 分页查询

1. 调用方使用 `PageContext.of(...)` 创建分页作用域并执行 Mapper 查询。
2. `PaginationQueryProcessor` 检测到作用域且未进入 `PagingEscape`。
3. 需要总数时，从原查询生成 count SQL 并执行。
4. 合并经过安全过滤的排序字段。
5. 根据 JDBC URL 识别数据库，使用 MySQL 或 PostgreSQL 方言生成分页 SQL 和参数。
6. 查询结果写入当前 `Page`，处理完成后清理作用域引用。

## SQL 解析缓存

`SqlParserDelegate` 解析 SQL。缓存实现以原始 SQL 为键，通过 Kryo 将 AST 序列化为字节数组后存入 Caffeine；读取时反序列化为独立对象，降低可变 AST 在并发请求间共享的风险。默认缓存上限 1024，访问后 2 小时过期，并使用软引用值。

## 自动配置

Starter 在 `SqlSessionFactory` 存在时生效。默认处理器顺序为 `20000`，可通过 `g2rain.mybatis.pagination.order` 调整。若应用已提供同类型处理器或组合拦截器，默认 Bean 会回退。
