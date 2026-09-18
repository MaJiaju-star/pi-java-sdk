# 实验性远程 CBOR 协议

`pi-java-remote` 面向 PI Protocol v1 服务端。它与 `pi --mode rpc` 的 JSONL 协议不是同一接口，不能混用。

## 依赖

```xml
<dependency>
    <groupId>works.earendil.pi</groupId>
    <artifactId>pi-java-remote</artifactId>
    <version>0.1.1-SNAPSHOT</version>
</dependency>
```

## 传输接口

应用实现 `PiRemoteTransportFactory`，每次返回一个完成认证的有序双向字节流：

```java
PiRemoteClientConfig config = PiRemoteClientConfig.defaults(() -> new PiRemoteTransport() {
    public InputStream input() { return socketInput; }
    public OutputStream output() { return socketOutput; }
    public void close() throws Exception { socket.close(); }
});

try (PiRemoteClient client = PiRemoteClient.connect(config)) {
    JsonNode sessions = client.listSessions().join();
}
```

认证在传输层完成，协议 hello 中不携带凭据。

## 帧格式

每条消息为：

```text
4 字节无符号大端负载长度 + 1 个定长 CBOR 项
```

`PiCborCodec` 实现协议要求的严格子集：有限数字、JavaScript 安全整数、严格 UTF-8、字符串键 map、定长数组和 map，并拒绝 tag、不定长值、重复键、尾随数据和超限帧。

## 会话命令

```java
JsonNode created = client.createSession(
        "/workspace", "审查", "anthropic", "claude-sonnet-4-6", "high").join();
String id = created.path("session").path("id").asText();

client.attachSession(id).join();
client.prompt(id, "检查项目").join();
client.steer(id, "先看测试").join();
client.setModel(id, "openai", "gpt-5.4").join();
client.setThinking(id, "high").join();
client.abort(id).join();
client.detachSession(id).join();
```

## 状态原则

- `server_snapshot` 和 `session_snapshot` 是权威状态。
- `session_progress` 只是临时 UI 提示，不应自行归并为权威会话。
- 一条远程连接可以附加多个会话。
- 当前协议是实验性的，不保证兼容；生产集成优先使用稳定 JSONL SDK。
