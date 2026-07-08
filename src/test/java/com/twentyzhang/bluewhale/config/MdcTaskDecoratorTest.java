package com.twentyzhang.bluewhale.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MdcTaskDecoratorTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void copiesSubmissionContextIntoDecoratedTask() {
        MdcTaskDecorator decorator = new MdcTaskDecorator();
        AtomicReference<String> requestId = new AtomicReference<>();

        MDC.put("requestId", "req-123");
        Runnable decorated = decorator.decorate(() -> requestId.set(MDC.get("requestId")));
        MDC.clear();

        decorated.run();

        assertEquals("req-123", requestId.get());
        assertNull(MDC.get("requestId"));
    }

    @Test
    void restoresWorkerThreadContextAfterTaskCompletes() {
        MdcTaskDecorator decorator = new MdcTaskDecorator();
        AtomicReference<Map<String, String>> workerContextDuringRun = new AtomicReference<>();

        MDC.put("requestId", "submit-456");
        Runnable decorated = decorator.decorate(() -> {
            workerContextDuringRun.set(MDC.getCopyOfContextMap());
            MDC.put("requestId", "mutated-inside-task");
        });
        MDC.clear();

        MDC.put("requestId", "worker-original");
        decorated.run();

        assertEquals("submit-456", workerContextDuringRun.get().get("requestId"));
        assertEquals("worker-original", MDC.get("requestId"));
    }
}
