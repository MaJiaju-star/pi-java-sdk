package works.earendil.pi.rpc;

import java.util.concurrent.CompletableFuture;
import works.earendil.pi.event.PiEvent;

/**
 * 一次 PI 运行。accepted 只表示 prompt 已接收，settled 才表示重试、压缩和后续队列均已结束。
 *
 * @param accepted prompt 被 PI 接收时完成的 future
 * @param settled 收到 {@code agent_settled} 事件时完成的 future
 */
public record PiRun(
        CompletableFuture<PiResponse> accepted,
        CompletableFuture<PiEvent> settled
) {
}
