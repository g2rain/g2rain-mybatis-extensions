# g2rain-mybatis-extensions

[![Maven Central](https://img.shields.io/maven-central/v/com.g2rain/g2rain-mybatis-extension.svg)](https://search.maven.org/artifact/com.g2rain/g2rain-mybatis-extension)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java Version](https://img.shields.io/badge/Java-25+-orange.svg)](https://openjdk.java.net/)
[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)](https://github.com/g2rain/g2rain-mybatis-extensions)

> 一个基于 MyBatis 拦截器的分页与扩展框架，面向 JDK 25 设计，利用虚拟线程与 `ScopedValue` 提供高性能、低侵入的分页能力，并抽象统一的插件链机制，便于在 MyBatis 执行链上扩展（数据隔离、多租户、审计等）。

## 📋 项目简介

g2rain-mybatis-extensions 提供 MyBatis 拦截器扩展基础设施与分页插件实现，并提供 Spring Boot Starter 开箱即用接入能力。

## ✨ 核心特性

### 📄 模块概览

- **g2rain-mybatis-extension**：MyBatis 拦截器扩展核心
  - 提供 `CompositeInterceptor` / `ExecutorCompositeInterceptor` 等基础能力
  - 基于 `PluginProcessor` 插件链扩展 MyBatis `Executor` 执行逻辑
- **g2rain-mybatis-pagination**：分页插件实现
  - 基于 `Page` / `PageContext` 和 `PaginationQueryProcessor` 实现 SQL 自动分页与 count 统计
  - 使用 JDK 25 的 `ScopedValue` 管理分页上下文，兼容虚拟线程
- **g2rain-starter-mybatis-pagination**：Spring Boot Starter
  - 自动装配分页插件与拦截器，无需手动注册
  - 通过配置属性控制分页插件在插件链中的执行顺序

### 🔄 分页与SQL处理

- **基于 MyBatis 拦截器的 SQL 分页**
  - 自动构建分页 SQL 与 count SQL，无需在 Mapper 中手写 `LIMIT/OFFSET`
  - 针对复杂 SQL（`distinct`、`group by`、union 等）自动降级为子查询 count

### 🧵 上下文与虚拟线程友好

- **分页上下文与结果封装**：`Page<E>` 继承自 `ArrayList<E>`，同时包含 `pageNum`、`pageSize`、`total`、`pages`、`orderBy` 等字段；`PageContext` 通过 `ScopedValue` 管理分页上下文
- **JDK 25 / 虚拟线程友好**
  - 使用 `ScopedValue` 而不是 `ThreadLocal` 保存分页上下文，在虚拟线程场景下性能更好、泄漏风险更低
  - 适配 `Thread.ofVirtual().start(...)` 等虚拟线程用法

### 🧩 插件链与扩展能力

- **插件链与扩展能力**
  - `CompositeInterceptor` + `PluginProcessor` 的设计，可以将分页当作一个插件挂在执行链上
  - 便于后续扩展数据隔离、多租户、审计日志等其他拦截逻辑
- **Spring Boot Starter 集成**
  - 开箱即用集成 `mybatis-spring-boot-starter`
  - 只需引入依赖并简单配置，即可启用分页功能

### 🧠 核心设计说明

- **分页结果封装（`Page<E>`）**
  - 继承 `ArrayList<E>`，天然兼容 MyBatis List 返回值
  - 额外提供 `startRow`、`pageNum`、`pageSize`、`total`、`pages`、`orderBy` 等字段
- **分页上下文（`PageContext`）**
  - 提供多种 `of(...)` 重载：可设置是否 count、排序字段等
  - 内部通过 `ScopedValue<AtomicReference<Page<?>>>` 存储当前分页对象
  - 提供 `peek()` 获取当前上下文、`clear()` 清理上下文
- **分页处理器（`PaginationQueryProcessor`）**
  - 在 `shouldIntercept` 中：检测 `PageContext` 是否存在，决定是否启用分页逻辑
  - 构建并缓存 count `MappedStatement`，使用 JSqlParser 优化 count SQL（去除可安全移除的 `order by`、处理 `distinct`/`group by` 等）
  - 在 `onQuery` 中：按数据库方言生成分页 SQL 并注入参数
  - 在 `onResult` 中：将查询结果填充回 `Page` 对象
- **拦截器扩展框架（`CompositeInterceptor` / `ExecutorCompositeInterceptor`）**
  - 封装 MyBatis `Interceptor` 的链式执行流程（pre / post / afterCompletion）
  - 使用 `PluginProcessor#order()` 排序，保证插件执行顺序可控
  - `afterCompletion` 采用逆序执行，确保资源按开启顺序反向释放

### 🧭 配置与扩展建议

- **控制执行顺序**
  - 多个插件并存时，可通过 `PaginationProperties.order` 或 `PaginationQueryProcessor` 构造参数控制分页插件在链中的位置
- **组合其他拦截逻辑**
  - 建议将数据隔离、多租户、审计等逻辑也封装为 `PluginProcessor`，统一挂载在 `ExecutorCompositeInterceptor` 上，避免多拦截器互相干扰
- **复杂 SQL 性能调优**
  - 默认会对包含 `distinct`、`group by` 或参数化 select 项的 SQL 走子查询 `SELECT COUNT(*) FROM (..)`，如有极端大表/复杂视图场景，可在业务侧拆分查询或手动控制是否执行 count

## 🚀 快速开始

### 环境要求

- **JDK**：25 及以上（`pom.xml` 中通过 `maven-enforcer-plugin` 强制 `[25,)`）
- **MyBatis**：3.5.19
- **Spring Boot**：4.x（示例依赖于 `spring-boot-autoconfigure:4.0.3`、`mybatis-spring-boot-starter:4.0.1`）

### Maven 依赖

#### 父 POM（可选）

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.g2rain</groupId>
            <artifactId>g2rain-mybatis-extension</artifactId>
            <version>1.0.1</version>
        </dependency>
        <dependency>
            <groupId>com.g2rain</groupId>
            <artifactId>g2rain-mybatis-pagination</artifactId>
            <version>1.0.1</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

#### Spring Boot Starter（推荐）

```xml
<dependency>
    <groupId>com.g2rain</groupId>
    <artifactId>g2rain-starter-mybatis-pagination</artifactId>
    <version>1.0.1</version>
</dependency>
```

#### 非 Starter 场景按需引入

```xml
<dependency>
    <groupId>com.g2rain</groupId>
    <artifactId>g2rain-mybatis-extension</artifactId>
    <version>1.0.1</version>
</dependency>
<dependency>
    <groupId>com.g2rain</groupId>
    <artifactId>g2rain-mybatis-pagination</artifactId>
    <version>1.0.1</version>
</dependency>
```

### Gradle 依赖

```groovy
dependencies {
    implementation "com.g2rain:g2rain-starter-mybatis-pagination:1.0.1"

    // 或按需引入（非 Starter 场景）
    // implementation "com.g2rain:g2rain-mybatis-extension:1.0.1"
    // implementation "com.g2rain:g2rain-mybatis-pagination:1.0.1"
}
```

### 基本使用

#### Spring Boot 场景（推荐）

引入 `g2rain-starter-mybatis-pagination` 后，`PaginationAutoConfiguration` 会自动完成以下工作：

- **注册分页处理器**：`PaginationQueryProcessor`
- **注册拦截器**：`ExecutorCompositeInterceptor`（bean 名称为 `paginationExecutorCompositeInterceptor`）

只需在配置文件中按需调整顺序属性（可选）：

```yaml
g2rain:
  mybatis:
    pagination:
      # 分页处理器在插件链中的执行顺序，数字越小越靠前，默认 20000
      order: 20000
```

在业务代码中使用 `PageContext` 发起分页查询：

```java
import com.g2rain.mybatis.pagination.model.Page;
import com.g2rain.mybatis.pagination.model.OrderItem;
import com.g2rain.mybatis.pagination.PageContext;
import java.util.List;

Page<User> page = PageContext.of(1, 10, "id desc", () -> {
    // 在回调中执行 MyBatis Mapper 查询
    userMapper.selectList(...);
});

long total = page.getTotal();
int pages = page.getPages();
List<User> records = page.getResult();
```

其他常用重载：

```java
// 1) 最简用法：默认 count=true，orderBy=null
Page<User> page1 = PageContext.of(1, 10, () -> {
    userMapper.selectList(...);
});

// 2) 可控制是否执行 count（不查总数时建议 count=false）
Page<User> page2 = PageContext.of(1, 10, false, () -> {
    userMapper.selectList(...);
});

// 3) 直接传 List<OrderItem> 指定排序（推荐：可避免手写字符串解析）
List<OrderItem> orderBy = List.of(
    new OrderItem("id", "desc"),
    new OrderItem("create_time", "asc")
);
Page<User> page3 = PageContext.of(1, 10, orderBy, () -> {
    userMapper.selectList(...);
});

// 4) 同时控制 count + List<OrderItem> 排序
Page<User> page4 = PageContext.of(1, 10, false, orderBy, () -> {
    userMapper.selectList(...);
});
```

说明：

- **回调开始前**，`PageContext` 会将 `Page` 放入 `ScopedValue`
- **分页拦截器** 通过 `PageContext.peek()` 判断是否需要分页，并自动重写 SQL
- **回调结束后**，`Page` 中已经填充好结果集与总记录数

#### 原生 MyBatis 场景

如未使用 Spring Boot Starter，可手动注册拦截器与插件：

```java
import com.g2rain.mybatis.extension.ExecutorCompositeInterceptor;
import com.g2rain.mybatis.pagination.PaginationQueryProcessor;
import org.apache.ibatis.session.Configuration;

Configuration configuration = sqlSessionFactory.getConfiguration();

ExecutorCompositeInterceptor interceptor = new ExecutorCompositeInterceptor();
interceptor.addPluginProcessor(new PaginationQueryProcessor(20000));

configuration.addInterceptor(interceptor);
```

之后分页使用方式与 Spring Boot 场景相同，仍然通过 `PageContext.of(...)` 包裹 Mapper 调用。

#### 与虚拟线程配合使用

本项目使用 `ScopedValue` 管理分页上下文，因此在虚拟线程下同样安全可用。例如：

```java
Thread.ofVirtual().start(() -> {
    Page<User> page = PageContext.of(1, 20, () -> {
        userMapper.selectList(...);
    });
    // 这里可以直接读取 page.getTotal() / page.getResult()
});
```

虚拟线程中每个任务都会拥有独立的 `ScopedValue` 绑定，不会出现传统 `ThreadLocal` 在线程池/虚拟线程场景下的上下文“串线”问题。

## 📚 模块文档

| 模块 | 功能描述 | 文档链接 |
|------|----------|----------|
| `g2rain-mybatis-extension` | MyBatis 拦截器扩展核心 | 源码（建议补充 `package-info.java`） |
| `g2rain-mybatis-pagination` | 分页插件实现 | 源码（建议补充 `package-info.java`） |
| `g2rain-starter-mybatis-pagination` | Spring Boot Starter | 源码 |

## 🏗️ 项目结构

```
g2rain-mybatis-extensions/
├── g2rain-mybatis-extension/         # 拦截器扩展核心
├── g2rain-mybatis-pagination/        # 分页插件
├── g2rain-starter-mybatis-pagination/# Spring Boot Starter
├── pom.xml                           # Maven 聚合配置
└── README.md                         # 项目文档
```

## 🔧 开发指南

### 代码规范

项目遵循 Google Java 代码规范，建议使用以下工具确保代码质量：

- **Checkstyle**：代码风格检查
- **PMD**：代码潜在问题检测
- **SpotBugs**：静态代码分析
- **JaCoCo**：代码覆盖率检查

### 构建命令

```bash
# 编译项目
mvn compile

# 运行测试
mvn test

# 代码质量检查（如项目已集成对应插件）
mvn checkstyle:check pmd:check spotbugs:check

# 生成代码覆盖率报告（如项目已集成对应插件）
mvn jacoco:report

# 打包
mvn package
```

## 🤝 贡献指南

我们欢迎所有形式的贡献！

### 贡献流程

1. **Fork** 本仓库
2. **创建特性分支**：`git checkout -b feature/your-feature-name`
3. **提交更改**：`git commit -m "Add some feature"`
4. **推送分支**：`git push origin feature/your-feature-name`
5. **提交 Pull Request**

### 代码贡献要求

- 遵循 Google Java 代码规范
- 添加适当的单元测试
- 更新相关文档
- 确保所有测试通过
- 代码覆盖率不低于 80%

## 📄 许可证

本项目基于 [Apache 2.0许可证](LICENSE) 开源。

## 📞 联系我们

- **Issues**: [GitHub Issues](https://github.com/g2rain/g2rain/issues)
- **讨论**: [GitHub Discussions](https://github.com/g2rain/g2rain/discussions)
- **邮箱**: g2rain_developer@163.com

## 🙏 致谢

感谢所有为这个项目做出贡献的开发者们！

---

⭐ 如果这个项目对您有帮助，请给我们一个 Star！
