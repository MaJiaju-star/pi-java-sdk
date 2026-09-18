# API 覆盖与兼容性

## 当前覆盖

| 能力 | 覆盖情况 |
| --- | --- |
| Coding Agent JSONL RPC 命令 | 33/33，全部有枚举；常用返回值已强类型化 |
| prompt、steer、follow-up、图片和中止 | 完整 |
| 模型、思考等级和队列模式 | 完整强类型方法 |
| 压缩、自动重试和直接 Bash | 完整强类型方法与事件 |
| 会话切换、分支、克隆、统计、导出和树 | 完整方法；开放消息内容保留 `JsonNode` |
| 历史会话枚举 | RPC 协议无此命令；由 `SessionLister` 在文件系统层面提供（不启动 PI） |
| 流式事件 | 全部已知顶层事件有 `PiTypedEvent` 视图，未知事件保留原始 JSON |
| Extension UI | 请求解析、订阅以及 value、confirm、cancel 回复完整 |
| 请求超时、stderr、退出通知和事件反压 | 完整 |
| 多会话进程池和显式恢复 | 已提供 |
| PI Protocol v1 CBOR | 独立实验模块，覆盖帧、CBOR、握手和 9 个命令 |
| `pi-ai` Provider 直连、Agent 内核嵌入、TUI | 不提供；这些是 Node.js 内部 SDK，不属于 RPC 客户端 |

## 为什么部分字段仍是 JsonNode

PI 的 `AgentMessage`、会话条目、工具详情和扩展数据是开放联合类型，扩展可以增加自定义结构。强行封闭为 Java 类会在升级或加载第三方扩展时丢字段。因此顶层命令和事件采用强类型，开放内容保留 `JsonNode`；这属于兼容设计，不是命令缺失。

## 协议漂移检查

测试 `PiRpcCommandCoverageTest` 会读取 TypeScript 的 `rpc-types.ts`，比较 Java `PiRpcCommand` 枚举。当 PI 增加或移除命令时，构建会失败并提示同步 SDK。

该测试用 `Assumptions.assumeTrue` 保护，只有本地存在 PI 源码（`packages/coding-agent/src/modes/rpc/rpc-types.ts`）时才实际运行；启用方法见 [17-对齐审计](17-对齐审计.md)。

## 已验证版本

| 组件 | 版本 |
| --- | --- |
| JDK | 21 |
| PI 仓库/CLI（运行时 `TESTED_VERSION`） | 0.84.4 |
| PI 源码（协议对齐审计） | 0.85.1 |
| Solon | 4.0.6 |
| Jackson | 2.22.2 |

运行时可用 `PiCliVersion.detect(config)` 探测 CLI。JSONL RPC 没有独立协议版本号，因此升级 PI 后应运行 SDK 测试和真实进程冒烟测试。

## 与 TypeScript SDK 的边界

Java SDK 现在对“外部程序控制 PI Coding Agent”已接近完整。它不会复制 TypeScript 的 Provider 实现、Agent 循环、扩展运行时或终端组件；如果目标是在 JVM 内重新实现整个 PI 内核，那是另一套项目，而不是 RPC SDK 的完备度问题。
