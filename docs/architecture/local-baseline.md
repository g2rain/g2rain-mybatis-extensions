# 本地工程基线

中央 G2rain 工程目录目前没有适合 MyBatis 扩展套件的正式 Profile，也没有本项目的登记项。因此本次初始化采用本地基线 `mybatis-extension-suite 1.0.0-local`，它只是对当前仓库事实的归纳，不代表已发布的中央标准。

## 基线约束

- Maven 多模块聚合工程，Java release 25。
- 根 POM 统一版本和依赖版本，子模块引用 `${revision}`。
- 基础扩展层不得依赖分页模块；分页模块依赖基础扩展层；Starter 依赖分页模块。
- Spring Boot 自动配置使用 `AutoConfiguration.imports`，并允许用户 Bean 覆盖默认处理器或拦截器。
- 发布产物包含源码、Javadoc、签名，并通过 Central Publishing 插件提交。

将来若中央目录新增正式 Profile，应单独评审差异后再迁移，不能仅修改基线名称。
