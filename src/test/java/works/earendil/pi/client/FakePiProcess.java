package works.earendil.pi.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class FakePiProcess {
    /** 启动参数：向 stderr 写入非 UTF-8 字节，用于验证容错解码。 */
    static final String GBK_STDERR_FLAG = "--gbk-stderr";
    /** 以 GBK 写入 stderr 的文本；测试用它断言解码结果可读。 */
    static final String GBK_STDERR_TEXT = "警告：检测到 localhost 代理配置";

    private FakePiProcess() {
    }

    public static void main(String[] args) throws Exception {
        if (Arrays.asList(args).contains(GBK_STDERR_FLAG)) {
            // 直接写原始字节：绕过 PrintStream 的编码器，模拟中文 Windows 上输出 GBK 的子进程。
            System.err.write(GBK_STDERR_TEXT.getBytes(Charset.forName("GBK")));
            System.err.write('\n');
            System.err.flush();
        }
        ObjectMapper mapper = new ObjectMapper();
        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             BufferedWriter output = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
            String line;
            while ((line = input.readLine()) != null) {
                JsonNode command = mapper.readTree(line);
                String id = command.path("id").asText();
                String type = command.path("type").asText();
                switch (type) {
                    case "get_state" -> success(mapper, output, id, type,
                            mapper.createObjectNode()
                                    .put("thinkingLevel", "off")
                                    .put("isStreaming", false)
                                    .put("sessionId", "fake-session"));
                    case "prompt" -> {
                        success(mapper, output, id, type, null);
                        write(mapper, output, mapper.createObjectNode().put("type", "agent_start"));
                        ObjectNode delta = mapper.createObjectNode()
                                .put("type", "message_update")
                                .set("assistantMessageEvent", mapper.createObjectNode()
                                        .put("type", "text_delta")
                                        .put("delta", "你好\u2028PI"));
                        write(mapper, output, delta);
                        write(mapper, output, mapper.createObjectNode()
                                .put("type", "extension_ui_request")
                                .put("id", "ui-1")
                                .put("method", "confirm")
                                .put("title", "确认")
                                .put("message", "继续吗？"));
                        write(mapper, output, mapper.createObjectNode().put("type", "agent_settled"));
                    }
                    case "get_available_models" -> success(mapper, output, id, type,
                            mapper.createObjectNode().set("models", mapper.createArrayNode().add(
                                    mapper.createObjectNode()
                                            .put("id", "fake-model")
                                            .put("name", "Fake Model")
                                            .put("api", "fake")
                                            .put("provider", "fake")
                                            .put("baseUrl", "http://localhost")
                                            .put("reasoning", false)
                                            .set("input", mapper.createArrayNode().add("text")))));
                    case "get_available_thinking_levels" -> success(mapper, output, id, type,
                            mapper.createObjectNode().set("levels", mapper.createArrayNode().add("off").add("high")));
                    case "clear_queue" -> success(mapper, output, id, type,
                            mapper.createObjectNode()
                                    .set("steering", mapper.createArrayNode()));
                    case "extension_ui_response", "hang" -> {
                        // 通知没有 RPC response；hang 用于验证客户端超时。
                    }
                    default -> failure(mapper, output, id, type, "unsupported command");
                }
            }
        }
    }

    private static void success(
            ObjectMapper mapper,
            BufferedWriter output,
            String id,
            String command,
            JsonNode data
    ) throws Exception {
        ObjectNode response = mapper.createObjectNode()
                .put("id", id)
                .put("type", "response")
                .put("command", command)
                .put("success", true);
        if (data != null) {
            response.set("data", data);
        }
        write(mapper, output, response);
    }

    private static void failure(
            ObjectMapper mapper,
            BufferedWriter output,
            String id,
            String command,
            String error
    ) throws Exception {
        write(mapper, output, mapper.createObjectNode()
                .put("id", id)
                .put("type", "response")
                .put("command", command)
                .put("success", false)
                .put("error", error));
    }

    private static void write(ObjectMapper mapper, BufferedWriter output, JsonNode value) throws Exception {
        output.write(mapper.writeValueAsString(value));
        output.write('\n');
        output.flush();
    }
}
