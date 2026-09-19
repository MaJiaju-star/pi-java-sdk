package works.earendil.pi.sse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 基于 JDK 内置 {@link HttpServer} 的零依赖 SSE 适配器。
 *
 * <p>提供两个端点：</p>
 * <ul>
 *   <li>{@code GET <eventsPath>}：SSE 事件流，支持 {@code Last-Event-ID} 请求头或
 *       {@code ?lastEventId=} 查询参数重放；</li>
 *   <li>{@code POST <extensionUiPath>}：可选的 Extension UI 回复入口，JSON 请求体交给
 *       {@code extensionUiResponder} 处理。</li>
 * </ul>
 *
 * <p>本类仅用于本地开发或简单内嵌场景。生产部署建议用 Solon、Spring 等框架实现
 * {@link SseConnection} 并接入现有认证与路由。</p>
 */
public final class SseHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final String eventsPath;
    private final String extensionUiPath;

    private SseHttpServer(HttpServer server, String eventsPath, String extensionUiPath) {
        this.server = server;
        this.eventsPath = eventsPath;
        this.extensionUiPath = extensionUiPath;
    }

    /**
     * 创建适配器构建器。
     *
     * @param broadcaster 事件广播器
     * @return 新构建器
     * @throws NullPointerException 当广播器为 {@code null} 时
     */
    public static Builder builder(SseBroadcaster broadcaster) {
        return new Builder(broadcaster);
    }

    /**
     * 返回实际绑定的端口。
     *
     * @return 监听端口
     */
    public int port() {
        return server.getAddress().getPort();
    }

    /**
     * 返回 SSE 事件流路径。
     *
     * @return 事件流路径
     */
    public String eventsPath() {
        return eventsPath;
    }

    /**
     * 返回 Extension UI 回复路径；未配置回复处理器时为 {@code null}。
     *
     * @return Extension UI 回复路径或 {@code null}
     */
    public String extensionUiPath() {
        return extensionUiPath;
    }

    /** 关闭 HTTP 服务；重复调用安全。 */
    @Override
    public void close() {
        server.stop(0);
    }

    /** SSE HTTP 适配器构建器。 */
    public static final class Builder {
        private final SseBroadcaster broadcaster;
        private int port = 8080;
        private String eventsPath = "/events";
        private String extensionUiPath = "/ui-responses";
        private Consumer<JsonNode> extensionUiResponder;
        private ObjectMapper mapper = new ObjectMapper();

        private Builder(SseBroadcaster broadcaster) {
            this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster");
        }

        /**
         * 设置监听端口，{@code 0} 表示随机空闲端口。
         *
         * @param port 端口
         * @return 当前构建器
         * @throws IllegalArgumentException 当端口不在 0-65535 时
         */
        public Builder port(int port) {
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("port 必须在 0-65535 之间");
            }
            this.port = port;
            return this;
        }

        /**
         * 设置 SSE 事件流路径。
         *
         * @param eventsPath 以 {@code /} 开头的路径
         * @return 当前构建器
         * @throws NullPointerException 当路径为 {@code null} 时
         */
        public Builder eventsPath(String eventsPath) {
            this.eventsPath = Objects.requireNonNull(eventsPath, "eventsPath");
            return this;
        }

        /**
         * 设置 Extension UI 回复路径。
         *
         * @param extensionUiPath 以 {@code /} 开头的路径
         * @return 当前构建器
         * @throws NullPointerException 当路径为 {@code null} 时
         */
        public Builder extensionUiPath(String extensionUiPath) {
            this.extensionUiPath = Objects.requireNonNull(extensionUiPath, "extensionUiPath");
            return this;
        }

        /**
         * 设置 Extension UI 回复处理器；设置后才会注册对应端点。
         *
         * @param extensionUiResponder 接收请求体 JSON 的处理器
         * @return 当前构建器
         */
        public Builder extensionUiResponder(Consumer<JsonNode> extensionUiResponder) {
            this.extensionUiResponder = extensionUiResponder;
            return this;
        }

        /**
         * 设置请求体解析用的 Jackson 映射器。
         *
         * @param mapper 映射器
         * @return 当前构建器
         * @throws NullPointerException 当映射器为 {@code null} 时
         */
        public Builder mapper(ObjectMapper mapper) {
            this.mapper = Objects.requireNonNull(mapper, "mapper");
            return this;
        }

        /**
         * 启动 HTTP 服务。
         *
         * @return 已启动的适配器
         * @throws IOException 当端口绑定失败时
         */
        public SseHttpServer start() throws IOException {
            //1. 创建服务并绑定端口；每个请求用一条虚拟线程处理。
            HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
            http.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

            //2. 事件流端点始终注册。
            http.createContext(eventsPath, this::handleEvents);

            //3. Extension UI 端点仅在有回复处理器时注册。
            String effectiveExtensionUiPath = null;
            if (extensionUiResponder != null) {
                http.createContext(extensionUiPath, this::handleExtensionUi);
                effectiveExtensionUiPath = extensionUiPath;
            }

            //4. 启动并返回适配器。
            http.start();
            return new SseHttpServer(http, eventsPath, effectiveExtensionUiPath);
        }

        private void handleEvents(HttpExchange exchange) throws IOException {
            //1. 事件流只接受 GET。
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            //2. 写 SSE 必需响应头；禁用缓存与反向代理缓冲，否则事件会被攒住不发。
            var headers = exchange.getResponseHeaders();
            headers.add("Content-Type", "text/event-stream; charset=utf-8");
            headers.add("Cache-Control", "no-cache");
            headers.add("Connection", "keep-alive");
            headers.add("X-Accel-Buffering", "no");
            // 长度 0 表示分块传输，连接会一直保持到客户端断开。
            exchange.sendResponseHeaders(200, 0);

            //3. 注册连接（可按 Last-Event-ID 重放），然后阻塞等待连接结束。
            JdkConnection connection = new JdkConnection(exchange.getResponseBody());
            broadcaster.add(connection, parseLastEventId(exchange));
            try {
                connection.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                //4. 请求线程退出前释放连接，让广播器摘除注册。
                connection.close();
            }
        }

        private void handleExtensionUi(HttpExchange exchange) throws IOException {
            //1. 回复入口只接受 POST。
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            //2. 解析 JSON 请求体；非法 JSON 直接返回 400。
            JsonNode body;
            try (var input = exchange.getRequestBody()) {
                body = mapper.readTree(input);
            } catch (IOException error) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                return;
            }

            //3. 交给处理器，成功无响应体。
            extensionUiResponder.accept(body);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        }

        private static long parseLastEventId(HttpExchange exchange) {
            //1. 优先读 Last-Event-ID 请求头。
            String header = exchange.getRequestHeaders().getFirst("Last-Event-ID");
            Long value = parseLong(header);
            if (value != null) {
                return value;
            }
            //2. 回退到 ?lastEventId= 查询参数（浏览器 EventSource 无法自定义请求头）。
            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                for (String part : query.split("&")) {
                    if (part.startsWith("lastEventId=")) {
                        Long parsed = parseLong(part.substring("lastEventId=".length()));
                        if (parsed != null) {
                            return parsed;
                        }
                    }
                }
            }
            //3. 都取不到时从 0 开始，即不回放。
            return 0L;
        }

        private static Long parseLong(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException error) {
                return null;
            }
        }
    }

    private static final class JdkConnection implements SseConnection {
        private final OutputStream output;
        private final CountDownLatch closed = new CountDownLatch(1);
        private final AtomicBoolean open = new AtomicBoolean(true);

        private JdkConnection(OutputStream output) {
            this.output = output;
        }

        @Override
        public void send(String frame) throws IOException {
            // 关闭后写入视为失败，广播器据此摘除连接。
            if (!open.get()) {
                throw new IOException("SSE 连接已关闭");
            }
            output.write(frame.getBytes(StandardCharsets.UTF_8));
            // 立即 flush，否则事件会留在缓冲区里不能让客户端及时收到。
            output.flush();
        }

        @Override
        public void close() {
            if (open.compareAndSet(true, false)) {
                closed.countDown();
            }
        }

        @Override
        public boolean isOpen() {
            return open.get();
        }

        private void await() throws InterruptedException {
            closed.await();
        }
    }
}
