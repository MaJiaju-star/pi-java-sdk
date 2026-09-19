package works.earendil.pi.process;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import works.earendil.pi.config.PiClientConfig;
import works.earendil.pi.exception.PiProcessException;

/**
 * PI CLI 版本探测结果。
 *
 * @param raw {@code pi --version} 的完整输出
 * @param major 主版本号；无法解析时为 {@code null}
 * @param minor 次版本号；无法解析时为 {@code null}
 * @param patch 修订版本号；无法解析时为 {@code null}
 */
public record PiCliVersion(String raw, Integer major, Integer minor, Integer patch) {
    private static final Pattern VERSION = Pattern.compile("(?<!\\d)(\\d+)\\.(\\d+)\\.(\\d+)(?!\\d)");
    /** SDK 完成兼容验证的 PI CLI 版本。 */
    public static final String TESTED_VERSION = "0.84.4";

    /**
     * 使用十秒超时探测 PI CLI 版本。
     *
     * @param config 用于定位和启动 PI 的配置
     * @return 版本探测结果
     * @throws IOException 当无法启动进程或读取输出时
     * @throws PiProcessException 当命令超时、线程被中断或进程返回非零退出码时
     */
    public static PiCliVersion detect(PiClientConfig config) throws IOException {
        return detect(config, Duration.ofSeconds(10));
    }

    /**
     * 使用指定超时探测 PI CLI 版本。
     *
     * @param config 用于定位和启动 PI 的配置
     * @param timeout 等待版本命令结束的最长时间
     * @return 版本探测结果
     * @throws IOException 当无法启动进程或读取输出时
     * @throws NullPointerException 当任一参数为 {@code null} 时
     * @throws PiProcessException 当命令超时、线程被中断或进程返回非零退出码时
     */
    public static PiCliVersion detect(PiClientConfig config, Duration timeout) throws IOException {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(timeout, "timeout");

        //1. 拼装 pi --version 命令，并合并 stderr 到 stdout 以免丢失诊断信息。
        ArrayList<String> command = new ArrayList<>(config.command());
        command.add("--version");
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(config.workingDirectory().toFile())
                .redirectErrorStream(true);
        builder.environment().putAll(config.environment());

        //2. 启动进程并在超时内等待；超时或被中断都强制终止子进程。
        Process process = builder.start();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new PiProcessException("探测 PI CLI 版本超时", null, "");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new PiProcessException("探测 PI CLI 版本时线程被中断", null, "");
        }
        //3. 读取输出；非零退出码视为探测失败。
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        if (process.exitValue() != 0) {
            throw new PiProcessException("PI CLI 版本命令失败", process.exitValue(), output);
        }

        //4. 用正则抓取语义版本；抓不到时三个版本号留空，仍保留原始输出。
        Matcher matcher = VERSION.matcher(output);
        return matcher.find()
                ? new PiCliVersion(output, Integer.valueOf(matcher.group(1)), Integer.valueOf(matcher.group(2)),
                        Integer.valueOf(matcher.group(3)))
                : new PiCliVersion(output, null, null, null);
    }

    /**
     * 返回标准语义版本字符串。
     *
     * @return 可解析时为 {@code major.minor.patch}，否则为空
     */
    public Optional<String> semanticVersion() {
        return major == null ? Optional.empty() : Optional.of(major + "." + minor + "." + patch);
    }

    /**
     * 判断该版本是否为 SDK 已验证版本。
     *
     * @return 与 {@link #TESTED_VERSION} 相同时返回 {@code true}
     */
    public boolean isTestedVersion() {
        return semanticVersion().filter(TESTED_VERSION::equals).isPresent();
    }
}
