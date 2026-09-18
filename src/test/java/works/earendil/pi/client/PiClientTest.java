package works.earendil.pi.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import works.earendil.pi.config.PiClientConfig;
import works.earendil.pi.conversation.ThinkingLevel;
import works.earendil.pi.event.PiEvent;
import works.earendil.pi.exception.PiRequestTimeoutException;
import works.earendil.pi.exception.PiRpcException;
import works.earendil.pi.extension.PiExtensionUiRequest;
import works.earendil.pi.extension.PiExtensionUiResponse;
import works.earendil.pi.pool.PiClientPool;
import works.earendil.pi.rpc.PiRun;

class PiClientTest {
    @Test
    void correlatesResponsesAndCompletesRunOnAgentSettled() throws Exception {
        List<PiEvent> events = new CopyOnWriteArrayList<>();
        CountDownLatch uiReceived = new CountDownLatch(1);
        try (PiClient client = startFakeClient()) {
            client.subscribe(events::add);
            client.subscribeExtensionUi(request -> {
                assertEquals(PiExtensionUiRequest.Method.CONFIRM, request.method());
                client.respond(PiExtensionUiResponse.confirmed(request.id(), true));
                uiReceived.countDown();
            });

            PiRun run = client.prompt("测试");
            assertEquals("prompt", run.accepted().get(5, TimeUnit.SECONDS).command());
            assertEquals("agent_settled", run.settled().get(5, TimeUnit.SECONDS).type());

            assertTrue(events.stream()
                    .flatMap(event -> event.textDelta().stream())
                    .anyMatch("你好\u2028PI"::equals));
            assertEquals("fake-model", client.getAvailableModels().get(5, TimeUnit.SECONDS).getFirst().id());
            assertEquals(List.of(ThinkingLevel.OFF, ThinkingLevel.HIGH),
                    client.getAvailableThinkingLevels().get(5, TimeUnit.SECONDS));
            assertTrue(uiReceived.await(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void mapsRpcFailuresToTypedException() throws Exception {
        try (PiClient client = startFakeClient()) {
            ExecutionException error = assertThrows(ExecutionException.class,
                    () -> client.request("unknown").get(5, TimeUnit.SECONDS));

            assertTrue(error.getCause() instanceof PiRpcException);
            assertEquals("unknown", ((PiRpcException) error.getCause()).command());
        }
    }

    @Test
    void timesOutRequestsWithoutStoppingClient() throws Exception {
        try (PiClient client = startFakeClient()) {
            ExecutionException error = assertThrows(ExecutionException.class,
                    () -> client.request("hang", java.util.Map.of(), Duration.ofMillis(50))
                            .get(5, TimeUnit.SECONDS));

            assertTrue(error.getCause() instanceof PiRequestTimeoutException);
            assertEquals("fake-session", client.getState().get(5, TimeUnit.SECONDS).sessionId());
        }
    }

    @Test
    void poolLimitsAndRecoversClosedClients() throws Exception {
        try (PiClientPool pool = PiClientPool.fromConfig(1, fakeConfig())) {
            PiClientPool.Entry entry = pool.create();
            assertThrows(IllegalStateException.class, pool::create);

            entry.client().close();
            PiClient recovered = pool.getOrRecover(entry.id());
            assertNotSame(entry.client(), recovered);
            assertEquals("fake-session", recovered.getState().get(5, TimeUnit.SECONDS).sessionId());
        }
    }

    private static PiClient startFakeClient() throws Exception {
        return PiClient.start(fakeConfig());
    }

    private static PiClientConfig fakeConfig() {
        String javaExecutable = Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java"
        ).toString();
        return PiClientConfig.builder()
                .command(List.of(
                        javaExecutable,
                        "-Xms16m",
                        "-Xmx64m",
                        "-XX:+UseSerialGC",
                        "-cp",
                        System.getProperty("java.class.path"),
                        FakePiProcess.class.getName()
                ))
                .startupTimeout(Duration.ofSeconds(5))
                .build();
    }
}
