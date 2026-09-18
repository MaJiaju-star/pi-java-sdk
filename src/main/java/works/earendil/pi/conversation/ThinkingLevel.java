package works.earendil.pi.conversation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** PI 支持的统一思考等级。 */
public enum ThinkingLevel {
    /** 不使用推理预算。 */
    OFF("off"),
    /** 使用最小推理预算。 */
    MINIMAL("minimal"),
    /** 使用低推理预算。 */
    LOW("low"),
    /** 使用中等推理预算。 */
    MEDIUM("medium"),
    /** 使用高推理预算。 */
    HIGH("high"),
    /** 使用超高推理预算。 */
    XHIGH("xhigh"),
    /** 使用模型允许的最大推理预算。 */
    MAX("max");

    private final String wireValue;

    ThinkingLevel(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * 返回 RPC 协议使用的字符串值。
     *
     * @return 协议值
     */
    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /**
     * 将 RPC 协议值转换为枚举。
     *
     * @param value 协议值
     * @return 对应的思考等级
     * @throws IllegalArgumentException 当协议值未知时
     */
    @JsonCreator
    public static ThinkingLevel fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(level -> level.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知思考等级: " + value));
    }
}
