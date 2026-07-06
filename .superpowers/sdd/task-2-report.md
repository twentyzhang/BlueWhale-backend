# Task 2 Report: Request Trace Filter

## Summary

Implemented `RequestTraceFilter` to propagate `X-Request-Id` into the response and `MDC["requestId"]` during downstream filter execution, generating a UUID when the request header is missing.

## Files Changed

- `src/main/java/com/twentyzhang/bluewhale/filter/RequestTraceFilter.java`
- `src/test/java/com/twentyzhang/bluewhale/filter/RequestTraceFilterTest.java`

## TDD Notes

1. Added the focused test class first.
2. Ran `mvn -Dtest=RequestTraceFilterTest test` and confirmed it failed because `RequestTraceFilter` did not exist yet.
3. Added the minimal filter implementation.
4. Re-ran the same focused test and confirmed it passed.

## Verification

- Focused test: `mvn -Dtest=RequestTraceFilterTest test`
- Result: 3 tests passed, 0 failures, 0 errors

## Notes

- Scope was kept to the two task-owned files requested for this task.
- Repository-wide docs were not edited because the task instructions explicitly limited file changes outside the task scope.

---

## Review Fix: MDC propagation for stream executors

### Summary

Added a focused `MdcTaskDecorator` so servlet-thread `MDC["requestId"]` is copied into asynchronous `ThreadPoolTaskExecutor` tasks used by the RAG and Agent SSE flows, then restored/cleared correctly after each task.

### Files Changed

- `src/main/java/com/twentyzhang/bluewhale/config/MdcTaskDecorator.java`
- `src/main/java/com/twentyzhang/bluewhale/config/RagExecutorConfig.java`
- `src/main/java/com/twentyzhang/bluewhale/config/AgentExecutorConfig.java`
- `src/test/java/com/twentyzhang/bluewhale/config/MdcTaskDecoratorTest.java`

### TDD Notes

1. Added `MdcTaskDecoratorTest` first.
2. Ran `mvn "-Dtest=RequestTraceFilterTest,MdcTaskDecoratorTest" test` and confirmed the red phase after fixing the PowerShell argument form:
   - first command form failed in PowerShell parsing because the comma-separated `-Dtest` value was unquoted;
   - quoted Maven command then failed because `MdcTaskDecorator` did not exist yet.
3. Added the minimal decorator implementation and wired it into both existing stream executors.
4. Re-ran the same focused test command and confirmed it passed.

### Verification

- Focused test: `mvn "-Dtest=RequestTraceFilterTest,MdcTaskDecoratorTest" test`
- Result: 5 tests passed, 0 failures, 0 errors

### Notes

- Scope stayed local to executor configuration and the new decorator; no RAG or Agent business logic was changed.
