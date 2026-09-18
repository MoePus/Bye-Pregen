package com.moepus.byepregen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The two access inputs that ship have to widen the same members: the NeoForge access transformer
 * and the Fabric access widener are maintained by hand, and 26.3 already drifted once (the YA light
 * task type was widened in the retired monolith transformer only, which the Fabric side then read
 * without an entry). This keeps them in step.
 */
final class AccessRulesParityTest {
    @Test
    void accessTransformerAndWidenerTargetTheSameMembers() throws Exception {
        assertEquals(readAccessTransformer(), readAccessWidener());
    }

    private static Set<Rule> readAccessTransformer() throws IOException, URISyntaxException {
        Set<Rule> rules = new LinkedHashSet<>();
        for (String line : read("/META-INF/arena-core.cfg")) {
            String[] parts = line.split("\\s+");
            if (parts.length < 2) continue;
            String owner = parts[1].replace('.', '/');
            if (parts.length == 2) {
                rules.add(new Rule("class", owner, ""));
            } else if (parts[2].contains("(")) {
                rules.add(new Rule("method", owner, parts[2]));
            } else {
                rules.add(new Rule("field", owner, parts[2]));
            }
        }
        return rules;
    }

    private static Set<Rule> readAccessWidener() throws IOException, URISyntaxException {
        Set<Rule> rules = new LinkedHashSet<>();
        for (String line : read("/byepregen.arena.accesswidener")) {
            String[] parts = line.split("\\s+");
            if (parts.length < 3 || (!parts[0].equals("accessible") && !parts[0].equals("extendable"))) continue;
            if (parts[1].equals("class")) {
                rules.add(new Rule("class", parts[2], ""));
            } else if (parts[1].equals("method")) {
                rules.add(new Rule("method", parts[2], parts[3] + parts[4]));
            } else {
                rules.add(new Rule("field", parts[2], parts[3]));
            }
        }
        return rules;
    }

    private static List<String> read(String resource) throws IOException, URISyntaxException {
        return Files.readAllLines(Path.of(AccessRulesParityTest.class.getResource(resource).toURI())).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#") && !line.startsWith("accessWidener"))
                .toList();
    }

    private record Rule(String kind, String owner, String member) {
    }
}
