package works.earendil.pi.pool;

import java.io.IOException;
import works.earendil.pi.client.PiClient;

/** 为会话池创建一个全新 PI 客户端。 */
@FunctionalInterface
public interface PiClientFactory {
    /**
     * 启动一个全新客户端。
     *
     * @return 已完成 RPC 就绪探测的客户端
     * @throws IOException 当 PI 进程无法启动时
     */
    PiClient start() throws IOException;
}
