package works.earendil.pi.sse;

import java.io.IOException;

/**
 * SSE 连接传输抽象。
 *
 * <p>由 Web 层实现：{@link #send(String)} 把一帧文本写入 HTTP 响应体并刷新，
 * {@link #close()} 关闭连接，{@link #isOpen()} 反映连接是否仍可写。</p>
 *
 * <p>{@link SseBroadcaster} 会在每个连接自己的虚拟线程上串行调用 {@link #send(String)}，
 * 因此实现无需自行处理并发写。</p>
 */
public interface SseConnection {

    /**
     * 写入一帧 SSE 文本并刷新。实现应保证帧不丢写、不乱序。
     *
     * @param frame 完整 SSE 帧文本
     * @throws IOException 当连接已断开或写入失败时
     */
    void send(String frame) throws IOException;

    /**
     * 关闭连接。重复调用应安全。
     */
    void close();

    /**
     * 判断连接是否仍可写。
     *
     * @return 连接打开时返回 {@code true}
     */
    boolean isOpen();
}
