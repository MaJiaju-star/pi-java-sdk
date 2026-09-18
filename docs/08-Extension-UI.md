# Extension UI

PI 扩展可在 RPC 模式中要求宿主显示选择框、确认框或输入框。SDK 将其解析为 `PiExtensionUiRequest`。

## 订阅与回复

```java
PiSubscription subscription = client.subscribeExtensionUi(request -> {
    switch (request.method()) {
        case SELECT -> client.respond(PiExtensionUiResponse.value(
                request.id(), request.options().getFirst()));
        case CONFIRM -> client.respond(PiExtensionUiResponse.confirmed(request.id(), true));
        case INPUT, EDITOR -> client.respond(PiExtensionUiResponse.cancelled(request.id()));
        case NOTIFY -> System.out.println(request.message());
        default -> { }
    }
});
```

## 需要回复的方法

| 方法 | 请求字段 | 回复 |
| --- | --- | --- |
| `SELECT` | `title`、`options`、可选 `timeout` | `value` 或取消 |
| `CONFIRM` | `title`、`message`、可选 `timeout` | `confirmed` 或取消 |
| `INPUT` | `title`、可选 `placeholder`、`timeout` | `value` 或取消 |
| `EDITOR` | `title`、可选 `prefill` | `value` 或取消 |

`request.expectsResponse()` 可判断是否需要回复。PI 给出超时时间时，扩展侧会在到期后自行使用默认结果；宿主仍应尽快响应。

## 单向通知

以下方法不回复：

- `NOTIFY`：显示信息、警告或错误。
- `SET_STATUS`：设置或清除状态项。
- `SET_WIDGET`：设置或清除文本组件。
- `SET_TITLE`：设置窗口标题。
- `SET_EDITOR_TEXT`：设置编辑器文本。

未知方法会映射为 `UNKNOWN`，原始字段在 `raw()` 中保留。

## Web 服务注意事项

Extension UI 是一个需要双向交互的子协议。只把请求推送到 SSE 不够；Web 应用还要提供带请求 ID 的回复接口，并校验回复者属于同一个会话。仓库中的 Solon 示例已提供 `/ui-responses` 接口。
