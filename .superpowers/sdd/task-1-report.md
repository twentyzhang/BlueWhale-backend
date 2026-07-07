# Task 1 Report: Strategy Components

## Status
DONE_WITH_CONCERNS

## What I changed
- Added `src/main/java/com/twentyzhang/bluewhale/service/agent/AgentIntent.java`
- Added `src/main/java/com/twentyzhang/bluewhale/service/agent/AgentIntentClassifier.java`
- Added `src/main/java/com/twentyzhang/bluewhale/service/agent/AgentClarificationPolicy.java`
- Added `src/test/java/com/twentyzhang/bluewhale/service/agent/AgentIntentClassifierTest.java`
- Added `src/test/java/com/twentyzhang/bluewhale/service/agent/AgentClarificationPolicyTest.java`
- Updated `docs/进度.md` with a Task 1 progress note

## Behavior implemented
- Classifies vague recommendation prompts like `推荐点东西` and `买什么好` as `AgentIntent.UNCLEAR`
- Classifies concrete recommendation prompts like `送长辈的健康礼物，预算100` and `夏天喝点无糖的` as `AgentIntent.PRODUCT_RECOMMENDATION`
- Classifies personal order and coupon questions before generic product intent
- Classifies stock and detail questions as `AgentIntent.STOCK_OR_DETAIL`
- Falls back to `AgentIntent.GENERAL_GUIDANCE` for unrelated text and `null`
- Returns the exact clarification question only when the intent is `UNCLEAR`

## Verification
- I wrote the tests first and ran the focused Maven command from the brief
- The first and second focused Maven runs both failed before compilation because Maven could not resolve `org.springframework.boot:spring-boot-starter-parent:3.4.0`
- The failure was environmental: the sandbox cannot reach Maven Central, so the requested test command could not complete here

## Test summary
- `mvn "-Dtest=AgentIntentClassifierTest,AgentClarificationPolicyTest" test` -> blocked by parent POM resolution in this environment

## Concerns
- Focused Maven verification is blocked by external dependency resolution, so I could not observe the final green run in this sandbox
