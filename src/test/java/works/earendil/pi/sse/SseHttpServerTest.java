package works.earendil.pi.sse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import works.earendil.pi.event.PiEvent;

class SseHttpServerTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void streamsEventsOverHttp() throws Exception {
        SseBroadcasterConfig config = SseBroadcasterConfig.builder()
                .heartbeatInterval(Duration.ofMillis(100))
                .build();
        try (SseBroadcaster broadcaster = new SseBroadcaster(mapper, config);
             SseHttpServer server = SseHttpServer.builder(broadcaster).port(0).start();
             Socket socket = new Socket("127.0.0.1", server.port())) {

            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + server.eventsPath()
                    + " HTTP/1.1\r\nHost: localhost\r\nAccept: text/event-stream\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();

            List<String> lines = new CopyOnWriteArrayList<>();
            CountDownLatch sawEvent = new CountDownLatch(1);
            Thread reader = Thread.ofVirtual().start(() -> {
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        lines.add(line);
                        if (line.startsWith("data: ") && line.contains("\"agent_start\"")) {
                            sawEvent.countDown();
                            return;
                        }
                    }
                } catch (Exception ignored) {
                    // 读取结束或连接被关闭
                }
            });

            await(() -> broadcaster.connectionCount() == 1);
            broadcaster.publish(new PiEvent("agent_start", mapper.readTree("{\"type\":\"agent_start\"}")));

            assertTrue(sawEvent.await(5, TimeUnit.SECONDS), "未收到 SSE 事件: " + lines);
            assertTrue(lines.get(0).startsWith("HTTP/1.1 200"), lines.toString());
            assertTrue(lines.stream().anyMatch(
                            line -> line.toLowerCase(Locale.ROOT).startsWith("content-type: text/event-stream")),
                    lines.toString());
            assertTrue(lines.contains("id: 1"), lines.toString());
            assertTrue(lines.contains("event: agent_start"), lines.toString());
            assertTrue(lines.stream().anyMatch(line -> line.startsWith("data: ") && line.contains("\"agent_start\"")),
                    lines.toString());
            reader.join(1000);
        }
    }

    @Test
    void forwardsExtensionUiResponses() throws Exception {
        List<JsonNode> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        try (SseBroadcaster broadcaster = new SseBroadcaster(mapper, SseBroadcasterConfig.builder().build());
             SseHttpServer server = SseHttpServer.builder(broadcaster)
                     .port(0)
                     .extensionUiResponder(body -> {
                         received.add(body);
                         latch.countDown();
                     })
                     .start();
             Socket socket = new Socket("127.0.0.1", server.port())) {

            socket.setSoTimeout(5000);
            byte[] body = "{\"id\":\"r1\",\"value\":\"x\"}".getBytes(StandardCharsets.UTF_8);
            OutputStream out = socket.getOutputStream();
            out.write(("POST " + server.extensionUiPath() + " HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + body.length + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.write(body);
            out.flush();

            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                assertTrue(in.readLine().startsWith("HTTP/1.1 204"));
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals("r1", received.get(0).path("id").asText());
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
}
