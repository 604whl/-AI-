package com.shortvideoscripagent.xhsagentyunying.domain.topic;

import com.shortvideoscripagent.xhsagentyunying.util.topic.TopicNormalize;

/**
 * Deduplication key utilities for TopicPool.
 * Delegates normalization to TopicNormalize (from 1F).
 */
public final class TopicDedupeKey {

    private TopicDedupeKey() {
    }

    /**
     * Normalize a topic title for deduplication comparison.
     * Applies LOWER + TRIM + collapse whitespace (delegated to TopicNormalize).
     *
     * @param title raw title (may be null or blank)
     * @return normalized title, or empty string if input is null/blank
     */
    public static String normalize(String title) {
        return TopicNormalize.normalizeTitle(title);
    }
}