package works.earendil.pi.process;

/**
 * PI 子进程的最终退出信息。
 *
 * @param exitCode 进程退出码
 * @param expected 是否由客户端正常关闭触发
 * @param stderr 退出前保留的标准错误
 */
public record PiProcessExit(int exitCode, boolean expected, String stderr) {
}
