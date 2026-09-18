package works.earendil.pi.config;

/** 业务事件缓冲区满时的处理策略。 */
public enum PiEventOverflowStrategy {
    /** 阻塞协议读取线程，通过操作系统管道向 PI 施加反压，不丢事件。 */
    BLOCK,
    /** 丢弃缓冲区中最旧的业务事件。 */
    DROP_OLDEST,
    /** 丢弃新到达的业务事件。 */
    DROP_LATEST,
    /** 使客户端失败并终止 PI 子进程。 */
    FAIL
}
