package works.earendil.pi.exception;

/** PI Java SDK 的基础运行时异常。 */
public class PiClientException extends RuntimeException {
    /**
     * 使用说明消息创建异常。
     *
     * @param message 错误说明
     */
    public PiClientException(String message) {
        super(message);
    }

    /**
     * 使用说明消息和根因创建异常。
     *
     * @param message 错误说明
     * @param cause 根因
     */
    public PiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
