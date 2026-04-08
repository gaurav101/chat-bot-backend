package com.chatbot.service.action;

import com.chatbot.model.Node;
import com.chatbot.model.NodeAction;
import com.chatbot.model.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map; /**
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
