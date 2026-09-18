package works.earendil.pi.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PiEventDecoderTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void decodesMessageAndToolEvents() throws Exception {
        PiEvent event = new PiEvent("message_update", mapper.readTree("""
                {"type":"message_update","usage":{"input":1,"output":2,"cacheRead":0,"cacheWrite":0,
                "totalTokens":3,"cost":{"input":0,"output":0,"cacheRead":0,"cacheWrite":0,"total":0}},
                "assistantMessageEvent":{"type":"text_delta","contentIndex":0,"delta":"你好"}}
                """));

        PiTypedEvent.MessageUpdate update = assertInstanceOf(PiTypedEvent.MessageUpdate.class, event.typed(mapper));
        assertEquals("你好", update.assistantMessageEvent().delta());
        assertEquals(3, update.usage().totalTokens());
    }
}
