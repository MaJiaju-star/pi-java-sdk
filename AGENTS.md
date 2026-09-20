# AGENTS.md — pi-java-sdk

PI Coding Agent 的 **RPC 模式 JDK 21 客户端**。本文件供 AI 编码代理（pi / Claude 等）在修改本仓库前读取，描述项目约定、命令与陷阱。

## 项目概览

- 一个 `PiClient` 实例 = 一个 `pi --mode rpc` 子进程 + 一个活动会话，通过 stdin/stdout 上的 UTF-8 JSONL 协议通信。
- 定位：**本地 Coding Agent 的 Java 客户端**，不是 PI Server 客户端（远程 CBOR 协议在独立的实验性模块 `pi-java-remote` 中，不在本仓库）。
- 仅运行时依赖：`com.fasterxml.jackson.core:jackson-databind`（2.22.2）。测试依赖 JUnit 5（5.13.4）。
- 要求：JDK 21+（使用 record、sealed interface、virtual thread、switch 模式匹配等特性）、Maven 3.9+。

## 常用命令

```bash
mvn clean install      # 编译 + 测试 + 打包 + 安装到本地仓库（README 要求的发布方式）
mvn test               # 只编译并跑测试
mvn -DskipTests package  # 跳过测试打包
```

- 工程 groupId `works.earendil.pi`，artifactId `pi-java-sdk`，version `0.1.2-SNAPSHOT`。
- 测试全为单元测试（用 `FakePiProcess` 模拟子进程），**不依赖真实 PI CLI**，因此 `mvn test` 无网络/进程依赖即可运行。
- `PiRpcCommandCoverageTest` 是一个覆盖率守卫测试，默认 `@Disabled` 跳过。

## 架构

包结构（`src/main/java/works/earendil/pi/`）：

| 包 | 职责 |
| --- | --- |
| `client` | `PiClient`（主入口，线程安全）、`StrictJsonlReader`（stdout 解析） |
| `config` | `PiClientConfig`（builder 式配置）、`PiEventOverflowStrategy` |
| `conversation` | `PiImage`、`ThinkingLevel`、`QueueMode`、`PiStreamingBehavior` |
| `event` | `PiEvent`、`PiTypedEvent`（sealed）、`PiEventType`、`PiEventDecoder`、`PiSubscription` |
| `exception` | `PiClientException` 及其子类（见下） |
| `extension` | `PiExtensionUiRequest` / `PiExtensionUiResponse`（Extension UI 协议） |
| `pool` | `PiClientPool`、`PiClientFactory`（会话池与故障恢复） |
| `process` | `PiCliVersion`（版本探测）、`PiProcessExit` |
| `session` | `SessionLister`（历史会话文件的本地只读访问，不启动 PI 子进程） |
| `sse` | `SseEvent`、`SseEventMapper`、`SseFrameEncoder`、`SseBroadcaster`、`SseConnection`、`SseHttpServer`（PI 事件→SSE 桥接，零 Web 依赖） |
| `rpc` | `PiRpcCommand`（枚举）、`PiRpcTypes`（类型化响应 record）、`PiResponse`、`PiRun` |

关键类：

- `PiClient.start(PiClientConfig)` —— 唯一推荐入口；`close()` 先关 stdin 请求终止，超时后强杀子进程。
- `PiClientConfig.builder()` —— 所有配置走 builder；`command(...)` 传命令前缀，**不需要**写 `--mode rpc`（SDK 自动追加）。
- `PiRpcTypes` —— 各 RPC 命令响应的类型化 record（`Model`、`SessionState`、`Usage`、`BashResult` 等）。
- `PiRpcCommand` —— 所有支持命令的枚举，`wireValue()` 为协议字符串。

## 编码约定

- **公开 API 的 Javadoc 用中文**，且必须通过 `javadoc -Werror -Xdoclint:all`（缺 `@param`/`@return`/`@throws` 会失败）。每个 Java 包要有 `package-info.java` 说明职责。
- **较长的方法（≥ 15 行或含多个阶段）用 `//1.` `//2.` 分步骤注释**，每步说明「为什么」而非复述代码；短方法与纯 getter 不强行套用。
- 返回 `CompletableFuture` 的 RPC 方法**不同步抛服务端错误**：拒绝/超时/断连使 future 异常完成；方法 `@throws` 只列调用阶段同步异常。
- 数据载体用 `record`；可扩展变体用 `sealed interface`（`PiTypedEvent`、`PiExtensionUiResponse`）。
- 枚举与 JSON 协议字符串的映射用 `wireValue()` / `fromWireValue()`。
- 新增 RPC 命令：在 `PiRpcCommand` 加枚举值 + 在 `PiRpcTypes` 加响应 record，并补 `PiRpcCommandCoverageTest` 与中文文档（`docs/14-RPC命令参考.md`）。
- 文档位于 `docs/`（00–18 号中文文档），`docs/README.md` 是分类索引，改动公共 API 需同步。

## 关键语义与陷阱

1. **`accepted()` ≠ 完成**。`PiRun.accepted()` 只是 PI 已接受/排队 prompt；真正的完成点是 `PiRun.settled()`（收到 `agent_settled`，自动重试/压缩/延续消息均已结束）。
2. **`prompt()` 默认禁止并行**：同一客户端不能同时跑两个普通 run，否则无法关联无请求 ID 的流式事件。中途追加用 `steer()`（当前 tool call 后插入）或 `followUp()`（完全停止后处理）。
3. **监听器异常不会中断协议读取**，用 `listenerErrorHandler(...)` 集中记录。
4. **`workingDirectory` 不是安全沙箱**。PI 默认拥有 read/write/edit/bash 工具；不要把未认证公网请求直接转发给 PI。
5. **平台差异与 PI 路径**：Windows 默认启动 `pi.cmd`，macOS/Linux 默认 `pi`；两者都是**裸名**，靠 `PATH` 解析，所以默认无需配置路径。但 Windows 的 `CreateProcess` **不做 `PATHEXT` 补全**，`executable("pi")` 会直接失败（必须写 `pi.cmd`）；`PATH` 不可靠的环境（IDE/Windows 服务/Docker/CI）需显式配绝对路径或 `command(List.of("node", "<cli.js 路径>"))`。详见 `docs/02` 的「PI 可执行文件路径解析」。
6. **反压**：事件缓冲（`eventBufferCapacity`）+ 溢出策略（`PiEventOverflowStrategy`），慢消费者可能触发丢弃或阻塞。
7. 未识别的新版本 PI 事件仍以原始 `JsonNode` 交付，事件解码要向前兼容。
