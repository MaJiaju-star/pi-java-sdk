# Solon Web 对话助手

`pi-solon-assistant` 是 `pi-java-sdk` 的完整 Web 示例。默认使用本机 PI 配置，提供多活动会话、多轮对话、历史消息、SSE 流式事件、会话控制、Extension UI 和工作区文件接口。云部署时可以切换为按用户隔离配置。

## 本机默认模式

默认 `PI_USE_LOCAL_CONFIG=true`。服务不会设置 `PI_CODING_AGENT_DIR`，因此 PI 直接读取当前系统用户已有的 `~/.pi/agent/models.json`、`auth.json`、`settings.json`、扩展和提示词。新建对话不需要在页面重复填写 Provider、Model 或 API Key。

PI 工作目录默认是启动服务时的当前目录，可用 `PI_WORKSPACE` 修改。会话 JSONL 仍保存到应用的数据目录，便于 Web 页面统一列出和恢复。

## 运行结构

```text
认证网关 ──X-User-Id──> Solon API
                          ├─ 用户 A / 对话 1 ── PI RPC 进程 ── 用户 A API Key
                          ├─ 用户 A / 对话 2 ── PI RPC 进程 ── 用户 A API Key
                          └─ 用户 B / 对话 1 ── PI RPC 进程 ── 用户 B API Key
```

独立进程是凭证隔离的必要边界。例如用户 A 和用户 B 使用同一个自定义模型定义，但密钥不同；两个进程读取各自的 `PI_USER_MODEL_API_KEY`，不会修改共享模型定义，也不会共享内存中的密钥。

## 构建与启动

```powershell
cd java
mvn clean verify
java -jar pi-solon-assistant/target/pi-solon-assistant.jar
```

浏览器访问 `http://localhost:8080`。修改端口：

```powershell
java -jar pi-solon-assistant/target/pi-solon-assistant.jar --server.port=9090
```

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `PI_EXECUTABLE` | Windows 为 `pi.cmd`，其他系统为 `pi` | PI CLI 路径 |
| `PI_USE_LOCAL_CONFIG` | `true` | 直接使用当前系统用户的默认 PI 配置 |
| `PI_WORKSPACE` | 当前目录 | 本机默认模式下 PI 可以操作的工作目录 |
| `PI_PROVIDER` | PI 默认配置 | 默认模型提供商 |
| `PI_MODEL` | PI 默认配置 | 默认模型 ID |
| `PI_NO_SESSION` | `false` | 是否禁止 PI 会话落盘；开启后不能恢复会话 |
| `PI_ASSISTANT_DATA_ROOT` | `.pi-assistant/data` | 按用户保存 PI 配置副本和会话文件 |
| `PI_ASSISTANT_WORKSPACE_ROOT` | `.pi-assistant/workspaces` | 按用户隔离的工作区根目录 |
| `PI_MODELS_TEMPLATE` | 未设置 | 隔离模式使用的共享 `models.json` 模板路径 |
| `PI_REQUIRE_USER_HEADER` | `false` | 是否强制要求可信 `X-User-Id` 请求头 |
| `PI_MAX_CONVERSATIONS` | `100` | 全局活动 PI 进程上限 |
| `PI_MAX_CONVERSATIONS_PER_USER` | `5` | 单用户活动 PI 进程上限 |
| `PI_WORKSPACE_QUOTA_BYTES` | `52428800` | 单用户工作区配额 |
| `PI_MAX_TEXT_FILE_BYTES` | `2097152` | 文本读写接口单文件上限 |
| `PI_MAX_TREE_ENTRIES` | `5000` | 单次文件树查询最大节点数 |

> `PI_EXECUTABLE` 的默认值与 SDK 的 `defaultPiExecutable()` 一致，都是**裸可执行文件名**，由 `PATH` 解析。
> 服务化部署（systemd / Windows 服务 / 容器）下 `PATH` 往往与登录 shell 不同，建议直接填绝对路径；
> Windows 上必须带 `.cmd` 后缀。详见 [02-客户端配置与生命周期 — PI 可执行文件路径解析](02-客户端配置与生命周期.md#pi-可执行文件路径解析)。

## 对话 API

所有接口按 `X-User-Id` 区分数据。开发模式缺少该请求头时使用 `anonymous`。本机默认模式会共享本机 PI 认证配置，因此不构成安全的多租户边界。

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `POST` | `/api/conversations` | 创建新对话或按 `sessionId` 恢复会话 |
| `GET` | `/api/conversations` | 列出当前用户的活动对话 |
| `GET` | `/api/conversations/{id}` | 获取对话摘要 |
| `GET` | `/api/conversations/{id}/state` | 获取 PI 会话状态 |
| `GET` | `/api/conversations/{id}/messages` | 获取历史消息 |
| `GET` | `/api/conversations/{id}/stats` | 获取 token、费用和工具调用统计 |
| `GET` | `/api/conversations/{id}/models` | 获取当前凭证可用模型 |
| `GET` | `/api/conversations/{id}/events` | 订阅原始 PI SSE 事件 |
| `POST` | `/api/conversations/{id}/messages` | 发送、steer 或排队消息 |
| `POST` | `/api/conversations/{id}/abort` | 中止当前生成 |
| `POST` | `/api/conversations/{id}/title` | 修改会话标题 |
| `POST` | `/api/conversations/{id}/ui-responses` | 回复 Extension UI 请求 |
| `DELETE` | `/api/conversations/{id}` | 关闭活动进程，保留持久化历史 |

持久化历史另有独立接口：

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `GET` | `/api/history` | 列出当前用户已落盘的 PI 会话 |
| `GET` | `/api/history/{sessionId}/messages` | 读取 JSONL 中的原始消息对象 |
| `DELETE` | `/api/history/{sessionId}` | 删除非活动会话文件 |

### 创建对话

```http
POST /api/conversations
Content-Type: application/json
X-User-Id: user-42

{
  "title": "代码审查",
  "provider": "my-provider",
  "model": "my-model",
  "apiKey": "用户自己的密钥"
}
```

`apiKey` 通过新 PI 子进程配置的 `PI_USER_MODEL_API_KEY` 传递，不写入响应、日志、会话文件或 `models.json`。SDK 的进程配置会在该活动对话生命周期内保留环境值，因此 JVM 进程仍属于密钥的可信边界。生产系统更适合由后端凭证库按已认证用户查询密钥，而不是由浏览器传入。

响应中的 `id` 是活动进程的 Web ID，`sessionId` 是 PI 的持久化会话 ID。服务重启或关闭活动进程后，用后者恢复：

```json
{"sessionId":"此前返回的 PI sessionId","apiKey":"该用户的密钥"}
```

### 发送消息

正常开始新一轮：

```json
{"message":"分析当前项目结构","behavior":"prompt"}
```

PI 正在生成时可以改变当前方向：

```json
{"message":"只检查 Java 模块","behavior":"steer"}
```

也可以将消息排到本轮之后：

```json
{"message":"完成后给出迁移清单","behavior":"follow_up"}
```

### SSE 事件

```http
GET /api/conversations/{id}/events
Accept: text/event-stream
```

每条 `data` 都是 PI 原始 RPC 事件。内置页面处理 `thinking_start`、`thinking_delta`、`thinking_end` 的完整思考生命周期，并在 `message_end` 使用最终消息补全可能遗漏的增量；思考内容显示在默认展开、可手动折叠的独立卡片中。`text_delta` 显示在 PI 正文卡片中。最终消息应以 `message_end.message` 为准；`agent_settled` 表示重试、压缩和后续队列均已结束。

消息接口会把 SDK 内部的 Jackson `JsonNode` 转换为标准 JSON 对象后再交给 Solon 序列化，避免默认序列化器把历史消息输出为空数组。活动对话和持久化历史均使用相同的页面渲染结构。

内置页面会把工具生命周期单独显示：

| PI 事件 | 页面显示 | 内容 |
| --- | --- | --- |
| `tool_execution_start` | `PreTool` | 工具名称、调用 ID 和调用参数 |
| `tool_execution_update` | 更新对应的 `PreTool` | 当前累计执行结果 |
| `tool_execution_end` | `PostTool` | 最终结果和成功或失败状态 |

页面通过 `toolCallId` 关联同一次调用，并把 `PreTool` 和 `PostTool` 合并到一张可折叠生命周期卡片中。卡片分别展示输入参数、累计执行进度和最终结果；成功后自动收起，失败时保持展开。重新打开活动对话或持久化历史时，助手消息中的 `toolCall` 和对应的 `toolResult` 会恢复到同一张卡片。

## 工作区文件 API

文件接口只接受相对路径，拒绝越过用户根目录和通过符号链接访问。删除接口不能删除工作区根目录。

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `GET` | `/api/files/usage` | 查询容量和配额 |
| `GET` | `/api/files/tree?path=src&depth=4` | 获取文件树 |
| `GET` | `/api/files/content?path=README.md` | 读取 UTF-8 文本 |
| `POST` | `/api/files/content` | 写入 `{"path":"a.txt","content":"..."}` |
| `POST` | `/api/files/directories` | 创建 `{"path":"src/main/java"}` |
| `GET` | `/api/files/download?path=result.zip` | 下载普通文件 |
| `DELETE` | `/api/files?path=build` | 递归删除文件或目录 |

## 当前边界

- 活动对话元数据在内存中；PI 消息由 JSONL 会话文件持久化。调用方应保存创建响应中的 `sessionId`，以便服务重启后恢复。
- 一个活动对话只能运行一个普通 `prompt`；生成期间应使用 `steer` 或 `follow_up`。
- 默认本机配置模式只用于单用户开发。云部署必须设置 `PI_USE_LOCAL_CONFIG=false`，否则所有用户会共享宿主机的 PI 配置和凭证。
- 工作区目录隔离不能替代操作系统隔离。PI 可以执行命令，云部署应让不同用户运行在不同容器或受限系统账号中。
- 浏览器 `EventSource` 不能设置自定义请求头。生产环境应由同源认证网关从 Cookie 或登录会话解析身份并注入 `X-User-Id`。
- 当前没有实现文件上传和压缩包解压；可基于同一 `UserWorkspaceService` 的路径与配额边界扩展。
