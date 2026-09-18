package works.earendil.pi.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import works.earendil.pi.conversation.ThinkingLevel;
import works.earendil.pi.extension.PiExtensionUiRequest;
import works.earendil.pi.rpc.PiRpcTypes;

/**
 * PI 事件的强类型视图。
 *
 * <p>每个视图仍保存原始 JSON，以便调用方读取新版本增加但 SDK 尚未建模的字段。</p>
 */
public sealed interface PiTypedEvent {
    /**
     * 返回事件类型。
     *
     * @return 已知事件类型，未知协议值返回 {@link PiEventType#UNKNOWN}
     */
    PiEventType type();

    /**
     * 返回原始事件。
     *
     * @return PI 输出的完整原始事件 JSON
     */
    JsonNode raw();

    /**
     * 不包含额外结构化字段的生命周期标记事件。
     *
     * @param type 事件类型
     * @param raw 完整原始事件 JSON
     */
    record Marker(PiEventType type, JsonNode raw) implements PiTypedEvent {
    }

    /**
     * Agent 一轮运行结束事件。
     *
     * @param messages 本轮产生的消息
     * @param willRetry 是否将自动重试
     * @param raw 完整原始事件 JSON
     */
    record AgentEnd(List<JsonNode> messages, boolean willRetry, JsonNode raw) implements PiTypedEvent {
        @Override
        public PiEventType type() {
            return PiEventType.AGENT_END;
        }
    }

    /**
     * 单个模型回合结束事件。
     *
     * @param message 助手消息
     * @param toolResults 本回合的工具结果
     * @param raw 完整原始事件 JSON
     */
    record TurnEnd(JsonNode message, List<JsonNode> toolResults, JsonNode raw) implements PiTypedEvent {
        @Override
        public PiEventType type() {
            return PiEventType.TURN_END;
        }
    }

    /**
     * 消息开始或结束事件。
     *
     * @param type {@link PiEventType#MESSAGE_START} 或 {@link PiEventType#MESSAGE_END}
     * @param message 完整消息
     * @param raw 完整原始事件 JSON
     */
    record MessageLifecycle(PiEventType type, JsonNode message, JsonNode raw) implements PiTypedEvent {
    }

    /**
     * 助手消息的内部增量事件。
     *
     * @param type 增量类型，例如 {@code text_delta}
     * @param contentIndex 消息内容块索引
     * @param delta 本次增量文本
     * @param content 当前累计内容
     * @param id 工具调用 ID
     * @param toolName 工具名称
     * @param toolCall 工具调用原始对象
     */
    record AssistantMessageEvent(
            String type,
            int contentIndex,
            String delta,
            String content,
            String id,
            String toolName,
            JsonNode toolCall
    ) {
    }

    /**
     * 助手消息增量事件。
     *
     * @param usage 当前累计用量；协议未提供时为 {@code null}
     * @param assistantMessageEvent 具体消息增量
     * @param raw 完整原始事件 JSON
     */
    record MessageUpdate(
            PiRpcTypes.Usage usage,
            AssistantMessageEvent assistantMessageEvent,
            JsonNode raw
    ) implements PiTypedEvent {
        @Override
        public PiEventType type() {
            return PiEventType.MESSAGE_UPDATE;
        }
    }

    /**
     * 工具执行开始、更新或结束事件。
     *
     * @param type 工具执行生命周期类型
     * @param toolCallId 工具调用 ID
     * @param toolName 工具名称
     * @param args 工具参数
     * @param partialResult 执行中的部分结果
     * @param result 最终结果
     * @param error 最终结果是否为错误
     * @param raw 完整原始事件 JSON
     */
    record ToolExecution(
            PiEventType type,
            String toolCallId,
            String toolName,
            JsonNode args,
            JsonNode partialResult,
            JsonNode result,
            boolean error,
            JsonNode raw
    ) implements PiTypedEvent {
    }

    /**
     * steering 和 follow-up 队列状态事件。
     *
     * @param steering 等待注入当前运行的消息
     * @param followUp 等待后续运行处理的消息
     * @param raw 完整原始事件 JSON
     */
    record QueueUpdate(List<String> steering, List<String> followUp, JsonNode raw) implements PiTypedEvent {
        @Override
        public PiEventType type() {
            return PiEventType.QUEUE_UPDATE;
        }
    }

    /**
     * 上下文压缩开始或结束事件。
     *
     * @param type 压缩生命周期类型
     * @param reason 触发原因
     * @param result 压缩结果
     * @param aborted 是否已中止
     * @param willRetry 是否将重试
     * @param errorMessage 错误信息
     * @param raw 完整原始事件 JSON
     */
    record Compaction(
            PiEventType type,
            String reason,
            PiRpcTypes.CompactionResult result,
            boolean aborted,
            boolean willRetry,
            String errorMessage,
            JsonNode raw
    ) implements PiTypedEvent {
    }

    /**
     * 自动重试开始或结束事件。
     *
     * @param type 重试生命周期类型
     * @param attempt 当前尝试次数
     * @param maxAttempts 最大尝试次数
     * @param delayMillis 下一次尝试前的延迟毫秒数
     * @param errorMessage 本次错误信息
     * @param success 最终是否成功
     * @param finalError 最终错误信息
     * @param raw 完整原始事件 JSON
     */
    record Retry(
            PiEventType type,
            int attempt,
            int maxAttempts,
            long delayMillis,
            String errorMessage,
            boolean success,
            String finalError,
            JsonNode raw
    ) implements PiTypedEvent {
    }

    /**
     * 摘要生成重试事件。
     *
     * @param type 重试阶段类型
     * @param source 摘要来源
     * @param reason 重试原因
     * @param raw 完整原始事件 JSON
     */
    record SummarizationRetry(PiEventType type, String source, String reason, JsonNode raw) implements PiTypedEvent {
    }

    /**
     * Bash 命令输出增量事件。
     *
     * @param requestId Bash 请求 ID
     * @param delta 输出增量
     * @param raw 完整原始事件 JSON
     */
    record BashUpdate(String requestId, String delta, JsonNode raw) implements PiTypedEvent {
        @Override
        public PiEventType type() {
            return PiEventType.BASH_EXECUTION_UPDATE;
        }
    }

    /**
     * 会话条目追加事件。
     *
     * @param entry 新条目
     * @param raw 完整原始事件 JSON
     */
    record EntryAppended(JsonNode entry, JsonNode raw) implements PiTypedEvent {
        @Override
        public PiEventType type() {
            return PiEventType.ENTRY_APPENDED;
        }
    }

    /**
     * 会话信息变更事件。
     *
     * @param name 新会话名称
     * @param raw 完整原始事件 JSON
     */
    record SessionInfoChanged(String name, JsonNode raw) implements PiTypedEvent {
        @Override
        public PiEventType type() {
            return PiEventType.SESSION_INFO_CHANGED;
        }
    }

    /**
     * 思考等级变更事件。
     *
     * @param level 新思考等级
     * @param raw 完整原始事件 JSON
     */
    record ThinkingLevelChanged(ThinkingLevel level, JsonNode raw) implements PiTypedEvent {
        @Override
        public PiEventType type() {
            return PiEventType.THINKING_LEVEL_CHANGED;
        }
    }

    /**
     * PI 扩展错误事件。
     *
     * @param extensionPath 扩展路径
     * @param event 发生错误的扩展事件名
     * @param error 错误信息
     * @param raw 完整原始事件 JSON
     */
    record ExtensionError(String extensionPath, String event, String error, JsonNode raw) implements PiTypedEvent {
        @Override
        public PiEventType type() {
            return PiEventType.EXTENSION_ERROR;
        }
    }

    /**
     * Extension UI 请求事件。
     *
     * @param request 强类型 UI 请求
     * @param raw 完整原始事件 JSON
     */
    record ExtensionUi(PiExtensionUiRequest request, JsonNode raw) implements PiTypedEvent {
        @Override
        public PiEventType type() {
            return PiEventType.EXTENSION_UI_REQUEST;
        }
    }

    /**
     * 当前 SDK 尚未识别的事件。
     *
     * @param wireType 原始协议类型
     * @param raw 完整原始事件 JSON
     */
    record Unknown(String wireType, JsonNode raw) implements PiTypedEvent {
        @Override
        public PiEventType type() {
            return PiEventType.UNKNOWN;
        }
    }
}
