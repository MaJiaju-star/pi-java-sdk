package works.earendil.pi.sse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import works.earendil.pi.event.PiEvent;
import works.earendil.pi.extension.PiExtensionUiRequest;

class SseEventMapperTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SseEventMapper sseMapper = new SseEventMapper(mapper);

    @Test
    void mapsTextDeltaAndAssignsSeq() throws Exception {
        JsonNode raw = json("""
                {"type":"message_update",
                 "usage":{"input":1,"output":2,"cacheRead":0,"cacheWrite":0,"totalTokens":3},
                 "assistantMessageEvent":{"type":"text_delta","contentIndex":0,"delta":"你好"}}
                """);
        SseEvent first = sseMapper.map(new PiEvent("message_update", raw));
        SseEvent.TextDelta delta = assertInstanceOf(SseEvent.TextDelta.class, first);
        assertEquals("text_delta", delta.type());
        assertEquals(1, delta.seq());
        assertEquals(0, delta.contentIndex());
        assertEquals("你好", delta.text());
        assertSame(raw, delta.raw());

        SseEvent second = sseMapper.map(new PiEvent("agent_start", json("{\"type\":\"agent_start\"}")));
        assertEquals(2, second.seq());
    }

    @Test
    void mapsThinkingDelta() throws Exception {
        JsonNode raw = json("""
                {"type":"message_update",
                 "assistantMessageEvent":{"type":"thinking_delta","contentIndex":1,"delta":"思考中"}}
                """);
        SseEvent.ThinkingDelta delta =
                assertInstanceOf(SseEvent.ThinkingDelta.class, sseMapper.map(new PiEvent("message_update", raw)));
        assertEquals("thinking_delta", delta.type());
        assertEquals(1, delta.contentIndex());
        assertEquals("思考中", delta.text());
    }

    @Test
    void mapsToolCallStartAndEnd() throws Exception {
        JsonNode start = json("""
                {"type":"message_update",
                 "assistantMessageEvent":{"type":"toolcall_start","contentIndex":0,"id":"call-1","toolName":"read"}}
                """);
        SseEvent.ToolCallStart callStart =
                assertInstanceOf(SseEvent.ToolCallStart.class, sseMapper.map(new PiEvent("message_update", start)));
        assertEquals("toolcall_start", callStart.type());
        assertEquals("call-1", callStart.toolCallId());
        assertEquals("read", callStart.toolName());

        JsonNode end = json("""
                {"type":"message_update",
                 "assistantMessageEvent":{"type":"toolcall_end","contentIndex":0,
                   "toolCall":{"type":"toolCall","id":"call-1","name":"read","arguments":{"path":"a.txt"}}}}
                """);
        SseEvent.ToolCallEnd callEnd =
                assertInstanceOf(SseEvent.ToolCallEnd.class, sseMapper.map(new PiEvent("message_update", end)));
        assertEquals("call-1", callEnd.toolCallId());
        assertEquals("read", callEnd.toolName());
        assertEquals("a.txt", callEnd.arguments().path("path").asText());
    }

    @Test
    void mapsMessageDoneAndError() throws Exception {
        JsonNode done = json("""
                {"type":"message_update",
                 "assistantMessageEvent":{"type":"done","reason":"stop"}}
                """);
        SseEvent.MessageDone messageDone =
                assertInstanceOf(SseEvent.MessageDone.class, sseMapper.map(new PiEvent("message_update", done)));
        assertEquals("message_done", messageDone.type());
        assertEquals("stop", messageDone.reason());

        JsonNode error = json("""
                {"type":"message_update",
                 "assistantMessageEvent":{"type":"error","reason":"aborted","error":{"message":"boom"}}}
                """);
        SseEvent.MessageFailed failed =
                assertInstanceOf(SseEvent.MessageFailed.class, sseMapper.map(new PiEvent("message_update", error)));
        assertEquals("message_error", failed.type());
        assertEquals("aborted", failed.reason());
        assertEquals("boom", failed.error().path("message").asText());
    }

    @Test
    void mapsToolExecutionLifecycle() throws Exception {
        SseEvent.ToolStart start = assertInstanceOf(SseEvent.ToolStart.class, sseMapper.map(new PiEvent(
                "tool_execution_start",
                json("""
                        {"type":"tool_execution_start","toolCallId":"c1","toolName":"bash","args":{"command":"ls"}}
                        """))));
        assertEquals("tool_start", start.type());
        assertEquals("c1", start.toolCallId());
        assertEquals("bash", start.toolName());

        SseEvent.ToolUpdate update = assertInstanceOf(SseEvent.ToolUpdate.class, sseMapper.map(new PiEvent(
                "tool_execution_update",
                json("""
                        {"type":"tool_execution_update","toolCallId":"c1","toolName":"bash","partialResult":{"x":1}}
                        """))));
        assertEquals(1, update.partialResult().path("x").asInt());

        SseEvent.ToolEnd end = assertInstanceOf(SseEvent.ToolEnd.class, sseMapper.map(new PiEvent(
                "tool_execution_end",
                json("""
                        {"type":"tool_execution_end","toolCallId":"c1","toolName":"bash","result":{"ok":true},"isError":false}
                        """))));
        assertEquals("tool_end", end.type());
        assertTrue(end.result().path("ok").asBoolean());
        assertFalse(end.error());
    }

    @Test
    void mapsLifecycleMarkersAndAgentEnd() throws Exception {
        assertEquals("agent_start", sseMapper
                .map(new PiEvent("agent_start", json("{\"type\":\"agent_start\"}"))).type());
        assertEquals("agent_settled", sseMapper
                .map(new PiEvent("agent_settled", json("{\"type\":\"agent_settled\"}"))).type());
        assertEquals("turn_start", sseMapper
                .map(new PiEvent("turn_start", json("{\"type\":\"turn_start\"}"))).type());
        assertEquals("summarization_retry_finished", sseMapper
                .map(new PiEvent("summarization_retry_finished",
                        json("{\"type\":\"summarization_retry_finished\"}"))).type());

        SseEvent.AgentEnd agentEnd = assertInstanceOf(SseEvent.AgentEnd.class, sseMapper.map(new PiEvent(
                "agent_end", json("{\"type\":\"agent_end\",\"messages\":[{},{}],\"willRetry\":true}"))));
        assertEquals(2, agentEnd.messageCount());
        assertTrue(agentEnd.willRetry());

        SseEvent.TurnEnd turnEnd = assertInstanceOf(SseEvent.TurnEnd.class, sseMapper.map(new PiEvent(
                "turn_end", json("{\"type\":\"turn_end\",\"message\":{\"role\":\"assistant\"},\"toolResults\":[{},{}]}"))));
        assertEquals(2, turnEnd.toolResultCount());
    }

    @Test
    void mapsMessageEndWithUsage() throws Exception {
        SseEvent.MessageEnd end = assertInstanceOf(SseEvent.MessageEnd.class, sseMapper.map(new PiEvent(
                "message_end",
                json("""
                        {"type":"message_end","message":{"role":"assistant",
                         "usage":{"input":10,"output":20,"cacheRead":0,"cacheWrite":0,"totalTokens":30}}}
                        """))));
        assertEquals("message_end", end.type());
        assertEquals(10, end.usage().input());
        assertEquals(30, end.usage().totalTokens());
    }

    @Test
    void mapsQueueCompactionAndRetry() throws Exception {
        SseEvent.QueueUpdate queue = assertInstanceOf(SseEvent.QueueUpdate.class, sseMapper.map(new PiEvent(
                "queue_update", json("{\"type\":\"queue_update\",\"steering\":[\"a\"],\"followUp\":[]}"))));
        assertEquals(1, queue.steering().size());

        SseEvent.CompactionStart start = assertInstanceOf(SseEvent.CompactionStart.class, sseMapper.map(new PiEvent(
                "compaction_start", json("{\"type\":\"compaction_start\",\"reason\":\"manual\"}"))));
        assertEquals("manual", start.reason());

        SseEvent.CompactionEnd end = assertInstanceOf(SseEvent.CompactionEnd.class, sseMapper.map(new PiEvent(
                "compaction_end",
                json("""
                        {"type":"compaction_end","reason":"threshold","aborted":false,"willRetry":false,
                         "result":{"summary":"s","firstKeptEntryId":"e1","tokensBefore":100,"estimatedTokensAfter":20}}
                        """))));
        assertEquals(100, end.tokensBefore());
        assertEquals(20, end.estimatedTokensAfter());

        SseEvent.RetryStart retryStart = assertInstanceOf(SseEvent.RetryStart.class, sseMapper.map(new PiEvent(
                "auto_retry_start",
                json("{\"type\":\"auto_retry_start\",\"attempt\":1,\"maxAttempts\":3,\"delayMs\":500,\"errorMessage\":\"x\"}"))));
        assertEquals(500, retryStart.delayMillis());

        SseEvent.RetryEnd retryEnd = assertInstanceOf(SseEvent.RetryEnd.class, sseMapper.map(new PiEvent(
                "auto_retry_end", json("{\"type\":\"auto_retry_end\",\"success\":true,\"attempt\":2}"))));
        assertTrue(retryEnd.success());

        SseEvent.RetryScheduled scheduled = assertInstanceOf(SseEvent.RetryScheduled.class, sseMapper.map(new PiEvent(
                "summarization_retry_scheduled",
                json("{\"type\":\"summarization_retry_scheduled\",\"attempt\":1,\"maxAttempts\":2,\"delayMs\":100,\"errorMessage\":\"y\"}"))));
        assertEquals(100, scheduled.delayMillis());

        SseEvent.SummarizationRetry summarization =
                assertInstanceOf(SseEvent.SummarizationRetry.class, sseMapper.map(new PiEvent(
                        "summarization_retry_attempt_start",
                        json("{\"type\":\"summarization_retry_attempt_start\",\"source\":\"compaction\",\"reason\":\"manual\"}"))));
        assertEquals("compaction", summarization.source());
    }

    @Test
    void mapsSessionBashAndExtensionEvents() throws Exception {
        SseEvent.SessionInfo info = assertInstanceOf(SseEvent.SessionInfo.class, sseMapper.map(new PiEvent(
                "session_info_changed", json("{\"type\":\"session_info_changed\",\"name\":\"我的会话\"}"))));
        assertEquals("我的会话", info.name());

        SseEvent.ThinkingLevelChanged level = assertInstanceOf(SseEvent.ThinkingLevelChanged.class, sseMapper.map(
                new PiEvent("thinking_level_changed", json("{\"type\":\"thinking_level_changed\",\"level\":\"high\"}"))));
        assertEquals("high", level.level());

        SseEvent.BashOutput bash = assertInstanceOf(SseEvent.BashOutput.class, sseMapper.map(new PiEvent(
                "bash_execution_update", json("{\"type\":\"bash_execution_update\",\"id\":\"b1\",\"delta\":\"out\"}"))));
        assertEquals("b1", bash.requestId());
        assertEquals("out", bash.delta());

        SseEvent.Error error = assertInstanceOf(SseEvent.Error.class, sseMapper.map(new PiEvent(
                "extension_error",
                json("{\"type\":\"extension_error\",\"extensionPath\":\"/e.ts\",\"event\":\"agent_start\",\"error\":\"bad\"}"))));
        assertEquals("/e.ts", error.extensionPath());

        SseEvent.ExtensionUi ui = assertInstanceOf(SseEvent.ExtensionUi.class, sseMapper.map(new PiEvent(
                "extension_ui_request",
                json("{\"type\":\"extension_ui_request\",\"id\":\"r1\",\"method\":\"confirm\",\"title\":\"t\",\"message\":\"m\"}"))));
        assertEquals("r1", ui.request().id());
        assertEquals(PiExtensionUiRequest.Method.CONFIRM, ui.request().method());
    }

    @Test
    void mapsUnknownEvent() throws Exception {
        SseEvent.Unknown unknown = assertInstanceOf(SseEvent.Unknown.class, sseMapper.map(new PiEvent(
                "brand_new_event", json("{\"type\":\"brand_new_event\",\"x\":1}"))));
        assertEquals("unknown", unknown.type());
        assertEquals("brand_new_event", unknown.sourceType());
    }

    private JsonNode json(String value) throws Exception {
        return mapper.readTree(value);
    }
}
