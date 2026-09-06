# 本地开发

## 环境

- JDK 25
- Maven 3.9 或与项目插件兼容的更新版本
- Git

## 构建

在仓库根目录执行：

```shell
mvn test
mvn clean verify
```

按模块开发时使用 `-pl` 并带上依赖模块，例如：

```shell
mvn -pl g2rain-mybatis-pagination -am test
```

不要提交 Maven 生成的 `target/` 和 `.flattened-pom.xml`。开发过程中若修改版本，以根 POM 的 `revision` 为唯一代码事实来源，并同步示例文档。
