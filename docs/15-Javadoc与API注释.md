# Javadoc 与 API 注释

`pi-java-sdk` 和 `pi-java-remote` 的公开 API 使用中文 Javadoc。注释可以在 IDE 中直接查看，也可以通过 JDK 21 的 `javadoc` 工具生成 HTML。

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

## 方法内分步骤注释

对**流程较长**的方法（大致 15 行以上，或包含多个明显阶段），在方法体内用 `//1.`、`//2.` 标注步骤：

```java
public static PiClient start(PiClientConfig config) throws IOException {
    Objects.requireNonNull(config, "config");

    //1. 拼装命令行：pi --mode rpc + 用户附加参数。
    List<String> commandLine = new ArrayList<>(config.command());
    commandLine.add("--mode");
    commandLine.add("rpc");
    commandLine.addAll(config.arguments());

    //2. 启动子进程，并写入配置的工作目录与环境变量。
    ProcessBuilder processBuilder = new ProcessBuilder(commandLine)
            .directory(config.workingDirectory().toFile());
    processBuilder.environment().putAll(config.environment());
    Process process = processBuilder.start();

    //3. 建立客户端并立即启动读取线程，避免 PI 因管道写满而阻塞。
    PiClient client = new PiClient(config, new ObjectMapper(), process);
    client.startReaders();

    //4. 用 get_state 探测 RPC 就绪；失败时先关闭客户端，避免泄漏子进程。
    try {
        client.getState().get(config.startupTimeout().toMillis(), TimeUnit.MILLISECONDS);
        return client;
    } catch (...) {
        client.close();
        throw ...;
    }
}
```

约定：

- **说明「为什么」，不要复述代码。** 「先启动替代实例再关闭旧实例，避免窗口期出现无可用客户端」比「启动客户端」有用。
- **一个步骤对应一个语义阶段**，不按语句逐行编号。
- **编号从 `1` 开始且连续**；中途有分支时仍按主流程编号。
- **短方法、纯 getter 与 record 组件不需要步骤注释**，不强行套用。
- 单点提醒可用不带编号的 `//` 注释（协议怪癖、回编译陷阱等）。

目前采用此约定的典型方法：`PiClient.start`、`PiClient.request`、`PiClient.startReaders`、`PiClient.close`、`SessionLister.peek`、`SseBroadcaster.publish`、`SseHttpServer.Builder.handleEvents`、`StrictJsonlReader.read`、`PiCliVersion.detect`。

## 严格校验

源码使用 JDK 21 的以下检查策略验证：

```text
javadoc -Werror -Xdoclint:all ...
```

`-Xdoclint:all` 检查缺失说明、标签、链接和 HTML 结构；`-Werror` 将警告视为失败。核心 SDK 与实验性远程模块分别执行校验。
