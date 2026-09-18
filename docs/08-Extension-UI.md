# Extension UI

PI 扩展可在 RPC 模式中要求宿主显示选择框、确认框或输入框，SDK 将其解析为 `PiExtensionUiRequest`。该子协议是双向的：扩展发出请求，宿主必须通过 `respond()` 回复带请求 ID 的响应。

## 订阅与回复

```java
PiSubscription subscription = client.subscribeExtensionUi(request -> {
    switch (request.method()) {
        case SELECT -> client.respond(PiExtensionUiResponse.value(
                request.id(), request.options().getFirst()));
        case CONFIRM -> client.respond(PiExtensionUiResponse.confirmed(request.id(), true));
        case INPUT, EDITOR -> client.respond(PiExtensionUiResponse.value(
                request.id(), userInput));
        case NOTIFY -> System.out.println(request.message());
        default -> { }
    }
});
```

也可以从通用事件流中获取：`extension_ui_request` 事件对应的 `PiTypedEvent.ExtensionUi` 视图含 `request()`。

## `PiExtensionUiRequest` 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `String` | 请求 ID，需要响应的方法用它关联回复 |
| `method` | `Method` | 强类型方法 |
| `title` | `String` | 标题 |
| `options` | `List<String>` | 可选项（`SELECT`） |
| `timeoutMillis` | `Long` | 超时毫秒数，协议未提供时为 `null` |
| `message` | `String` | 提示消息（`CONFIRM`、`NOTIFY`） |
| `placeholder` | `String` | 输入占位文本（`INPUT`） |
| `prefill` | `String` | 预填文本（`EDITOR`） |
| `notifyType` | `String` | 通知类型：`info` / `warning` / `error` |
| `statusKey` | `String` | 状态栏键（`SET_STATUS`） |
| `statusText` | `String` | 状态栏文本（`SET_STATUS`） |
| `widgetKey` | `String` | 组件键（`SET_WIDGET`） |
| `widgetLines` | `List<String>` | 组件显示行（`SET_WIDGET`） |
| `widgetPlacement` | `String` | 组件位置：`aboveEditor` / `belowEditor` |
| `text` | `String` | 编辑器或标题文本 |
| `raw` | `JsonNode` | 完整原始请求 JSON |

`request.timeout()` 返回 `Optional<Duration>`，`request.expectsResponse()` 判断是否需要回复。

## 需要回复的方法

| 方法 | 请求字段 | 回复 |
| --- | --- | --- |
| `SELECT` | `title`、`options`、可选 `timeout` | `value` 或取消 |
| `CONFIRM` | `title`、`message`、可选 `timeout` | `confirmed` 或取消 |
| `INPUT` | `title`、可选 `placeholder`、`timeout` | `value` 或取消 |
| `EDITOR` | `title`、可选 `prefill` | `value` 或取消 |

PI 给出超时时间时，扩展侧会在到期后自行使用默认结果；宿主仍应尽快响应。

## 回复类型

`PiExtensionUiResponse` 是 sealed interface，三种实现对应协议的三种响应形态：

| 静态工厂 | 实现 | 协议 JSON |
| --- | --- | --- |
| `PiExtensionUiResponse.value(id, value)` | `Value` | `{"type":"extension_ui_response","id":...,"value":...}` |
| `PiExtensionUiResponse.confirmed(id, bool)` | `Confirmation` | `{"type":"extension_ui_response","id":...,"confirmed":...}` |
| `PiExtensionUiResponse.cancelled(id)` | `Cancellation` | `{"type":"extension_ui_response","id":...,"cancelled":true}` |

## 单向通知

以下方法不需要回复：

- `NOTIFY`：显示信息、警告或错误（`notifyType` 区分级别）。
- `SET_STATUS`：设置或清除状态项（`statusKey` + `statusText`）。
- `SET_WIDGET`：设置或清除文本组件（`widgetKey` + `widgetLines` + `widgetPlacement`）。
- `SET_TITLE`：设置窗口标题（`title`）。
- `SET_EDITOR_TEXT`：设置编辑器文本（`text`）。

未知方法映射为 `UNKNOWN`，原始字段在 `raw()` 中保留。

## Web 服务注意事项

Extension UI 是一个需要双向交互的子协议。只把请求推送到 SSE 不够；Web 应用还要提供带请求 ID 的回复接口，并校验回复者属于同一个会话。仓库中的 Solon 示例已提供 `/ui-responses` 接口（见 [11-Solon-Web对话助手](11-Solon-Web对话助手.md)）。
