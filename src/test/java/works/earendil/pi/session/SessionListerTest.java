package works.earendil.pi.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionListerTest {
    @TempDir
    Path tempDir;

    @Test
    void encodeCwdMatchesPiConvention() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            assertEquals("--D--code-PI--", SessionLister.encodeCwd(Path.of("D:/code/PI")));
        } else {
            assertEquals("--home-user--", SessionLister.encodeCwd(Path.of("/home/user")));
        }
    }

    @Test
    void listRecursivelyFindsSessionFiles() throws IOException {
        Path sessionDir = tempDir.resolve("sessions");
        Path cwdDir = sessionDir.resolve("--D--code-PI--");
        Files.createDirectories(cwdDir);
        Path file = cwdDir.resolve("2026-01-01_abc.jsonl");
        Files.writeString(file, sessionJsonl());

        List<SessionLister.SessionFile> all = SessionLister.list(sessionDir);
        assertEquals(1, all.size());
        assertEquals(file, all.getFirst().file());
        assertTrue(all.getFirst().sizeBytes() > 0);
    }

    @Test
    void listByCwdUsesEncodedDirectory() throws IOException {
        Path cwd = tempDir.resolve("my-project");
        Files.createDirectories(cwd);

        Path sessionDir = tempDir.resolve("sessions");
        Path encoded = sessionDir.resolve(SessionLister.encodeCwd(cwd));
        Files.createDirectories(encoded);
        Path file = encoded.resolve("2026-01-01_abc.jsonl");
        Files.writeString(file, sessionJsonl());

        List<SessionLister.SessionFile> byCwd = SessionLister.list(sessionDir, cwd);
        assertEquals(1, byCwd.size());
        assertEquals(file, byCwd.getFirst().file());
    }

    @Test
    void listMissingDirectoryReturnsEmpty() throws IOException {
        assertTrue(SessionLister.list(tempDir.resolve("does-not-exist")).isEmpty());
    }

    @Test
    void peekReadsHeaderAndCounts() throws IOException {
        Path file = tempDir.resolve("session.jsonl");
        Files.writeString(file, sessionJsonl());

        SessionLister.SessionSummary summary = SessionLister.peek(file);
        assertEquals("sess-1", summary.sessionId());
        assertEquals(3, summary.version());
        assertEquals("/work", summary.cwd());
        assertEquals("2026-01-01T00:00:00Z", summary.timestamp());
        assertEquals(3, summary.entryCount());
        assertEquals(2, summary.messageCount());
        assertEquals("hi there", summary.lastText());
    }

    @Test
    void peekSkipsMalformedLines() throws IOException {
        Path file = tempDir.resolve("session.jsonl");
        Files.writeString(file,
                "{\"type\":\"session\",\"version\":3,\"id\":\"sess-1\"}\n"
                        + "not-json\n"
                        + "{\"type\":\"message\",\"id\":\"e1\",\"message\":{\"role\":\"user\",\"content\":\"ok\"}}\n");

        SessionLister.SessionSummary summary = SessionLister.peek(file);
        assertEquals(1, summary.entryCount());
        assertEquals(1, summary.messageCount());
        assertEquals("ok", summary.lastText());
    }

    @Test
    void peekMissingHeaderFieldsAreNull() throws IOException {
        Path file = tempDir.resolve("session.jsonl");
        Files.writeString(file, "{\"type\":\"session\",\"version\":3}\n");

        SessionLister.SessionSummary summary = SessionLister.peek(file);
        assertNull(summary.sessionId());
        assertNull(summary.cwd());
        assertNull(summary.timestamp());
        assertEquals(0, summary.entryCount());
    }

    private static String sessionJsonl() {
        return "{\"type\":\"session\",\"version\":3,\"id\":\"sess-1\",\"timestamp\":\"2026-01-01T00:00:00Z\",\"cwd\":\"/work\"}\n"
                + "{\"type\":\"model_change\",\"id\":\"e1\",\"parentId\":null,\"timestamp\":\"2026-01-01T00:00:01Z\",\"provider\":\"x\",\"modelId\":\"y\"}\n"
                + "{\"type\":\"message\",\"id\":\"e2\",\"parentId\":\"e1\",\"timestamp\":\"2026-01-01T00:00:02Z\",\"message\":{\"role\":\"user\",\"content\":\"hello\"}}\n"
                + "{\"type\":\"message\",\"id\":\"e3\",\"parentId\":\"e2\",\"timestamp\":\"2026-01-01T00:00:03Z\",\"message\":{\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"hi there\"}]}}\n";
    }
}
