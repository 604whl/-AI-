package com.shortvideoscripagent.xhsagentyunying.infra.taskqueue;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.handler.AnalysisTaskHandler;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.handler.CoverTaskHandler;
import com.shortvideoscripagent.xhsagentyunying.infra.taskqueue.handler.TitleTaskHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * P0-1: TaskRegistry 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class TaskRegistryTest {

    @Mock
    private AnalysisTaskHandler analysisHandler;

    @Mock
    private TitleTaskHandler titleHandler;

    @Mock
    private CoverTaskHandler coverHandler;

    @Test
    void init_registersAllHandlers() {
        when(analysisHandler.taskType()).thenReturn("ANALYSIS");
        when(titleHandler.taskType()).thenReturn("TITLE_GENERATION");
        when(coverHandler.taskType()).thenReturn("COVER_ANALYSIS");

        TaskRegistry registry = new TaskRegistry(
                List.of(analysisHandler, titleHandler, coverHandler)
        );
        registry.init();

        assertTrue(registry.hasHandler("ANALYSIS"));
        assertTrue(registry.hasHandler("TITLE_GENERATION"));
        assertTrue(registry.hasHandler("COVER_ANALYSIS"));
        assertFalse(registry.hasHandler("UNKNOWN"));
    }

    @Test
    void getHandler_validType_returnsHandler() {
        when(analysisHandler.taskType()).thenReturn("ANALYSIS");

        TaskRegistry registry = new TaskRegistry(List.of(analysisHandler));
        registry.init();

        TaskHandler handler = registry.getHandler("ANALYSIS");
        assertNotNull(handler);
        assertEquals("ANALYSIS", handler.taskType());
    }

    @Test
    void getHandler_unknownType_throwsNotFoundException() {
        when(analysisHandler.taskType()).thenReturn("ANALYSIS");

        TaskRegistry registry = new TaskRegistry(List.of(analysisHandler));
        registry.init();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> registry.getHandler("UNKNOWN_TYPE"));
        assertEquals(40401, ex.getCode());
        assertTrue(ex.getMessage().contains("UNKNOWN_TYPE"));
    }

    @Test
    void registeredTypes_returnsAllTypes() {
        when(analysisHandler.taskType()).thenReturn("ANALYSIS");
        when(titleHandler.taskType()).thenReturn("TITLE_GENERATION");

        TaskRegistry registry = new TaskRegistry(List.of(analysisHandler, titleHandler));
        registry.init();

        List<String> types = registry.registeredTypes();
        assertEquals(2, types.size());
        assertTrue(types.contains("ANALYSIS"));
        assertTrue(types.contains("TITLE_GENERATION"));
    }
}
