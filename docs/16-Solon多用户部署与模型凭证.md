# Solon 多用户部署与模型凭证

## models.json 如何区分用户

不要为每个用户动态修改同一个 `models.json`，也不要把真实 API Key 写进该文件。`pi-solon-assistant` 使用两层配置：

1. `PI_MODELS_TEMPLATE` 指向管理员维护的共享模型定义，API Key 写成环境变量占位符 `PI_USER_MODEL_API_KEY`。
2. 创建对话时，把模板复制到该用户的 PI 配置目录，并把用户密钥只注入该对话的 PI 子进程环境。

模板示例：

```json
{
  "providers": {
    "company-gateway": {
      "baseUrl": "https://llm.example.com/v1",
      "api": "openai-completions",
      "apiKey": "$PI_USER_MODEL_API_KEY",
      "models": [
        {
          "id": "company-chat",
          "name": "Company Chat",
          "reasoning": false,
          "input": ["text"],
          "contextWindow": 128000,
          "maxTokens": 8192
        }
      ]
    }
  }
}
```

PowerShell：

```powershell
$env:PI_USE_LOCAL_CONFIG = "false"
$env:PI_MODELS_TEMPLATE = "D:\config\pi\models.json"
$env:PI_ASSISTANT_DATA_ROOT = "D:\data\pi-assistant"
$env:PI_ASSISTANT_WORKSPACE_ROOT = "D:\workspaces\pi-assistant"
$env:PI_REQUIRE_USER_HEADER = "true"
java -jar pi-solon-assistant\target\pi-solon-assistant.jar
```

最终目录结构：

```text
data-root/
├─ user-a/
│  ├─ agent/models.json
│  └─ sessions/*.jsonl
└─ user-b/
   ├─ agent/models.json
   └─ sessions/*.jsonl

workspace-root/
├─ user-a/
└─ user-b/
```

`models.json` 副本只包含模型元数据和环境变量名。用户 A、B 即使选择相同模型，实际密钥也来自各自 PI 进程环境。

## 身份边界

`X-User-Id` 不是登录机制。正确链路是：

```text
浏览器 Cookie/JWT → 认证网关验证 → 删除外部 X-User-Id → 注入可信 X-User-Id → Solon
```

必须让外部请求不能伪造该请求头。用户 ID 只允许字母、数字、点、下划线和连字符，长度为 1-64，用于会话归属检查和安全目录名。

## 密钥来源

示例 API 允许创建对话时传 `apiKey`，适合本地开发。生产建议把 `PiConversationManager` 前增加凭证服务：

```text
userId + provider → KMS/Vault 中的密钥 → PI 子进程环境
```

这样浏览器永远接触不到服务端托管密钥。无论哪种来源，都不要记录请求体，不要把密钥放入命令行参数，也不要在会话摘要中返回。活动对话的 Java 进程配置会在内存中保留环境值，直到对话关闭并等待垃圾回收，因此 JVM 和堆转储也必须作为密钥边界保护。

## 云上进程与资源限制

一个活动对话对应一个 PI 子进程。`PI_MAX_CONVERSATIONS` 控制节点总量，`PI_MAX_CONVERSATIONS_PER_USER` 防止单用户耗尽进程。多节点部署时，活动对话 ID 只存在创建它的节点内存中，因此需要负载均衡粘性会话，或把对话路由表放入共享存储。

文件目录和进程环境只能避免应用层串租户。PI 具备 Shell 和文件工具，强隔离应使用容器、虚拟机或不同的受限系统账号，并为每个用户设置 CPU、内存、进程数、磁盘和网络策略。
