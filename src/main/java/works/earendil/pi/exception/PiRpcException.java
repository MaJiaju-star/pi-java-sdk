package works.earendil.pi.exception;

/** PI RPC 返回 {@code success: false} 时抛出的异常。 */
public final class PiRpcException extends PiClientException {
    /** 失败的命令名。 */
    private final String command;

    /**
     * 创建服务端返回的 RPC 错误。
     *
     * @param command 失败的命令名
     * @param message 服务端错误说明
     */
    public PiRpcException(String command, String message) {
        super("PI RPC 命令失败 [" + command + "]: " + message);
        this.command = command;
    }

    /**
     * 返回失败的命令名。
     *
     * @return 命令名
     */
    public String command() {
        return command;
    }
}
