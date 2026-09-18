# PI Java 文档

本目录是 Java SDK 与 Solon 示例的中文文档入口。稳定集成优先使用 `pi-java-sdk`；只有连接独立 PI Server 时才使用实验性的 `pi-java-remote`。

## 入门

- [包结构](00-包结构.md)
- [安装与快速开始](01-安装与快速开始.md)
- [客户端配置与生命周期](02-客户端配置与生命周期.md)
- [对话与图片](03-对话与图片.md)
- [流式事件](04-流式事件.md)

## 功能

- [模型与思考等级](05-模型与思考等级.md)
- [队列、压缩、重试与 Bash](06-队列压缩重试与Bash.md)
- [会话与持久化](07-会话与持久化.md)
- [Extension UI](08-Extension-UI.md)
- [会话池与故障恢复](09-会话池与故障恢复.md)

## 集成与运维

- [实验性远程 CBOR 协议](10-实验性远程CBOR协议.md)
- [Solon Web 对话助手](11-Solon-Web对话助手.md)
- [错误处理、安全与反压](12-错误处理安全与反压.md)
- [API 覆盖与兼容性](13-API覆盖与兼容性.md)
- [RPC 命令参考](14-RPC命令参考.md)
- [Javadoc 与 API 注释](15-Javadoc与API注释.md)
- [Solon 多用户部署与模型凭证](16-Solon多用户部署与模型凭证.md)
- [对齐审计（Java vs TypeScript RPC）](17-对齐审计.md)

## 模块

| 模块 | 稳定性 | 用途 |
| --- | --- | --- |
| `pi-java-sdk` | 稳定接口 | 启动 `pi --mode rpc`，控制本地 Coding Agent |
| `pi-java-remote` | 实验性 | 通过任意有序字节传输连接 PI Protocol v1 服务端 |
| `pi-solon-assistant` | 示例 | 多用户、多会话、REST + SSE 的浏览器对话助手 |
