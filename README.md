# PI Java SDK

按功能分类的完整中文文档见 [`docs/README.md`](docs/README.md)。本页保留 SDK 概览和最小示例。

`pi-java-sdk` 是 PI Coding Agent RPC 模式的 JDK 25 客户端。它启动一个 `pi --mode rpc` 子进程，通过 stdin/stdout 上的 UTF-8 JSONL 协议发送命令并接收流式事件。

## 环境要求

- JDK 25
- Maven 3.9+
- 已安装 PI CLI，或者能够提供 PI CLI 的 Node.js 启动命令
- 已通过 PI 配置模型凭据

Windows 默认启动 `pi.cmd`，macOS 和 Linux 默认启动 `pi`。

## 引入依赖

当前工程尚未发布到 Maven Central。先在 `java` 目录执行：

```bash
mvn install
```

然后在业务项目中添加：

```xml
<dependency>
    <groupId>works.earendil.pi</groupId>
    <artifactId>pi-java-sdk</artifactId>
    <version>0.1.2-SNAPSHOT</version>
</dependency>
```

## 最小示例

```java
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import works.earendil.pi.client.PiClient;
import works.earendil.pi.config.PiClientConfig;
import works.earendil.pi.rpc.PiRun;

PiClientConfig config = PiClientConfig.builder()
        .workingDirectory(Path.of("D:/my-project"))
        .provider("anthropic")
        .model("claude-sonnet-4-6")
        .noSession(true)
        .stderrConsumer(System.err::print)
        .build();

try (PiClient client = PiClient.start(config)) {
    client.subscribe(event -> event.textDelta().ifPresent(System.out::print));

    PiRun run = client.prompt("检查这个项目并说明目录结构");
    run.accepted().get(10, TimeUnit.SECONDS);
    run.settled().join();
}
```

## 运行状态

一次 prompt 有两个不同的完成点：

1. `PiRun.accepted()`：PI 已接受或排队该 prompt。
2. `PiRun.settled()`：收到 `agent_settled`，说明自动重试、自动压缩和排队的延续消息均已结束。

不要把 `accepted()` 当成模型回答完成。

## 流式事件

```java
PiSubscription subscription = client.subscribe(event -> {
    switch (event.type()) {
        case "message_update" -> event.textDelta().ifPresent(System.out::print);
        case "tool_execution_start" -> System.out.println("工具开始: " + event.raw());
        case "tool_execution_end" -> System.out.println("工具结束: " + event.raw());
        case "extension_ui_request" -> handleExtensionUi(event.raw());
        default -> {
            // 新版本 PI 的未知事件仍会以原始 JsonNode 形式交付。
        }
    }
});

subscription.close();
```

监听器异常不会停止 stdout 协议读取。可以通过 `listenerErrorHandler(...)` 集中记录这些异常。

## SSE 桥接

`works.earendil.pi.sse` 把流式事件规范化为强类型实体并桥接到 Server-Sent Events，适合 Web 前端：

```java
try (PiClient client = PiClient.start(config);
     SseBroadcaster broadcaster = SseBroadcaster.attach(client, SseBroadcasterConfig.builder().build());
     SseHttpServer server = SseHttpServer.builder(broadcaster).port(8080).start()) {
    System.out.println("SSE: http://localhost:8080" + server.eventsPath());
    client.prompt("分析项目结构").settled().join();
}
```

前端收到规范化事件（`event: text_delta`、`event: tool_end` 等），详见 [`docs/18-SSE事件桥接.md`](docs/18-SSE事件桥接.md)。不依赖 Web 框架，接入 Solon/Spring 只需实现 `SseConnection`。

## 运行中追加消息

```java
client.steer("先检查测试失败原因");
client.followUp("完成后再给出变更摘要");
client.abort();
```

- `steer`：当前 assistant turn 的工具调用结束后插入。
- `followUp`：当前 Agent 完全停止后再处理。
- `prompt`：SDK 默认禁止同一客户端并行启动两次普通运行，避免无法关联无请求 ID 的流式事件。

## 模型和思考等级

```java
client.setModel("openai", "gpt-5.4").join();
client.setThinkingLevel(ThinkingLevel.HIGH).join();

List<PiRpcTypes.Model> models = client.getAvailableModels().join();
PiRpcTypes.SessionState state = client.getState().join();
```

## 图片

```java
PiImage image = PiImage.fromPath(Path.of("screen.png"), "image/png");
PiRun run = client.prompt("说明图片中的问题", List.of(image));
run.settled().join();
```

## 通用命令入口

SDK 对常用聊天方法提供了强语义封装。其他 RPC 命令可直接调用：

```java
client.request("set_auto_retry", Map.of("enabled", true)).join();
client.request("get_session_stats").thenAccept(response -> {
    JsonNode stats = response.data();
});
client.request("export_html", Map.of("outputPath", "session.html")).join();
```

PI 当前支持的命令包括：

- 对话：`prompt`、`steer`、`follow_up`、`abort`、`clear_queue`
- 状态：`get_state`、`get_messages`
- 模型：`set_model`、`cycle_model`、`get_available_models`
- 思考：`set_thinking_level`、`cycle_thinking_level`、`get_available_thinking_levels`
- 队列：`set_steering_mode`、`set_follow_up_mode`
- 压缩和重试：`compact`、`set_auto_compaction`、`set_auto_retry`、`abort_retry`
- Bash：`bash`、`abort_bash`
- 会话：`new_session`、`switch_session`、`fork`、`clone`、`get_entries`、`get_tree`、`get_session_stats`、`export_html`、`set_session_name`
- 资源：`get_commands`、`get_fork_messages`、`get_last_assistant_text`

## 从源码运行 PI

如果没有全局安装 PI，可以传入完整命令前缀：

```java
PiClientConfig config = PiClientConfig.builder()
        .command(List.of(
                "node",
                "D:/code/pi-main/pi-main/packages/coding-agent/dist/bundle/cli.js"
        ))
        .workingDirectory(Path.of("D:/my-project"))
        .build();
```

`command(...)` 后不需要写 `--mode rpc`，SDK 会自动追加。

## 进程与错误处理

- `PiRpcException`：PI 返回 `success: false`。
- `PiProcessException`：进程启动失败、异常退出或 stdin 写入失败。
- `PiProtocolException`：stdout 出现非法 UTF-8、JSON 或不匹配的响应。
- stderr 会被独立虚拟线程持续读取，避免管道阻塞；默认保留最后 64 KiB 字符。
- `close()` 先关闭 stdin 并请求终止，超时后强制结束子进程。

## 安全说明

PI 默认拥有 `read`、`write`、`edit` 和 `bash` 工具。`workingDirectory` 不是安全沙箱。不要把未认证的公网请求直接转发给 PI；生产服务需要增加身份认证、目录隔离、并发限制和容器沙箱。
