package works.earendil.pi.event;

import java.util.Arrays;

/** PI RPC stdout 中已知的事件类型。 */
public enum PiEventType {
    /** Agent 开始运行。 */
    AGENT_START("agent_start"),
    /** Agent 一轮运行结束。 */
    AGENT_END("agent_end"),
    /** 重试、压缩和后续队列均已结束。 */
    AGENT_SETTLED("agent_settled"),
    /** 模型回合开始。 */
    TURN_START("turn_start"),
    /** 模型回合结束。 */
    TURN_END("turn_end"),
    /** 消息开始生成。 */
    MESSAGE_START("message_start"),
    /** 消息内容发生增量更新。 */
    MESSAGE_UPDATE("message_update"),
    /** 消息生成结束。 */
    MESSAGE_END("message_end"),
    /** 工具调用开始执行。 */
    TOOL_EXECUTION_START("tool_execution_start"),
    /** 工具调用执行进度更新。 */
    TOOL_EXECUTION_UPDATE("tool_execution_update"),
    /** 工具调用执行结束。 */
    TOOL_EXECUTION_END("tool_execution_end"),
    /** steering 或 follow-up 队列变更。 */
    QUEUE_UPDATE("queue_update"),
    /** 上下文压缩开始。 */
    COMPACTION_START("compaction_start"),
    /** 上下文压缩结束。 */
    COMPACTION_END("compaction_end"),
    /** 会话条目已追加。 */
    ENTRY_APPENDED("entry_appended"),
    /** 会话名称等信息已变更。 */
    SESSION_INFO_CHANGED("session_info_changed"),
    /** 思考等级已变更。 */
    THINKING_LEVEL_CHANGED("thinking_level_changed"),
    /** 自动重试开始。 */
    AUTO_RETRY_START("auto_retry_start"),
    /** 自动重试结束。 */
    AUTO_RETRY_END("auto_retry_end"),
    /** 摘要生成重试已排期。 */
    SUMMARIZATION_RETRY_SCHEDULED("summarization_retry_scheduled"),
    /** 摘要生成重试开始。 */
    SUMMARIZATION_RETRY_ATTEMPT_START("summarization_retry_attempt_start"),
    /** 摘要生成重试结束。 */
    SUMMARIZATION_RETRY_FINISHED("summarization_retry_finished"),
    /** Bash 命令输出增量更新。 */
    BASH_EXECUTION_UPDATE("bash_execution_update"),
    /** 扩展处理事件时发生错误。 */
    EXTENSION_ERROR("extension_error"),
    /** 扩展请求宿主执行 UI 操作。 */
    EXTENSION_UI_REQUEST("extension_ui_request"),
    /** 当前 SDK 尚未识别的协议事件。 */
    UNKNOWN("unknown");

    private final String wireValue;

    PiEventType(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * 返回 RPC 协议使用的字符串值。
     *
     * @return 协议值
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * 将协议值转换为事件类型。
     *
     * @param value 协议值
     * @return 对应类型；无法识别时返回 {@link #UNKNOWN}
     */
    public static PiEventType fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(type -> type != UNKNOWN && type.wireValue.equals(value))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
