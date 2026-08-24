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

final class AccessRulesParityTest {
    @Test
    void accessTransformerAndWidenerTargetTheSameMembers() throws Exception {
        assertEquals(readAccessTransformer(), readAccessWidener());
    }

    private static Set<Rule> readAccessTransformer() throws IOException, URISyntaxException {
        Set<Rule> rules = new LinkedHashSet<>();
        for (String line : read("/META-INF/accesstransformer.cfg")) {
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
        for (String line : read("/byepregen.accesswidener")) {
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
