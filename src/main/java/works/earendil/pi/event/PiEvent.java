package works.earendil.pi.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;

/**
 * PI 推送的原始事件。未知事件类型也会保留，便于协议向前兼容。
 *
 * @param type 事件的协议类型
 * @param raw 完整原始事件 JSON
 */
public record PiEvent(String type, JsonNode raw) {
    /**
     * 判断事件是否为指定协议类型。
     *
     * @param expectedType 期望的协议类型
     * @return 类型相同时返回 {@code true}
     */
    public boolean is(String expectedType) {
        return type.equals(expectedType);
    }

    /**
     * 当事件为 {@code message_update/text_delta} 时返回增量文本。
     *
     * @return 增量文本；事件类型不匹配或没有文本时为空
     */
    public Optional<String> textDelta() {
        if (!is("message_update")) {
            return Optional.empty();
        }
        JsonNode deltaEvent = raw.path("assistantMessageEvent");
        if (!"text_delta".equals(deltaEvent.path("type").asText())) {
            return Optional.empty();
        }
        JsonNode delta = deltaEvent.get("delta");
        return delta != null && delta.isTextual() ? Optional.of(delta.textValue()) : Optional.empty();
    }

    /**
     * 将事件解析为覆盖当前 RPC 协议的强类型视图。
     *
     * @param mapper 用于转换嵌套对象的 Jackson 映射器
     * @return 强类型事件；未知事件返回 {@link PiTypedEvent.Unknown}
     */
    public PiTypedEvent typed(ObjectMapper mapper) {
        return PiEventDecoder.decode(this, mapper);
    }
}
