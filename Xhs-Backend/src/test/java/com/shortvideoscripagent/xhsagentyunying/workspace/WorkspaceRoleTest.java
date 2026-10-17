package com.shortvideoscripagent.xhsagentyunying.workspace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceRoleTest {

    @Test
    void rank_order_is_correct() {
        assertTrue(WorkspaceRole.OWNER.atLeast(WorkspaceRole.ADMIN));
        assertTrue(WorkspaceRole.OWNER.atLeast(WorkspaceRole.OWNER));
        assertTrue(WorkspaceRole.ADMIN.atLeast(WorkspaceRole.EDITOR));
        assertTrue(WorkspaceRole.EDITOR.atLeast(WorkspaceRole.REVIEWER));
        assertTrue(WorkspaceRole.REVIEWER.atLeast(WorkspaceRole.VIEWER));
    }

    @Test
    void lower_roles_cannot_match_higher() {
        assertFalse(WorkspaceRole.VIEWER.atLeast(WorkspaceRole.EDITOR));
        assertFalse(WorkspaceRole.REVIEWER.atLeast(WorkspaceRole.EDITOR));
        assertFalse(WorkspaceRole.EDITOR.atLeast(WorkspaceRole.ADMIN));
    }

    @Test
    void parse_normalizes_to_uppercase() {
        assertSame(WorkspaceRole.OWNER, WorkspaceRole.parse("owner"));
        assertSame(WorkspaceRole.ADMIN, WorkspaceRole.parse(" admin "));
    }

    @Test
    void parse_falls_back_to_viewer_on_invalid_or_blank() {
        assertSame(WorkspaceRole.VIEWER, WorkspaceRole.parse(null));
        assertSame(WorkspaceRole.VIEWER, WorkspaceRole.parse(""));
        assertSame(WorkspaceRole.VIEWER, WorkspaceRole.parse("not-a-role"));
    }
}