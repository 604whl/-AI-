package com.shortvideoscripagent.xhsagentyunying.util.topic;

import java.util.regex.Pattern;

public final class TopicNormalize {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TopicNormalize() {
    }

    public static String normalizeTitle(String title) {
        if (title == null) {
            return "";
        }
        return WHITESPACE.matcher(title.trim().toLowerCase()).replaceAll(" ");
    }
}