package works.earendil.pi.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class PiRpcCommandCoverageTest {
    @Test
    void commandEnumMatchesTypeScriptProtocolDefinition() throws Exception {
        Path source = Path.of("..", "..", "packages", "coding-agent", "src", "modes", "rpc", "rpc-types.ts");
        Assumptions.assumeTrue(Files.exists(source));
        String commandSection = Files.readString(source).split("RPC Slash Command", 2)[0];
        Matcher matcher = Pattern.compile("type: \\\"([^\\\"]+)\\\"").matcher(commandSection);
        Set<String> protocolCommands = new java.util.HashSet<>();
        while (matcher.find()) {
            protocolCommands.add(matcher.group(1));
        }
        Set<String> javaCommands = Arrays.stream(PiRpcCommand.values())
                .map(PiRpcCommand::wireValue)
                .collect(Collectors.toSet());

        assertEquals(protocolCommands, javaCommands);
    }
}
