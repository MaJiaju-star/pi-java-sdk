package works.earendil.pi.conversation;

/** PI 正在流式运行时，新 prompt 的排队方式。 */
public enum PiStreamingBehavior {
    /** 将新消息立即注入当前运行。 */
    STEER("steer"),
    /** 在当前运行结束后处理新消息。 */
    FOLLOW_UP("followUp");

    private final String wireValue;

    PiStreamingBehavior(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * 返回 RPC 协议使用的字符串值。
     *
     * @return 协议值
     */
    public String wireValue() {
        return wireValue;
    }
}
