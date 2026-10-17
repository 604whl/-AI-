package com.shortvideoscripagent.xhsagentyunying.freeze;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 0：服务层单元测试。
 */
class Stage0ServiceTest {

    @Test
    void normalizeBillableUnitKnownActions() {
        Stage0Service service = new Stage0Service(null, new ObjectMapper());
        assertEquals(BillableUnit.ANALYSIS, service.normalizeBillableUnit("ANALYSIS"));
        assertEquals(BillableUnit.TITLE_GENERATION, service.normalizeBillableUnit("title_generation"));
        assertEquals(BillableUnit.BODY_GENERATION, service.normalizeBillableUnit("body"));
        assertEquals(BillableUnit.COVER_ANALYSIS, service.normalizeBillableUnit("cover"));
        assertEquals(BillableUnit.EXPORT, service.normalizeBillableUnit("EXPORT"));
        assertEquals(BillableUnit.EMBEDDING, service.normalizeBillableUnit("embedding"));
        assertEquals(BillableUnit.SEARCH, service.normalizeBillableUnit("web_search"));
    }

    @Test
    void normalizeBillableUnitUnknownFallsBackToAnalysis() {
        Stage0Service service = new Stage0Service(null, new ObjectMapper());
        assertEquals(BillableUnit.ANALYSIS, service.normalizeBillableUnit("FOO_BAR"));
        assertEquals(BillableUnit.ANALYSIS, service.normalizeBillableUnit(null));
    }

    @Test
    void riskLevelBlocksPublication() {
        assertFalse(RiskLevel.LOW.blocksPublication());
        assertFalse(RiskLevel.MEDIUM.blocksPublication());
        assertTrue(RiskLevel.HIGH.blocksPublication());
        assertTrue(RiskLevel.BLOCKED.blocksPublication());
    }

    @Test
    void parseJsonSafelyReturnsEmpty() {
        Stage0Service service = new Stage0Service(null, new ObjectMapper());
        assertTrue(service.parseJsonSafely(null).isEmpty());
        assertTrue(service.parseJsonSafely("").isEmpty());
        assertTrue(service.parseJsonSafely("not-json").isEmpty());
    }

    @Test
    void parseJsonSafelyParsesValid() {
        Stage0Service service = new Stage0Service(null, new ObjectMapper());
        assertTrue(service.parseJsonSafely("{\"a\":1}").isPresent());
        assertTrue(service.parseJsonSafely("[1,2,3]").isPresent());
    }

    @Test
    void connectorLevelCanWrite() {
        assertTrue(Stage0Service.ConnectorLevel.OFFICIAL_API.canWrite());
        assertTrue(Stage0Service.ConnectorLevel.AUTHORIZED_CONNECTOR.canWrite());
        assertTrue(Stage0Service.ConnectorLevel.BROWSER_ASSIST.canWrite());
        assertFalse(Stage0Service.ConnectorLevel.MANUAL_IMPORT.canWrite());
        assertFalse(Stage0Service.ConnectorLevel.DISABLED.canWrite());
    }

    @Test
    void connectorLevelCanRead() {
        assertTrue(Stage0Service.ConnectorLevel.OFFICIAL_API.canRead());
        assertTrue(Stage0Service.ConnectorLevel.MANUAL_IMPORT.canRead());
        assertFalse(Stage0Service.ConnectorLevel.DISABLED.canRead());
    }

    @Test
    void connectorLevelOf() {
        assertEquals(Stage0Service.ConnectorLevel.OFFICIAL_API,
                Stage0Service.ConnectorLevel.of("OFFICIAL_API"));
        assertEquals(Stage0Service.ConnectorLevel.MANUAL_IMPORT,
                Stage0Service.ConnectorLevel.of("manual_import"));
        assertNull(Stage0Service.ConnectorLevel.of("UNKNOWN"));
        assertNull(Stage0Service.ConnectorLevel.of(null));
        assertNull(Stage0Service.ConnectorLevel.of(""));
    }

    @Test
    void requireTransitionAllowedThrowsOnIllegal() {
        Stage0Service service = new Stage0Service(null, new ObjectMapper());
        assertDoesNotThrow(() ->
                service.requireTransitionAllowed(ContentStatus.DRAFT, ContentStatus.IN_REVIEW));
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.requireTransitionAllowed(ContentStatus.DRAFT, ContentStatus.PUBLISHED));
        assertTrue(ex.getMessage().contains("状态机"));
    }
}
