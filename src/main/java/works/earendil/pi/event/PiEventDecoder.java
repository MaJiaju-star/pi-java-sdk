package works.earendil.pi.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.StreamSupport;
import works.earendil.pi.conversation.ThinkingLevel;
import works.earendil.pi.extension.PiExtensionUiRequest;
import works.earendil.pi.rpc.PiRpcTypes;

final class PiEventDecoder {
    private PiEventDecoder() {
    }

    static PiTypedEvent decode(PiEvent event, ObjectMapper mapper) {
        //1. 取出原始 JSON 并解析协议事件类型；未知类型归入 UNKNOWN。
        JsonNode raw = event.raw();
        PiEventType type = PiEventType.fromWireValue(event.type());
        //2. 按类型分派到强类型 record；SDK 未建模的字段一律保留原始 JsonNode。
        return switch (type) {
            case AGENT_START, AGENT_SETTLED, TURN_START, SUMMARIZATION_RETRY_FINISHED ->
                    new PiTypedEvent.Marker(type, raw);
            case AGENT_END -> new PiTypedEvent.AgentEnd(nodes(raw.get("messages")), raw.path("willRetry").asBoolean(), raw);
            case TURN_END -> new PiTypedEvent.TurnEnd(raw.get("message"), nodes(raw.get("toolResults")), raw);
            case MESSAGE_START, MESSAGE_END -> new PiTypedEvent.MessageLifecycle(type, raw.get("message"), raw);
            case MESSAGE_UPDATE -> messageUpdate(raw, mapper);
            case TOOL_EXECUTION_START, TOOL_EXECUTION_UPDATE, TOOL_EXECUTION_END -> new PiTypedEvent.ToolExecution(
                    type,
                    text(raw, "toolCallId"),
                    text(raw, "toolName"),
                    raw.get("args"),
                    raw.get("partialResult"),
                    raw.get("result"),
                    raw.path("isError").asBoolean(),
                    raw
            );
            case QUEUE_UPDATE -> new PiTypedEvent.QueueUpdate(strings(raw.get("steering")), strings(raw.get("followUp")), raw);
            case COMPACTION_START, COMPACTION_END -> new PiTypedEvent.Compaction(
                    type,
                    text(raw, "reason"),
                    convertNullable(mapper, raw.get("result"), PiRpcTypes.CompactionResult.class),
                    raw.path("aborted").asBoolean(),
                    raw.path("willRetry").asBoolean(),
                    text(raw, "errorMessage"),
                    raw
            );
            case AUTO_RETRY_START, AUTO_RETRY_END, SUMMARIZATION_RETRY_SCHEDULED -> new PiTypedEvent.Retry(
                    type,
                    raw.path("attempt").asInt(),
                    raw.path("maxAttempts").asInt(),
                    raw.path("delayMs").asLong(),
                    text(raw, "errorMessage"),
                    raw.path("success").asBoolean(),
                    text(raw, "finalError"),
                    raw
            );
            case SUMMARIZATION_RETRY_ATTEMPT_START -> new PiTypedEvent.SummarizationRetry(
                    type, text(raw, "source"), text(raw, "reason"), raw);
            case BASH_EXECUTION_UPDATE -> new PiTypedEvent.BashUpdate(text(raw, "id"), text(raw, "delta"), raw);
            case ENTRY_APPENDED -> new PiTypedEvent.EntryAppended(raw.get("entry"), raw);
            case SESSION_INFO_CHANGED -> new PiTypedEvent.SessionInfoChanged(text(raw, "name"), raw);
            case THINKING_LEVEL_CHANGED -> new PiTypedEvent.ThinkingLevelChanged(
                    ThinkingLevel.fromWireValue(raw.path("level").asText()), raw);
            case EXTENSION_ERROR -> new PiTypedEvent.ExtensionError(
                    text(raw, "extensionPath"), text(raw, "event"), text(raw, "error"), raw);
            case EXTENSION_UI_REQUEST -> new PiTypedEvent.ExtensionUi(PiExtensionUiRequest.from(raw), raw);
            case UNKNOWN -> new PiTypedEvent.Unknown(event.type(), raw);
        };
    }

    private static PiTypedEvent.MessageUpdate messageUpdate(JsonNode raw, ObjectMapper mapper) {
        //1. message_update 的增量信息在 assistantMessageEvent 子树中。
        JsonNode value = raw.path("assistantMessageEvent");
        PiTypedEvent.AssistantMessageEvent update = new PiTypedEvent.AssistantMessageEvent(
                text(value, "type"),
                value.path("contentIndex").asInt(),
                text(value, "delta"),
                text(value, "content"),
                text(value, "id"),
                text(value, "toolName"),
                value.get("toolCall")
        );
        //2. 顶层 usage 为累计用量，缺失或为 null 时置空。
        return new PiTypedEvent.MessageUpdate(
                convertNullable(mapper, raw.get("usage"), PiRpcTypes.Usage.class), update, raw);
    }

    private static <T> T convertNullable(ObjectMapper mapper, JsonNode value, Class<T> type) {
        return value == null || value.isNull() ? null : mapper.convertValue(value, type);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static List<String> strings(JsonNode value) {
        if (value == null || !value.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(value.spliterator(), false)
                .filter(JsonNode::isTextual)
                .map(JsonNode::textValue)
                .toList();
    }

    private static List<JsonNode> nodes(JsonNode value) {
        return value == null || !value.isArray()
                ? List.of()
                : StreamSupport.stream(value.spliterator(), false).toList();
    }
}
