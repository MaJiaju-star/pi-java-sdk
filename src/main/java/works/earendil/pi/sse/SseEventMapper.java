package works.earendil.pi.sse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import works.earendil.pi.event.PiEvent;
import works.earendil.pi.event.PiTypedEvent;
import works.earendil.pi.rpc.PiRpcTypes;

/**
 * 把 PI 原始事件规范化为 {@link SseEvent}。
 *
 * <p>内部复用 SDK 自带的 {@link PiEvent#typed(ObjectMapper)} 解码，再把嵌套结构扁平化，并分配单调递增序号。</p>
 *
 * <p>本类线程安全：序号用原子的递增计数器维护，适合被 {@link SseBroadcaster} 单点调用。</p>
 */
public final class SseEventMapper {
    private final ObjectMapper mapper;
    private final AtomicLong sequence = new AtomicLong();

    /**
     * 创建映射器。
     *
     * @param mapper 用于解码嵌套对象的 Jackson 映射器
     * @throws NullPointerException 当映射器为 {@code null} 时
     */
    public SseEventMapper(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * 映射一个原始事件并分配下一个序号。
     *
     * @param event 原始事件
     * @return 规范化事件
     * @throws NullPointerException 当事件为 {@code null} 时
     */
    public SseEvent map(PiEvent event) {
        Objects.requireNonNull(event, "event");

        //1. 先分配序号，保证即使后续解码失败也不会出现序号空缺或重复。
        long seq = sequence.incrementAndGet();
        //2. 复用 SDK 解码器得到强类型事件，再扁平化。
        return toSseEvent(event.typed(mapper), seq);
    }

    /**
     * 返回当前已分配的最大序号。
     *
     * @return 当前序号
     */
    public long currentSeq() {
        return sequence.get();
    }

    private SseEvent toSseEvent(PiTypedEvent typed, long seq) {
        //1. 原始 JSON 会被每个 SseEvent 原样带出，作为回退通道。
        JsonNode raw = typed.raw();
        //2. 按强类型事件分派，把嵌套结构摊平为「一个语义事件一个 record」。
        return switch (typed) {
            case PiTypedEvent.Marker marker -> marker(marker, seq);
            case PiTypedEvent.AgentEnd agent -> new SseEvent.AgentEnd(
                    seq, agent.messages().size(), agent.willRetry(), raw);
            case PiTypedEvent.TurnEnd turn -> new SseEvent.TurnEnd(
                    seq, turn.message(), turn.toolResults().size(), raw);
            case PiTypedEvent.MessageLifecycle lifecycle -> messageLifecycle(lifecycle, seq);
            case PiTypedEvent.MessageUpdate update -> messageUpdate(update, seq);
            case PiTypedEvent.ToolExecution tool -> toolExecution(tool, seq);
            case PiTypedEvent.QueueUpdate queue -> new SseEvent.QueueUpdate(
                    seq, queue.steering(), queue.followUp(), raw);
            case PiTypedEvent.Compaction compaction -> compaction(compaction, seq);
            case PiTypedEvent.Retry retry -> retry(retry, seq);
            case PiTypedEvent.SummarizationRetry summarization -> new SseEvent.SummarizationRetry(
                    seq, summarization.source(), summarization.reason(), raw);
            case PiTypedEvent.BashUpdate bash -> new SseEvent.BashOutput(
                    seq, bash.requestId(), bash.delta(), raw);
            case PiTypedEvent.EntryAppended entry -> new SseEvent.EntryAppended(seq, entry.entry(), raw);
            case PiTypedEvent.SessionInfoChanged info -> new SseEvent.SessionInfo(seq, info.name(), raw);
            case PiTypedEvent.ThinkingLevelChanged level -> new SseEvent.ThinkingLevelChanged(
                    seq, level.level() == null ? null : level.level().wireValue(), raw);
            case PiTypedEvent.ExtensionError error -> new SseEvent.Error(
                    seq, error.extensionPath(), error.event(), error.error(), raw);
            case PiTypedEvent.ExtensionUi ui -> new SseEvent.ExtensionUi(seq, ui.request(), raw);
            case PiTypedEvent.Unknown unknown -> new SseEvent.Unknown(seq, unknown.wireType(), raw);
        };
    }

    private SseEvent marker(PiTypedEvent.Marker marker, long seq) {
        return switch (marker.type()) {
            case AGENT_START -> new SseEvent.AgentStart(seq, marker.raw());
            case AGENT_SETTLED -> new SseEvent.AgentSettled(seq, marker.raw());
            case TURN_START -> new SseEvent.TurnStart(seq, marker.raw());
            case SUMMARIZATION_RETRY_FINISHED -> new SseEvent.SummarizationRetryFinished(seq, marker.raw());
            default -> new SseEvent.Unknown(seq, marker.type().wireValue(), marker.raw());
        };
    }

    private SseEvent messageLifecycle(PiTypedEvent.MessageLifecycle lifecycle, long seq) {
        return switch (lifecycle.type()) {
            case MESSAGE_START -> new SseEvent.MessageStart(seq, lifecycle.message(), lifecycle.raw());
            case MESSAGE_END -> new SseEvent.MessageEnd(
                    seq, lifecycle.message(), usage(lifecycle.message()), lifecycle.raw());
            default -> new SseEvent.Unknown(seq, lifecycle.type().wireValue(), lifecycle.raw());
        };
    }

    private SseEvent messageUpdate(PiTypedEvent.MessageUpdate update, long seq) {
        //1. 增量事件在协议中是 message_update 与 assistantMessageEvent.type 的二级结构。
        JsonNode raw = update.raw();
        PiTypedEvent.AssistantMessageEvent event = update.assistantMessageEvent();
        String subtype = event.type() == null ? "" : event.type();
        JsonNode detail = raw.path("assistantMessageEvent");
        //2. 按子类型映射为扁平事件；未识别的子类型降级为 MessageChunk 保留原始 JSON。
        return switch (subtype) {
            case "text_start" -> new SseEvent.TextStart(seq, event.contentIndex(), raw);
            case "text_delta" -> new SseEvent.TextDelta(seq, event.contentIndex(), event.delta(), raw);
            case "text_end" -> new SseEvent.TextEnd(seq, event.contentIndex(), event.content(), raw);
            case "thinking_start" -> new SseEvent.ThinkingStart(seq, event.contentIndex(), raw);
            case "thinking_delta" -> new SseEvent.ThinkingDelta(seq, event.contentIndex(), event.delta(), raw);
            case "thinking_end" -> new SseEvent.ThinkingEnd(seq, event.contentIndex(), event.content(), raw);
            case "toolcall_start" -> new SseEvent.ToolCallStart(
                    seq, event.contentIndex(), event.id(), event.toolName(), raw);
            case "toolcall_delta" -> new SseEvent.ToolCallDelta(seq, event.contentIndex(), event.delta(), raw);
            case "toolcall_end" -> new SseEvent.ToolCallEnd(
                    seq,
                    event.contentIndex(),
                    text(event.toolCall(), "id"),
                    text(event.toolCall(), "name"),
                    event.toolCall() == null ? null : event.toolCall().get("arguments"),
                    raw
            );
            case "done" -> new SseEvent.MessageDone(seq, text(detail, "reason"), raw);
            case "error" -> new SseEvent.MessageFailed(
                    seq, text(detail, "reason"), detail.get("error"), raw);
            default -> new SseEvent.MessageChunk(seq, subtype, raw);
        };
    }

    private SseEvent toolExecution(PiTypedEvent.ToolExecution tool, long seq) {
        return switch (tool.type()) {
            case TOOL_EXECUTION_START -> new SseEvent.ToolStart(
                    seq, tool.toolCallId(), tool.toolName(), tool.args(), tool.raw());
            case TOOL_EXECUTION_UPDATE -> new SseEvent.ToolUpdate(
                    seq, tool.toolCallId(), tool.toolName(), tool.partialResult(), tool.raw());
            case TOOL_EXECUTION_END -> new SseEvent.ToolEnd(
                    seq, tool.toolCallId(), tool.toolName(), tool.result(), tool.error(), tool.raw());
            default -> new SseEvent.Unknown(seq, tool.type().wireValue(), tool.raw());
        };
    }

    private SseEvent compaction(PiTypedEvent.Compaction compaction, long seq) {
        //1. 开始事件只有原因，直接透传。
        if (compaction.type() == works.earendil.pi.event.PiEventType.COMPACTION_START) {
            return new SseEvent.CompactionStart(seq, compaction.reason(), compaction.raw());
        }
        //2. 结束事件的 result 可能缺失（中止或失败），此时 token 数用 0 占位。
        PiRpcTypes.CompactionResult result = compaction.result();
        return new SseEvent.CompactionEnd(
                seq,
                compaction.reason(),
                compaction.aborted(),
                compaction.willRetry(),
                compaction.errorMessage(),
                result == null ? 0L : result.tokensBefore(),
                result == null ? 0L : result.estimatedTokensAfter(),
                compaction.raw()
        );
    }

    private SseEvent retry(PiTypedEvent.Retry retry, long seq) {
        return switch (retry.type()) {
            case AUTO_RETRY_START -> new SseEvent.RetryStart(
                    seq, retry.attempt(), retry.maxAttempts(), retry.delayMillis(), retry.errorMessage(), retry.raw());
            case AUTO_RETRY_END -> new SseEvent.RetryEnd(
                    seq, retry.success(), retry.attempt(), retry.finalError(), retry.raw());
            case SUMMARIZATION_RETRY_SCHEDULED -> new SseEvent.RetryScheduled(
                    seq, retry.attempt(), retry.maxAttempts(), retry.delayMillis(), retry.errorMessage(), retry.raw());
            default -> new SseEvent.Unknown(seq, retry.type().wireValue(), retry.raw());
        };
    }

    private PiRpcTypes.Usage usage(JsonNode message) {
        // usage 只出现在对象形态下；缺失或类型不符时返回 null 而不是空对象。
        JsonNode node = message == null ? null : message.get("usage");
        if (node == null || !node.isObject()) {
            return null;
        }
        return mapper.convertValue(node, PiRpcTypes.Usage.class);
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }
}
