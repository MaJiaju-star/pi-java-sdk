package works.earendil.pi.sse;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import works.earendil.pi.extension.PiExtensionUiRequest;
import works.earendil.pi.rpc.PiRpcTypes;

/**
 * 面向 SSE 的规范化流式事件。
 *
 * <p>PI 的原始事件（{@link works.earendil.pi.event.PiEvent}）忠于协议原貌：{@code message_update}
 * 需要读取嵌套的 {@code assistantMessageEvent}，工具调用参数流与执行流分属两类事件。本接口把它们
 * 规范化、扁平化为自描述实体，每个语义事件对应一个 record，便于前端和后端直接反序列化。</p>
 *
 * <p>每个事件都携带：{@link #seq()} 单调递增序号（用于 {@code Last-Event-ID} 重放）、
 * {@link #type()} 语义事件名（用于 SSE 的 {@code event:} 字段）、{@link #raw()} 原始 PI 事件 JSON
 * （向前兼容，保留全部未建模字段）。</p>
 *
 * <p>实体由 {@link SseEventMapper} 生成，帧由 {@link SseFrameEncoder} 编码。</p>
 */
public sealed interface SseEvent {

    /**
     * 事件序号，从 1 开始单调递增。
     *
     * @return 事件序号
     */
    long seq();

    /**
     * 语义事件名，用于 SSE 的 {@code event:} 字段。
     *
     * @return 语义事件名
     */
    String type();

    /**
     * 原始 PI 事件 JSON，保留全部未建模字段。
     *
     * @return 原始事件 JSON
     */
    JsonNode raw();

    // ========================================================================
    // message_update：文本与思考增量
    // ========================================================================

    /**
     * 文本块开始。
     *
     * @param seq 事件序号
     * @param contentIndex 消息内容块索引
     * @param raw 原始事件 JSON
     */
    record TextStart(long seq, int contentIndex, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "text_start";
        }
    }

    /**
     * 文本增量，实时正文。
     *
     * @param seq 事件序号
     * @param contentIndex 消息内容块索引
     * @param text 本次增量文本
     * @param raw 原始事件 JSON
     */
    record TextDelta(long seq, int contentIndex, String text, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "text_delta";
        }
    }

    /**
     * 文本块结束，携带该块完整文本。
     *
     * @param seq 事件序号
     * @param contentIndex 消息内容块索引
     * @param text 该块完整文本
     * @param raw 原始事件 JSON
     */
    record TextEnd(long seq, int contentIndex, String text, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "text_end";
        }
    }

    /**
     * 思考块开始。
     *
     * @param seq 事件序号
     * @param contentIndex 消息内容块索引
     * @param raw 原始事件 JSON
     */
    record ThinkingStart(long seq, int contentIndex, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "thinking_start";
        }
    }

    /**
     * 思考增量。
     *
     * @param seq 事件序号
     * @param contentIndex 消息内容块索引
     * @param text 本次增量文本
     * @param raw 原始事件 JSON
     */
    record ThinkingDelta(long seq, int contentIndex, String text, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "thinking_delta";
        }
    }

    /**
     * 思考块结束，携带该块完整文本。
     *
     * @param seq 事件序号
     * @param contentIndex 消息内容块索引
     * @param text 该块完整文本
     * @param raw 原始事件 JSON
     */
    record ThinkingEnd(long seq, int contentIndex, String text, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "thinking_end";
        }
    }

    // ========================================================================
    // message_update：工具调用参数流
    // ========================================================================

    /**
     * 工具调用参数开始。
     *
     * @param seq 事件序号
     * @param contentIndex 消息内容块索引
     * @param toolCallId 工具调用 ID
     * @param toolName 工具名称
     * @param raw 原始事件 JSON
     */
    record ToolCallStart(long seq, int contentIndex, String toolCallId, String toolName, JsonNode raw)
            implements SseEvent {
        @Override
        public String type() {
            return "toolcall_start";
        }
    }

    /**
     * 工具调用参数增量。
     *
     * @param seq 事件序号
     * @param contentIndex 消息内容块索引
     * @param delta 本次增量文本
     * @param raw 原始事件 JSON
     */
    record ToolCallDelta(long seq, int contentIndex, String delta, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "toolcall_delta";
        }
    }

    /**
     * 工具调用参数完成。
     *
     * @param seq 事件序号
     * @param contentIndex 消息内容块索引
     * @param toolCallId 工具调用 ID
     * @param toolName 工具名称
     * @param arguments 工具调用参数
     * @param raw 原始事件 JSON
     */
    record ToolCallEnd(long seq, int contentIndex, String toolCallId, String toolName, JsonNode arguments, JsonNode raw)
            implements SseEvent {
        @Override
        public String type() {
            return "toolcall_end";
        }
    }

    // ========================================================================
    // message_update：助手消息级事件
    // ========================================================================

    /**
     * 助手消息助手侧完成（{@code assistantMessageEvent.type == "done"}）。
     *
     * @param seq 事件序号
     * @param reason 停止原因，例如 {@code stop}、{@code toolUse}
     * @param raw 原始事件 JSON
     */
    record MessageDone(long seq, String reason, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "message_done";
        }
    }

    /**
     * 助手消息出错（{@code assistantMessageEvent.type == "error"}）。
     *
     * @param seq 事件序号
     * @param reason 错误原因，例如 {@code aborted}、{@code error}
     * @param error 错误详情 JSON
     * @param raw 原始事件 JSON
     */
    record MessageFailed(long seq, String reason, JsonNode error, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "message_error";
        }
    }

    /**
     * 其它助手消息增量子类型（当前未单独建模的 {@code message_update}）。
     *
     * @param seq 事件序号
     * @param subtype 原始子类型
     * @param raw 原始事件 JSON
     */
    record MessageChunk(long seq, String subtype, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "message_chunk";
        }
    }

    // ========================================================================
    // 消息生命周期
    // ========================================================================

    /**
     * 消息开始。
     *
     * @param seq 事件序号
     * @param message 消息 JSON
     * @param raw 原始事件 JSON
     */
    record MessageStart(long seq, JsonNode message, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "message_start";
        }
    }

    /**
     * 消息结束，携带最终权威消息。
     *
     * @param seq 事件序号
     * @param message 最终消息 JSON
     * @param usage token 用量；协议未提供时为 {@code null}
     * @param raw 原始事件 JSON
     */
    record MessageEnd(long seq, JsonNode message, PiRpcTypes.Usage usage, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "message_end";
        }
    }

    // ========================================================================
    // Agent 与 Turn 生命周期
    // ========================================================================

    /**
     * Agent 开始运行。
     *
     * @param seq 事件序号
     * @param raw 原始事件 JSON
     */
    record AgentStart(long seq, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "agent_start";
        }
    }

    /**
     * Agent 本轮运行结束。
     *
     * @param seq 事件序号
     * @param messageCount 本轮产生的消息数
     * @param willRetry 是否将自动重试
     * @param raw 原始事件 JSON
     */
    record AgentEnd(long seq, int messageCount, boolean willRetry, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "agent_end";
        }
    }

    /**
     * 重试、压缩和后续队列均已结束，整轮真正稳定。
     *
     * @param seq 事件序号
     * @param raw 原始事件 JSON
     */
    record AgentSettled(long seq, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "agent_settled";
        }
    }

    /**
     * 模型回合开始。
     *
     * @param seq 事件序号
     * @param raw 原始事件 JSON
     */
    record TurnStart(long seq, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "turn_start";
        }
    }

    /**
     * 模型回合结束。
     *
     * @param seq 事件序号
     * @param message 本回合助手消息
     * @param toolResultCount 本回合工具结果数
     * @param raw 原始事件 JSON
     */
    record TurnEnd(long seq, JsonNode message, int toolResultCount, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "turn_end";
        }
    }

    // ========================================================================
    // 工具执行
    // ========================================================================

    /**
     * 工具开始执行。
     *
     * @param seq 事件序号
     * @param toolCallId 工具调用 ID
     * @param toolName 工具名称
     * @param args 工具参数
     * @param raw 原始事件 JSON
     */
    record ToolStart(long seq, String toolCallId, String toolName, JsonNode args, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "tool_start";
        }
    }

    /**
     * 工具执行进度更新。
     *
     * @param seq 事件序号
     * @param toolCallId 工具调用 ID
     * @param toolName 工具名称
     * @param partialResult 累计部分结果
     * @param raw 原始事件 JSON
     */
    record ToolUpdate(long seq, String toolCallId, String toolName, JsonNode partialResult, JsonNode raw)
            implements SseEvent {
        @Override
        public String type() {
            return "tool_update";
        }
    }

    /**
     * 工具执行结束。
     *
     * @param seq 事件序号
     * @param toolCallId 工具调用 ID
     * @param toolName 工具名称
     * @param result 最终结果
     * @param error 是否为错误结果
     * @param raw 原始事件 JSON
     */
    record ToolEnd(long seq, String toolCallId, String toolName, JsonNode result, boolean error, JsonNode raw)
            implements SseEvent {
        @Override
        public String type() {
            return "tool_end";
        }
    }

    // ========================================================================
    // 队列、压缩、重试
    // ========================================================================

    /**
     * steering 或 follow-up 队列变更。
     *
     * @param seq 事件序号
     * @param steering 等待注入当前运行的消息
     * @param followUp 等待后续运行处理的消息
     * @param raw 原始事件 JSON
     */
    record QueueUpdate(long seq, List<String> steering, List<String> followUp, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "queue_update";
        }
    }

    /**
     * 上下文压缩开始。
     *
     * @param seq 事件序号
     * @param reason 触发原因：{@code manual}、{@code threshold} 或 {@code overflow}
     * @param raw 原始事件 JSON
     */
    record CompactionStart(long seq, String reason, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "compaction_start";
        }
    }

    /**
     * 上下文压缩结束。
     *
     * @param seq 事件序号
     * @param reason 触发原因
     * @param aborted 是否已中止
     * @param willRetry 是否将重试
     * @param errorMessage 错误信息；无错误时为 {@code null}
     * @param tokensBefore 压缩前 token 数；未知时为 0
     * @param estimatedTokensAfter 压缩后估算 token 数；未知时为 0
     * @param raw 原始事件 JSON
     */
    record CompactionEnd(
            long seq,
            String reason,
            boolean aborted,
            boolean willRetry,
            String errorMessage,
            long tokensBefore,
            long estimatedTokensAfter,
            JsonNode raw
    ) implements SseEvent {
        @Override
        public String type() {
            return "compaction_end";
        }
    }

    /**
     * 自动重试开始。
     *
     * @param seq 事件序号
     * @param attempt 当前尝试次数
     * @param maxAttempts 最大尝试次数
     * @param delayMillis 下次尝试前的延迟毫秒数
     * @param errorMessage 本次错误信息
     * @param raw 原始事件 JSON
     */
    record RetryStart(long seq, int attempt, int maxAttempts, long delayMillis, String errorMessage, JsonNode raw)
            implements SseEvent {
        @Override
        public String type() {
            return "retry_start";
        }
    }

    /**
     * 自动重试结束。
     *
     * @param seq 事件序号
     * @param success 最终是否成功
     * @param attempt 已尝试次数
     * @param finalError 最终错误信息；成功时为 {@code null}
     * @param raw 原始事件 JSON
     */
    record RetryEnd(long seq, boolean success, int attempt, String finalError, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "retry_end";
        }
    }

    /**
     * 摘要生成重试已排期。
     *
     * @param seq 事件序号
     * @param attempt 当前尝试次数
     * @param maxAttempts 最大尝试次数
     * @param delayMillis 下次尝试前的延迟毫秒数
     * @param errorMessage 本次错误信息
     * @param raw 原始事件 JSON
     */
    record RetryScheduled(long seq, int attempt, int maxAttempts, long delayMillis, String errorMessage, JsonNode raw)
            implements SseEvent {
        @Override
        public String type() {
            return "retry_scheduled";
        }
    }

    /**
     * 摘要生成重试开始。
     *
     * @param seq 事件序号
     * @param source 摘要来源：{@code branchSummary} 或 {@code compaction}
     * @param reason 重试原因；协议未提供时为 {@code null}
     * @param raw 原始事件 JSON
     */
    record SummarizationRetry(long seq, String source, String reason, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "summarization_retry";
        }
    }

    /**
     * 摘要生成重试结束。
     *
     * @param seq 事件序号
     * @param raw 原始事件 JSON
     */
    record SummarizationRetryFinished(long seq, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "summarization_retry_finished";
        }
    }

    // ========================================================================
    // 会话信息
    // ========================================================================

    /**
     * 会话条目已追加。
     *
     * @param seq 事件序号
     * @param entry 新条目
     * @param raw 原始事件 JSON
     */
    record EntryAppended(long seq, JsonNode entry, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "entry_appended";
        }
    }

    /**
     * 会话名称等信息变更。
     *
     * @param seq 事件序号
     * @param name 新会话名称；清除时为 {@code null}
     * @param raw 原始事件 JSON
     */
    record SessionInfo(long seq, String name, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "session_info";
        }
    }

    /**
     * 思考等级变更。
     *
     * @param seq 事件序号
     * @param level 新思考等级
     * @param raw 原始事件 JSON
     */
    record ThinkingLevelChanged(long seq, String level, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "thinking_level";
        }
    }

    // ========================================================================
    // Bash 与扩展
    // ========================================================================

    /**
     * Bash 命令输出增量。
     *
     * @param seq 事件序号
     * @param requestId Bash 请求 ID；协议未提供时为 {@code null}
     * @param delta 本次输出增量
     * @param raw 原始事件 JSON
     */
    record BashOutput(long seq, String requestId, String delta, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "bash_output";
        }
    }

    /**
     * 扩展请求宿主执行 UI 操作。
     *
     * @param seq 事件序号
     * @param request 强类型 UI 请求
     * @param raw 原始事件 JSON
     */
    record ExtensionUi(long seq, PiExtensionUiRequest request, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "extension_ui";
        }
    }

    /**
     * 扩展处理事件时发生错误。
     *
     * @param seq 事件序号
     * @param extensionPath 扩展路径
     * @param event 发生错误的扩展事件名
     * @param message 错误信息
     * @param raw 原始事件 JSON
     */
    record Error(long seq, String extensionPath, String event, String message, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "error";
        }
    }

    /**
     * 当前 SDK 尚未识别的协议事件。
     *
     * @param seq 事件序号
     * @param sourceType 原始协议类型
     * @param raw 原始事件 JSON
     */
    record Unknown(long seq, String sourceType, JsonNode raw) implements SseEvent {
        @Override
        public String type() {
            return "unknown";
        }
    }
}
