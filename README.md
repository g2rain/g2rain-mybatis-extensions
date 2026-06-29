# g2rain-mybatis-extensions

[![Maven Central](https://img.shields.io/maven-central/v/com.g2rain/g2rain-mybatis-extension.svg)](https://search.maven.org/artifact/com.g2rain/g2rain-mybatis-extension)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java Version](https://img.shields.io/badge/Java-25+-orange.svg)](https://openjdk.java.net/)
[![Build Status](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven&logoColor=white)](https://github.com/g2rain/g2rain-mybatis-extensions)

## 1. 徽标与状态标识
- 当前版本通过 `Maven Central` 发布
- 当前运行时要求 `Java 25+`
- 当前构建方式以 `Maven` 为准
- 当前开源许可证为 `Apache 2.0`

## 2. 项目简介
`g2rain-mybatis-extensions` 是 G2rain 平台围绕 MyBatis 执行链扩展沉淀的基础仓库，用于统一提供复合拦截器模型、插件处理器机制、分页上下文、SQL 解析与改写、数据库方言分页以及 Spring Boot 自动接入能力。它解决的不是单一分页工具问题，而是为后续数据隔离、权限 SQL 增强等能力提供可复用的底层扩展骨架。

## 3. 平台定位

`g2rain-mybatis-extensions` 位于 G2rain 平台公共基础能力层，是平台数据访问工程化能力的一部分。  
它主要服务于需要统一分页能力、统一 MyBatis 拦截链能力的 Java 后端项目。  
它不承载具体业务接口，而是作为底层执行链引擎，为更上层 Starter、权限扩展与业务服务提供支撑。

## 4. 核心能力

- 复合拦截器与插件处理器模型：统一抽象 MyBatis `QUERY`、`UPDATE`、`PREPARE` 执行链
- 分页上下文管理：通过 `PageContext + ScopedValue` 在回调作用域内管理分页状态
- Count SQL 优化：基于 `JSqlParser` 自动优化或降级生成 count 查询
- 数据库方言分页：按数据库类型生成 MySQL、PostgreSQL 等分页 SQL
- 安全排序处理：对 `orderBy` 进行过滤，降低排序注入风险
- Spring Boot 自动接入：通过 Starter 自动注册分页处理器与复合拦截器

## 5. 技术栈

- 语言与运行时：`Java 25`
- 构建工具：`Maven`
- 打包方式：根工程 `pom`，子模块 `jar`
- 核心依赖：`MyBatis`、`JSqlParser`、`Caffeine`、`Kryo`
- Starter 依赖：`Spring Boot 4.0.5`、`mybatis-spring-boot-starter 4.0.1`
- 质量工具：`Checkstyle`、`PMD`、`SpotBugs`、`JaCoCo`
- 发布目标：`Maven Central / Sonatype Central Portal`

## 6. 快速开始
### 环境要求

- `JDK 25`
- `Maven 3.9+`

### Maven 依赖

#### 方式一：直接引入分页 Starter

```xml
<dependency>
    <groupId>com.g2rain</groupId>
    <artifactId>g2rain-starter-mybatis-pagination</artifactId>
    <version>1.0.4</version>
</dependency>
```

#### 方式二：按需引入底层模块

```xml
<dependency>
    <groupId>com.g2rain</groupId>
    <artifactId>g2rain-mybatis-extension</artifactId>
    <version>1.0.4</version>
</dependency>

<dependency>
    <groupId>com.g2rain</groupId>
    <artifactId>g2rain-mybatis-pagination</artifactId>
    <version>1.0.4</version>
</dependency>
```

### Starter 配置

```yaml
g2rain:
  mybatis:
    pagination:
      order: 20000
```

### 基本使用

```java
Page<User> page = PageContext.of(1, 10, "id desc", () -> {
    userMapper.selectList(query);
});

long total = page.getTotal();
int pages = page.getPages();
List<User> records = page.getResult();
```

### 非 Starter 场景手动接入

```java
Configuration configuration = sqlSessionFactory.getConfiguration();

ExecutorCompositeInterceptor interceptor = new ExecutorCompositeInterceptor();
interceptor.addPluginProcessor(new PaginationQueryProcessor(20000));

configuration.addInterceptor(interceptor);
```

### 本地构建

```bash
mvn clean install
```

### 本地测试

```bash
mvn test
```

### 发布说明

- 正式版通过 Git Tag 触发 `release.yml`
- `develop` 分支上的 `-SNAPSHOT` 版本可通过 `snapshot.yml` 发布
- Release 流程包含源码包、Javadoc 包和 GPG 签名

## 7. 项目结构

```text
g2rain-mybatis-extensions/
├── g2rain-mybatis-extension/
├── g2rain-mybatis-pagination/
├── g2rain-starter-mybatis-pagination/
├── .github/workflows/
│   ├── release.yml
│   └── snapshot.yml
└── pom.xml
```

### 核心能力结构说明

#### 1. `g2rain-mybatis-extension`：MyBatis 扩展底层引擎
- 解决问题：避免把分页、数据隔离、审计等增强逻辑散落在多个独立拦截器中，难以统一排序和清理
- 核心逻辑：
  - `CompositeInterceptor` 统一组织 `preHandle -> MyBatis 执行 -> postHandle -> afterCompletion`
  - `PluginProcessor` 用于抽象不同拦截点的扩展处理器
  - `InvocationContext` 统一封装 `MappedStatement`、`BoundSql`、`RowBounds`、`ResultHandler`
- 典型接入方式：扩展新的查询/更新/prepare 增强时，优先实现 `PluginProcessor` 子类，而不是再新增一套零散拦截器

典型写法：
```java
public class CustomQueryProcessor extends QueryProcessor {
    @Override
    protected void onQuery(Executor executor, MappedStatement ms, Object parameter,
                           RowBounds rowBounds, ResultHandler<?> resultHandler, BoundSql boundSql) {
        // 在这里改写 SQL 或补充参数
    }

    @Override
    public int order() {
        return 30000;
    }
}
```

#### 2. `g2rain-mybatis-extension.cache`：SQL 解析缓存与深拷贝支持
- 解决问题：复杂 SQL 经常重复解析，直接复用解析结果又可能产生共享对象污染
- 核心逻辑：
  - 通过 `Caffeine` 缓存语句解析结果
  - 通过 `Kryo` 做深拷贝，降低复用解析树时的副作用
- 典型使用场景：后续如果新增更复杂的 SQL 改写能力，可以沿用这套缓存思路，而不是每次都重新解析

#### 3. `g2rain-mybatis-pagination`：分页上下文、Count 优化与方言分页
- 解决问题：让业务查询无需手写 `limit/offset` 和 count SQL，同时保持分页参数在一次查询作用域内安全可控
- 核心逻辑：
  - `PageContext.of(...)` 通过 `ScopedValue` 绑定当前分页对象
  - `PaginationQueryProcessor` 仅在存在 `PageContext` 时才触发
  - 对简单 SQL 直接优化为 `COUNT(*)`，对 `distinct`、`group by`、`union` 等复杂 SQL 自动降级为子查询 count
  - 合并排序字段时进行安全过滤，最终由 `Dialect` 生成数据库方言分页 SQL
- 典型用法：把 Mapper 查询包裹在 `PageContext.of(...)` 内，不改动原 Mapper 签名

典型写法：
```java
Page<Order> page = PageContext.of(1, 20, false, List.of(
    new OrderItem("create_time", "desc")
), () -> {
    orderMapper.selectList(query);
});
```

#### 4. `g2rain-starter-mybatis-pagination`：Spring Boot 自动装配入口
- 解决问题：减少业务服务手动注册拦截器和处理器的样板代码
- 核心逻辑：
  - `PaginationAutoConfiguration` 在存在 `SqlSessionFactory` 时自动生效
  - 自动注册 `PaginationQueryProcessor`
  - 自动注册 `paginationExecutorCompositeInterceptor`
  - 通过 `PaginationProperties` 暴露插件顺序配置
- 典型接入方式：Spring Boot + MyBatis 项目优先使用这个 Starter，而不是手动 wiring

典型写法：
```yaml
g2rain:
  mybatis:
    pagination:
      order: 20000
```

### 接入建议与边界
- 如果目标是统一分页接入，优先引入 `g2rain-starter-mybatis-pagination`
- 如果目标是扩展更复杂的数据隔离或权限 SQL 改写，建议基于 `PluginProcessor` 机制继续扩展
- 本仓库负责底层扩展引擎，不直接替代更上层的完整平台数据权限方案

## 8. 常用命令

```bash
mvn compile
mvn test
mvn checkstyle:check pmd:check spotbugs:check
mvn jacoco:report
mvn package
```

## 9. 质量与测试
- 当前扫描到主源码文件 `29` 个，测试文件 `1` 个
- 当前回归测试集中在 `PaginationQueryProcessorIdempotentTest`
- 已启用 `maven-enforcer-plugin`、`maven-checkstyle-plugin`、`maven-pmd-plugin`、`spotbugs-maven-plugin` 和 `jacoco-maven-plugin`
- 当前仍建议后续继续补充复杂 SQL、不同方言和 `PagingEscape` 相关测试

## 10. 相关仓库

- `g2rain-common`
- `g2rain-spring-boot-starter`
- `g2rain-iam`
- `g2rain-department`
- `g2rain-infra`

## 11. 使用建议

- 适合作为平台内 MyBatis 扩展能力的统一底座
- Spring Boot 场景建议优先通过 Starter 接入
- 扩展新的 SQL 改写能力时，优先复用复合拦截器与插件处理器模型
- 不建议把该仓库简单理解为“分页工具库”

## 12. 贡献指南

欢迎通过文档改进、Issue 反馈、测试补充、代码优化、功能增强等形式参与贡献。  
建议流程：
1. Fork 本仓库
2. 创建特性分支
3. 提交修改
4. 推送分支
5. 提交 Pull Request

提交前请尽量确保：
- 遵循现有技术栈与代码规范
- 更新相关文档
- 补充必要测试

## 13. 许可证

本项目基于 [Apache 2.0许可证](LICENSE) 开源。

## 14. 联系我们

- **站点**: https://www.g2rain.com/
- **Issues**: [GitHub Issues](https://github.com/g2rain/g2rain/issues)
- **讨论**: [GitHub Discussions](https://github.com/g2rain/g2rain/discussions)
- **邮箱**: g2rain_developer@163.com

## 15. 致谢

感谢所有为这个项目做出贡献的开发者们。  
如果这个项目对您有帮助，欢迎 Star 支持。
