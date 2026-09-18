package works.earendil.pi.pool;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import works.earendil.pi.client.PiClient;
import works.earendil.pi.config.PiClientConfig;

/**
 * 多会话 PI 进程池。每个池 ID 对应一个独立客户端和会话，不会把并发 prompt 混入同一事件流。
 */
public final class PiClientPool implements AutoCloseable {
    /**
     * 池条目。
     *
     * @param id 池内稳定标识
     * @param client 当前关联的客户端
     */
    public record Entry(String id, PiClient client) {
    }

    private final PiClientFactory factory;
    private final int maxClients;
    private final ConcurrentHashMap<String, PiClient> clients = new ConcurrentHashMap<>();
    private final Object lifecycleLock = new Object();

    /**
     * 创建客户端池。
     *
     * @param maxClients 最大客户端数量
     * @param factory 客户端工厂
     * @throws IllegalArgumentException 当最大数量小于 1 时
     * @throws NullPointerException 当工厂为 {@code null} 时
     */
    public PiClientPool(int maxClients, PiClientFactory factory) {
        if (maxClients < 1) {
            throw new IllegalArgumentException("maxClients 必须大于 0");
        }
        this.maxClients = maxClients;
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    /**
     * 创建所有客户端共享同一启动配置的池。
     *
     * @param maxClients 最大客户端数量
     * @param config 客户端启动配置
     * @return 新客户端池
     * @throws IllegalArgumentException 当最大数量小于 1 时
     * @throws NullPointerException 当配置为 {@code null} 时
     */
    public static PiClientPool fromConfig(int maxClients, PiClientConfig config) {
        Objects.requireNonNull(config, "config");
        return new PiClientPool(maxClients, () -> PiClient.start(config));
    }

    /**
     * 启动客户端并加入池。
     *
     * @return 新池条目
     * @throws IOException 当客户端无法启动时
     * @throws IllegalStateException 当池已达到容量限制时
     */
    public Entry create() throws IOException {
        synchronized (lifecycleLock) {
            if (clients.size() >= maxClients) {
                throw new IllegalStateException("PI 客户端数量已达到限制: " + maxClients);
            }
            PiClient client = factory.start();
            String id = UUID.randomUUID().toString();
            clients.put(id, client);
            return new Entry(id, client);
        }
    }

    /**
     * 获取活动客户端；进程异常退出时使用同一池 ID 自动创建替代实例。
     *
     * @param id 池条目 ID
     * @return 活动客户端
     * @throws IOException 当替代客户端无法启动时
     * @throws IllegalArgumentException 当 ID 不存在时
     */
    public PiClient getOrRecover(String id) throws IOException {
        PiClient current = require(id);
        if (current.isAlive()) {
            return current;
        }
        return recover(id);
    }

    /**
     * 替换已经退出的客户端。调用方需要重新注册原客户端上的事件监听器。
     *
     * @param id 池条目 ID
     * @return 替代客户端；原客户端仍活动时直接返回原实例
     * @throws IOException 当替代客户端无法启动时
     * @throws IllegalArgumentException 当 ID 不存在时
     */
    public PiClient recover(String id) throws IOException {
        synchronized (lifecycleLock) {
            PiClient previous = require(id);
            if (previous.isAlive()) {
                return previous;
            }
            PiClient replacement = factory.start();
            clients.put(id, replacement);
            previous.close();
            return replacement;
        }
    }

    /**
     * 获取指定池条目。
     *
     * @param id 池条目 ID
     * @return 对应客户端
     * @throws IllegalArgumentException 当 ID 不存在时
     * @throws NullPointerException 当 ID 为 {@code null} 时
     */
    public PiClient require(String id) {
        PiClient client = clients.get(Objects.requireNonNull(id, "id"));
        if (client == null) {
            throw new IllegalArgumentException("PI 客户端不存在: " + id);
        }
        return client;
    }

    /**
     * 返回当前池条目快照。
     *
     * @return 条目列表；后续池变更不会修改该列表
     */
    public List<Entry> entries() {
        return clients.entrySet().stream()
                .map(entry -> new Entry(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * 从池中移除并关闭客户端。
     *
     * @param id 池条目 ID
     * @return 找到并移除时返回 {@code true}
     */
    public boolean remove(String id) {
        PiClient client = clients.remove(id);
        if (client == null) {
            return false;
        }
        client.close();
        return true;
    }

    /** 关闭池中的所有客户端并清空池；重复调用安全。 */
    @Override
    public void close() {
        clients.values().forEach(PiClient::close);
        clients.clear();
    }
}
