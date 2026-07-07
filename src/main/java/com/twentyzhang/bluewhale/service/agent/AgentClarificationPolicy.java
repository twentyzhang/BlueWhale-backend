package com.twentyzhang.bluewhale.service.agent;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AgentClarificationPolicy {

    static final String DEFAULT_QUESTION = "你是想自己吃/用，还是送人？如果送人，大概预算是多少？";

    public Optional<String> maybeAsk(String q, AgentIntent intent) {
        if (intent != AgentIntent.UNCLEAR) {
            return Optional.empty();
        }
        return Optional.of(DEFAULT_QUESTION);
    }
}
