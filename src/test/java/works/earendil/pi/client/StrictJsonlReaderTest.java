package works.earendil.pi.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import works.earendil.pi.exception.PiProtocolException;

class StrictJsonlReaderTest {
    @Test
    void splitsOnlyOnLfAndPreservesUnicodeSeparators() throws IOException {
        String input = "{\"text\":\"甲\u2028乙\u2029丙\"}\r\n{\"text\":\"你好\"}";
        List<String> lines = new ArrayList<>();

        StrictJsonlReader.read(oneByteAtATime(input.getBytes(StandardCharsets.UTF_8)), 4096, lines::add);

        assertEquals(List.of(
                "{\"text\":\"甲\u2028乙\u2029丙\"}",
                "{\"text\":\"你好\"}"
        ), lines);
    }

    @Test
    void rejectsOversizedRecordBeforeNewline() {
        byte[] input = ("x".repeat(1025) + "\n").getBytes(StandardCharsets.UTF_8);

        assertThrows(PiProtocolException.class,
                () -> StrictJsonlReader.read(new ByteArrayInputStream(input), 1024, ignored -> { }));
    }

    private static InputStream oneByteAtATime(byte[] bytes) {
        return new ByteArrayInputStream(bytes) {
            @Override
            public synchronized int read(byte[] target, int offset, int length) {
                return super.read(target, offset, Math.min(length, 1));
            }
        };
    }
}
