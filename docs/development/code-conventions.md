# 代码约定

- 包名保持在 `com.g2rain.mybatis` 命名空间内。
- 通用拦截机制放在 extension 模块，分页策略放在 pagination 模块，Spring 装配置于 starter 模块。
- 新处理器明确 `order()`、适用拦截点、前置条件、完成与异常清理行为。
- SQL AST 是可变对象；跨调用缓存时必须返回独立副本，不能共享可变实例。
- 修改 `BoundSql` 时同步维护参数映射和附加参数。
- 所有外部输入，尤其排序列与方向，必须在进入 SQL AST 前验证。
- 公共类和复杂 SQL 变换应保留准确的 Javadoc，并以测试证明边界行为。
