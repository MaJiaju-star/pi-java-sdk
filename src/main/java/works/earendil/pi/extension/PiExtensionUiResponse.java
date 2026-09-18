package works.earendil.pi.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;

/** 对 Extension UI 对话请求的强类型响应。 */
public sealed interface PiExtensionUiResponse {
    /**
     * 返回关联请求 ID。
     *
     * @return 需要响应的 Extension UI 请求 ID
     */
    String requestId();

    /**
     * 转换为可直接发送给 PI 的协议消息。
     *
     * @param mapper Jackson 映射器
     * @return {@code extension_ui_response} JSON 对象
     */
    ObjectNode toJson(ObjectMapper mapper);

    /**
     * 创建选中值或输入文本响应。
     *
     * @param requestId 请求 ID
     * @param value 选中值或输入文本
     * @return 值响应
     */
    static PiExtensionUiResponse value(String requestId, String value) {
        return new Value(requestId, value);
    }

    /**
     * 创建确认响应。
     *
     * @param requestId 请求 ID
     * @param confirmed 是否确认
     * @return 确认响应
     */
    static PiExtensionUiResponse confirmed(String requestId, boolean confirmed) {
        return new Confirmation(requestId, confirmed);
    }

    /**
     * 创建取消响应。
     *
     * @param requestId 请求 ID
     * @return 取消响应
     */
    static PiExtensionUiResponse cancelled(String requestId) {
        return new Cancellation(requestId);
    }

    /**
     * 选中值或输入文本响应。
     *
     * @param requestId 请求 ID
     * @param value 选中值或输入文本
     */
    record Value(String requestId, String value) implements PiExtensionUiResponse {
        /**
         * 创建并校验值响应。
         *
         * @throws NullPointerException 当任一参数为 {@code null} 时
         */
        public Value {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(value, "value");
        }

        @Override
        public ObjectNode toJson(ObjectMapper mapper) {
            return mapper.createObjectNode()
                    .put("type", "extension_ui_response")
                    .put("id", requestId)
                    .put("value", value);
        }
    }

    /**
     * 布尔确认响应。
     *
     * @param requestId 请求 ID
     * @param confirmed 是否确认
     */
    record Confirmation(String requestId, boolean confirmed) implements PiExtensionUiResponse {
        /**
         * 创建并校验确认响应。
         *
         * @throws NullPointerException 当请求 ID 为 {@code null} 时
         */
        public Confirmation {
            Objects.requireNonNull(requestId, "requestId");
        }

        @Override
        public ObjectNode toJson(ObjectMapper mapper) {
            return mapper.createObjectNode()
                    .put("type", "extension_ui_response")
                    .put("id", requestId)
                    .put("confirmed", confirmed);
        }
    }

    /**
     * 取消响应。
     *
     * @param requestId 请求 ID
     */
    record Cancellation(String requestId) implements PiExtensionUiResponse {
        /**
         * 创建并校验取消响应。
         *
         * @throws NullPointerException 当请求 ID 为 {@code null} 时
         */
        public Cancellation {
            Objects.requireNonNull(requestId, "requestId");
        }

        @Override
        public ObjectNode toJson(ObjectMapper mapper) {
            return mapper.createObjectNode()
                    .put("type", "extension_ui_response")
                    .put("id", requestId)
                    .put("cancelled", true);
        }
    }
}
