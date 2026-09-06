# 发布说明

## 快照

`develop` 分支推送或手动触发 `snapshot.yml` 会执行跳过测试的部署。进入该流程前应在本地或前置检查中完成测试。快照凭据使用 Central Portal 的 `central-portal-snapshots` server 配置。

## 正式发布

推送 `v*.*.*` 标签或手动触发 `release.yml`，使用 JDK 25 执行：

```shell
mvn -B -P release clean deploy
```

release Profile 会生成源码和 Javadoc、进行 GPG 签名，并由 Central Publishing 插件自动发布。需要 Central Portal Token、GPG 私钥与口令。

## 发布检查

- 根 POM、README、CHANGELOG 和 Git 标签版本一致。
- `mvn clean verify` 已通过，测试数量符合预期。
- 发布凭据只存在于受保护的 CI Secret 中。
- 确认目标是快照或正式仓库，检查产物坐标与签名。

正式部署会产生外部、难以撤回的结果，禁止仅为验证而执行。
