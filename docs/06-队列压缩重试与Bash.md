# 队列、压缩、重试与 Bash

## 队列模式

```java
client.setSteeringMode(QueueMode.ONE_AT_A_TIME).join();
client.setFollowUpMode(QueueMode.ALL).join();
```

`ALL` 在排空点注入所有待处理消息；`ONE_AT_A_TIME` 每次只注入最早的一条。

## 上下文压缩

```java
PiRpcTypes.CompactionResult automatic = client.compact().join();
PiRpcTypes.CompactionResult guided = client.compact("保留 API 决策和未完成任务").join();
client.setAutoCompaction(true).join();
```

压缩可能调用模型，耗时通常比普通状态命令长。需要时为单次通用请求设置更长超时。

## 自动重试

```java
client.setAutoRetry(true).join();
client.abortRetry().join();
```

自动重试过程通过 `auto_retry_start`、`auto_retry_end` 和 `summarization_retry_*` 事件报告。

## 直接执行 Bash

```java
PiRpcTypes.BashResult result = client.bash("git status --short", true).join();
System.out.print(result.output());
System.out.println(result.exitCode());
client.abortBash().join();
```

`excludeFromContext=true` 表示命令及输出不进入模型上下文。执行期间的完整增量通过 `bash_execution_update` 推送，最终响应中的输出可能被截断；`fullOutputPath` 指向保留完整输出的临时文件。

Bash 拥有工作目录内的系统权限，不是沙箱。
