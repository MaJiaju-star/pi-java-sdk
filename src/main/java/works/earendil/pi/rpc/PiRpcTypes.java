package works.earendil.pi.rpc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import works.earendil.pi.conversation.QueueMode;
import works.earendil.pi.conversation.ThinkingLevel;

/** RPC 响应使用的强类型数据对象。复杂且可扩展的消息内容同时保留为 {@link JsonNode}。 */
public final class PiRpcTypes {
    private PiRpcTypes() {
    }

    /**
     * 模型的基础 token 单价。
     *
     * @param input 输入单价
     * @param output 输出单价
     * @param cacheRead 缓存读取单价
     * @param cacheWrite 缓存写入单价
     * @param tiers 按输入 token 数量划分的阶梯单价
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelCost(double input, double output, double cacheRead, double cacheWrite, List<ModelCostTier> tiers) {
    }

    /**
     * 超过指定输入 token 数量后使用的模型单价。
     *
     * @param input 输入单价
     * @param output 输出单价
     * @param cacheRead 缓存读取单价
     * @param cacheWrite 缓存写入单价
     * @param inputTokensAbove 生效的输入 token 下限
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelCostTier(
            double input,
            double output,
            double cacheRead,
            double cacheWrite,
            long inputTokensAbove
    ) {
    }

    /**
     * PI 模型描述。
     *
     * @param id 模型 ID
     * @param name 显示名称
     * @param api API 类型
     * @param provider 提供商名称
     * @param baseUrl API 基础地址
     * @param reasoning 是否支持推理
     * @param thinkingLevelMap 统一思考等级到提供商值的映射
     * @param input 支持的输入类型
     * @param cost 单价信息
     * @param contextWindow 上下文窗口 token 数
     * @param maxTokens 最大输出 token 数
     * @param samplingParams 采样参数
     * @param headers 附加请求头
     * @param compat 提供商兼容配置
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Model(
            String id,
            String name,
            String api,
            String provider,
            String baseUrl,
            boolean reasoning,
            Map<String, String> thinkingLevelMap,
            List<String> input,
            ModelCost cost,
            long contextWindow,
            long maxTokens,
            JsonNode samplingParams,
            Map<String, String> headers,
            JsonNode compat
    ) {
    }

    /**
     * 当前 PI 会话状态。
     *
     * @param model 当前模型
     * @param thinkingLevel 当前思考等级
     * @param isStreaming 是否正在生成
     * @param isCompacting 是否正在压缩上下文
     * @param steeringMode steering 队列模式
     * @param followUpMode follow-up 队列模式
     * @param sessionFile 会话文件路径
     * @param sessionId 会话 ID
     * @param sessionName 会话名称
     * @param autoCompactionEnabled 是否启用自动压缩
     * @param messageCount 已持久化消息数
     * @param pendingMessageCount 等待处理的消息数
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionState(
            Model model,
            ThinkingLevel thinkingLevel,
            boolean isStreaming,
            boolean isCompacting,
            QueueMode steeringMode,
            QueueMode followUpMode,
            String sessionFile,
            String sessionId,
            String sessionName,
            boolean autoCompactionEnabled,
            int messageCount,
            int pendingMessageCount
    ) {
    }

    /**
     * 消息队列状态。
     *
     * @param steering steering 队列
     * @param followUp follow-up 队列
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QueueState(List<String> steering, List<String> followUp) {
    }

    /**
     * 可用模型查询结果。
     *
     * @param models 可用模型列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelList(List<Model> models) {
    }

    /**
     * 循环切换模型的结果。
     *
     * @param model 切换后的模型
     * @param thinkingLevel 切换后的思考等级
     * @param isScoped 是否受当前模型范围限制
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelCycle(Model model, ThinkingLevel thinkingLevel, boolean isScoped) {
    }

    /**
     * 可用思考等级查询结果。
     *
     * @param levels 当前模型支持的思考等级
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ThinkingLevelList(List<ThinkingLevel> levels) {
    }

    /**
     * 循环切换思考等级的结果。
     *
     * @param level 切换后的思考等级
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ThinkingLevelCycle(ThinkingLevel level) {
    }

    /**
     * Token 用量和费用。
     *
     * @param input 输入 token 数
     * @param output 输出 token 数
     * @param cacheRead 缓存读取 token 数
     * @param cacheWrite 缓存写入 token 数
     * @param totalTokens token 总数
     * @param cost 费用明细
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(long input, long output, long cacheRead, long cacheWrite, long totalTokens, UsageCost cost) {
    }

    /**
     * Token 费用明细。
     *
     * @param input 输入费用
     * @param output 输出费用
     * @param cacheRead 缓存读取费用
     * @param cacheWrite 缓存写入费用
     * @param total 总费用
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UsageCost(double input, double output, double cacheRead, double cacheWrite, double total) {
    }

    /**
     * 上下文压缩结果。
     *
     * @param summary 生成的摘要
     * @param firstKeptEntryId 第一个保留条目 ID
     * @param tokensBefore 压缩前 token 数
     * @param estimatedTokensAfter 压缩后估算 token 数
     * @param usage 压缩请求用量
     * @param details 额外协议字段
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompactionResult(
            String summary,
            String firstKeptEntryId,
            long tokensBefore,
            long estimatedTokensAfter,
            Usage usage,
            JsonNode details
    ) {
    }

    /**
     * Bash 命令执行结果。
     *
     * @param output 已捕获输出
     * @param exitCode 退出码；未正常结束时可为 {@code null}
     * @param cancelled 是否被取消
     * @param truncated 输出是否被截断
     * @param fullOutputPath 完整输出文件路径
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BashResult(
            String output,
            Integer exitCode,
            boolean cancelled,
            boolean truncated,
            String fullOutputPath
    ) {
    }

    /**
     * 会话统计信息。
     *
     * @param sessionFile 会话文件路径
     * @param sessionId 会话 ID
     * @param userMessages 用户消息数
     * @param assistantMessages 助手消息数
     * @param toolCalls 工具调用数
     * @param toolResults 工具结果数
     * @param totalMessages 消息总数
     * @param tokens token 统计
     * @param cost 总费用
     * @param contextUsage 上下文用量详情
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionStats(
            String sessionFile,
            String sessionId,
            int userMessages,
            int assistantMessages,
            int toolCalls,
            int toolResults,
            int totalMessages,
            TokenStats tokens,
            double cost,
            JsonNode contextUsage
    ) {
    }

    /**
     * Token 数量统计。
     *
     * @param input 输入 token 数
     * @param output 输出 token 数
     * @param cacheRead 缓存读取 token 数
     * @param cacheWrite 缓存写入 token 数
     * @param total token 总数
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenStats(long input, long output, long cacheRead, long cacheWrite, long total) {
    }

    /**
     * 包含取消状态的会话操作结果。
     *
     * @param cancelled 切换会话前的运行是否被取消
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cancelled(boolean cancelled) {
    }

    /**
     * 分支会话结果。
     *
     * @param text 分支点消息文本
     * @param cancelled 原运行是否被取消
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ForkResult(String text, boolean cancelled) {
    }

    /**
     * 文件生成结果。
     *
     * @param path 生成文件的路径
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PathResult(String path) {
    }

    /**
     * 可作为分支点的消息。
     *
     * @param entryId 会话条目 ID
     * @param text 消息文本
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ForkMessage(String entryId, String text) {
    }

    /**
     * 分支点消息查询结果。
     *
     * @param messages 可作为分支点的消息列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ForkMessages(List<ForkMessage> messages) {
    }

    /**
     * 会话条目查询结果。
     *
     * @param entries 会话条目
     * @param leafId 当前叶条目 ID
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entries(List<JsonNode> entries, String leafId) {
    }

    /**
     * 会话树节点。
     *
     * @param entry 会话条目
     * @param children 子节点
     * @param label 显示标签
     * @param labelTimestamp 标签时间
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionTreeNode(JsonNode entry, List<SessionTreeNode> children, String label, String labelTimestamp) {
    }

    /**
     * 会话分支树。
     *
     * @param tree 根节点列表
     * @param leafId 当前叶条目 ID
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionTree(List<SessionTreeNode> tree, String leafId) {
    }

    /**
     * 最后一条助手消息查询结果。
     *
     * @param text 消息文本；不存在时可为 {@code null}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LastAssistantText(String text) {
    }

    /**
     * 会话消息查询结果。
     *
     * @param messages 当前会话消息
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Messages(List<JsonNode> messages) {
    }

    /**
     * 可用斜杠命令。
     *
     * @param name 命令名
     * @param description 命令说明
     * @param source 命令来源
     * @param sourceInfo 来源详情
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SlashCommand(String name, String description, String source, JsonNode sourceInfo) {
    }

    /**
     * 斜杠命令查询结果。
     *
     * @param commands 可用斜杠命令列表
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SlashCommands(List<SlashCommand> commands) {
    }
}
