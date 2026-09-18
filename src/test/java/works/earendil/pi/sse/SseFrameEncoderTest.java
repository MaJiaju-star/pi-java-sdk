package works.earendil.pi.sse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SseFrameEncoderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SseFrameEncoder encoder = new SseFrameEncoder(mapper);

    @Test
    void encodesIdEventAndData() throws Exception {
        JsonNode raw = mapper.readTree("{\"type\":\"message_update\"}");
        SseEvent.TextDelta event = new SseEvent.TextDelta(7, 0, "hi", raw);

        String frame = encoder.encode(event);
        String[] lines = frame.split("\n", -1);

        assertEquals("id: 7", lines[0]);
        assertEquals("event: text_delta", lines[1]);
        assertTrue(lines[2].startsWith("data: "));
        assertEquals("", lines[3]);

        JsonNode data = mapper.readTree(lines[2].substring("data: ".length()));
        assertEquals(7, data.path("seq").asInt());
        assertEquals("text_delta", data.path("type").asText());
        assertEquals(0, data.path("contentIndex").asInt());
        assertEquals("hi", data.path("text").asText());
        assertEquals("message_update", data.path("raw").path("type").asText());
    }

    @Test
    void frameEndsWithBlankLine() throws Exception {
        SseEvent event = new SseEvent.AgentSettled(3, mapper.readTree("{\"type\":\"agent_settled\"}"));
        String frame = encoder.encode(event);
        assertTrue(frame.endsWith("\n\n"), frame);
        assertEquals("agent_settled", event.type());
    }

    @Test
    void heartbeatIsCommentFrame() {
        assertEquals(": ping\n\n", encoder.heartbeat());
    }
}
