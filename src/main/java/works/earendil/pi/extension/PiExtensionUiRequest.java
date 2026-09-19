package works.earendil.pi.extension;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * PI 扩展通过 RPC 请求宿主执行的 UI 操作。
 *
 * @param id 请求 ID；需要响应的方法用它关联响应
 * @param method 强类型方法
 * @param title 标题
 * @param options 可选项
 * @param timeoutMillis 超时毫秒数
 * @param message 提示消息
 * @param placeholder 输入占位文本
 * @param prefill 输入预填文本
 * @param notifyType 通知类型
 * @param statusKey 状态栏键
 * @param statusText 状态栏文本
 * @param widgetKey 组件键
 * @param widgetLines 组件显示行
 * @param widgetPlacement 组件位置
 * @param text 编辑器或标题文本
 * @param raw 完整原始请求 JSON
 */
public record PiExtensionUiRequest(
        String id,
        Method method,
        String title,
        List<String> options,
        Long timeoutMillis,
        String message,
        String placeholder,
        String prefill,
        String notifyType,
        String statusKey,
        String statusText,
        String widgetKey,
        List<String> widgetLines,
        String widgetPlacement,
        String text,
        JsonNode raw
) {
    /** Extension UI 方法。 */
    public enum Method {
        /** 显示单选列表并返回选中值。 */
        SELECT,
        /** 显示确认框并返回布尔值。 */
        CONFIRM,
        /** 显示单行输入框并返回文本。 */
        INPUT,
        /** 显示多行编辑器并返回文本。 */
        EDITOR,
        /** 显示无需响应的通知。 */
        NOTIFY,
        /** 设置或清除状态栏文本。 */
        SET_STATUS,
        /** 设置或清除自定义组件。 */
        SET_WIDGET,
        /** 设置界面标题。 */
        SET_TITLE,
        /** 设置编辑器文本。 */
        SET_EDITOR_TEXT,
        /** 当前 SDK 尚未识别的方法。 */
        UNKNOWN
    }

    /**
     * 判断该 UI 操作是否要求宿主回复。
     *
     * @return 需要通过 {@link PiExtensionUiResponse} 回复时返回 {@code true}
     */
    public boolean expectsResponse() {
        return method == Method.SELECT || method == Method.CONFIRM || method == Method.INPUT || method == Method.EDITOR;
    }

    /**
     * 返回请求超时。
     *
     * @return 协议提供超时时为对应时长，否则为空
     */
    public Optional<Duration> timeout() {
        return timeoutMillis == null ? Optional.empty() : Optional.of(Duration.ofMillis(timeoutMillis));
    }

    /**
     * 从原始 {@code extension_ui_request} 事件解析 UI 请求。
     *
     * @param raw 完整原始事件 JSON
     * @return 强类型 UI 请求
     */
    public static PiExtensionUiRequest from(JsonNode raw) {
        //1. 把协议字符串方法名映射为强类型枚举；未知方法归入 UNKNOWN，原始字段仍保留在 raw()。
        String methodValue = raw.path("method").asText();
        Method method = switch (methodValue) {
            case "select" -> Method.SELECT;
            case "confirm" -> Method.CONFIRM;
            case "input" -> Method.INPUT;
            case "editor" -> Method.EDITOR;
            case "notify" -> Method.NOTIFY;
            case "setStatus" -> Method.SET_STATUS;
            case "setWidget" -> Method.SET_WIDGET;
            case "setTitle" -> Method.SET_TITLE;
            case "set_editor_text" -> Method.SET_EDITOR_TEXT;
            default -> Method.UNKNOWN;
        };
        //2. 其余字段统一按可选文本读取；缺失或类型不符时为 null。
        return new PiExtensionUiRequest(
                text(raw, "id"), method, text(raw, "title"), strings(raw.get("options")),
                raw.has("timeout") ? raw.path("timeout").longValue() : null,
                text(raw, "message"), text(raw, "placeholder"), text(raw, "prefill"),
                text(raw, "notifyType"), text(raw, "statusKey"), text(raw, "statusText"),
                text(raw, "widgetKey"), strings(raw.get("widgetLines")), text(raw, "widgetPlacement"),
                text(raw, "text"), raw
        );
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static List<String> strings(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .filter(JsonNode::isTextual)
                .map(JsonNode::textValue)
                .toList();
    }
}
