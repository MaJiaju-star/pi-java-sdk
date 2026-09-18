package works.earendil.pi.rpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 一个已经与请求 ID 关联的 PI RPC 成功响应。
 *
 * @param id 请求 ID
 * @param command 响应对应的命令名
 * @param data 响应数据；协议没有数据时可为 {@code null}
 * @param raw 完整原始响应 JSON
 */
public record PiResponse(String id, String command, JsonNode data, JsonNode raw) {
    /**
     * 将响应数据转换为指定 Java 类型。
     *
     * @param mapper Jackson 映射器
     * @param type 目标类型
     * @param <T> 目标类型
     * @return 转换结果；响应没有数据时为 {@code null}
     * @throws IllegalArgumentException 当 Jackson 无法完成转换时
     */
    public <T> T dataAs(ObjectMapper mapper, Class<T> type) {
        if (data == null || data.isNull() || data.isMissingNode()) {
            return null;
        }
        return mapper.convertValue(data, type);
    }
}
