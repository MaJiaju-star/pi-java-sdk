package works.earendil.pi.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 历史会话文件的本地只读工具。
 *
 * <p>PI 的 JSONL RPC 协议没有「列出历史会话」命令，因此本类直接在文件系统层面枚举和读取会话文件，
 * 不需要启动 {@code pi --mode rpc} 子进程。会话默认存放于 {@code ~/.pi/agent/sessions/}，
 * 按工作目录编码为子目录，每个会话是一个 JSONL 文件。</p>
 *
 * <p>本类是纯本地文件操作，不依赖 PI CLI，也不会修改任何会话文件。</p>
 */
public final class SessionLister {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SessionLister() {
    }

    /**
     * 磁盘上的一个会话文件描述。
     *
     * @param file 会话 JSONL 文件路径
     * @param lastModified 文件最后修改时间
     * @param sizeBytes 文件字节数
     */
    public record SessionFile(Path file, Instant lastModified, long sizeBytes) {
    }

    /**
     * 会话文件头部信息与内容统计。
     *
     * @param sessionId 会话 ID；文件缺少头部或没有 ID 时为 {@code null}
     * @param version 会话格式版本；无法解析时为 0
     * @param cwd 会话启动时的工作目录；缺失时为 {@code null}
     * @param timestamp 会话创建时间戳字符串；缺失时为 {@code null}
     * @param entryCount 非头部条目总数（含 {@code model_change}、{@code thinking_level_change}、{@code message} 等）
     * @param messageCount 其中 {@code type == "message"} 的条目数
     * @param lastText 最后一条 user 或 assistant 消息的文本；没有时为 {@code null}
     */
    public record SessionSummary(
            String sessionId,
            int version,
            String cwd,
            String timestamp,
            int entryCount,
            int messageCount,
            String lastText
    ) {
    }

    /**
     * 返回默认会话目录。
     *
     * <p>默认是 {@code ~/.pi/agent/sessions}；设置 {@code PI_CODING_AGENT_DIR} 环境变量时，
     * 使用该目录下的 {@code sessions} 子目录。</p>
     *
     * @return 默认会话目录
     */
    public static Path defaultSessionDir() {
        //1. 优先用 PI_CODING_AGENT_DIR；未设置时回退到 ~/.pi/agent。
        String env = System.getenv("PI_CODING_AGENT_DIR");
        Path agentDir = env != null && !env.isBlank()
                ? Path.of(expandTilde(env))
                : Path.of(System.getProperty("user.home"), ".pi", "agent");
        //2. 会话固定放在该 agent 目录下的 sessions 子目录。
        return agentDir.resolve("sessions");
    }

    /**
     * 列出默认会话目录下的全部会话文件，按最后修改时间倒序。
     *
     * @return 会话文件列表
     * @throws IOException 当目录遍历或文件属性读取失败时
     */
    public static List<SessionFile> list() throws IOException {
        return list(defaultSessionDir());
    }

    /**
     * 递归列出指定会话目录下的全部 {@code .jsonl} 会话文件，按最后修改时间倒序。
     *
     * @param sessionDir 会话根目录或某个工作目录对应的子目录
     * @return 会话文件列表；目录不存在时返回空列表
     * @throws NullPointerException 当参数为 {@code null} 时
     * @throws IOException 当目录遍历或文件属性读取失败时
     */
    public static List<SessionFile> list(Path sessionDir) throws IOException {
        Objects.requireNonNull(sessionDir, "sessionDir");
        //1. 目录不存在视为「没有会话」，返回空列表而不是报错。
        if (!Files.isDirectory(sessionDir)) {
            return List.of();
        }

        //2. 递归收集目录下所有 .jsonl 文件。
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(sessionDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .forEach(files::add);
        }

        //3. 读取每个文件的最后修改时间与字节数。
        List<SessionFile> result = new ArrayList<>(files.size());
        for (Path file : files) {
            result.add(new SessionFile(
                    file,
                    Files.getLastModifiedTime(file).toInstant(),
                    Files.size(file)
            ));
        }

        //4. 按修改时间倒序，最近的会话排在最前。
        result.sort(Comparator.comparing(SessionFile::lastModified).reversed());
        return List.copyOf(result);
    }

    /**
     * 列出指定工作目录对应的会话文件。
     *
     * <p>使用与 PI 相同的目录编码规则（路径分隔符与冒号替换为 {@code -}，前后加 {@code --}）
     * 定位 {@code sessionDir} 下对应的工作目录子目录。</p>
     *
     * @param sessionDir 会话根目录
     * @param cwd 工作目录
     * @return 该工作目录的会话文件列表
     * @throws NullPointerException 当任一参数为 {@code null} 时
     * @throws IOException 当目录遍历或文件属性读取失败时
     */
    public static List<SessionFile> list(Path sessionDir, Path cwd) throws IOException {
        Objects.requireNonNull(sessionDir, "sessionDir");
        Objects.requireNonNull(cwd, "cwd");
        return list(sessionDir.resolve(encodeCwd(cwd)));
    }

    /**
     * 读取单个会话文件的头部和内容统计，不启动 PI 进程。
     *
     * <p>逐行解析 JSONL；无法解析的行会被跳过，不会抛出异常。首行若为 {@code type: "session"}
     * 则作为头部处理，否则按普通条目计入。</p>
     *
     * @param sessionFile 会话 JSONL 文件
     * @return 会话摘要
     * @throws NullPointerException 当参数为 {@code null} 时
     * @throws IOException 当文件无法读取时
     */
    public static SessionSummary peek(Path sessionFile) throws IOException {
        Objects.requireNonNull(sessionFile, "sessionFile");

        //1. 会话头部字段与累计统计。
        String sessionId = null;
        int version = 0;
        String cwd = null;
        String timestamp = null;
        int entryCount = 0;
        int messageCount = 0;
        String lastText = null;

        boolean first = true;
        try (BufferedReader reader = Files.newBufferedReader(sessionFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                //2. 空行和非法 JSON 行跳过，不让单行损坏中断整体统计。
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node;
                try {
                    node = MAPPER.readTree(line);
                } catch (IOException parseError) {
                    continue;
                }
                if (node == null || !node.isObject()) {
                    continue;
                }

                //3. 首行若为 session 头，则提取元数据且不计入条目数。
                if (first && "session".equals(node.path("type").asText())) {
                    sessionId = text(node, "id");
                    version = node.path("version").asInt(0);
                    cwd = text(node, "cwd");
                    timestamp = text(node, "timestamp");
                    first = false;
                    continue;
                }

                //4. 其余行按条目统计；message 条目额外提取角色与文本。
                first = false;
                entryCount++;
                JsonNode message = node.get("message");
                if (message != null && message.isObject()) {
                    messageCount++;
                    String role = message.path("role").asText();
                    if ("user".equals(role) || "assistant".equals(role)) {
                        String text = messageText(message);
                        if (text != null && !text.isBlank()) {
                            lastText = text;
                        }
                    }
                }
            }
        }

        //5. 组装摘要返回。
        return new SessionSummary(sessionId, version, cwd, timestamp, entryCount, messageCount, lastText);
    }

    /**
     * 使用与 PI 相同的规则把工作目录编码为安全的目录名。
     */
    static String encodeCwd(Path cwd) {
        String resolved = cwd.toAbsolutePath().normalize().toString();
        String stripped = resolved.replaceFirst("^[/\\\\]", "");
        String safe = stripped.replaceAll("[/\\\\:]", "-");
        return "--" + safe + "--";
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static String messageText(JsonNode message) {
        JsonNode content = message.get("content");
        if (content == null) {
            return null;
        }
        //1. content 为裸字符串时直接返回。
        if (content.isTextual()) {
            return content.textValue();
        }
        //2. content 为数组时，拼接全部 text 类型块（忽略图片等其它块）。
        if (content.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode block : content) {
                if (block.isObject() && "text".equals(block.path("type").asText())) {
                    JsonNode text = block.get("text");
                    if (text != null && text.isTextual()) {
                        builder.append(text.textValue());
                    }
                }
            }
            return builder.toString();
        }
        return null;
    }

    private static String expandTilde(String path) {
        if (path.equals("~") || path.startsWith("~/") || path.startsWith("~\\")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
}
