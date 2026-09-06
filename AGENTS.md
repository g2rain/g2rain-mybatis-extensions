# G2rain MyBatis Extensions 协作约定

## 项目定位

本仓库是 G2rain 的 MyBatis 扩展工具套件，提供可组合的 MyBatis 插件处理器、SQL 解析缓存、分页能力和 Spring Boot 分页 Starter。

## 修改边界

- 先阅读 `docs/index.md`、`docs/project.yaml` 和与任务相关的专题文档。
- 以 `pom.xml`、模块源码、测试和工作流为事实来源；文档与代码不一致时，先核对代码并同步文档。
- 公共 API、自动配置条件、配置前缀、SQL 改写、数据库方言或发布流程发生变化时，必须更新对应文档。
- 不在未验证的情况下声明数据库兼容、性能收益或覆盖率。
- 不提交 `target/`、`.flattened-pom.xml`、IDE 配置、凭据、签名材料或本地 Maven 配置。

## 常用验证

```shell
mvn test
mvn clean verify
```

发布命令具有外部影响，只能在明确授权、凭据齐全并完成发布检查后执行：

```shell
mvn -P release clean deploy
```

## 完成标准

- 变更位于正确模块，公共行为有对应测试。
- `mvn test` 至少通过；发布前执行 `mvn clean verify`。
- API、架构、配置、测试或发布行为变化已同步到 `docs/`。
- 不夹带无关修改，并明确说明未验证项与剩余风险。
