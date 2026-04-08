package com.chatbot.repository;

import com.chatbot.model.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

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
