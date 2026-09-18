package works.earendil.pi.sse;

/** 单个 SSE 连接的下游队列已满时的处理策略。 */
public enum SseConnectionOverflowStrategy {
    /** 丢弃该连接队列中最旧的帧，保留最新进度。 */
    DROP_OLDEST,
    /** 丢弃新到达的帧，保留先到事件。 */
    DROP_LATEST,
    /** 关闭该连接，避免慢消费者占用内存。 */
    CLOSE
}
