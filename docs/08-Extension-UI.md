# Extension UI

PI 扩展可在 RPC 模式中要求宿主显示选择框、确认框或输入框，SDK 将其解析为 `PiExtensionUiRequest`。该子协议是双向的：扩展发出请求，宿主必须通过 `respond()` 回复带请求 ID 的响应。

> **最常见的用途是拦截工具调用**（权限审批、危险命令确认、路径保护）。RPC 协议本身没有审批命令，这条子协议是唯一的阻塞式通道，见下文「用 Extension UI 拦截工具调用」。

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

## 用 Extension UI 拦截工具调用

这是 Extension UI 最重要的用途。**RPC 协议本身没有任何审批工具调用的命令**——33 个命令里没有注册钩子、批准或拒绝之类的能力，唯一的阻塞式通道就是这条子协议。

### 为什么必须绕这一圈

工具调用的拦截点在 PI 进程内部：扩展的 `tool_call` 钩子挂在 `AgentSession.beforeToolCall` 上。跨进程的 RPC 天然拿不到「阻塞」语义。事件时序是：

```
tool_execution_start   ← RPC 事件；Java 能看到，但拦不住
      ↓
tool_call              ← 扩展钩子；唯一能 block / 改参数的位置
      ↓
工具执行
      ↓
tool_result            ← 能改结果
      ↓
tool_execution_end
```

也就是说：Java 收到 `tool_execution_start` 时工具虽还没执行，但你无法阻止它；`abort()` 只是竞态，不是拦截。

### 第 1 步：写一个拦截扩展

`gate.ts`（TypeScript，运行在 PI 进程内）：

```typescript
import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";

export default function (pi: ExtensionAPI) {
  pi.on("tool_call", async (event, ctx) => {
    // 把决策权外抛给宿主；RPC 模式下会变成 extension_ui_request
    const decision = await ctx.ui.select(
      JSON.stringify({ tool: event.toolName, input: event.input }),
      ["allow", "deny"]
    );
    return decision === "allow"
      ? undefined
      : { block: true, reason: "Denied by policy service" };
  });
}
```

`ctx.ui.select()` 是四个需要回复的方法之一（`select` / `confirm` / `input` / `editor`），另外三个见上文「需要回复的方法」。

### 第 2 步：启动时装载扩展

```java
PiClient client = PiClient.start(PiClientConfig.builder()
        .argument("--extension", "D:/gate/gate.ts")
        .build());
```

`--extension`（短写 `-e`）可重复传，用 `argument(name, value)` 追加多次即可。也可以放进自动发现目录让 PI 自行加载：

| 位置 | 作用域 |
| --- | --- |
| `~/.pi/agent/extensions/*.ts` | 全局 |
| `.pi/extensions/*.ts` | 项目级，项目受信任后才加载 |

### 第 3 步：Java 侧担任审批大脑

```java
client.subscribeExtensionUi(request -> {
    if (request.method() == PiExtensionUiRequest.Method.SELECT) {
        boolean allow = policyService.approve(request.title());   // 你的审批逻辑
        client.respond(PiExtensionUiResponse.value(
                request.id(), allow ? "allow" : "deny"));
    } else if (request.expectsResponse()) {
        // 其余对话方法一律拒绝，避免扩展永久阻塞
        client.respond(PiExtensionUiResponse.cancelled(request.id()));
    }
});
```

> **不要阻塞事件分发线程。** `subscribeExtensionUi` 的监听器在 `pi-rpc-events` 这条单线程上顺序执行。审批若要走远程服务（HTTP、数据库），请提交到自己的 executor 后立即返回，否则整个事件流会被卡住。

### 语义细节

拦截行为的开关与陷阱，逐条对照：

| 项 | 行为 |
| --- | --- |
| 阻止调用 | 钩子返回 `{ block: true, reason?: string, terminate?: boolean }` |
| 修改参数 | `event.input` 是可变对象，就地修改即生效，且**修改后不再重新校验** |
| 异常处理 | 钩子抛异常会 **block 该工具**（fail-safe，不是放行） |
| 提前终止 | `terminate` 仅在阻止时生效；只有整批结果都终止，agent 才提前结束 |
| 并行工具 | 同一 assistant 消息的兄弟调用「先串行预检、再并发执行」，钩子看不到同批兄弟的结果 |
| 超时 | 只有扩展传了 `timeout` 才有时限，到期按默认值决定（`confirm` → `false` = 阻止）；不传则一直等 |
| 非交互模式 | 官方示例写法是 `if (!ctx.hasUI) return { block: true, ... }`；RPC 模式下 `ctx.hasUI` 为 `true` |
| 隔离性 | 扩展是**受信任代码**，跑在 PI 进程内且有完整系统权限，不是沙箱 |

### 替代方案：扩展直接回调你的后端

如果审批要携带完整参数、要缓存 / 批量 / 审计，或不想占用 Extension UI 通道，可以让扩展自己调后端：

```typescript
pi.on("tool_call", async (event, ctx) => {
  const response = await fetch("http://127.0.0.1:8080/policy/check", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ toolName: event.toolName, input: event.input }),
    signal: ctx.signal,
  });
  const { allow, reason } = await response.json();
  return allow ? undefined : { block: true, reason };
});
```

| | 经 Extension UI 往返 | 扩展直接回调 |
| --- | --- | --- |
| 决策方 | Java 进程（经 SDK） | 你的后端 |
| 传输 | 复用 RPC 子协议 | 自己的 HTTP |
| 完整 `event.input` | 只能塞进 `title` 字符串 | 结构化 JSON |
| 与真人弹窗 | 抢同一条通道 | 互不干扰 |
| 超时语义 | 由扩展的 `timeout` 决定 | 自己控制 |

### 其它可拦截的钩子

| 钩子 | 能力 |
| --- | --- |
| `tool_call` | 阻止或修改工具调用 |
| `tool_result` | 修改工具结果（链式，可返回部分 patch） |
| `user_bash` | 拦截用户 `!` / `!!` 命令，可替换执行后端 |
| `input` | 拦截、改写或接管用户输入 |
| `session_before_switch` / `session_before_fork` | 取消会话切换或分叉 |
| `compaction` | 自定义压缩策略 |

PI 自带 `permission-gate.ts`、`confirm-destructive.ts`、`protected-paths.ts`、`tool-override.ts`、`bash-spawn-hook.ts`、`project-trust.ts`、`sandbox/` 等示例，可作为起点。

## Web 服务注意事项

Extension UI 是一个需要双向交互的子协议。只把请求推送到 SSE 不够；Web 应用还要提供带请求 ID 的回复接口，并校验回复者属于同一个会话。仓库中的 Solon 示例已提供 `/ui-responses` 接口（见 [11-Solon-Web对话助手](11-Solon-Web对话助手.md)）。
