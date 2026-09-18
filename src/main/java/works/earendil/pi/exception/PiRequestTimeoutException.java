package works.earendil.pi.exception;

import java.time.Duration;

/** RPC 请求在指定时间内没有收到对应响应。 */
public final class PiRequestTimeoutException extends PiClientException {
    /** 超时的命令名。 */
    private final String command;
    /** 请求超时时长。 */
    private final Duration timeout;

    /**
     * 创建 RPC 请求超时异常。
     *
     * @param command 超时的命令名
     * @param timeout 等待时长
     */
    public PiRequestTimeoutException(String command, Duration timeout) {
        super("PI RPC 命令等待响应超时 [" + command + "]: " + timeout);
        this.command = command;
        this.timeout = timeout;
    }

    /**
     * 返回超时的命令名。
     *
     * @return 命令名
     */
    public String command() {
        return command;
    }

    /**
     * 返回请求超时时长。
     *
     * @return 超时时长
     */
    public Duration timeout() {
        return timeout;
    }
}
