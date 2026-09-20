package works.earendil.pi.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StderrDecoderTest {
    private static final String TEXT = "警告：检测到 localhost 代理配置";

    @BeforeAll
    static void requireGbk() {
        assumeTrue(Charset.isSupported("GBK"), "本测试需要 GBK 字符集");
    }

    @Test
    void decodesUtf8WithoutSwitchingToFallback() {
        StderrDecoder decoder = new StderrDecoder(Charset.forName("GBK"));

        assertEquals(TEXT, feed(decoder, TEXT.getBytes(StandardCharsets.UTF_8), 4096));
    }

    @Test
    void fallsBackToNativeCharsetForNonUtf8Bytes() {
        StderrDecoder decoder = new StderrDecoder(Charset.forName("GBK"));

        assertEquals(TEXT, feed(decoder, TEXT.getBytes(Charset.forName("GBK")), 4096));
    }

    @Test
    void keepsUtf8CharacterSplitAcrossReads() {
        // 逐字节喂入：任一实现只要在块尾漏掉未结束的序列，这里就会退化成乱码或丢字。
        StderrDecoder decoder = new StderrDecoder(Charset.forName("GBK"));

        assertEquals(TEXT, feed(decoder, TEXT.getBytes(StandardCharsets.UTF_8), 1));
    }

    @Test
    void keepsNativeCharacterSplitAcrossReads() {
        StderrDecoder decoder = new StderrDecoder(Charset.forName("GBK"));

        assertEquals(TEXT, feed(decoder, TEXT.getBytes(Charset.forName("GBK")), 1));
    }

    @Test
    void staysOnFallbackAfterSwitch() {
        StderrDecoder decoder = new StderrDecoder(Charset.forName("GBK"));
        Charset gbk = Charset.forName("GBK");

        // ASCII 前缀不会被判为非法，切换必须发生在 GBK 块上，并对其后的块持续生效。
        String decoded = feed(decoder, "pi: ".getBytes(StandardCharsets.US_ASCII), 4096)
                + feed(decoder, TEXT.getBytes(gbk), 4096)
                + feed(decoder, TEXT.getBytes(gbk), 4096);

        assertEquals("pi: " + TEXT + TEXT, decoded);
    }

    @Test
    void replacesIllegalBytesWhenNoFallbackIsAvailable() {
        StderrDecoder decoder = new StderrDecoder((Charset) null);

        String decoded = feed(decoder, TEXT.getBytes(Charset.forName("GBK")), 4096);

        assertTrue(decoded.contains("\uFFFD"), () -> "应退化为替换字符而不是抛异常: " + decoded);
        assertTrue(decoded.contains("localhost"), () -> "ASCII 部分应保持可读: " + decoded);
    }

    @Test
    void flushReportsTruncatedTrailingSequence() {
        StderrDecoder decoder = new StderrDecoder((Charset) null);
        byte[] utf8 = "中".getBytes(StandardCharsets.UTF_8);

        assertEquals("", decoder.decode(utf8, utf8.length - 1));
        assertTrue(decoder.flush().contains("\uFFFD"));
    }

    @Test
    void flushIsEmptyWhenNothingIsPending() {
        StderrDecoder decoder = new StderrDecoder(Charset.forName("GBK"));

        assertEquals(TEXT, feed(decoder, TEXT.getBytes(StandardCharsets.UTF_8), 4096));
        assertEquals("", decoder.flush());
    }

    /** 按固定分块喂入字节，模拟 InputStream 的读取边界。 */
    private static String feed(StderrDecoder decoder, byte[] bytes, int chunkSize) {
        StringBuilder text = new StringBuilder();
        for (int offset = 0; offset < bytes.length; offset += chunkSize) {
            int length = Math.min(chunkSize, bytes.length - offset);
            text.append(decoder.decode(Arrays.copyOfRange(bytes, offset, offset + length), length));
        }
        return text.toString();
    }
}
