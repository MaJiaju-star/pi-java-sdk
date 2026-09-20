package works.earendil.pi.client;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;
import java.util.List;

/**
 * PI 子进程 stderr 的容错增量解码器。
 *
 * <p>stdout 上的 JSONL 协议必须严格校验 UTF-8，非法字节直接报错（见 {@link StrictJsonlReader}）。
 * stderr 则相反：它是诊断通道，既不能因为编码不合法就中断客户端，也不应该静默产出乱码。
 * 所以这里先按 UTF-8 严格解码，一旦遇到 UTF-8 无法表示的字节，就切换到操作系统原生字符集
 * （中文 Windows 上为 GBK）并保持该选择，使 {@link works.earendil.pi.exception.PiProcessException}
 * 附带的标准错误仍然可读。</p>
 *
 * <p>解码是增量的：块尾未结束的多字节序列会暂存到下一次调用，因此字符被读取边界切断
 * 不会被误判成编码错误。</p>
 */
final class StderrDecoder {
    /** UTF-8 单个字符最长 4 字节；滞留更多说明数据本身异常，直接丢弃以免无界缓存。 */
    private static final int MAX_PENDING_BYTES = 4;
    private static final byte[] NONE = new byte[0];

    private final CharsetDecoder utf8;
    /** 回退字符集解码器；为 {@code null} 表示与 UTF-8 相同或系统探测失败，不做回退。 */
    private final CharsetDecoder fallback;

    /** 当前生效的解码器；确认流不是 UTF-8 后固定为 {@link #fallback}。 */
    private CharsetDecoder active;
    /** 上一块残留的未结束多字节序列。 */
    private byte[] pending = NONE;

    /** 创建使用系统原生字符集作为回退的解码器。 */
    StderrDecoder() {
        this(detectNativeCharset());
    }

    /**
     * 创建指定回退字符集的解码器。
     *
     * @param fallbackCharset 回退字符集；为 {@code null} 或 UTF-8 时不回退
     */
    StderrDecoder(Charset fallbackCharset) {
        this.utf8 = strict(StandardCharsets.UTF_8);
        this.fallback = fallbackCharset == null || StandardCharsets.UTF_8.equals(fallbackCharset)
                ? null
                : strict(fallbackCharset);
        this.active = this.utf8;
    }

    /**
     * 增量解码一块 stderr 字节。
     *
     * @param bytes 原始字节
     * @param length 有效长度
     * @return 本次可确定的文本；块尾未结束的序列留到下次
     */
    String decode(byte[] bytes, int length) {
        byte[] data = join(pending, bytes, length);
        pending = NONE;
        return convert(data, false);
    }

    /**
     * 刷新暂存字节；流结束时调用，避免丢失末尾未结束的序列。
     *
     * @return 剩余文本；没有暂存字节时为空字符串
     */
    String flush() {
        if (pending.length == 0) {
            return "";
        }
        byte[] data = pending;
        pending = NONE;
        return convert(data, true);
    }

    /**
     * 探测操作系统原生字符集。
     *
     * <p>不能用 {@link Charset#defaultCharset()}：JDK 18 起（JEP 400）它固定返回 UTF-8，
     * 在中文 Windows 上拿不到 GBK。{@code native.encoding} 是反映系统编码的属性，
     * 更早的 JDK 回退到 {@code sun.jnu.encoding}。</p>
     *
     * @return 原生字符集；与 UTF-8 相同或无法识别时为 {@code null}
     */
    private static Charset detectNativeCharset() {
        for (String key : List.of("native.encoding", "sun.jnu.encoding")) {
            String name = System.getProperty(key);
            if (name == null || name.isBlank()) {
                continue;
            }
            try {
                Charset charset = Charset.forName(name);
                if (!StandardCharsets.UTF_8.equals(charset)) {
                    return charset;
                }
            } catch (IllegalCharsetNameException | UnsupportedCharsetException ignored) {
                // 属性不是合法字符集名时继续尝试下一个来源。
            }
        }
        return null;
    }

    private static CharsetDecoder strict(Charset charset) {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
    }

    private static byte[] join(byte[] head, byte[] tail, int length) {
        if (head.length == 0) {
            return Arrays.copyOf(tail, length);
        }
        byte[] joined = new byte[head.length + length];
        System.arraycopy(head, 0, joined, 0, head.length);
        System.arraycopy(tail, 0, joined, head.length, length);
        return joined;
    }

    private String convert(byte[] data, boolean endOfInput) {
        ByteBuffer input = ByteBuffer.wrap(data);
        int limit = input.limit();
        CharBuffer output = CharBuffer.allocate(limit + 1);
        while (true) {
            CoderResult result = active.decode(input, output, endOfInput);
            if (result.isOverflow()) {
                output = grow(output);
                continue;
            }
            if (result.isUnderflow()) {
                break;
            }
            //1. UTF-8 解不开说明 stderr 不是 UTF-8：切到原生字符集，从头重解整块。
            if (active == utf8 && fallback != null) {
                active = fallback;
            } else {
                //2. 没有回退字符集或回退后仍失败：宁可出现替换字符，也不能让诊断流中断客户端。
                active.onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE);
            }
            active.reset();
            input.position(0).limit(limit);
            output.clear();
        }
        //3. 块尾未结束的多字节序列留到下一次，避免字符被读取边界切断时误判编码。
        if (input.hasRemaining()) {
            byte[] rest = new byte[input.remaining()];
            input.get(rest);
            pending = rest.length > MAX_PENDING_BYTES ? NONE : rest;
        }
        output.flip();
        return output.toString();
    }

    private static CharBuffer grow(CharBuffer buffer) {
        CharBuffer grown = CharBuffer.allocate(Math.max(buffer.capacity() * 2, 16));
        buffer.flip();
        grown.put(buffer);
        return grown;
    }
}
