package works.earendil.pi.exception;

/** PI 子进程启动失败、异常退出或协议流中断时抛出的异常。 */
public final class PiProcessException extends PiClientException {
    /** 进程退出码。 */
    private final Integer exitCode;
    /** 已捕获的标准错误。 */
    private final String stderr;

    /**
     * 创建子进程异常，并将非空标准错误附加到异常消息。
     *
     * @param message 错误说明
     * @param exitCode 进程退出码；进程未退出或无法取得时为 {@code null}
     * @param stderr 已捕获的标准错误
     */
    public PiProcessException(String message, Integer exitCode, String stderr) {
        super(message + (stderr.isBlank() ? "" : "\nPI stderr:\n" + stderr));
        this.exitCode = exitCode;
        this.stderr = stderr;
    }

    /**
     * 返回进程退出码。
     *
     * @return 退出码；不可用时为 {@code null}
     */
    public Integer exitCode() {
        return exitCode;
    }

    /**
     * 返回已捕获的标准错误。
     *
     * @return 标准错误，可能为空字符串
     */
    public String stderr() {
        return stderr;
    }
}
