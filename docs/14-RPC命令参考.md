# RPC 命令参考

## 对话

| 命令 | Java 方法 | 返回 |
| --- | --- | --- |
| `prompt` | `prompt` | `PiRun` |
| `steer` | `steer` | `PiResponse` |
| `follow_up` | `followUp` | `PiResponse` |
| `abort` | `abort` | `PiResponse` |
| `clear_queue` | `clearQueue` | `QueueState` |
| `new_session` | `newSession` | `Cancelled` |

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

## 队列、压缩和重试

| 命令 | Java 方法 | 返回 |
| --- | --- | --- |
| `set_steering_mode` | `setSteeringMode` | `PiResponse` |
| `set_follow_up_mode` | `setFollowUpMode` | `PiResponse` |
| `compact` | `compact` | `CompactionResult` |
| `set_auto_compaction` | `setAutoCompaction` | `PiResponse` |
| `set_auto_retry` | `setAutoRetry` | `PiResponse` |
| `abort_retry` | `abortRetry` | `PiResponse` |

## Bash

| 命令 | Java 方法 | 返回 |
| --- | --- | --- |
| `bash` | `bash` | `BashResult` |
| `abort_bash` | `abortBash` | `PiResponse` |

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

## 消息与可调用资源

| 命令 | Java 方法 | 返回 |
| --- | --- | --- |
| `get_messages` | `getMessages` | `List<JsonNode>` |
| `get_commands` | `getCommands` | `List<SlashCommand>` |

PI 新版本命令尚未封装时，可以使用：

```java
client.request("new_command", Map.of("field", value)).join();
```

通用入口不会放弃请求 ID 关联、超时和错误映射。
