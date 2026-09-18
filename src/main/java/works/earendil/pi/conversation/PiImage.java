package works.earendil.pi.conversation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;

/**
 * 发送给 PI 的 Base64 图片。
 *
 * @param data 不带 Data URL 前缀的 Base64 数据
 * @param mimeType 图片的 MIME 类型，例如 {@code image/png}
 */
public record PiImage(String data, String mimeType) {
    /**
     * 创建图片参数并校验 MIME 类型。
     *
     * @throws NullPointerException 当 {@code data} 或 {@code mimeType} 为 {@code null} 时
     * @throws IllegalArgumentException 当 {@code mimeType} 为空白字符串时
     */
    public PiImage {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(mimeType, "mimeType");
        if (mimeType.isBlank()) {
            throw new IllegalArgumentException("mimeType 不能为空");
        }
    }

    /**
     * 将二进制图片编码为 Base64 参数。
     *
     * @param bytes 图片二进制数据
     * @param mimeType 图片的 MIME 类型
     * @return 可用于 prompt 的图片参数
     * @throws NullPointerException 当任一参数为 {@code null} 时
     */
    public static PiImage fromBytes(byte[] bytes, String mimeType) {
        return new PiImage(Base64.getEncoder().encodeToString(bytes), mimeType);
    }

    /**
     * 读取图片文件并编码为 Base64 参数。
     *
     * @param path 图片文件路径
     * @param mimeType 图片的 MIME 类型
     * @return 可用于 prompt 的图片参数
     * @throws IOException 当文件无法读取时
     * @throws NullPointerException 当任一参数为 {@code null} 时
     */
    public static PiImage fromPath(Path path, String mimeType) throws IOException {
        return fromBytes(Files.readAllBytes(path), mimeType);
    }
}
