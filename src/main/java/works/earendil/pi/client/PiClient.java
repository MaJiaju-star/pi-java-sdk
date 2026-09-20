package works.earendil.pi.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import works.earendil.pi.config.PiClientConfig;
import works.earendil.pi.conversation.PiImage;
import works.earendil.pi.conversation.PiStreamingBehavior;
import works.earendil.pi.conversation.QueueMode;
import works.earendil.pi.conversation.ThinkingLevel;
import works.earendil.pi.event.PiEvent;
import works.earendil.pi.event.PiSubscription;
import works.earendil.pi.exception.PiClientException;
import works.earendil.pi.exception.PiProcessException;
import works.earendil.pi.exception.PiProtocolException;
import works.earendil.pi.exception.PiRequestTimeoutException;
import works.earendil.pi.exception.PiRpcException;
import works.earendil.pi.extension.PiExtensionUiRequest;
import works.earendil.pi.extension.PiExtensionUiResponse;
import works.earendil.pi.process.PiProcessExit;
import works.earendil.pi.rpc.PiResponse;
import works.earendil.pi.rpc.PiRpcCommand;
import works.earendil.pi.rpc.PiRpcTypes;
import works.earendil.pi.rpc.PiRun;

/**
 * PI Coding Agent RPC Java 客户端。
 *
 * <p>一个实例对应一个 {@code pi --mode rpc} 子进程和一个活动会话。该类线程安全。</p>
 */
public final class PiClient implements AutoCloseable {
    private record PendingRequest(String command, CompletableFuture<PiResponse> future) {
    }

    private final PiClientConfig config;
    private final ObjectMapper mapper;
    private final Process process;
    private final BufferedWriter stdin;
    private final Object writeLock = new Object();
    private final ConcurrentHashMap<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Set<String> ignoredResponseIds = ConcurrentHashMap.newKeySet();
    private final CopyOnWriteArrayList<Consumer<PiEvent>> eventListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<PiExtensionUiRequest>> extensionUiListeners = new CopyOnWriteArrayList<>();
    private final ArrayBlockingQueue<PiEvent> eventBuffer;
    private final AtomicReference<CompletableFuture<PiEvent>> activeRun = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CompletableFuture<PiProcessExit> exitFuture = new CompletableFuture<>();
    private final StringBuilder stderr = new StringBuilder();
    private Thread eventDispatcher;

    private PiClient(PiClientConfig config, ObjectMapper mapper, Process process) {
        this.config = config;
        this.mapper = mapper;
        this.process = process;
        this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.eventBuffer = new ArrayBlockingQueue<>(config.eventBufferCapacity());
    }

    /**
     * 启动 PI 子进程，并用 {@code get_state} 探测 RPC 是否就绪。
     *
     * @param config 子进程和客户端配置
     * @return 已就绪的客户端
     * @throws IOException 当无法启动 PI 子进程时
     * @throws NullPointerException 当配置为 {@code null} 时
     * @throws PiClientException 当启动探测超时、被中断或 RPC 初始化失败时
     */
    public static PiClient start(PiClientConfig config) throws IOException {
        Objects.requireNonNull(config, "config");

        //1. 拼装命令行：pi --mode rpc + 用户附加参数。
        List<String> commandLine = new ArrayList<>(config.command());
        commandLine.add("--mode");
        commandLine.add("rpc");
        commandLine.addAll(config.arguments());

        //2. 启动子进程，并写入配置的工作目录与环境变量。
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine)
                .directory(config.workingDirectory().toFile());
        processBuilder.environment().putAll(config.environment());
        Process process = processBuilder.start();

        //3. 建立客户端并立即启动读取线程，避免 PI 因管道写满而阻塞。
        PiClient client = new PiClient(config, new ObjectMapper(), process);
        client.startReaders();

        //4. 用 get_state 探测 RPC 就绪；失败时先关闭客户端，避免泄漏子进程。
        try {
            client.getState().get(config.startupTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return client;
        } catch (TimeoutException error) {
            client.close();
            throw new PiProcessException("等待 PI RPC 启动超时", process.isAlive() ? null : process.exitValue(), client.stderr());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            client.close();
            throw new PiProcessException("等待 PI RPC 启动时线程被中断", null, client.stderr());
        } catch (java.util.concurrent.ExecutionException error) {
            client.close();
            Throwable cause = error.getCause();
            if (cause instanceof PiClientException piError) {
                throw piError;
            }
            throw new PiProcessException("PI RPC 启动失败: " + cause.getMessage(), null, client.stderr());
        }
    }

    /**
     * 使用 SDK 默认配置启动 PI。
     *
     * @return 已就绪的客户端
     * @throws IOException 当无法启动 PI 子进程时
     * @throws PiClientException 当 RPC 初始化失败时
     */
    public static PiClient start() throws IOException {
        return start(PiClientConfig.builder().build());
    }

    /**
     * 返回客户端用于协议转换的 Jackson 映射器。
     *
     * @return 当前客户端的映射器
     */
    public ObjectMapper objectMapper() {
        return mapper;
    }

    /**
     * 返回当前客户端配置。
     *
     * @return 启动当前客户端的不可变配置
     */
    public PiClientConfig config() {
        return config;
    }

    /**
     * 判断当前客户端是否可用。
     *
     * @return 客户端未关闭且 PI 子进程仍存活时返回 {@code true}
     */
    public boolean isAlive() {
        return !closed.get() && process.isAlive();
    }

    /**
     * 返回子进程退出通知。
     *
     * @return 子进程退出时完成的 future；正常 {@link #close()} 触发的退出也包含在内
     */
    public CompletableFuture<PiProcessExit> onExit() {
        return exitFuture;
    }

    /**
     * 关闭当前实例，并使用相同配置启动一个新客户端。
     *
     * <p>事件订阅不会自动复制。</p>
     *
     * @return 新客户端
     * @throws IOException 当新 PI 子进程无法启动时
     * @throws PiClientException 当新客户端 RPC 初始化失败时
     */
    public PiClient restart() throws IOException {
        close();
        return start(config);
    }

    /**
     * 返回当前保留的 PI 标准错误。
     *
     * @return 最多包含配置上限字符数的标准错误快照
     */
    public String stderr() {
        synchronized (stderr) {
            return stderr.toString();
        }
    }

    /**
     * 订阅所有 PI 事件。
     *
     * <p>监听器在专用事件分发线程中按顺序执行。监听器异常会被隔离并交给配置的错误处理器。</p>
     *
     * @param listener 事件监听器
     * @return 可用于取消订阅的句柄
     * @throws NullPointerException 当监听器为 {@code null} 时
     */
    public PiSubscription subscribe(Consumer<PiEvent> listener) {
        Objects.requireNonNull(listener, "listener");
        eventListeners.add(listener);
        return () -> eventListeners.remove(listener);
    }

    /**
     * 只订阅 Extension UI 请求。
     *
     * @param listener Extension UI 请求监听器
     * @return 可用于取消订阅的句柄
     * @throws NullPointerException 当监听器为 {@code null} 时
     */
    public PiSubscription subscribeExtensionUi(Consumer<PiExtensionUiRequest> listener) {
        Objects.requireNonNull(listener, "listener");
        extensionUiListeners.add(listener);
        return () -> extensionUiListeners.remove(listener);
    }

    /**
     * 回复需要用户输入的 Extension UI 请求。
     *
     * @param response 响应内容
     * @throws NullPointerException 当响应为 {@code null} 时
     * @throws PiProcessException 当客户端已关闭或写入失败时
     */
    public void respond(PiExtensionUiResponse response) {
        Objects.requireNonNull(response, "response");
        sendNotification(response.toJson(mapper));
    }

    /**
     * 发送纯文本 prompt。
     *
     * <p>一个客户端同一时间只允许一个由该方法启动且尚未 settled 的运行。</p>
     *
     * @param message 用户消息
     * @return 包含接收和最终稳定两个阶段的运行句柄
     * @throws NullPointerException 当消息为 {@code null} 时
     * @throws IllegalStateException 当上一轮 prompt 尚未 settled 时
     */
    public PiRun prompt(String message) {
        return prompt(message, List.of());
    }

    /**
     * 发送纯文本 prompt，并指定 PI 正在流式运行时的处理方式。
     *
     * @param message 用户消息
     * @param streamingBehavior 流式处理方式；{@code null} 表示使用 PI 默认行为
     * @return 运行句柄
     * @throws NullPointerException 当消息为 {@code null} 时
     * @throws IllegalStateException 当上一轮 prompt 尚未 settled 时
     */
    public PiRun prompt(String message, PiStreamingBehavior streamingBehavior) {
        return prompt(message, List.of(), streamingBehavior);
    }

    /**
     * 发送包含图片的 prompt。
     *
     * @param message 用户消息
     * @param images 图片列表；{@code null} 或空列表表示没有图片
     * @return 运行句柄
     * @throws NullPointerException 当消息为 {@code null} 时
     * @throws IllegalStateException 当上一轮 prompt 尚未 settled 时
     */
    public PiRun prompt(String message, List<PiImage> images) {
        return prompt(message, images, null);
    }

    /**
     * 发送 prompt，并指定 PI 正在流式运行时的处理方式。
     *
     * @param message 用户消息
     * @param images 图片列表；{@code null} 或空列表表示没有图片
     * @param streamingBehavior 流式处理方式；{@code null} 表示使用 PI 默认行为
     * @return 运行句柄
     * @throws NullPointerException 当消息为 {@code null} 时
     * @throws IllegalStateException 当上一轮 prompt 尚未 settled 时
     */
    public PiRun prompt(String message, List<PiImage> images, PiStreamingBehavior streamingBehavior) {
        Objects.requireNonNull(message, "message");

        //1. 抢占运行位：同一客户端同一时刻只允许一个未 settled 的运行。
        CompletableFuture<PiEvent> settled = new CompletableFuture<>();
        if (!activeRun.compareAndSet(null, settled)) {
            throw new IllegalStateException("PI 当前仍在运行；请使用 steer() 或 followUp()");
        }

        //2. 组装参数：消息、可选图片、可选流式行为。
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("message", message);
        if (images != null && !images.isEmpty()) {
            List<Map<String, String>> imageValues = images.stream()
                    .map(image -> Map.of("type", "image", "data", image.data(), "mimeType", image.mimeType()))
                    .toList();
            arguments.put("images", imageValues);
        }

        if (streamingBehavior != null) {
            arguments.put("streamingBehavior", streamingBehavior.wireValue());
        }

        //3. 发送命令；若未被 PI 接收，则释放运行位并让 settled 异常完成。
        CompletableFuture<PiResponse> accepted = request("prompt", arguments);
        accepted.whenComplete((response, error) -> {
            if (error != null) {
                settled.completeExceptionally(error);
                activeRun.compareAndSet(settled, null);
            }
        });

        //4. 返回「已接收」与「已稳定」两阶段句柄。
        return new PiRun(accepted, settled);
    }

    /**
     * 将纯文本消息注入当前运行。
     *
     * @param message 用户消息
     * @return RPC 响应 future
     */
    public CompletableFuture<PiResponse> steer(String message) {
        return steer(message, List.of());
    }

    /**
     * 将带图片的消息注入当前运行。
     *
     * @param message 用户消息
     * @param images 图片列表；{@code null} 或空列表表示没有图片
     * @return RPC 响应 future
     */
    public CompletableFuture<PiResponse> steer(String message, List<PiImage> images) {
        return request(PiRpcCommand.STEER, messageArguments(message, images));
    }

    /**
     * 将纯文本消息加入后续队列。
     *
     * @param message 用户消息
     * @return RPC 响应 future
     */
    public CompletableFuture<PiResponse> followUp(String message) {
        return followUp(message, List.of());
    }

    /**
     * 将带图片的消息加入后续队列。
     *
     * @param message 用户消息
     * @param images 图片列表；{@code null} 或空列表表示没有图片
     * @return RPC 响应 future
     */
    public CompletableFuture<PiResponse> followUp(String message, List<PiImage> images) {
        return request(PiRpcCommand.FOLLOW_UP, messageArguments(message, images));
    }

    /**
     * 中止当前 Agent 运行。
     *
     * @return RPC 响应 future
     */
    public CompletableFuture<PiResponse> abort() {
        return request(PiRpcCommand.ABORT);
    }

    /**
     * 清空 steering 和 follow-up 消息队列。
     *
     * @return 清空后的队列状态 future
     */
    public CompletableFuture<PiRpcTypes.QueueState> clearQueue() {
        return requestData(PiRpcCommand.CLEAR_QUEUE, Map.of(), PiRpcTypes.QueueState.class);
    }

    /**
     * 创建并切换到新会话。
     *
     * @param parentSession 父会话文件路径；{@code null} 表示没有父会话
     * @return 会话切换结果 future
     */
    public CompletableFuture<PiRpcTypes.Cancelled> newSession(String parentSession) {
        return requestData(PiRpcCommand.NEW_SESSION,
                parentSession == null ? Map.of() : Map.of("parentSession", parentSession),
                PiRpcTypes.Cancelled.class);
    }

    /**
     * 创建并切换到无父会话的新会话。
     *
     * @return 会话切换结果 future
     */
    public CompletableFuture<PiRpcTypes.Cancelled> newSession() {
        return newSession(null);
    }

    /**
     * 获取当前会话状态。
     *
     * @return 会话状态 future
     */
    public CompletableFuture<PiRpcTypes.SessionState> getState() {
        return requestData(PiRpcCommand.GET_STATE, Map.of(), PiRpcTypes.SessionState.class);
    }

    /**
     * 获取当前会话消息。
     *
     * @return 原始消息列表 future
     */
    public CompletableFuture<List<JsonNode>> getMessages() {
        return requestData(PiRpcCommand.GET_MESSAGES, Map.of(), PiRpcTypes.Messages.class)
                .thenApply(PiRpcTypes.Messages::messages);
    }

    /**
     * 获取可用模型。
     *
     * @return 模型列表 future
     */
    public CompletableFuture<List<PiRpcTypes.Model>> getAvailableModels() {
        return requestData(PiRpcCommand.GET_AVAILABLE_MODELS, Map.of(), PiRpcTypes.ModelList.class)
                .thenApply(PiRpcTypes.ModelList::models);
    }

    /**
     * 获取可用斜杠命令。
     *
     * @return 命令列表 future
     */
    public CompletableFuture<List<PiRpcTypes.SlashCommand>> getCommands() {
        return requestData(PiRpcCommand.GET_COMMANDS, Map.of(), PiRpcTypes.SlashCommands.class)
                .thenApply(PiRpcTypes.SlashCommands::commands);
    }

    /**
     * 设置当前模型。
     *
     * @param provider 提供商名称
     * @param modelId 模型 ID
     * @return 切换后的模型 future
     * @throws NullPointerException 当任一参数为 {@code null} 时
     */
    public CompletableFuture<PiRpcTypes.Model> setModel(String provider, String modelId) {
        return requestData(PiRpcCommand.SET_MODEL, Map.of(
                "provider", Objects.requireNonNull(provider, "provider"),
                "modelId", Objects.requireNonNull(modelId, "modelId")
        ), PiRpcTypes.Model.class);
    }

    /**
     * 循环切换模型。
     *
     * @return 切换结果 future
     */
    public CompletableFuture<PiRpcTypes.ModelCycle> cycleModel() {
        return requestData(PiRpcCommand.CYCLE_MODEL, Map.of(), PiRpcTypes.ModelCycle.class);
    }

    /**
     * 设置当前思考等级。
     *
     * @param level 思考等级
     * @return RPC 响应 future
     * @throws NullPointerException 当等级为 {@code null} 时
     */
    public CompletableFuture<PiResponse> setThinkingLevel(ThinkingLevel level) {
        return request(PiRpcCommand.SET_THINKING_LEVEL, Map.of("level", level.wireValue()));
    }

    /**
     * 循环切换思考等级。
     *
     * @return 切换结果 future
     */
    public CompletableFuture<PiRpcTypes.ThinkingLevelCycle> cycleThinkingLevel() {
        return requestData(PiRpcCommand.CYCLE_THINKING_LEVEL, Map.of(), PiRpcTypes.ThinkingLevelCycle.class);
    }

    /**
     * 获取当前模型可用的思考等级。
     *
     * @return 思考等级列表 future
     */
    public CompletableFuture<List<ThinkingLevel>> getAvailableThinkingLevels() {
        return requestData(PiRpcCommand.GET_AVAILABLE_THINKING_LEVELS, Map.of(), PiRpcTypes.ThinkingLevelList.class)
                .thenApply(PiRpcTypes.ThinkingLevelList::levels);
    }

    /**
     * 设置 steering 队列投递模式。
     *
     * @param mode 队列模式
     * @return RPC 响应 future
     * @throws NullPointerException 当模式为 {@code null} 时
     */
    public CompletableFuture<PiResponse> setSteeringMode(QueueMode mode) {
        return request(PiRpcCommand.SET_STEERING_MODE, Map.of("mode", mode.wireValue()));
    }

    /**
     * 设置 follow-up 队列投递模式。
     *
     * @param mode 队列模式
     * @return RPC 响应 future
     * @throws NullPointerException 当模式为 {@code null} 时
     */
    public CompletableFuture<PiResponse> setFollowUpMode(QueueMode mode) {
        return request(PiRpcCommand.SET_FOLLOW_UP_MODE, Map.of("mode", mode.wireValue()));
    }

    /**
     * 使用自定义指令压缩当前上下文。
     *
     * @param customInstructions 压缩指令；{@code null} 表示使用 PI 默认指令
     * @return 压缩结果 future
     */
    public CompletableFuture<PiRpcTypes.CompactionResult> compact(String customInstructions) {
        return requestData(PiRpcCommand.COMPACT,
                customInstructions == null ? Map.of() : Map.of("customInstructions", customInstructions),
                PiRpcTypes.CompactionResult.class);
    }

    /**
     * 使用默认指令压缩当前上下文。
     *
     * @return 压缩结果 future
     */
    public CompletableFuture<PiRpcTypes.CompactionResult> compact() {
        return compact(null);
    }

    /**
     * 启用或禁用自动上下文压缩。
     *
     * @param enabled 是否启用
     * @return RPC 响应 future
     */
    public CompletableFuture<PiResponse> setAutoCompaction(boolean enabled) {
        return request(PiRpcCommand.SET_AUTO_COMPACTION, Map.of("enabled", enabled));
    }

    /**
     * 启用或禁用自动重试。
     *
     * @param enabled 是否启用
     * @return RPC 响应 future
     */
    public CompletableFuture<PiResponse> setAutoRetry(boolean enabled) {
        return request(PiRpcCommand.SET_AUTO_RETRY, Map.of("enabled", enabled));
    }

    /**
     * 中止等待中的自动重试。
     *
     * @return RPC 响应 future
     */
    public CompletableFuture<PiResponse> abortRetry() {
        return request(PiRpcCommand.ABORT_RETRY);
    }

    /**
     * 在 PI 工作目录执行 Bash 命令。
     *
     * @param command shell 命令
     * @param excludeFromContext 是否不把命令和结果加入模型上下文
     * @return Bash 执行结果 future
     * @throws NullPointerException 当命令为 {@code null} 时
     */
    public CompletableFuture<PiRpcTypes.BashResult> bash(String command, boolean excludeFromContext) {
        return requestData(PiRpcCommand.BASH, Map.of(
                "command", Objects.requireNonNull(command, "command"),
                "excludeFromContext", excludeFromContext
        ), PiRpcTypes.BashResult.class);
    }

    /**
     * 在 PI 工作目录执行 Bash 命令，并将命令和结果加入模型上下文。
     *
     * @param command shell 命令
     * @return Bash 执行结果 future
     * @throws NullPointerException 当命令为 {@code null} 时
     */
    public CompletableFuture<PiRpcTypes.BashResult> bash(String command) {
        return bash(command, false);
    }

    /**
     * 中止当前 Bash 命令。
     *
     * @return RPC 响应 future
     */
    public CompletableFuture<PiResponse> abortBash() {
        return request(PiRpcCommand.ABORT_BASH);
    }

    /**
     * 获取当前会话统计信息。
     *
     * @return 会话统计 future
     */
    public CompletableFuture<PiRpcTypes.SessionStats> getSessionStats() {
        return requestData(PiRpcCommand.GET_SESSION_STATS, Map.of(), PiRpcTypes.SessionStats.class);
    }

    /**
     * 将当前会话导出为 HTML。
     *
     * @param outputPath 输出路径；{@code null} 表示由 PI 选择路径
     * @return 实际输出路径 future
     */
    public CompletableFuture<PiRpcTypes.PathResult> exportHtml(String outputPath) {
        return requestData(PiRpcCommand.EXPORT_HTML,
                outputPath == null ? Map.of() : Map.of("outputPath", outputPath),
                PiRpcTypes.PathResult.class);
    }

    /**
     * 使用 PI 默认路径导出当前会话。
     *
     * @return 实际输出路径 future
     */
    public CompletableFuture<PiRpcTypes.PathResult> exportHtml() {
        return exportHtml(null);
    }

    /**
     * 切换到已有会话。
     *
     * @param sessionPath 会话文件路径
     * @return 会话切换结果 future
     * @throws NullPointerException 当路径为 {@code null} 时
     */
    public CompletableFuture<PiRpcTypes.Cancelled> switchSession(String sessionPath) {
        return requestData(PiRpcCommand.SWITCH_SESSION,
                Map.of("sessionPath", Objects.requireNonNull(sessionPath, "sessionPath")),
                PiRpcTypes.Cancelled.class);
    }

    /**
     * 从指定会话条目创建分支。
     *
     * @param entryId 分支点条目 ID
     * @return 分支结果 future
     * @throws NullPointerException 当条目 ID 为 {@code null} 时
     */
    public CompletableFuture<PiRpcTypes.ForkResult> fork(String entryId) {
        return requestData(PiRpcCommand.FORK,
                Map.of("entryId", Objects.requireNonNull(entryId, "entryId")),
                PiRpcTypes.ForkResult.class);
    }

    /**
     * 克隆当前会话。
     *
     * @return 克隆结果 future
     */
    public CompletableFuture<PiRpcTypes.Cancelled> cloneSession() {
        return requestData(PiRpcCommand.CLONE, Map.of(), PiRpcTypes.Cancelled.class);
    }

    /**
     * 获取可作为分支点的消息。
     *
     * @return 消息列表 future
     */
    public CompletableFuture<List<PiRpcTypes.ForkMessage>> getForkMessages() {
        return requestData(PiRpcCommand.GET_FORK_MESSAGES, Map.of(), PiRpcTypes.ForkMessages.class)
                .thenApply(PiRpcTypes.ForkMessages::messages);
    }

    /**
     * 获取指定条目之后的会话条目。
     *
     * @param since 起始条目 ID；{@code null} 表示获取全部条目
     * @return 会话条目结果 future
     */
    public CompletableFuture<PiRpcTypes.Entries> getEntries(String since) {
        return requestData(PiRpcCommand.GET_ENTRIES,
                since == null ? Map.of() : Map.of("since", since), PiRpcTypes.Entries.class);
    }

    /**
     * 获取全部会话条目。
     *
     * @return 会话条目 future
     */
    public CompletableFuture<PiRpcTypes.Entries> getEntries() {
        return getEntries(null);
    }

    /**
     * 获取当前会话分支树。
     *
     * @return 会话树 future
     */
    public CompletableFuture<PiRpcTypes.SessionTree> getTree() {
        return requestData(PiRpcCommand.GET_TREE, Map.of(), PiRpcTypes.SessionTree.class);
    }

    /**
     * 获取最后一条助手消息文本。
     *
     * @return 文本 future；不存在时结果可为 {@code null}
     */
    public CompletableFuture<String> getLastAssistantText() {
        return requestData(PiRpcCommand.GET_LAST_ASSISTANT_TEXT, Map.of(), PiRpcTypes.LastAssistantText.class)
                .thenApply(PiRpcTypes.LastAssistantText::text);
    }

    /**
     * 设置当前会话名称。
     *
     * @param name 新名称
     * @return RPC 响应 future
     * @throws NullPointerException 当名称为 {@code null} 时
     */
    public CompletableFuture<PiResponse> setSessionName(String name) {
        return request(PiRpcCommand.SET_SESSION_NAME, Map.of("name", Objects.requireNonNull(name, "name")));
    }

    /**
     * 发送没有额外字段的自定义 RPC 命令。
     *
     * @param command 协议命令名
     * @return RPC 响应 future
     */
    public CompletableFuture<PiResponse> request(String command) {
        return request(command, Map.of());
    }

    /**
     * 发送没有额外字段的已知 RPC 命令。
     *
     * @param command 已知命令
     * @return RPC 响应 future
     */
    public CompletableFuture<PiResponse> request(PiRpcCommand command) {
        return request(command, Map.of());
    }

    /**
     * 发送带参数的已知 RPC 命令。
     *
     * @param command 已知命令
     * @param arguments 追加到请求 JSON 的字段
     * @return RPC 响应 future
     * @throws NullPointerException 当任一参数为 {@code null} 时
     */
    public CompletableFuture<PiResponse> request(PiRpcCommand command, Map<String, ?> arguments) {
        Objects.requireNonNull(command, "command");
        return request(command.wireValue(), arguments);
    }

    /**
     * 通用 RPC 命令入口。arguments 会被转换为 JSON 并与自动生成的 id、type 合并。
     *
     * @param command 协议命令名
     * @param arguments 追加到请求 JSON 的字段
     * @return RPC 响应 future
     * @throws NullPointerException 当任一参数为 {@code null} 时
     */
    public CompletableFuture<PiResponse> request(String command, Map<String, ?> arguments) {
        return request(command, arguments, config.requestTimeout());
    }

    /**
     * 使用本次调用指定的超时时间发送 RPC 命令。
     *
     * @param command 协议命令名
     * @param arguments 追加到请求 JSON 的字段
     * @param timeout 等待响应的最长时间
     * @return RPC 响应 future
     * @throws NullPointerException 当任一参数为 {@code null} 时
     * @throws IllegalArgumentException 当超时时间不为正数时
     */
    public CompletableFuture<PiResponse> request(String command, Map<String, ?> arguments, Duration timeout) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(arguments, "arguments");
        ObjectNode body = mapper.valueToTree(arguments);
        body.put("type", command);
        return request(body, timeout);
    }

    /**
     * 发送自定义命令，适用于 PI 新增但 SDK 尚未封装的 RPC 命令。
     *
     * @param command 必须包含非空 {@code type} 的命令对象；SDK 会覆盖其 {@code id}
     * @return RPC 响应 future
     */
    public CompletableFuture<PiResponse> request(ObjectNode command) {
        return request(command, config.requestTimeout());
    }

    /**
     * 发送自定义命令，并为本次请求指定响应超时。
     *
     * @param command 必须包含非空 {@code type} 的命令对象；SDK 会复制对象并覆盖其 {@code id}
     * @param timeout 等待响应的最长时间
     * @return RPC 响应 future；超时或服务端错误通过 future 异常完成
     * @throws NullPointerException 当任一参数为 {@code null} 时
     * @throws IllegalArgumentException 当超时时间不为正数或命令缺少非空 {@code type} 时
     * @throws PiProcessException 当客户端已关闭时
     */
    public CompletableFuture<PiResponse> request(ObjectNode command, Duration timeout) {
        //1. 前置校验：客户端可用、超时合法。
        ensureOpen();
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout 必须大于 0");
        }

        //2. 复制命令体并补全请求 ID；调用方的节点不会被修改。
        ObjectNode body = command.deepCopy();
        String commandType = body.path("type").asText();
        if (commandType.isBlank()) {
            throw new IllegalArgumentException("RPC 命令必须包含非空 type");
        }
        String id = UUID.randomUUID().toString();
        body.put("id", id);

        //3. 登记待完成请求，让 stdout 读到响应时能找回对应 future。
        CompletableFuture<PiResponse> future = new CompletableFuture<>();
        PendingRequest pending = new PendingRequest(commandType, future);
        pendingRequests.put(id, pending);

        //4. 挂超时定时器；超时后摘除登记并记住该 ID，使迟到响应被忽略。
        CompletableFuture.delayedExecutor(timeout.toMillis(), TimeUnit.MILLISECONDS).execute(() -> {
            if (pendingRequests.remove(id, pending)) {
                ignoredResponseIds.add(id);
                future.completeExceptionally(new PiRequestTimeoutException(commandType, timeout));
            }
        });

        //5. 写入 stdin；写入失败立即摘除登记，避免残留。
        try {
            writeJson(body);
        } catch (RuntimeException error) {
            pendingRequests.remove(id);
            future.completeExceptionally(error);
        }
        return future;
    }

    private <T> CompletableFuture<T> requestData(PiRpcCommand command, Map<String, ?> arguments, Class<T> type) {
        return request(command, arguments).thenApply(response -> response.dataAs(mapper, type));
    }

    private static Map<String, Object> messageArguments(String message, List<PiImage> images) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("message", Objects.requireNonNull(message, "message"));
        if (images != null && !images.isEmpty()) {
            arguments.put("images", images.stream()
                    .map(image -> Map.of("type", "image", "data", image.data(), "mimeType", image.mimeType()))
                    .toList());
        }
        return arguments;
    }

    /**
     * 发送不期待 response 的协议消息，例如 {@code extension_ui_response}。
     *
     * @param message 完整协议消息
     * @throws NullPointerException 当消息为 {@code null} 时
     * @throws PiProcessException 当客户端已关闭或写入失败时
     */
    public void sendNotification(ObjectNode message) {
        ensureOpen();
        writeJson(message);
    }

    private void startReaders() {
        //1. 事件分发线程：串行执行监听器，保证事件顺序。
        eventDispatcher = Thread.ofVirtual().name("pi-rpc-events").start(this::dispatchEvents);

        //2. stdout 读取线程：逐行解析 JSONL；解析失败视为协议错误并终止子进程。
        Thread.ofVirtual().name("pi-rpc-stdout").start(() -> {
            try {
                StrictJsonlReader.read(process.getInputStream(), config.maxJsonLineBytes(), this::handleLine);
            } catch (Throwable error) {
                if (!closed.get()) {
                    fail(new PiProtocolException("读取 PI RPC stdout 失败", error));
                    process.destroy();
                }
            }
        });

        //3. stderr 读取线程：必须持续排空，否则 PI 写满管道会阻塞；同时保留诊断快照。
        //   按原始字节读取再做容错解码，避免子进程输出非 UTF-8（中文 Windows 上的 GBK）时静默变成乱码。
        Thread.ofVirtual().name("pi-rpc-stderr").start(() -> {
            StderrDecoder decoder = new StderrDecoder();
            byte[] buffer = new byte[2048];
            try (InputStream stream = process.getErrorStream()) {
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    publishStderr(decoder.decode(buffer, read));
                }
                //流结束后冲刷暂存字节，避免丢失末尾一个未结束的多字节序列。
                publishStderr(decoder.flush());
            } catch (IOException error) {
                if (!closed.get()) {
                    reportListenerError(error);
                }
            }
        });

        //4. 子进程退出回调：完成 onExit()；非主动关闭时让所有未来请求失败。
        process.onExit().thenAccept(exited -> {
            exitFuture.complete(new PiProcessExit(exited.exitValue(), closed.get(), stderr()));
            if (!closed.get()) {
                fail(new PiProcessException("PI 子进程意外退出", exited.exitValue(), stderr()));
            }
        });
    }

    private void dispatchEvents() {
        //1. 即使已关闭也排空缓冲区，保证已入队事件不丢。
        while (!closed.get() || !eventBuffer.isEmpty()) {
            final PiEvent event;
            try {
                event = eventBuffer.take();
            } catch (InterruptedException error) {
                if (closed.get()) {
                    return;
                }
                Thread.currentThread().interrupt();
                return;
            }
            //2. 顺序交给所有事件监听器；单个监听器异常被隔离，不影响分发。
            for (Consumer<PiEvent> listener : eventListeners) {
                try {
                    listener.accept(event);
                } catch (Throwable error) {
                    reportListenerError(error);
                }
            }
            //3. Extension UI 请求额外分发给专用监听器，便于宿主用同步方式回复。
            if (event.is("extension_ui_request")) {
                PiExtensionUiRequest request = PiExtensionUiRequest.from(event.raw());
                for (Consumer<PiExtensionUiRequest> listener : extensionUiListeners) {
                    try {
                        listener.accept(request);
                    } catch (Throwable error) {
                        reportListenerError(error);
                    }
                }
            }
        }
    }

    private void handleLine(String line) {
        //1. 解析为 JSON 对象；非法 JSON 或非对象均视为协议错误。
        final JsonNode message;
        try {
            message = mapper.readTree(line);
        } catch (JsonProcessingException error) {
            throw new PiProtocolException(
                    "PI RPC 输出了非法 JSON: " + (line.length() <= 256 ? line : line.substring(0, 256) + "..."),
                    error
            );
        }
        if (message == null || !message.isObject()) {
            throw new PiProtocolException("PI RPC 消息必须是 JSON 对象");
        }

        String type = message.path("type").asText();
        //2. response 走请求-响应通道，其余均作为事件处理。
        if ("response".equals(type)) {
            handleResponse(message);
            return;
        }

        PiEvent event = new PiEvent(type.isBlank() ? "unknown" : type, message);

        //3. 先完成当前运行的两阶段句柄（agent_settled 才算真正稳定），再广播事件。
        if (event.is("agent_settled")) {
            CompletableFuture<PiEvent> settled = activeRun.getAndSet(null);
            if (settled != null) {
                settled.complete(event);
            }
        }
        enqueueEvent(event);
    }

    private void enqueueEvent(PiEvent event) {
        //1. 按配置的溢出策略入队；除 BLOCK 外都不会阻塞协议读取线程。
        try {
            switch (config.eventOverflowStrategy()) {
                case BLOCK -> eventBuffer.put(event);
                case DROP_LATEST -> eventBuffer.offer(event);
                case DROP_OLDEST -> {
                    if (!eventBuffer.offer(event)) {
                        eventBuffer.poll();
                        eventBuffer.offer(event);
                    }
                }
                case FAIL -> {
                    //2. FAIL 策略把「缓冲区满」视为致命错误，失败客户端并终止子进程。
                    if (!eventBuffer.offer(event)) {
                        PiProtocolException error = new PiProtocolException("PI 事件缓冲区已满");
                        fail(error);
                        process.destroy();
                        throw error;
                    }
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new PiProtocolException("等待 PI 事件缓冲区时被中断", error);
        }
    }

    private void handleResponse(JsonNode message) {
        //1. 按 id 找回待完成请求；找不到时区分「已超时被忽略」与真正的协议错误。
        String id = message.path("id").asText();
        PendingRequest pending = pendingRequests.remove(id);
        if (pending == null) {
            if (ignoredResponseIds.remove(id)) {
                return;
            }
            reportListenerError(new PiProtocolException("收到未知请求 ID 的 PI RPC 响应: " + id));
            return;
        }
        String responseCommand = message.path("command").asText();
        //2. 校验响应 command 与请求登记的一致，避免响应错配污染结果。
        if (!pending.command().equals(responseCommand)) {
            pending.future().completeExceptionally(new PiProtocolException(
                    "PI RPC 响应命令不匹配，期望 " + pending.command() + "，实际 " + responseCommand));
            return;
        }
        //3. success=false 映射为 PiRpcException，携带服务端错误文案。
        if (!message.path("success").asBoolean(false)) {
            pending.future().completeExceptionally(new PiRpcException(
                    responseCommand,
                    message.path("error").asText("未知错误")
            ));
            return;
        }
        //4. 正常完成：暴露解析后的 data 与完整原始 JSON。
        pending.future().complete(new PiResponse(id, responseCommand, message.get("data"), message));
    }

    private void writeJson(JsonNode value) {
        try {
            String json = mapper.writeValueAsString(value);
            synchronized (writeLock) {
                ensureOpen();
                stdin.write(json);
                stdin.write('\n');
                stdin.flush();
            }
        } catch (IOException error) {
            PiProcessException processError = new PiProcessException("写入 PI RPC stdin 失败", null, stderr());
            fail(processError);
            throw processError;
        }
    }

    private void publishStderr(String chunk) {
        if (chunk.isEmpty()) {
            return;
        }
        captureStderr(chunk);
        try {
            config.stderrConsumer().accept(chunk);
        } catch (Throwable error) {
            reportListenerError(error);
        }
    }

    private void captureStderr(String chunk) {
        synchronized (stderr) {
            stderr.append(chunk);
            int excess = stderr.length() - config.maxCapturedStderrChars();
            if (excess > 0) {
                stderr.delete(0, excess);
            }
        }
    }

    private void fail(PiClientException error) {
        pendingRequests.forEach((id, pending) -> pending.future().completeExceptionally(error));
        pendingRequests.clear();
        CompletableFuture<PiEvent> settled = activeRun.getAndSet(null);
        if (settled != null) {
            settled.completeExceptionally(error);
        }
    }

    private void reportListenerError(Throwable error) {
        try {
            config.listenerErrorHandler().accept(error);
        } catch (Throwable ignored) {
            // 监听器错误处理器也必须与协议读取循环隔离。
        }
    }

    private void ensureOpen() {
        if (closed.get() || !process.isAlive()) {
            throw new PiProcessException("PI 客户端已关闭或子进程已退出", process.isAlive() ? null : process.exitValue(), stderr());
        }
    }

    /**
     * 关闭客户端和 PI 子进程，并使未完成请求异常完成。
     *
     * <p>先请求正常终止；超过关闭超时后强制终止。重复调用安全。</p>
     */
    @Override
    public void close() {
        //1. CAS 保证幂等：重复关闭直接返回。
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        //2. 让所有未完成请求以「已关闭」异常完成，避免调用方永久挂起。
        PiProcessException closedError = new PiProcessException("PI 客户端已关闭", null, stderr());
        fail(closedError);

        //3. 关闭 stdin 促使 PI 正常退出，再请求终止子进程。
        try {
            stdin.close();
        } catch (IOException ignored) {
            // 进程可能已经关闭了管道。
        }
        process.destroy();

        //4. 等待优雅退出；超过关闭超时则强制终止。
        Duration timeout = config.shutdownTimeout();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }

        //5. 清理监听器与缓冲区，并中断事件分发线程。
        eventListeners.clear();
        extensionUiListeners.clear();
        eventBuffer.clear();
        if (eventDispatcher != null) {
            eventDispatcher.interrupt();
        }
    }
}
