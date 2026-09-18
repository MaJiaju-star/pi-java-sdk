package works.earendil.pi.sse;

import java.time.Duration;
import java.util.Objects;

/**
 * {@link SseBroadcaster} 的不可变配置。
 *
 * <p>通过 {@link #builder()} 创建，构建后可安全共享。</p>
 */
public final class SseBroadcasterConfig {
    private final int queueCapacity;
    private final Duration heartbeatInterval;
    private final int replayBufferSize;
    private final SseConnectionOverflowStrategy overflowStrategy;

    private SseBroadcasterConfig(Builder builder) {
        this.queueCapacity = builder.queueCapacity;
        this.heartbeatInterval = builder.heartbeatInterval;
        this.replayBufferSize = builder.replayBufferSize;
        this.overflowStrategy = builder.overflowStrategy;
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
     * 返回每个连接的下游队列容量。
     *
     * @return 队列容量
     */
    public int queueCapacity() {
        return queueCapacity;
    }

    /**
     * 返回空闲心跳间隔；{@link Duration#ZERO} 表示关闭心跳。
     *
     * @return 心跳间隔
     */
    public Duration heartbeatInterval() {
        return heartbeatInterval;
    }

    /**
     * 返回断线重放缓冲的帧数上限；0 表示不保留重放。
     *
     * @return 重放缓冲大小
     */
    public int replayBufferSize() {
        return replayBufferSize;
    }

    /**
     * 返回连接下游队列已满时的处理策略。
     *
     * @return 溢出策略
     */
    public SseConnectionOverflowStrategy overflowStrategy() {
        return overflowStrategy;
    }

    /** SSE 广播配置构建器。 */
    public static final class Builder {
        private int queueCapacity = 256;
        private Duration heartbeatInterval = Duration.ofSeconds(15);
        private int replayBufferSize = 256;
        private SseConnectionOverflowStrategy overflowStrategy = SseConnectionOverflowStrategy.DROP_OLDEST;

        /** 创建使用默认值的构建器。 */
        public Builder() {
        }

        /**
         * 设置每个连接的下游队列容量。
         *
         * @param queueCapacity 队列容量，必须大于 0
         * @return 当前构建器
         * @throws IllegalArgumentException 当容量小于 1 时
         */
        public Builder queueCapacity(int queueCapacity) {
            if (queueCapacity < 1) {
                throw new IllegalArgumentException("queueCapacity 必须大于 0");
            }
            this.queueCapacity = queueCapacity;
            return this;
        }

        /**
         * 设置空闲心跳间隔；{@link Duration#ZERO} 表示关闭心跳。
         *
         * @param heartbeatInterval 心跳间隔，不能为负
         * @return 当前构建器
         * @throws NullPointerException 当参数为 {@code null} 时
         * @throws IllegalArgumentException 当间隔为负时
         */
        public Builder heartbeatInterval(Duration heartbeatInterval) {
            Objects.requireNonNull(heartbeatInterval, "heartbeatInterval");
            if (heartbeatInterval.isNegative()) {
                throw new IllegalArgumentException("heartbeatInterval 不能为负");
            }
            this.heartbeatInterval = heartbeatInterval;
            return this;
        }

        /**
         * 设置断线重放缓冲的帧数上限；0 表示不保留重放。
         *
         * @param replayBufferSize 缓冲大小，不能为负
         * @return 当前构建器
         * @throws IllegalArgumentException 当大小为负时
         */
        public Builder replayBufferSize(int replayBufferSize) {
            if (replayBufferSize < 0) {
                throw new IllegalArgumentException("replayBufferSize 不能为负");
            }
            this.replayBufferSize = replayBufferSize;
            return this;
        }

        /**
         * 设置连接下游队列已满时的处理策略。
         *
         * @param overflowStrategy 溢出策略
         * @return 当前构建器
         * @throws NullPointerException 当参数为 {@code null} 时
         */
        public Builder overflowStrategy(SseConnectionOverflowStrategy overflowStrategy) {
            this.overflowStrategy = Objects.requireNonNull(overflowStrategy, "overflowStrategy");
            return this;
        }

        /**
         * 构建不可变配置。
         *
         * @return 新配置
         */
        public SseBroadcasterConfig build() {
            return new SseBroadcasterConfig(this);
        }
    }
}
