# g2rain-mybatis-extensions 文档

本仓库提供 G2rain 的 MyBatis 扩展基础设施：以处理器组合 MyBatis 拦截点，缓存 SQL 语法树，并提供分页组件与 Spring Boot 自动配置。

## 导航

- [项目元数据](project.yaml)
- [架构总览](architecture/overview.md)
- [模块说明](architecture/modules.md)
- [依赖关系](architecture/dependencies.md)
- [运行流程](architecture/runtime-flows.md)
- [本地基线](architecture/local-baseline.md)
- [差异与风险](architecture/deviations.md)
- [公共 API](api/public-api.md)
- [本地开发](development/local-development.md)
- [代码约定](development/code-conventions.md)
- [测试策略](development/testing.md)
- [完成标准](development/definition-of-done.md)
- [Git 工作流](development/git-workflow.md)
- [发布说明](operations/publishing.md)
- [安全边界](security/security-boundaries.md)
- [需求记录](requirements/README.md)
- [架构决策](decisions/README.md)
- [社区与贡献](community.md)

## 当前状态

2026-09-06 执行 `mvn test` 成功，4 个 Reactor 项目均构建通过，实际执行 2 个测试。当前版本事实来源是根 `pom.xml` 的 `revision=1.0.4`。
