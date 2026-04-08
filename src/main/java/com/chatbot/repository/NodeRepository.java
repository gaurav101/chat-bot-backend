package com.chatbot.repository;

import com.chatbot.model.Node;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

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
