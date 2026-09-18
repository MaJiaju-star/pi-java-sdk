# SSE 事件桥接

`pi-java-sdk` 是纯 RPC 客户端，本身不提供 SSE。本模块把 PI 的流式事件桥接到 Server-Sent Events，并把事件规范化为强类型实体，便于前端直接映射。

包：`works.earendil.pi.sse`。**不依赖任何 Web 框架**，只额外使用 JDK 内置的 `com.sun.net.httpserver`（零 Maven 依赖）。

## 为什么需要规范化

PI 的原始 `message_update` 事件是嵌套结构，前端要自己判断两层 `type`；工具调用参数流（`toolcall_*`）和执行流（`tool_execution_*`）分属两类事件。本模块把它们扁平化为自描述实体：

```json
// 原始事件
{"type":"message_update","usage":{...},
 "assistantMessageEvent":{"type":"text_delta","contentIndex":0,"delta":"你好"}}

// 规范化后的 SSE 帧
id: 42
event: text_delta
data: {"seq":42,"type":"text_delta","contentIndex":0,"text":"你好","raw":{...}}
```

## 核心类

| 类 | 职责 |
| --- | --- |
| `SseEvent`（sealed interface） | 规范化事件实体，每个语义事件一个 record |
| `SseEventMapper` | `PiEvent` → `SseEvent`，分配递增序号 |
| `SseFrameEncoder` | `SseEvent` → SSE 文本帧（`id:`/`event:`/`data:`） |
| `SseBroadcaster` | 订阅一次 `PiClient`，扇出到多个连接 |
| `SseBroadcasterConfig` | 队列容量、心跳、重放缓冲、溢出策略 |
| `SseConnection` | 传输抽象，Web 框架实现 |
| `SseHttpServer` | 基于 JDK HttpServer 的零依赖适配器 |

## 事件实体映射

| PI 原始事件 | 实体类型 | `type` |
| --- | --- | --- |
| `message_update` / `text_start` | `TextStart` | `text_start` |
| `message_update` / `text_delta` | `TextDelta` | `text_delta` |
| `message_update` / `text_end` | `TextEnd` | `text_end` |
| `message_update` / `thinking_start` | `ThinkingStart` | `thinking_start` |
| `message_update` / `thinking_delta` | `ThinkingDelta` | `thinking_delta` |
| `message_update` / `thinking_end` | `ThinkingEnd` | `thinking_end` |
| `message_update` / `toolcall_start` | `ToolCallStart` | `toolcall_start` |
| `message_update` / `toolcall_delta` | `ToolCallDelta` | `toolcall_delta` |
| `message_update` / `toolcall_end` | `ToolCallEnd` | `toolcall_end` |
| `message_update` / `done` | `MessageDone` | `message_done` |
| `message_update` / `error` | `MessageFailed` | `message_error` |
| `message_start` | `MessageStart` | `message_start` |
| `message_end` | `MessageEnd` | `message_end` |
| `agent_start` | `AgentStart` | `agent_start` |
| `agent_end` | `AgentEnd` | `agent_end` |
| `agent_settled` | `AgentSettled` | `agent_settled` |
| `turn_start` | `TurnStart` | `turn_start` |
| `turn_end` | `TurnEnd` | `turn_end` |
| `tool_execution_start` | `ToolStart` | `tool_start` |
| `tool_execution_update` | `ToolUpdate` | `tool_update` |
| `tool_execution_end` | `ToolEnd` | `tool_end` |
| `queue_update` | `QueueUpdate` | `queue_update` |
| `compaction_start` | `CompactionStart` | `compaction_start` |
| `compaction_end` | `CompactionEnd` | `compaction_end` |
| `auto_retry_start` | `RetryStart` | `retry_start` |
| `auto_retry_end` | `RetryEnd` | `retry_end` |
| `summarization_retry_scheduled` | `RetryScheduled` | `retry_scheduled` |
| `summarization_retry_attempt_start` | `SummarizationRetry` | `summarization_retry` |
| `summarization_retry_finished` | `SummarizationRetryFinished` | `summarization_retry_finished` |
| `entry_appended` | `EntryAppended` | `entry_appended` |
| `session_info_changed` | `SessionInfo` | `session_info` |
| `thinking_level_changed` | `ThinkingLevelChanged` | `thinking_level` |
| `bash_execution_update` | `BashOutput` | `bash_output` |
| `extension_ui_request` | `ExtensionUi` | `extension_ui` |
| `extension_error` | `Error` | `error` |
| 未知事件 | `Unknown` | `unknown` |

所有实体的公共契约：

```java
public sealed interface SseEvent {
    long seq();      // 单调递增，用于 Last-Event-ID 重放
    String type();   // 语义事件名，即 SSE 的 event: 字段
    JsonNode raw();  // 原始 PI 事件 JSON，向前兼容兜底
}
```

## SSE 帧格式

```
id: 42
event: text_delta
data: {"seq":42,"type":"text_delta","contentIndex":0,"text":"你好","raw":{...}}

```

- `id` 使用事件序号，浏览器断线重连时会带 `Last-Event-ID`，广播器据此重放。
- 空闲时连接线程自动发送心跳注释帧 `: ping`。

## 快速开始

```java
import java.nio.file.Path;
import works.earendil.pi.client.PiClient;
import works.earendil.pi.config.PiClientConfig;
import works.earendil.pi.extension.PiExtensionUiResponse;
import works.earendil.pi.sse.SseBroadcaster;
import works.earendil.pi.sse.SseBroadcasterConfig;
import works.earendil.pi.sse.SseHttpServer;

PiClientConfig config = PiClientConfig.builder()
        .workingDirectory(Path.of("D:/my-project"))
        .build();

try (PiClient client = PiClient.start(config);
     SseBroadcaster broadcaster = SseBroadcaster.attach(client, SseBroadcasterConfig.builder().build());
     SseHttpServer server = SseHttpServer.builder(broadcaster)
             .port(8080)
             .extensionUiResponder(body -> {
                 String id = body.path("id").asText();
                 if (body.has("value")) {
                     client.respond(PiExtensionUiResponse.value(id, body.path("value").asText()));
                 } else if (body.has("confirmed")) {
                     client.respond(PiExtensionUiResponse.confirmed(id, body.path("confirmed").asBoolean()));
                 } else {
                     client.respond(PiExtensionUiResponse.cancelled(id));
                 }
             })
             .start()) {

    System.out.println("SSE: http://localhost:8080" + server.eventsPath());
    client.prompt("分析项目结构").settled().join();
}
```

浏览器端：

```javascript
const events = new EventSource("/events");
events.addEventListener("text_delta", e => appendText(JSON.parse(e.data).text));
events.addEventListener("thinking_delta", e => appendThinking(JSON.parse(e.data).text));
events.addEventListener("tool_start", e => showTool(JSON.parse(e.data)));
events.addEventListener("tool_end", e => finishTool(JSON.parse(e.data)));
events.addEventListener("message_end", e => finalizeMessage(JSON.parse(e.data).message));
events.addEventListener("agent_settled", () => markDone());
```

`SseHttpServer` 提供两个端点：

| 方法 | 默认路径 | 作用 |
| --- | --- | --- |
| `GET` | `/events` | SSE 事件流，支持 `Last-Event-ID` 头或 `?lastEventId=` 参数重放 |
| `POST` | `/ui-responses` | Extension UI 回复入口（需配置 `extensionUiResponder`） |

## 接入其它 Web 框架

`SseHttpServer` 只用于本地开发。生产环境建议实现 `SseConnection` 接入 Solon、Spring 等框架：

```java
SseConnection connection = new SseConnection() {
    @Override public void send(String frame) throws IOException {
        response.getOutputStream().write(frame.getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }
    @Override public void close() { /* 记录日志，等待请求线程结束 */ }
    @Override public boolean isOpen() { return !response.isCommitted() || /* 仍可写 */ true; }
};

broadcaster.add(connection, lastEventId);
```

`SseBroadcaster` 会为每个连接创建独立虚拟线程串行调用 `send`，实现无需处理并发写。

## 反压、心跳与重放

| 配置（`SseBroadcasterConfig`） | 默认 | 说明 |
| --- | --- | --- |
| `queueCapacity` | 256 | 每连接下游队列容量 |
| `heartbeatInterval` | 15 秒 | 空闲心跳间隔；`Duration.ZERO` 关闭 |
| `replayBufferSize` | 256 | 断线重放缓冲帧数；0 关闭 |
| `overflowStrategy` | `DROP_OLDEST` | 队列满时：`DROP_OLDEST` / `DROP_LATEST` / `CLOSE` |

- **永不阻塞 SDK**：`publish` 的下游队列操作全为非阻塞，慢消费者只影响自身连接。
- **断连检测依赖心跳**：连接写失败才会被移除，因此 `SseHttpServer` 场景**不要关闭心跳**，否则空闲断线无法被察觉。
- **重放**：客户端重连时带 `Last-Event-ID`，广播器回放序号更大的缓冲帧。

## 注意事项

1. **`EventSource` 不能设置自定义请求头**。身份应由同源认证网关从 Cookie/会话解析后注入，不能靠浏览器传 `X-User-Id`。
2. **Extension UI 是双向的**。SSE 只下行，回复需单独的 POST 端点（示例已内置 `/ui-responses`）；生产环境要校验回复者属于同一会话。
3. **`message_update` 只有增量**。前端应累加 `delta`，并用 `message_end` 的最终消息校正；`agent_settled` 才表示整轮结束。
4. **HTTP 分块编码**。原始 socket 读取时会看到 chunk 长度行，SSE 客户端（`EventSource`）会自动处理。
5. **序号从 1 开始**，全局单调递增，跨重连保持（同一 `SseBroadcaster` 实例内）。

## 独立使用映射器与编码器

不需要 SSE 服务器时，也可以只用规范化能力：

```java
SseEventMapper mapper = new SseEventMapper(client.objectMapper());
SseFrameEncoder encoder = new SseFrameEncoder(client.objectMapper());

client.subscribe(event -> {
    SseEvent sse = mapper.map(event);
    String frame = encoder.encode(sse);   // 直接写入任意 SSE 通道
});
```
