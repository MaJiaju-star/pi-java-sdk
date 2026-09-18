package works.earendil.pi.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * PI RPC 子进程的不可变启动配置。
 *
 * <p>通过 {@link #builder()} 创建。集合属性在构建时复制，构建后的配置可安全地在多个线程间共享。</p>
 */
public final class PiClientConfig {
    private final List<String> command;
    private final Path workingDirectory;
    private final Map<String, String> environment;
    private final List<String> arguments;
    private final Duration startupTimeout;
    private final Duration shutdownTimeout;
    private final Duration requestTimeout;
    private final int maxJsonLineBytes;
    private final int maxCapturedStderrChars;
    private final int eventBufferCapacity;
    private final PiEventOverflowStrategy eventOverflowStrategy;
    private final Consumer<String> stderrConsumer;
    private final Consumer<Throwable> listenerErrorHandler;

    private PiClientConfig(Builder builder) {
        this.command = List.copyOf(builder.command);
        this.workingDirectory = builder.workingDirectory.toAbsolutePath().normalize();
        this.environment = Map.copyOf(builder.environment);
        this.arguments = List.copyOf(builder.arguments);
        this.startupTimeout = builder.startupTimeout;
        this.shutdownTimeout = builder.shutdownTimeout;
        this.requestTimeout = builder.requestTimeout;
        this.maxJsonLineBytes = builder.maxJsonLineBytes;
        this.maxCapturedStderrChars = builder.maxCapturedStderrChars;
        this.eventBufferCapacity = builder.eventBufferCapacity;
        this.eventOverflowStrategy = builder.eventOverflowStrategy;
        this.stderrConsumer = builder.stderrConsumer;
        this.listenerErrorHandler = builder.listenerErrorHandler;
    }

    /**
     * 创建使用默认值的配置构建器。
     *
     * @return 新构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回 PI 可执行命令前缀。
     *
     * @return 不可变列表
     */
    public List<String> command() {
        return command;
    }

    /**
     * 返回 PI 子进程工作目录。
     *
     * @return 绝对规范化路径
     */
    public Path workingDirectory() {
        return workingDirectory;
    }

    /**
     * 返回追加到子进程环境中的变量。
     *
     * @return 不可变环境变量映射
     */
    public Map<String, String> environment() {
        return environment;
    }

    /**
     * 返回追加到 {@code --mode rpc} 后的命令行参数。
     *
     * @return 不可变参数列表
     */
    public List<String> arguments() {
        return arguments;
    }

    /**
     * 返回 RPC 就绪探测超时。
     *
     * @return 超时时间
     */
    public Duration startupTimeout() {
        return startupTimeout;
    }

    /**
     * 返回子进程关闭超时。
     *
     * @return 正常关闭和强制关闭分别等待的最长时间
     */
    public Duration shutdownTimeout() {
        return shutdownTimeout;
    }

    /**
     * 返回请求响应超时。
     *
     * @return 单个 RPC 请求等待响应的默认最长时间
     */
    public Duration requestTimeout() {
        return requestTimeout;
    }

    /**
     * 返回 JSONL 消息大小上限。
     *
     * @return 单条消息允许的最大字节数
     */
    public int maxJsonLineBytes() {
        return maxJsonLineBytes;
    }

    /**
     * 返回标准错误保留上限。
     *
     * @return 内存中保留的最大字符数
     */
    public int maxCapturedStderrChars() {
        return maxCapturedStderrChars;
    }

    /**
     * 返回事件缓冲区容量。
     *
     * @return 等待业务监听器消费的最大事件数
     */
    public int eventBufferCapacity() {
        return eventBufferCapacity;
    }

    /**
     * 返回事件溢出策略。
     *
     * @return 事件缓冲区满时的处理策略
     */
    public PiEventOverflowStrategy eventOverflowStrategy() {
        return eventOverflowStrategy;
    }

    /**
     * 返回标准错误回调。
     *
     * @return 接收 PI 标准错误增量文本的回调
     */
    public Consumer<String> stderrConsumer() {
        return stderrConsumer;
    }

    /**
     * 返回监听器错误处理器。
     *
     * @return 接收事件监听器异常的回调
     */
    public Consumer<Throwable> listenerErrorHandler() {
        return listenerErrorHandler;
    }

    /**
     * 配置构建器。
     *
     * <p>默认在 Windows 使用 {@code pi.cmd}，其他系统使用 {@code pi}；工作目录为当前目录。</p>
     */
    public static final class Builder {
        private List<String> command = new ArrayList<>(List.of(defaultPiExecutable()));
        private Path workingDirectory = Path.of(".");
        private final Map<String, String> environment = new LinkedHashMap<>();
        private final List<String> arguments = new ArrayList<>();
        private Duration startupTimeout = Duration.ofSeconds(15);
        private Duration shutdownTimeout = Duration.ofSeconds(3);
        private Duration requestTimeout = Duration.ofMinutes(5);
        private int maxJsonLineBytes = 16 * 1024 * 1024;
        private int maxCapturedStderrChars = 64 * 1024;
        private int eventBufferCapacity = 1024;
        private PiEventOverflowStrategy eventOverflowStrategy = PiEventOverflowStrategy.BLOCK;
        private Consumer<String> stderrConsumer = ignored -> { };
        private Consumer<Throwable> listenerErrorHandler = Throwable::printStackTrace;

        /** 创建使用默认值的构建器。 */
        public Builder() {
        }

        private static String defaultPiExecutable() {
            return System.getProperty("os.name", "").toLowerCase().contains("win") ? "pi.cmd" : "pi";
        }

        /**
         * 设置单个 PI 可执行文件。
         *
         * @param executable 可执行文件名或路径
         * @return 当前构建器
         * @throws NullPointerException 当参数为 {@code null} 时
         */
        public Builder executable(String executable) {
            this.command = new ArrayList<>(List.of(Objects.requireNonNull(executable, "executable")));
            return this;
        }

        /**
         * 设置完整命令前缀。例如 {@code ["node", "/path/to/cli.js"]}。
         * SDK 会继续追加 {@code --mode rpc} 和其他 PI 参数。
         *
         * @param command 非空命令及参数列表
         * @return 当前构建器
         * @throws IllegalArgumentException 当列表为 {@code null} 或空列表时
         */
        public Builder command(List<String> command) {
            if (command == null || command.isEmpty()) {
                throw new IllegalArgumentException("command 不能为空");
            }
            this.command = new ArrayList<>(command);
            return this;
        }

        /**
         * 设置 PI 子进程工作目录。
         *
         * @param workingDirectory 工作目录
         * @return 当前构建器
         * @throws NullPointerException 当参数为 {@code null} 时
         */
        public Builder workingDirectory(Path workingDirectory) {
            this.workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory");
            return this;
        }

        /**
         * 为 PI 子进程追加或覆盖一个环境变量。
         *
         * @param name 环境变量名
         * @param value 环境变量值
         * @return 当前构建器
         * @throws NullPointerException 当任一参数为 {@code null} 时
         */
        public Builder environment(String name, String value) {
            this.environment.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * 设置 {@code --provider} 参数。
         *
         * @param provider 提供商名称
         * @return 当前构建器
         * @throws NullPointerException 当参数为 {@code null} 时
         */
        public Builder provider(String provider) {
            return argument("--provider", provider);
        }

        /**
         * 设置 {@code --model} 参数。
         *
         * @param model 模型 ID
         * @return 当前构建器
         * @throws NullPointerException 当参数为 {@code null} 时
         */
        public Builder model(String model) {
            return argument("--model", model);
        }

        /**
         * 设置 {@code --name} 会话名称参数。
         *
         * @param name 会话名称
         * @return 当前构建器
         * @throws NullPointerException 当参数为 {@code null} 时
         */
        public Builder sessionName(String name) {
            return argument("--name", name);
        }

        /**
         * 设置 {@code --session-dir} 会话存储目录。
         *
         * @param path 会话目录
         * @return 当前构建器
         * @throws NullPointerException 当参数为 {@code null} 时
         */
        public Builder sessionDirectory(Path path) {
            return argument("--session-dir", path.toAbsolutePath().normalize().toString());
        }

        /**
         * 按条件追加 {@code --no-session} 参数。
         *
         * @param noSession {@code true} 表示禁用会话持久化
         * @return 当前构建器
         */
        public Builder noSession(boolean noSession) {
            if (noSession) {
                arguments.add("--no-session");
            }
            return this;
        }

        /**
         * 追加一个原始 PI 命令行参数。
         *
         * @param argument 参数
         * @return 当前构建器
         * @throws NullPointerException 当参数为 {@code null} 时
         */
        public Builder argument(String argument) {
            arguments.add(Objects.requireNonNull(argument, "argument"));
            return this;
        }

        /**
         * 追加一个名称和值分离的 PI 命令行参数。
         *
         * @param name 参数名
         * @param value 参数值
         * @return 当前构建器
         * @throws NullPointerException 当任一参数为 {@code null} 时
         */
        public Builder argument(String name, String value) {
            arguments.add(Objects.requireNonNull(name, "name"));
            arguments.add(Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * 设置 RPC 启动就绪探测超时。
         *
         * @param startupTimeout 正数时长
         * @return 当前构建器
         * @throws NullPointerException 当参数为 {@code null} 时
         * @throws IllegalArgumentException 当时长不为正数时
         */
        public Builder startupTimeout(Duration startupTimeout) {
            this.startupTimeout = requirePositive(startupTimeout, "startupTimeout");
            return this;
        }

        /**
         * 设置关闭子进程时每个等待阶段的超时。
         *
         * @param shutdownTimeout 正数时长
         * @return 当前构建器
         * @throws NullPointerException 当参数为 {@code null} 时
         * @throws IllegalArgumentException 当时长不为正数时
         */
        public Builder shutdownTimeout(Duration shutdownTimeout) {
            this.shutdownTimeout = requirePositive(shutdownTimeout, "shutdownTimeout");
            return this;
        }

        /**
         * 设置单个 RPC 请求等待响应的默认最长时间。
         *
         * @param requestTimeout 正数时长
         * @return 当前构建器
         * @throws NullPointerException 当参数为 {@code null} 时
         * @throws IllegalArgumentException 当时长不为正数时
         */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requirePositive(requestTimeout, "requestTimeout");
            return this;
        }

        /**
         * 设置单条 JSONL 消息的字节上限。
         *
         * @param maxJsonLineBytes 字节上限，不能小于 1024
         * @return 当前构建器
         * @throws IllegalArgumentException 当上限小于 1024 时
         */
        public Builder maxJsonLineBytes(int maxJsonLineBytes) {
            if (maxJsonLineBytes < 1024) {
                throw new IllegalArgumentException("maxJsonLineBytes 不能小于 1024");
            }
            this.maxJsonLineBytes = maxJsonLineBytes;
            return this;
        }

        /**
         * 设置内存中保留的标准错误字符上限。
         *
         * @param maxCapturedStderrChars 字符上限；零表示不保留
         * @return 当前构建器
         * @throws IllegalArgumentException 当上限为负数时
         */
        public Builder maxCapturedStderrChars(int maxCapturedStderrChars) {
            if (maxCapturedStderrChars < 0) {
                throw new IllegalArgumentException("maxCapturedStderrChars 不能为负数");
            }
            this.maxCapturedStderrChars = maxCapturedStderrChars;
            return this;
        }

        /**
         * 设置等待业务监听器消费的最大事件数。
         *
         * @param eventBufferCapacity 正数容量
         * @return 当前构建器
         * @throws IllegalArgumentException 当容量小于 1 时
         */
        public Builder eventBufferCapacity(int eventBufferCapacity) {
            if (eventBufferCapacity < 1) {
                throw new IllegalArgumentException("eventBufferCapacity 必须大于 0");
            }
            this.eventBufferCapacity = eventBufferCapacity;
            return this;
        }

        /**
         * 设置事件缓冲区已满时的处理方式。
         *
         * @param eventOverflowStrategy 溢出策略
         * @return 当前构建器
         * @throws NullPointerException 当参数为 {@code null} 时
         */
        public Builder eventOverflowStrategy(PiEventOverflowStrategy eventOverflowStrategy) {
            this.eventOverflowStrategy = Objects.requireNonNull(eventOverflowStrategy, "eventOverflowStrategy");
            return this;
        }

        /**
         * 设置标准错误增量文本回调。
         *
         * @param stderrConsumer 回调；在专用虚拟线程中执行
         * @return 当前构建器
         * @throws NullPointerException 当参数为 {@code null} 时
         */
        public Builder stderrConsumer(Consumer<String> stderrConsumer) {
            this.stderrConsumer = Objects.requireNonNull(stderrConsumer, "stderrConsumer");
            return this;
        }

        /**
         * 设置事件监听器异常处理器。
         *
         * @param listenerErrorHandler 异常处理器
         * @return 当前构建器
         * @throws NullPointerException 当参数为 {@code null} 时
         */
        public Builder listenerErrorHandler(Consumer<Throwable> listenerErrorHandler) {
            this.listenerErrorHandler = Objects.requireNonNull(listenerErrorHandler, "listenerErrorHandler");
            return this;
        }

        /**
         * 构建不可变配置。
         *
         * @return 新配置
         */
        public PiClientConfig build() {
            return new PiClientConfig(this);
        }

        private static Duration requirePositive(Duration value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException(name + " 必须大于 0");
            }
            return value;
        }
    }
}
