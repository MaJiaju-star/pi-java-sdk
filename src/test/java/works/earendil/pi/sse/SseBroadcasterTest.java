package works.earendil.pi.sse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import works.earendil.pi.event.PiEvent;

class SseBroadcasterTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private PiEvent event(String type) {
        try {
            return new PiEvent(type, mapper.readTree("{\"type\":\"" + type + "\"}"));
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private static SseBroadcasterConfig noHeartbeat() {
        return SseBroadcasterConfig.builder().heartbeatInterval(Duration.ZERO).build();
    }

    @Test
    void fansOutToAllConnections() throws Exception {
        try (SseBroadcaster broadcaster = new SseBroadcaster(mapper, noHeartbeat())) {
            FakeConnection a = new FakeConnection();
            FakeConnection b = new FakeConnection();
            broadcaster.add(a);
            broadcaster.add(b);

            broadcaster.publish(event("agent_start"));
            broadcaster.publish(event("agent_settled"));

            await(() -> a.frames.size() == 2 && b.frames.size() == 2);
            assertTrue(a.frames.get(0).contains("event: agent_start"));
            assertTrue(a.frames.get(1).contains("id: 2"));
            assertEquals(2, broadcaster.connectionCount());
        }
    }

    @Test
    void replaysBufferedEventsAfterLastEventId() {
        try (SseBroadcaster broadcaster = new SseBroadcaster(mapper, noHeartbeat())) {
            broadcaster.publish(event("agent_start"));
            broadcaster.publish(event("agent_settled"));

            FakeConnection replay = new FakeConnection();
            broadcaster.add(replay, 1);

            await(() -> replay.frames.size() == 1);
            assertTrue(replay.frames.get(0).contains("id: 2"), replay.frames.get(0));
        }
    }

    @Test
    void removesConnectionOnSendFailure() {
        try (SseBroadcaster broadcaster = new SseBroadcaster(mapper, noHeartbeat())) {
            FakeConnection broken = new FakeConnection();
            broken.failOnSend = true;
            broadcaster.add(broken);

            broadcaster.publish(event("agent_start"));
            await(() -> broadcaster.connectionCount() == 0);
        }
    }

    @Test
    void sendsHeartbeatWhenIdle() {
        SseBroadcasterConfig config = SseBroadcasterConfig.builder()
                .heartbeatInterval(Duration.ofMillis(30))
                .build();
        try (SseBroadcaster broadcaster = new SseBroadcaster(mapper, config)) {
            FakeConnection connection = new FakeConnection();
            broadcaster.add(connection);
            await(() -> connection.frames.stream().anyMatch(frame -> frame.equals(": ping\n\n")));
        }
    }

    @Test
    void closeClosesConnections() {
        SseBroadcaster broadcaster = new SseBroadcaster(mapper, noHeartbeat());
        FakeConnection connection = new FakeConnection();
        broadcaster.add(connection);

        broadcaster.close();
        await(() -> !connection.open);
        assertEquals(0, broadcaster.connectionCount());
    }

    @Test
    void removeClosesConnection() {
        try (SseBroadcaster broadcaster = new SseBroadcaster(mapper, noHeartbeat())) {
            FakeConnection connection = new FakeConnection();
            String id = broadcaster.add(connection);

            assertTrue(broadcaster.remove(id));
            await(() -> !connection.open);
            assertEquals(0, broadcaster.connectionCount());
        }
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                fail("等待被中断");
            }
        }
        fail("条件未在 3 秒内满足");
    }

    private static final class FakeConnection implements SseConnection {
        private final List<String> frames = Collections.synchronizedList(new ArrayList<>());
        private volatile boolean open = true;
        private volatile boolean failOnSend;

        @Override
        public void send(String frame) throws IOException {
            if (failOnSend) {
                throw new IOException("模拟写入失败");
            }
            frames.add(frame);
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }
}
