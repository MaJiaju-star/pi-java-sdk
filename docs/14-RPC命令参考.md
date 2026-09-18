# RPC 命令参考

本文是 33 个 RPC 命令的完整参考，含参数、返回类型和 Java 方法签名。所有命令也可通过通用入口 `client.request(...)` 调用。

## 对话

| 命令 | Java 方法 | 返回 |
| --- | --- | --- |
| `prompt` | `prompt` | `PiRun`（`accepted()` + `settled()`） |
| `steer` | `steer` | `PiResponse` |
| `follow_up` | `followUp` | `PiResponse` |
| `abort` | `abort` | `PiResponse` |
| `clear_queue` | `clearQueue` | `QueueState` |
| `new_session` | `newSession` | `Cancelled` |

### prompt

```java
public PiRun prompt(String message)
public PiRun prompt(String message, List<PiImage> images)
public PiRun prompt(String message, List<PiImage> images, PiStreamingBehavior streamingBehavior)
public PiRun prompt(String message, PiStreamingBehavior streamingBehavior)
```

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `message` | `String` | 是 | 用户消息 |
| `images` | `List<PiImage>` | 否 | 图片列表 |
| `streamingBehavior` | `PiStreamingBehavior` | 否 | `STEER` 或 `FOLLOW_UP`，`null` 用 PI 默认 |

同一客户端在上一轮 `settled()` 前再调用会抛 `IllegalStateException`。

### steer / follow_up

```java
public CompletableFuture<PiResponse> steer(String message)
public CompletableFuture<PiResponse> steer(String message, List<PiImage> images)
public CompletableFuture<PiResponse> followUp(String message)
public CompletableFuture<PiResponse> followUp(String message, List<PiImage> images)
```

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `message` | `String` | 是 | 注入或排队的消息 |
| `images` | `List<PiImage>` | 否 | 图片列表 |

### abort / clear_queue / new_session

```java
public CompletableFuture<PiResponse> abort()
public CompletableFuture<PiRpcTypes.QueueState> clearQueue()
public CompletableFuture<PiRpcTypes.Cancelled> newSession(String parentSession)
public CompletableFuture<PiRpcTypes.Cancelled> newSession()
```

| 命令 | 参数 | 说明 |
| --- | --- | --- |
| `abort` | 无 | 中止当前 Agent 运行 |
| `clear_queue` | 无 | 清空 steering 与 follow-up 队列，返回清空前状态 |
| `new_session` | `parentSession`（可选） | 创建并切换到新会话，可指定父会话 |

## 状态、模型和思考

| 命令 | Java 方法 | 返回 |
| --- | --- | --- |
| `get_state` | `getState` | `SessionState` |
| `set_model` | `setModel` | `Model` |
| `cycle_model` | `cycleModel` | `ModelCycle` 或 `null` |
| `get_available_models` | `getAvailableModels` | `List<Model>` |
| `set_thinking_level` | `setThinkingLevel` | `PiResponse` |
| `cycle_thinking_level` | `cycleThinkingLevel` | `ThinkingLevelCycle` 或 `null` |
| `get_available_thinking_levels` | `getAvailableThinkingLevels` | `List<ThinkingLevel>` |

```java
public CompletableFuture<PiRpcTypes.SessionState> getState()
public CompletableFuture<PiRpcTypes.Model> setModel(String provider, String modelId)
public CompletableFuture<PiRpcTypes.ModelCycle> cycleModel()
public CompletableFuture<List<PiRpcTypes.Model>> getAvailableModels()
public CompletableFuture<PiResponse> setThinkingLevel(ThinkingLevel level)
public CompletableFuture<PiRpcTypes.ThinkingLevelCycle> cycleThinkingLevel()
public CompletableFuture<List<ThinkingLevel>> getAvailableThinkingLevels()
```

| 命令 | 参数 | 说明 |
| --- | --- | --- |
| `get_state` | 无 | 当前会话状态（12 字段） |
| `set_model` | `provider`、`modelId` | 找不到模型时抛 `PiRpcException` |
| `cycle_model` | 无 | 无可循环模型时返回 `null` |
| `get_available_models` | 无 | 当前凭证可用模型 |
| `set_thinking_level` | `level`（`ThinkingLevel`） | 设置思考等级 |
| `cycle_thinking_level` | 无 | 无可切换等级时返回 `null` |
| `get_available_thinking_levels` | 无 | 当前模型支持的思考等级 |

## 队列、压缩和重试

| 命令 | Java 方法 | 返回 |
| --- | --- | --- |
| `set_steering_mode` | `setSteeringMode` | `PiResponse` |
| `set_follow_up_mode` | `setFollowUpMode` | `PiResponse` |
| `compact` | `compact` | `CompactionResult` |
| `set_auto_compaction` | `setAutoCompaction` | `PiResponse` |
| `set_auto_retry` | `setAutoRetry` | `PiResponse` |
| `abort_retry` | `abortRetry` | `PiResponse` |

```java
public CompletableFuture<PiResponse> setSteeringMode(QueueMode mode)
public CompletableFuture<PiResponse> setFollowUpMode(QueueMode mode)
public CompletableFuture<PiRpcTypes.CompactionResult> compact(String customInstructions)
public CompletableFuture<PiRpcTypes.CompactionResult> compact()
public CompletableFuture<PiResponse> setAutoCompaction(boolean enabled)
public CompletableFuture<PiResponse> setAutoRetry(boolean enabled)
public CompletableFuture<PiResponse> abortRetry()
```

| 命令 | 参数 | 说明 |
| --- | --- | --- |
| `set_steering_mode` | `mode`（`QueueMode`） | steering 队列投递模式 |
| `set_follow_up_mode` | `mode`（`QueueMode`） | follow-up 队列投递模式 |
| `compact` | `customInstructions`（可选） | 压缩上下文，可带自定义指令 |
| `set_auto_compaction` | `enabled`（`boolean`） | 启用/禁用自动压缩 |
| `set_auto_retry` | `enabled`（`boolean`） | 启用/禁用自动重试 |
| `abort_retry` | 无 | 中止等待中的自动重试 |

## Bash

| 命令 | Java 方法 | 返回 |
| --- | --- | --- |
| `bash` | `bash` | `BashResult` |
| `abort_bash` | `abortBash` | `PiResponse` |

```java
public CompletableFuture<PiRpcTypes.BashResult> bash(String command, boolean excludeFromContext)
public CompletableFuture<PiRpcTypes.BashResult> bash(String command)
public CompletableFuture<PiResponse> abortBash()
```

| 命令 | 参数 | 说明 |
| --- | --- | --- |
| `bash` | `command`（`String`）、`excludeFromContext`（`boolean`，可选默认 `false`） | 在 PI 工作目录执行 Bash |
| `abort_bash` | 无 | 中止当前 Bash 命令 |

## 会话

| 命令 | Java 方法 | 返回 |
| --- | --- | --- |
| `get_session_stats` | `getSessionStats` | `SessionStats` |
| `export_html` | `exportHtml` | `PathResult` |
| `switch_session` | `switchSession` | `Cancelled` |
| `fork` | `fork` | `ForkResult` |
| `clone` | `cloneSession` | `Cancelled` |
| `get_fork_messages` | `getForkMessages` | `List<ForkMessage>` |
| `get_entries` | `getEntries` | `Entries` |
| `get_tree` | `getTree` | `SessionTree` |
| `get_last_assistant_text` | `getLastAssistantText` | `String` |
| `set_session_name` | `setSessionName` | `PiResponse` |

```java
public CompletableFuture<PiRpcTypes.SessionStats> getSessionStats()
public CompletableFuture<PiRpcTypes.PathResult> exportHtml(String outputPath)
public CompletableFuture<PiRpcTypes.PathResult> exportHtml()
public CompletableFuture<PiRpcTypes.Cancelled> switchSession(String sessionPath)
public CompletableFuture<PiRpcTypes.ForkResult> fork(String entryId)
public CompletableFuture<PiRpcTypes.Cancelled> cloneSession()
public CompletableFuture<List<PiRpcTypes.ForkMessage>> getForkMessages()
public CompletableFuture<PiRpcTypes.Entries> getEntries(String since)
public CompletableFuture<PiRpcTypes.Entries> getEntries()
public CompletableFuture<PiRpcTypes.SessionTree> getTree()
public CompletableFuture<String> getLastAssistantText()
public CompletableFuture<PiResponse> setSessionName(String name)
```

| 命令 | 参数 | 说明 |
| --- | --- | --- |
| `get_session_stats` | 无 | token、费用和消息统计 |
| `export_html` | `outputPath`（可选） | 导出 HTML，省略用 PI 默认路径 |
| `switch_session` | `sessionPath` | 切换到已有会话文件 |
| `fork` | `entryId` | 从指定条目分支 |
| `clone` | 无 | 克隆当前会话 |
| `get_fork_messages` | 无 | 可作为分支点的消息 |
| `get_entries` | `since`（可选） | 会话条目；带 `since` 时返回增量 |
| `get_tree` | 无 | 会话分支树 |
| `get_last_assistant_text` | 无 | 最后一条助手文本，无则为 `null` |
| `set_session_name` | `name` | 空名称 PI 报错 |

## 消息与可调用资源

| 命令 | Java 方法 | 返回 |
| --- | --- | --- |
| `get_messages` | `getMessages` | `List<JsonNode>` |
| `get_commands` | `getCommands` | `List<SlashCommand>` |

```java
public CompletableFuture<List<JsonNode>> getMessages()
public CompletableFuture<List<PiRpcTypes.SlashCommand>> getCommands()
```

| 命令 | 参数 | 说明 |
| --- | --- | --- |
| `get_messages` | 无 | 当前会话消息（开放结构，保留 `JsonNode`） |
| `get_commands` | 无 | 可用斜杠命令；`SlashCommand` 含 `name`、`description`、`source`、`sourceInfo` |

## 通用命令入口

PI 新版本命令尚未封装时，可以使用：

```java
client.request("new_command", Map.of("field", value)).join();
```

通用入口的多种重载：

```java
public CompletableFuture<PiResponse> request(String command)
public CompletableFuture<PiResponse> request(PiRpcCommand command)
public CompletableFuture<PiResponse> request(String command, Map<String, ?> arguments)
public CompletableFuture<PiResponse> request(PiRpcCommand command, Map<String, ?> arguments)
public CompletableFuture<PiResponse> request(String command, Map<String, ?> arguments, Duration timeout)
public CompletableFuture<PiResponse> request(ObjectNode command)
public CompletableFuture<PiResponse> request(ObjectNode command, Duration timeout)
```

通用入口不会放弃请求 ID 关联、超时和错误映射。`PiResponse` 含 `id`、`command`、`data`、`raw`，可用 `dataAs(mapper, Type.class)` 转换数据。
