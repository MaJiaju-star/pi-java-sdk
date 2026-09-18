package works.earendil.pi.event;

/** 事件订阅句柄；关闭后不再接收事件。 */
@FunctionalInterface
public interface PiSubscription extends AutoCloseable {
    /** 取消订阅；重复调用不会产生额外效果。 */
    @Override
    void close();
}
