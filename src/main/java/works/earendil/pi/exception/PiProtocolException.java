package works.earendil.pi.exception;

/** PI stdout 中出现非法 JSONL 或非法 RPC 消息时抛出的异常。 */
public final class PiProtocolException extends PiClientException {
    /**
     * 使用说明消息创建协议异常。
     *
     * @param message 错误说明
     */
    public PiProtocolException(String message) {
        super(message);
    }

    /**
     * 使用说明消息和根因创建协议异常。
     *
     * @param message 错误说明
     * @param cause 根因
     */
    public PiProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
