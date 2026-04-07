package com.chatbot.service.action;

import com.chatbot.model.Models.*;
import com.chatbot.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/**
 * ActionExecutor
 *
 * Dispatches ACTION nodes to the appropriate ActionHandler.
 * To add a new action type, implement ActionHandler and register it as a Spring bean.
 * The dispatcher auto-discovers all handlers via the List<ActionHandler> injection.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ActionExecutor {

    private final List<ActionHandler> handlers;

    /**
     * Executes the action attached to the given node.
     * Returns the success or failure node based on outcome.
     */
    public Node execute(Node actionNode, Session session) {
        NodeAction action = actionNode.getAction();
        if (action == null) {
            log.warn("ACTION node '{}' has no action config — using success path.", actionNode.getNodeKey());
            return actionNode.getAction().getOnSuccessNode() != null
                    ? actionNode.getAction().getOnSuccessNode() : actionNode;
        }

        ActionHandler handler = handlers.stream()
                .filter(h -> h.supports(action.getActionType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No handler for action type: " + action.getActionType()));

        try {
            Map<String, Object> result = handler.execute(action.getConfigJson(), session);
            // Merge action result into session context
            session.getContextData().putAll(result);
            log.info("Action '{}' on node '{}' succeeded.", action.getActionType(), actionNode.getNodeKey());
            return action.getOnSuccessNode() != null ? action.getOnSuccessNode() : actionNode;
        } catch (Exception ex) {
            log.error("Action '{}' on node '{}' failed: {}", action.getActionType(),
                      actionNode.getNodeKey(), ex.getMessage());
            return action.getOnFailureNode() != null ? action.getOnFailureNode() : actionNode;
        }
    }
}

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

// ════════════════════════════════════════════════════════════════
// HTTP_CALL handler
// config keys: url, method (GET/POST), headers (map), bodyTemplate (SpEL)
// ════════════════════════════════════════════════════════════════
@Component
@Slf4j
@SuppressWarnings("unchecked")
public class HttpCallActionHandler implements ActionHandler {

    private final WebClient.Builder webClientBuilder;

    public HttpCallActionHandler(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public boolean supports(NodeAction.ActionType type) {
        return type == NodeAction.ActionType.HTTP_CALL;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> config, Session session) throws Exception {
        String url    = (String) config.get("url");
        String method = ((String) config.getOrDefault("method", "GET")).toUpperCase();

        WebClient client = webClientBuilder.baseUrl(url).build();
        Map<String, Object> responseBody;

        if ("POST".equals(method)) {
            Map<String, Object> body = (Map<String, Object>) config.getOrDefault("body", Map.of());
            responseBody = client.post()
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        } else {
            responseBody = client.get()
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("httpResponse", responseBody);
        return result;
    }
}

// ════════════════════════════════════════════════════════════════
// CUSTOM_BEAN handler — delegates to a named Spring bean
// config keys: beanName (required)
// ════════════════════════════════════════════════════════════════
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomBeanActionHandler implements ActionHandler {

    private final org.springframework.context.ApplicationContext appCtx;

    @Override
    public boolean supports(NodeAction.ActionType type) {
        return type == NodeAction.ActionType.CUSTOM_BEAN;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> config, Session session) throws Exception {
        String beanName = (String) config.get("beanName");
        if (beanName == null) throw new IllegalArgumentException("beanName is required for CUSTOM_BEAN action");

        // The named bean must implement ActionHandler (the execute method)
        ActionHandler bean = appCtx.getBean(beanName, ActionHandler.class);
        return bean.execute(config, session);
    }
}

// ════════════════════════════════════════════════════════════════
// EMAIL handler (stub — wire to JavaMailSender / SendGrid etc.)
// config keys: to, subject, templateName
// ════════════════════════════════════════════════════════════════
@Component
@Slf4j
public class SendEmailActionHandler implements ActionHandler {

    @Override
    public boolean supports(NodeAction.ActionType type) {
        return type == NodeAction.ActionType.SEND_EMAIL;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> config, Session session) throws Exception {
        String to      = (String) config.get("to");
        String subject = (String) config.get("subject");
        // TODO: inject JavaMailSender / Spring Mail / SendGrid SDK here
        log.info("STUB: sending email to={} subject={}", to, subject);
        return Map.of("emailSent", true, "emailTo", to);
    }
}
