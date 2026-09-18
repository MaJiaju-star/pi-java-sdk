package works.earendil.pi.rpc;

/** PI Coding Agent RPC 当前公开的全部命令。 */
public enum PiRpcCommand {
    /** 提交普通 prompt。 */
    PROMPT("prompt"),
    /** 将消息注入当前运行。 */
    STEER("steer"),
    /** 将消息加入后续队列。 */
    FOLLOW_UP("follow_up"),
    /** 中止当前 Agent 运行。 */
    ABORT("abort"),
    /** 清空 steering 和 follow-up 队列。 */
    CLEAR_QUEUE("clear_queue"),
    /** 创建并切换到新会话。 */
    NEW_SESSION("new_session"),
    /** 获取当前会话状态。 */
    GET_STATE("get_state"),
    /** 设置当前模型。 */
    SET_MODEL("set_model"),
    /** 循环切换模型。 */
    CYCLE_MODEL("cycle_model"),
    /** 获取可用模型。 */
    GET_AVAILABLE_MODELS("get_available_models"),
    /** 设置思考等级。 */
    SET_THINKING_LEVEL("set_thinking_level"),
    /** 循环切换思考等级。 */
    CYCLE_THINKING_LEVEL("cycle_thinking_level"),
    /** 获取当前模型可用的思考等级。 */
    GET_AVAILABLE_THINKING_LEVELS("get_available_thinking_levels"),
    /** 设置 steering 队列投递模式。 */
    SET_STEERING_MODE("set_steering_mode"),
    /** 设置 follow-up 队列投递模式。 */
    SET_FOLLOW_UP_MODE("set_follow_up_mode"),
    /** 压缩当前会话上下文。 */
    COMPACT("compact"),
    /** 启用或禁用自动压缩。 */
    SET_AUTO_COMPACTION("set_auto_compaction"),
    /** 启用或禁用自动重试。 */
    SET_AUTO_RETRY("set_auto_retry"),
    /** 中止等待中的自动重试。 */
    ABORT_RETRY("abort_retry"),
    /** 在 PI 工作目录执行 Bash 命令。 */
    BASH("bash"),
    /** 中止当前 Bash 命令。 */
    ABORT_BASH("abort_bash"),
    /** 获取当前会话统计信息。 */
    GET_SESSION_STATS("get_session_stats"),
    /** 将当前会话导出为 HTML。 */
    EXPORT_HTML("export_html"),
    /** 切换到已有会话。 */
    SWITCH_SESSION("switch_session"),
    /** 从指定条目创建分支会话。 */
    FORK("fork"),
    /** 克隆当前会话。 */
    CLONE("clone"),
    /** 获取可作为分支点的消息。 */
    GET_FORK_MESSAGES("get_fork_messages"),
    /** 获取会话条目。 */
    GET_ENTRIES("get_entries"),
    /** 获取会话分支树。 */
    GET_TREE("get_tree"),
    /** 获取最后一条助手文本。 */
    GET_LAST_ASSISTANT_TEXT("get_last_assistant_text"),
    /** 设置当前会话名称。 */
    SET_SESSION_NAME("set_session_name"),
    /** 获取当前会话消息。 */
    GET_MESSAGES("get_messages"),
    /** 获取可用斜杠命令。 */
    GET_COMMANDS("get_commands");

    private final String wireValue;

    PiRpcCommand(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * 返回 RPC 协议使用的命令名。
     *
     * @return 协议命令名
     */
    public String wireValue() {
        return wireValue;
    }
}
