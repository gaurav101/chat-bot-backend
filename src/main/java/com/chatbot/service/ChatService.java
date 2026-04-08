package com.chatbot.service;

import com.chatbot.model.*;
import com.chatbot.nlp.NlpResult;
import com.chatbot.repository.*;
import com.chatbot.service.action.ActionExecutor;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * ChatService
 *
 * Orchestration layer for the chatbot lifecycle:
 *   startSession → sendMessage (loop) → endSession
 *
 * Responsibilities:
 *  • Create/load sessions
 *  • Persist inbound & outbound messages
 *  • Invoke DecisionTreeEngine for routing
 *  • Delegate ACTION nodes to ActionExecutor
 *  • Guard session TTL and state
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatService {

    private final SessionRepository         sessionRepo;
    private final NodeRepository            nodeRepo;
    private final ConversationMessageRepo   messageRepo;
    private final DecisionTreeRepository    treeRepo;
    private final DecisionTreeEngine        engine;
    private final ActionExecutor            actionExecutor;

    @Value("${chatbot.session.ttl-minutes:30}") private int ttlMinutes;

    // ──────────────────────────────────────────────────────────────
    // Start Session
    // ──────────────────────────────────────────────────────────────

    @Transactional
    public ChatResponse startSession(Long treeId, String channel, String userIdentifier) {
        DecisionTree tree = treeRepo.findByIdWithNodes(treeId)
                .orElseThrow(() -> new NoSuchElementException("Tree not found: " + treeId));

        Node rootNode = tree.getNodes().stream()
                .filter(Node::isRoot)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Tree has no root node: " + treeId));

        Session session = new Session();
        session.setSessionToken(UUID.randomUUID().toString());
        session.setTree(tree);
        session.setCurrentNode(rootNode);
        session.setChannel(channel != null ? channel : "WEB");
        session.setUserIdentifier(userIdentifier);
        session.setExpiresAt(LocalDateTime.now().plusMinutes(ttlMinutes));
        sessionRepo.save(session);

        // Persist outbound welcome message
        persistMessage(session, ConversationMessage.Direction.OUTBOUND, rootNode.getMessageText(), rootNode, null);

        log.info("Session started: {} on tree '{}' ch={}", session.getSessionToken(), tree.getName(), channel);
        return buildResponse(session, rootNode, null);
    }

    // ──────────────────────────────────────────────────────────────
    // Send Message (main processing loop)
    // ──────────────────────────────────────────────────────────────

    @Transactional
    public ChatResponse sendMessage(String sessionToken, String userInput) {

        // ── Load & validate session ──
        Session session = sessionRepo.findBySessionTokenWithRelations(sessionToken)
                .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionToken));

        validateSession(session);

        // ── Persist inbound message ──
        persistMessage(session, ConversationMessage.Direction.INBOUND, userInput,
                       session.getCurrentNode(), null);

        // ── Route ──
        DecisionTreeEngine.RouteResult route = engine.route(session, userInput);
        Node nextNode = route.getNextNode();

        // ── Handle ACTION node ──
        if (nextNode.getNodeType() == Node.NodeType.ACTION && nextNode.getAction() != null) {
            nextNode = actionExecutor.execute(nextNode, session);
        }

        // ── Update session ──
        session.setCurrentNode(nextNode);
        session.setLastActivity(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusMinutes(ttlMinutes));

        if (nextNode.getNodeType() == Node.NodeType.END) {
            session.setState(Session.State.COMPLETED);
        }

        sessionRepo.save(session);

        // ── Persist outbound message ──
        NlpResult nlp = route.getNlpResult();
        ConversationMessage outbound = persistMessage(
                session, ConversationMessage.Direction.OUTBOUND,
                nextNode.getMessageText(), nextNode, nlp);

        log.debug("[{}] {} → {}", sessionToken,
                  session.getCurrentNode().getNodeKey(), nextNode.getNodeKey());

        return buildResponse(session, nextNode, nlp);
    }

    // ──────────────────────────────────────────────────────────────
    // Get History
    // ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MessageDto> getHistory(String sessionToken) {
        Session session = sessionRepo.findBySessionTokenWithRelations(sessionToken)
                .orElseThrow(() -> new NoSuchElementException("Session not found: " + sessionToken));

        return session.getMessages().stream().map(m -> MessageDto.builder()
                .direction(m.getDirection().name())
                .text(m.getMessageText())
                .intent(m.getIntentDetected())
                .confidence(m.getConfidence())
                .sentAt(m.getSentAt().toString())
                .build()).toList();
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private void validateSession(Session session) {
        if (session.getState() != Session.State.ACTIVE) {
            throw new IllegalStateException("Session is not active: " + session.getState());
        }
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
            session.setState(Session.State.EXPIRED);
            sessionRepo.save(session);
            throw new IllegalStateException("Session has expired");
        }
    }

    private ConversationMessage persistMessage(Session session, ConversationMessage.Direction dir,
                                               String text, Node node, NlpResult nlp) {
        ConversationMessage msg = new ConversationMessage();
        msg.setSession(session);
        msg.setDirection(dir);
        msg.setMessageText(text);
        msg.setNode(node);
        if (nlp != null) {
            msg.setIntentDetected(nlp.getTopIntent());
            msg.setConfidence(nlp.getConfidence());
            msg.setEntitiesJson(nlp.getEntities() != null
                    ? new HashMap<>(nlp.getEntities()) : null);
            msg.setRawNlpResponse(nlp.getRawProviderResponse());
        }
        return messageRepo.save(msg);
    }

    private ChatResponse buildResponse(Session session, Node node, NlpResult nlp) {
        return ChatResponse.builder()
                .sessionToken(session.getSessionToken())
                .message(node.getMessageText())
                .nodeKey(node.getNodeKey())
                .nodeType(node.getNodeType().name())
                .sessionState(session.getState().name())
                .metadata(node.getMetadata())
                .intent(nlp != null ? nlp.getTopIntent() : null)
                .confidence(nlp != null ? nlp.getConfidence() : null)
                .build();
    }

    // ──────────────────────────────────────────────────────────────
    // DTOs
    // ──────────────────────────────────────────────────────────────

    @Getter @Builder
    public static class ChatResponse {
        private String              sessionToken;
        private String              message;
        private String              nodeKey;
        private String              nodeType;
        private String              sessionState;
        private Map<String, Object> metadata;
        private String              intent;
        private Double              confidence;
    }

    @Getter @Builder
    public static class MessageDto {
        private String direction;
        private String text;
        private String intent;
        private Double confidence;
        private String sentAt;
    }
}
