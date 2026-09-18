# Javadoc 与 API 注释

`pi-java-sdk` 和 `pi-java-remote` 的公开 API 使用中文 Javadoc。注释可以在 IDE 中直接查看，也可以通过 JDK 25 的 `javadoc` 工具生成 HTML。

## 注释范围

- 公开类和接口说明职责、线程安全性或生命周期约束。
- 公开方法使用 `@param`、`@return` 和 `@throws` 描述调用契约。
- `record` 在类型注释中使用 `@param` 描述每个组件。
- 枚举常量分别说明对应的协议行为。
- 每个 Java 包通过 `package-info.java` 说明职责。
- 包级实现类不作为公共 API，只保留解释协议边界或并发行为所需的内部注释。

## 异步异常

返回 `CompletableFuture` 的 RPC 方法通常不会直接抛出服务端错误。服务端拒绝、请求超时和连接中断会使 future 异常完成。方法的 `@throws` 只列出调用阶段同步抛出的异常。

示例：

```java
client.getState().whenComplete((state, error) -> {
    if (error != null) {
        // 处理 PiRpcException、PiRequestTimeoutException 或 PiProcessException
        return;
    }
    System.out.println(state.sessionId());
});
```

## 严格校验

源码使用 JDK 25 的以下检查策略验证：

```text
javadoc -Werror -Xdoclint:all ...
```

`-Xdoclint:all` 检查缺失说明、标签、链接和 HTML 结构；`-Werror` 将警告视为失败。核心 SDK 与实验性远程模块分别执行校验。
