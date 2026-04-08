package com.chatbot.repository;

import com.chatbot.model.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// ─── ConversationMessage ───────────────────────────────────────────────────
public interface ConversationMessageRepo extends JpaRepository<ConversationMessage, Long> {

    @Query("SELECT m FROM ConversationMessage m WHERE m.session.sessionToken = :token ORDER BY m.sentAt ASC")
    java.util.List<ConversationMessage> findBySessionToken(@Param("token") String token);
}
