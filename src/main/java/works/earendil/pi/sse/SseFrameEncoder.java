package works.earendil.pi.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

/**
 * 把 {@link SseEvent} 编码为 SSE 文本帧。
 *
 * <p>帧格式：</p>
 *
 * <pre>
 * id: 42
 * event: text_delta
 * data: {"seq":42,"type":"text_delta","contentIndex":0,"text":"你好","raw":{...}}
 * </pre>
 *
 * <p>{@code id} 使用事件序号，支持浏览器断线重连时通过 {@code Last-Event-ID} 回放。
 * {@code data} 为事件的紧凑 JSON，包含 {@code type} 与 {@code raw} 原始事件。</p>
 */
public final class SseFrameEncoder {
    private static final String HEARTBEAT = ": ping\n\n";

    private final ObjectMapper mapper;

    /**
     * 创建编码器。
     *
     * @param mapper 用于 JSON 序列化的 Jackson 映射器
     * @throws NullPointerException 当映射器为 {@code null} 时
     */
    public SseFrameEncoder(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * 编码一个事件帧。
     *
     * @param event 规范化事件
     * @return 可直接写入 HTTP 响应的 SSE 帧文本
     * @throws NullPointerException 当事件为 {@code null} 时
     * @throws IllegalStateException 当事件无法序列化为 JSON 时
     */
    public String encode(SseEvent event) {
        Objects.requireNonNull(event, "event");

        //1. 序列化事件为 JSON；每个 record 组件就是一个 payload 字段。
        ObjectNode node = mapper.valueToTree(event);
        node.put("type", event.type());
        node.put("seq", event.seq());

        String json;
        try {
            json = mapper.writeValueAsString(node);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("无法序列化 SSE 事件: " + event.type(), error);
        }

        //2. 组装帧：id 供断线回放，event 供前端路由，data 为紧凑 JSON。
        StringBuilder frame = new StringBuilder(64 + json.length());
        frame.append("id: ").append(event.seq()).append('\n');
        frame.append("event: ").append(event.type()).append('\n');
        appendData(frame, json);
        frame.append('\n');
        return frame.toString();
    }

    /**
     * 返回心跳注释帧，用于保活空闲连接。
     *
     * @return 心跳帧文本
     */
    public String heartbeat() {
        return HEARTBEAT;
    }

    private static void appendData(StringBuilder frame, String json) {
        // JSON 可能含换行（例如工具结果），每个物理行都要单独加 data: 前缀。
        for (String line : json.split("\n", -1)) {
            frame.append("data: ").append(line).append('\n');
        }
    }
}
