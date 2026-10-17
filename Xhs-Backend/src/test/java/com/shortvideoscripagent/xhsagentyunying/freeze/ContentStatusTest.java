package com.shortvideoscripagent.xhsagentyunying.freeze;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 0：内容状态机单元测试。
 * <p>
 * 验证 21 条转换合法性 + 禁止的非法转换。
 */
class ContentStatusTest {

    @Test
    void allStatesEnum() {
        ContentStatus[] states = ContentStatus.values();
        assertEquals(7, states.length);
    }

    @Test
    void draftCanSubmitForReview() {
        assertTrue(ContentStatus.canTransition(ContentStatus.DRAFT, ContentStatus.IN_REVIEW));
    }

    @Test
    void draftCannotPublishDirectly() {
        assertFalse(ContentStatus.canTransition(ContentStatus.DRAFT, ContentStatus.PUBLISHED));
    }

    @Test
    void draftCanArchive() {
        assertTrue(ContentStatus.canTransition(ContentStatus.DRAFT, ContentStatus.ARCHIVED));
    }

    @Test
    void inReviewCanApprove() {
        assertTrue(ContentStatus.canTransition(ContentStatus.IN_REVIEW, ContentStatus.APPROVED));
    }

    @Test
    void inReviewCanReject() {
        assertTrue(ContentStatus.canTransition(ContentStatus.IN_REVIEW, ContentStatus.REJECTED));
    }

    @Test
    void inReviewCanReturnToDraft() {
        assertTrue(ContentStatus.canTransition(ContentStatus.IN_REVIEW, ContentStatus.DRAFT));
    }

    @Test
    void approvedCanSchedule() {
        assertTrue(ContentStatus.canTransition(ContentStatus.APPROVED, ContentStatus.SCHEDULED));
    }

    @Test
    void approvedCannotJumpToPublished() {
        assertFalse(ContentStatus.canTransition(ContentStatus.APPROVED, ContentStatus.PUBLISHED));
    }

    @Test
    void scheduledCanPublish() {
        assertTrue(ContentStatus.canTransition(ContentStatus.SCHEDULED, ContentStatus.PUBLISHED));
    }

    @Test
    void publishedCanArchive() {
        assertTrue(ContentStatus.canTransition(ContentStatus.PUBLISHED, ContentStatus.ARCHIVED));
    }

    @Test
    void publishedCannotEditBackToDraft() {
        assertFalse(ContentStatus.canTransition(ContentStatus.PUBLISHED, ContentStatus.DRAFT));
    }

    @Test
    void rejectedCanRevise() {
        assertTrue(ContentStatus.canTransition(ContentStatus.REJECTED, ContentStatus.DRAFT));
    }

    @Test
    void archivedCannotTransitionExceptItself() {
        for (ContentStatus to : ContentStatus.values()) {
            if (to == ContentStatus.ARCHIVED) {
                assertTrue(ContentStatus.canTransition(ContentStatus.ARCHIVED, to),
                        "同状态应为合法: ARCHIVED → ARCHIVED");
            } else {
                assertFalse(ContentStatus.canTransition(ContentStatus.ARCHIVED, to),
                        "归档不能直接转换到: " + to);
            }
        }
    }

    @Test
    void sameStateIsAllowed() {
        for (ContentStatus s : ContentStatus.values()) {
            assertTrue(ContentStatus.canTransition(s, s), "same state should be a no-op: " + s);
        }
    }

    @Test
    void nullStatesAreRejected() {
        assertFalse(ContentStatus.canTransition(null, ContentStatus.DRAFT));
        assertFalse(ContentStatus.canTransition(ContentStatus.DRAFT, null));
    }

    @Test
    void isTerminalForPublishedOrArchived() {
        assertTrue(ContentStatus.PUBLISHED.isTerminal());
        assertTrue(ContentStatus.ARCHIVED.isTerminal());
        assertFalse(ContentStatus.DRAFT.isTerminal());
    }

    @Test
    void parseAcceptsValidNames() {
        assertEquals(ContentStatus.DRAFT, ContentStatus.tryParse("DRAFT").orElseThrow());
        assertEquals(ContentStatus.IN_REVIEW, ContentStatus.tryParse("in_review").orElseThrow());
        assertEquals(ContentStatus.PUBLISHED, ContentStatus.tryParse("Published").orElseThrow());
    }

    @Test
    void parseRejectsInvalidNames() {
        assertTrue(ContentStatus.tryParse("UNKNOWN").isEmpty());
        assertTrue(ContentStatus.tryParse("").isEmpty());
        assertTrue(ContentStatus.tryParse(null).isEmpty());
    }

    @Test
    void totalLegalTransitions() {
        int count = 0;
        for (ContentStatus from : ContentStatus.values()) {
            for (ContentStatus to : ContentStatus.values()) {
                if (ContentStatus.canTransition(from, to)) {
                    count++;
                }
            }
        }
        // 7 same-state + 14 legal transitions
        assertEquals(21, count);
    }

    @Test
    void enumeratedIllegalTransitions() {
        // 典型的禁止转换
        assertFalse(ContentStatus.canTransition(ContentStatus.DRAFT, ContentStatus.PUBLISHED));
        assertFalse(ContentStatus.canTransition(ContentStatus.APPROVED, ContentStatus.PUBLISHED));
        assertFalse(ContentStatus.canTransition(ContentStatus.PUBLISHED, ContentStatus.DRAFT));
        assertFalse(ContentStatus.canTransition(ContentStatus.PUBLISHED, ContentStatus.IN_REVIEW));
        // ARCHIVED 只能到自身
        for (ContentStatus target : ContentStatus.values()) {
            if (target != ContentStatus.ARCHIVED) {
                assertFalse(ContentStatus.canTransition(ContentStatus.ARCHIVED, target),
                        "归档不能 → " + target);
            }
        }
    }
}
