package works.earendil.pi.conversation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** steering 和 follow-up 队列的投递模式。 */
public enum QueueMode {
    /** 在一次运行中投递队列中的全部消息。 */
    ALL("all"),
    /** 每次运行只投递队列中的一条消息。 */
    ONE_AT_A_TIME("one-at-a-time");

    private final String wireValue;

    QueueMode(String wireValue) {
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
     * @return 对应的队列模式
     * @throws IllegalArgumentException 当协议值未知时
     */
    @JsonCreator
    public static QueueMode fromWireValue(String value) {
        return Arrays.stream(values())
                .filter(mode -> mode.wireValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知队列模式: " + value));
    }
}
