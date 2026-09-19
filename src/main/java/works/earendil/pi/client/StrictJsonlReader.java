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
        //1. 逐块读取原始字节，自行按 0x0A 切分，以便同时实施大小限制与编码校验。
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = input.read(chunk)) != -1) {
            for (int i = 0; i < read; i++) {
                int value = chunk[i] & 0xff;
                if (value == '\n') {
                    //2. 遇到换行即产出一条完整记录。
                    emit(line, consumer);
                } else {
                    //3. 超限立即报错，避免畸形输入耗尽内存。
                    if (line.size() >= maxLineBytes) {
                        throw new PiProtocolException("PI JSONL 单条记录超过限制: " + maxLineBytes + " bytes");
                    }
                    line.write(value);
                }
            }
        }
        //4. 流以无换行结尾时，仍要产出最后一条记录。
        if (line.size() > 0) {
            emit(line, consumer);
        }
    }

    private static void emit(ByteArrayOutputStream line, Consumer<String> consumer) {
        //1. 取出并重置缓冲，顺带去掉可选的 \r。
        byte[] bytes = line.toByteArray();
        line.reset();
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') {
            length--;
        }
        //2. 严格 UTF-8 解码；非法字节直接报错，而不是静默替换为 U+FFFD。
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
