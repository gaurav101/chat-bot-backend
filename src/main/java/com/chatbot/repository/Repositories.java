package com.chatbot.repository;

import com.chatbot.model.Models.*;
import com.chatbot.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

// ─── DecisionTree ──────────────────────────────────────────────────────────
public interface DecisionTreeRepository extends JpaRepository<DecisionTree, Long> {

    @Query("""
        SELECT t FROM DecisionTree t
        LEFT JOIN FETCH t.nodes n
        LEFT JOIN FETCH n.outgoingEdges
        LEFT JOIN FETCH n.action
        WHERE t.id = :id AND t.isActive = true
    """)
    Optional<DecisionTree> findByIdWithNodes(@Param("id") Long id);

    Optional<DecisionTree> findByNameAndVersionAndIsActiveTrue(String name, String version);
}

// ─── Node ──────────────────────────────────────────────────────────────────
public interface NodeRepository extends JpaRepository<Node, Long> {

    @Query("""
        SELECT n FROM Node n
        LEFT JOIN FETCH n.outgoingEdges e
        LEFT JOIN FETCH e.toNode
        LEFT JOIN FETCH n.action a
        LEFT JOIN FETCH a.onSuccessNode
        LEFT JOIN FETCH a.onFailureNode
        WHERE n.id = :id
    """)
    Optional<Node> findByIdWithEdges(@Param("id") Long id);

    Optional<Node> findByTreeIdAndNodeKey(Long treeId, String nodeKey);
    Optional<Node> findByTreeIdAndIsRootTrue(Long treeId);
    Optional<Node> findByTreeIdAndIsFallbackTrue(Long treeId);
}

// ─── Session ───────────────────────────────────────────────────────────────
public interface SessionRepository extends JpaRepository<Session, Long> {

    @Query("""
        SELECT s FROM Session s
        JOIN FETCH s.tree t
        LEFT JOIN FETCH t.nodes n
        LEFT JOIN FETCH n.outgoingEdges e
        LEFT JOIN FETCH e.toNode
        LEFT JOIN FETCH n.action a
        LEFT JOIN FETCH a.onSuccessNode
        LEFT JOIN FETCH a.onFailureNode
        JOIN FETCH s.currentNode cn
        WHERE s.sessionToken = :token
    """)
    Optional<Session> findBySessionTokenWithRelations(@Param("token") String token);

    Optional<Session> findBySessionToken(String token);
}

// ─── ConversationMessage ───────────────────────────────────────────────────
public interface ConversationMessageRepo extends JpaRepository<ConversationMessage, Long> {

    @Query("SELECT m FROM ConversationMessage m WHERE m.session.sessionToken = :token ORDER BY m.sentAt ASC")
    java.util.List<ConversationMessage> findBySessionToken(@Param("token") String token);
}
