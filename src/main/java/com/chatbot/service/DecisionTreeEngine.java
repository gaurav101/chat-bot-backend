package com.chatbot.service;

import com.chatbot.model.*;
import com.chatbot.model.Models.*;
import com.chatbot.nlp.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * DecisionTreeEngine
 *
 * Given the current session (with its active node) and new user input,
 * walks the edge list and returns the next Node to transition to.
 *
 * Edge evaluation order (per DB priority DESC):
 *   1. EXACT    – case-insensitive full-string match
 *   2. CONTAINS – case-insensitive substring
 *   3. REGEX    – compiled pattern match
 *   4. INTENT   – matches NLP top intent name
 *   5. ENTITY   – matches NLP entity name (any value)
 *   6. CONDITION– SpEL expression over session context + NLP result
 *   7. DEFAULT  – catch-all (always matches)
 *
 * If no edge matches, returns the fallback node of the tree.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DecisionTreeEngine {

    private final NlpProvider nlpProvider;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    /**
     * Main routing method.
     *
     * @param session   the current session (must have currentNode loaded)
     * @param userInput raw text from the user
     * @return RouteResult containing the next node and NLP analysis
     */
    public RouteResult route(Session session, String userInput) {

        Node currentNode = session.getCurrentNode();
        if (currentNode == null) {
            throw new IllegalStateException("Session has no current node: " + session.getSessionToken());
        }

        List<Edge> edges = currentNode.getOutgoingEdges();

        // ── 1. NLP analysis (only when needed by edges or node type) ──
        NlpResult nlp = null;
        boolean needsNlp = currentNode.getNodeType() == Node.NodeType.NLP
                || edges.stream().anyMatch(e ->
                        e.getMatchType() == Edge.MatchType.INTENT ||
                        e.getMatchType() == Edge.MatchType.ENTITY  ||
                        e.getMatchType() == Edge.MatchType.CONDITION);

        if (needsNlp) {
            nlp = nlpProvider.analyse(
                    userInput,
                    session.getTree().getLanguageCode(),
                    session.getContextData()
            );
            log.debug("[{}] NLP → intent={} conf={}", session.getSessionToken(),
                      nlp.getTopIntent(), nlp.getConfidence());

            // Merge extracted entities into session context
            if (nlp.getEntities() != null) {
                session.getContextData().putAll(nlp.getEntities());
            }
        }

        // ── 2. Walk edges in priority order ──
        Node nextNode = null;
        Edge matchedEdge = null;

        for (Edge edge : edges) {
            if (!edge.isActive()) continue;
            if (matches(edge, userInput, nlp, session)) {
                nextNode    = edge.getToNode();
                matchedEdge = edge;
                log.debug("[{}] Edge matched: {} → {} ({})",
                          session.getSessionToken(),
                          currentNode.getNodeKey(),
                          nextNode.getNodeKey(),
                          edge.getMatchType());
                break;
            }
        }

        // ── 3. Fallback if no edge matched ──
        if (nextNode == null) {
            nextNode = findFallbackNode(session.getTree());
            log.warn("[{}] No edge matched from node '{}', using fallback.",
                     session.getSessionToken(), currentNode.getNodeKey());
        }

        return new RouteResult(nextNode, nlp, matchedEdge);
    }

    // ──────────────────────────────────────────────────────────────
    // Edge matching
    // ──────────────────────────────────────────────────────────────

    private boolean matches(Edge edge, String input, NlpResult nlp, Session session) {
        String lowerInput = input == null ? "" : input.trim().toLowerCase();
        String value      = edge.getMatchValue();

        return switch (edge.getMatchType()) {
            case EXACT     -> value != null && lowerInput.equals(value.toLowerCase());
            case CONTAINS  -> value != null && lowerInput.contains(value.toLowerCase());
            case REGEX     -> value != null && Pattern.compile(value, Pattern.CASE_INSENSITIVE)
                                                      .matcher(lowerInput).find();
            case INTENT    -> nlp != null && value != null && value.equalsIgnoreCase(nlp.getTopIntent());
            case ENTITY    -> nlp != null && nlp.getEntities() != null && nlp.getEntities().containsKey(value);
            case CONDITION -> evaluateSpEL(value, input, nlp, session);
            case DEFAULT   -> true;
        };
    }

    /**
     * SpEL condition evaluation.
     * Variables available: #input, #intent, #confidence, #entities, #ctx
     *
     * Example condition: "#confidence > 0.8 && #ctx['verified'] == true"
     */
    private boolean evaluateSpEL(String expression, String input, NlpResult nlp, Session session) {
        if (expression == null || expression.isBlank()) return false;
        try {
            var ctx = new StandardEvaluationContext();
            ctx.setVariable("input",      input);
            ctx.setVariable("intent",     nlp != null ? nlp.getTopIntent()  : null);
            ctx.setVariable("confidence", nlp != null ? nlp.getConfidence() : 0.0);
            ctx.setVariable("entities",   nlp != null ? nlp.getEntities()   : Map.of());
            ctx.setVariable("ctx",        session.getContextData());

            Boolean result = spelParser.parseExpression(expression).getValue(ctx, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("SpEL evaluation failed for expression '{}': {}", expression, e.getMessage());
            return false;
        }
    }

    private Node findFallbackNode(DecisionTree tree) {
        return tree.getNodes().stream()
                .filter(Node::isFallback)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No fallback node defined in tree: " + tree.getName()));
    }

    // ──────────────────────────────────────────────────────────────
    // Result DTO
    // ──────────────────────────────────────────────────────────────

    @Getter @AllArgsConstructor
    public static class RouteResult {
        private final Node      nextNode;
        private final NlpResult nlpResult;
        private final Edge      matchedEdge;    // null when fallback used
    }
}
