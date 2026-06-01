package com.shortvideoscripagent.xhsagentyunying.ai.kb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析 Markdown YAML front matter（仅支持扁平 key: value）。
 */
final class KbMarkdownFrontMatter {

    private KbMarkdownFrontMatter() {
    }

    record Parsed(Map<String, String> fields, String body) {
    }

    static Parsed parse(String raw) {
        if (raw == null || !raw.startsWith("---")) {
            return new Parsed(Map.of(), raw == null ? "" : raw.trim());
        }
        int end = raw.indexOf("---", 3);
        if (end < 0) {
            return new Parsed(Map.of(), raw.trim());
        }
        String yamlBlock = raw.substring(3, end).trim();
        String body = raw.substring(end + 3).trim();
        return new Parsed(parseYamlBlock(yamlBlock), body);
    }

    private static Map<String, String> parseYamlBlock(String yamlBlock) {
        Map<String, String> map = new HashMap<>();
        for (String line : yamlBlock.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).trim();
            String value = trimmed.substring(colon + 1).trim();
            if (value.startsWith("[") && value.endsWith("]")) {
                value = value.substring(1, value.length() - 1);
            }
            map.put(key, value);
        }
        return map;
    }

    static List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String[] parts = value.split("[,，]");
        List<String> list = new ArrayList<>();
        for (String part : parts) {
            String item = part.trim();
            if (!item.isEmpty()) {
                list.add(item);
            }
        }
        return list;
    }
}
