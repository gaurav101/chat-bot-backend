package com.chatbot.repository;

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

