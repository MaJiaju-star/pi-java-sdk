/**
 * 把 PI 流式事件桥接到 Server-Sent Events。
 *
 * <p>PI 的 JSONL RPC 协议本身没有 SSE；本包在 SDK 事件订阅之上提供：规范化的事件实体
 * （{@link works.earendil.pi.sse.SseEvent}）、事件映射器、SSE 帧编码器、多连接广播器，
 * 以及一个零依赖的 JDK HttpServer 适配器。</p>
 *
 * <p>本包不依赖任何 Web 框架：接入 Solon、Spring 等框架时只需实现
 * {@link works.earendil.pi.sse.SseConnection} 并复用 {@link works.earendil.pi.sse.SseBroadcaster}。</p>
 */
package works.earendil.pi.sse;
