package works.earendil.pi.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import works.earendil.pi.exception.PiProtocolException;

final class StrictJsonlReader {
    private StrictJsonlReader() {
    }

    static void read(InputStream input, int maxLineBytes, Consumer<String> consumer) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = input.read(chunk)) != -1) {
            for (int i = 0; i < read; i++) {
                int value = chunk[i] & 0xff;
                if (value == '\n') {
                    emit(line, consumer);
                } else {
                    if (line.size() >= maxLineBytes) {
                        throw new PiProtocolException("PI JSONL 单条记录超过限制: " + maxLineBytes + " bytes");
                    }
                    line.write(value);
                }
            }
        }
        if (line.size() > 0) {
            emit(line, consumer);
        }
    }

    private static void emit(ByteArrayOutputStream line, Consumer<String> consumer) {
        byte[] bytes = line.toByteArray();
        line.reset();
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') {
            length--;
        }
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, 0, length))
                    .toString();
            consumer.accept(value);
        } catch (CharacterCodingException error) {
            throw new PiProtocolException("PI JSONL 包含非法 UTF-8", error);
        }
    }
}
