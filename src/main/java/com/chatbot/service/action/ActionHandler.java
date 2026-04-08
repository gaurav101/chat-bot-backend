package com.chatbot.service.action;

import com.chatbot.model.*;
import com.chatbot.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

// ════════════════════════════════════════════════════════════════
// SPI — implement this to add custom action types
// ════════════════════════════════════════════════════════════════
public interface ActionHandler {
    boolean supports(NodeAction.ActionType type);

    /**
     * Execute the action.
     * @param config   JSON config from node_actions.config_json
     * @param session  current session (can read/write contextData)
     * @return         map of values to merge into session context
     * @throws Exception on failure — triggers on_failure_node path
     */
    Map<String, Object> execute(Map<String, Object> config, Session session) throws Exception;
}

