# 队列、压缩、重试与 Bash

## 队列模式

```java
client.setSteeringMode(QueueMode.ONE_AT_A_TIME).join();
client.setFollowUpMode(QueueMode.ALL).join();
```

`QueueMode` 两种取值：

| 枚举 | 协议值 | 说明 |
| --- | --- | --- |
| `ALL` | `all` | 在排空点注入队列中的全部待处理消息 |
| `ONE_AT_A_TIME` | `one-at-a-time` | 每次运行只注入最早的一条 |

当前队列内容可通过 `clearQueue()` 查询：

```java
PiRpcTypes.QueueState queue = client.clearQueue().join();
System.out.println(queue.steering());
System.out.println(queue.followUp());
```

`clearQueue` 命令既清空队列又返回清空前的状态，因此 `QueueState` 记录的是**清空前**的 steering 与 follow-up 队列。

## 上下文压缩

```java
PiRpcTypes.CompactionResult automatic = client.compact().join();
PiRpcTypes.CompactionResult guided = client.compact("保留 API 决策和未完成任务").join();
client.setAutoCompaction(true).join();
```

`CompactionResult` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `summary` | `String` | 生成的摘要 |
| `firstKeptEntryId` | `String` | 第一个保留条目 ID |
| `tokensBefore` | `long` | 压缩前 token 数 |
| `estimatedTokensAfter` | `long` | 压缩后估算 token 数 |
| `usage` | `Usage` | 压缩请求的模型用量 |
| `details` | `JsonNode` | 额外协议字段（开放结构） |

压缩可能调用模型，耗时通常比普通状态命令长。需要时用带超时的通用请求：

```java
client.request(PiRpcCommand.COMPACT, Map.of("customInstructions", "..."),
        Duration.ofMinutes(3)).join();
```

压缩过程通过 `compaction_start` / `compaction_end` 事件报告，事件的 `Compaction` 视图含 `reason`（`manual` / `threshold` / `overflow`）、`result`、`aborted`、`willRetry`、`errorMessage`。

## 自动重试

```java
client.setAutoRetry(true).join();
client.abortRetry().join();
```

自动重试过程通过 `auto_retry_start`、`auto_retry_end` 和 `summarization_retry_*` 事件报告：

| 事件 | 关键字段 |
| --- | --- |
| `auto_retry_start` | `attempt`、`maxAttempts`、`delayMs`、`errorMessage` |
| `auto_retry_end` | `success`、`attempt`、`finalError` |
| `summarization_retry_scheduled` | `attempt`、`maxAttempts`、`delayMs`、`errorMessage` |
| `summarization_retry_attempt_start` | `source`（`branchSummary` 或 `compaction`）及 `reason` |
| `summarization_retry_finished` | 无 |

## 直接执行 Bash

```java
PiRpcTypes.BashResult result = client.bash("git status --short", true).join();
System.out.print(result.output());
System.out.println(result.exitCode());
client.abortBash().join();
```

`bash(command, excludeFromContext)` 是完整形式；新增的 `bash(command)` 重载等价 `bash(command, false)`（命令和结果进入模型上下文）。

`BashResult` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `output` | `String` | 已捕获输出 |
| `exitCode` | `Integer` | 退出码；未正常结束时为 `null` |
| `cancelled` | `boolean` | 是否被取消 |
| `truncated` | `boolean` | 输出是否被截断 |
| `fullOutputPath` | `String` | 完整输出文件路径 |

`excludeFromContext=true` 表示命令及输出不进入模型上下文。执行期间的完整增量通过 `bash_execution_update` 事件推送（`BashUpdate` 视图含 `requestId`、`delta`）；最终响应中的 `output` 可能被截断，`fullOutputPath` 指向保留完整输出的临时文件。

> Bash 拥有工作目录内的系统权限，不是沙箱。生产服务需限制危险命令和网络出口。
