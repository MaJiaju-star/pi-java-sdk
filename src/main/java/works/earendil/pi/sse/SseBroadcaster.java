package works.earendil.pi.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import works.earendil.pi.client.PiClient;
import works.earendil.pi.event.PiEvent;
import works.earendil.pi.event.PiSubscription;

/**
 * 把 PI 流式事件扇出到多个 SSE 连接。
 *
 * <p>典型用法：用 {@link #attach(PiClient, SseBroadcasterConfig)} 订阅一个 {@link PiClient}，
 * 每个 Web 会话调用 {@link #add(SseConnection, long)} 注册一个连接。每个连接拥有独立的虚拟线程和
 * 有界队列，慢消费者不会阻塞 SDK 的事件流。</p>
 *
 * <p>特性：</p>
 * <ul>
 *   <li>每条事件分配单调递增序号，作为 SSE {@code id}，支持 {@code Last-Event-ID} 重放；</li>
 *   <li>连接空闲时由连接线程自动发送心跳，无需额外调度线程；</li>
 *   <li>连接写失败或下游队列溢出时自动移除并关闭；</li>
 *   <li>{@link #publish(PiEvent)} 永不阻塞 SDK 分发线程（队列操作均为非阻塞）。</li>
 * </ul>
 */
public final class SseBroadcaster implements AutoCloseable {
    private final SseEventMapper eventMapper;
    private final SseFrameEncoder encoder;
    private final int queueCapacity;
    private final long heartbeatMillis;
    private final int replayBufferSize;
    private final SseConnectionOverflowStrategy overflowStrategy;

    private final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();
    private final ArrayDeque<Frame> replay = new ArrayDeque<>();
    private final Object lock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();
    private PiSubscription subscription;

    private record Frame(long seq, String text) {
    }

    /**
     * 创建不自动订阅的广播器，适合手动调用 {@link #publish(PiEvent)} 或测试。
     *
     * @param mapper Jackson 映射器
     * @param config 广播配置
     * @throws NullPointerException 当任一参数为 {@code null} 时
     */
    public SseBroadcaster(ObjectMapper mapper, SseBroadcasterConfig config) {
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(config, "config");
        this.eventMapper = new SseEventMapper(mapper);
        this.encoder = new SseFrameEncoder(mapper);
        this.queueCapacity = config.queueCapacity();
        this.heartbeatMillis = config.heartbeatInterval().toMillis();
        this.replayBufferSize = config.replayBufferSize();
        this.overflowStrategy = config.overflowStrategy();
    }

    /**
     * 创建并自动订阅指定客户端的广播器。
     *
     * @param client 已启动的 PI 客户端
     * @param config 广播配置
     * @return 已订阅的广播器
     * @throws NullPointerException 当任一参数为 {@code null} 时
     */
    public static SseBroadcaster attach(PiClient client, SseBroadcasterConfig config) {
        Objects.requireNonNull(client, "client");
        SseBroadcaster broadcaster = new SseBroadcaster(client.objectMapper(), config);
        broadcaster.subscription = client.subscribe(broadcaster::publish);
        return broadcaster;
    }

    /**
     * 发布一个原始事件，规范化后扇出到所有连接。
     *
     * <p>本方法不阻塞：下游队列操作均为非阻塞，溢出按配置策略处理。</p>
     *
     * @param event 原始事件
     * @throws NullPointerException 当事件为 {@code null} 时
     */
    public void publish(PiEvent event) {
        Objects.requireNonNull(event, "event");
        if (closed.get()) {
            return;
        }

        //1. 规范化并编码为帧；事件序号在映射时分配。
        SseEvent mapped = eventMapper.map(event);
        Frame frame = new Frame(mapped.seq(), encoder.encode(mapped));

        //2. 持锁更新重放缓冲，并快照连接列表，避免遍历时被并发修改。
        List<Connection> targets;
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            if (replayBufferSize > 0) {
                replay.addLast(frame);
                while (replay.size() > replayBufferSize) {
                    replay.removeFirst();
                }
            }
            targets = new ArrayList<>(connections.values());
        }

        //3. 锁外扇出：入队均为非阻塞，慢消费者只影响自己的连接。
        for (Connection connection : targets) {
            connection.offer(frame);
        }
    }

    /**
     * 注册一个连接，从最新事件开始接收。
     *
     * @param connection 连接
     * @return 连接 ID，可用于 {@link #remove(String)}
     * @throws NullPointerException 当连接为 {@code null} 时
     * @throws IllegalStateException 当广播器已关闭时
     */
    public String add(SseConnection connection) {
        return add(connection, 0L);
    }

    /**
     * 注册一个连接，并回放序号大于 {@code lastEventId} 的缓冲事件。
     *
     * @param connection 连接
     * @param lastEventId 客户端最后收到的事件序号；0 表示不重放
     * @return 连接 ID
     * @throws NullPointerException 当连接为 {@code null} 时
     * @throws IllegalStateException 当广播器已关闭时
     */
    public String add(SseConnection connection, long lastEventId) {
        Objects.requireNonNull(connection, "connection");
        //1. 快速失败检查；锁内会再查一次，避开关闭竞争窗口。
        if (closed.get()) {
            throw new IllegalStateException("SSE 广播器已关闭");
        }
        Connection created = new Connection(UUID.randomUUID().toString(), connection);

        //2. 持锁先回放缓冲帧，再登记连接，保证回放帧排在后续新帧之前。
        synchronized (lock) {
            if (closed.get()) {
                throw new IllegalStateException("SSE 广播器已关闭");
            }
            if (lastEventId > 0 && replayBufferSize > 0) {
                for (Frame frame : replay) {
                    if (frame.seq() > lastEventId && !created.queue.offer(frame.text())) {
                        break;
                    }
                }
            }
            connections.put(created.id, created);
        }

        //3. 启动连接线程开始投递。
        created.start();
        return created.id;
    }

    /**
     * 移除并关闭指定连接。
     *
     * @param connectionId 连接 ID
     * @return 找到并移除时返回 {@code true}
     */
    public boolean remove(String connectionId) {
        Connection connection = connections.remove(connectionId);
        if (connection == null) {
            return false;
        }
        connection.close();
        return true;
    }

    /**
     * 返回当前连接数。
     *
     * @return 连接数
     */
    public int connectionCount() {
        return connections.size();
    }

    /** 取消订阅并关闭全部连接；重复调用安全。 */
    @Override
    public void close() {
        //1. CAS 保证幂等。
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        //2. 先退订，停止产生新帧。
        if (subscription != null) {
            subscription.close();
        }

        //3. 持锁取出全部连接并清空重放缓冲。
        List<Connection> all;
        synchronized (lock) {
            all = new ArrayList<>(connections.values());
            connections.clear();
            replay.clear();
        }

        //4. 锁外逐个关闭，避免持锁期间阻塞在连接关闭上。
        for (Connection connection : all) {
            connection.close();
        }
    }

    private final class Connection {
        private final String id;
        private final SseConnection target;
        private final ArrayBlockingQueue<String> queue;
        private final AtomicBoolean connectionClosed = new AtomicBoolean();
        private Thread worker;

        private Connection(String id, SseConnection target) {
            this.id = id;
            this.target = target;
            this.queue = new ArrayBlockingQueue<>(queueCapacity);
        }

        private void start() {
            worker = Thread.ofVirtual().name("pi-sse-" + id).start(this::run);
        }

        private void offer(Frame frame) {
            //1. 已关闭的连接直接丢弃，避免向已释放的队列写入。
            if (connectionClosed.get()) {
                return;
            }
            String text = frame.text();
            //2. 按溢出策略入队；除 CLOSE 策略外都不会阻塞调用方（即 SDK 事件分发线程）。
            switch (overflowStrategy) {
                case DROP_OLDEST -> {
                    if (!queue.offer(text)) {
                        queue.poll();
                        queue.offer(text);
                    }
                }
                case DROP_LATEST -> queue.offer(text);
                case CLOSE -> {
                    if (!queue.offer(text)) {
                        close();
                    }
                }
            }
        }

        private void run() {
            try {
                while (!connectionClosed.get()) {
                    //1. 有事件就取事件；超过心跳间隔仍空闲则改发心跳。
                    String frame;
                    if (heartbeatMillis == 0) {
                        frame = queue.take();
                    } else {
                        frame = queue.poll(heartbeatMillis, TimeUnit.MILLISECONDS);
                        if (frame == null) {
                            frame = encoder.heartbeat();
                        }
                    }
                    //2. 真正写失败才能被察觉，因此心跳同时兼任断连探测。
                    target.send(frame);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException failure) {
                // 连接断开或写入失败，由 finally 清理
            } finally {
                //3. 无论正常还是异常退出，都摘除注册并释放连接。
                connections.remove(id, this);
                close();
            }
        }

        private void close() {
            if (!connectionClosed.compareAndSet(false, true)) {
                return;
            }
            // 中断连接线程；自身调用时跳过，否则会打断 finally 清理。
            Thread current = worker;
            if (current != null && current != Thread.currentThread()) {
                current.interrupt();
            }
            try {
                target.close();
            } catch (RuntimeException ignored) {
                // 关闭失败无补救手段
            }
        }
    }
}
