package com.chatbot.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.*;

// ─────────────────────────────────────────────────────────────
// ConversationMessage
// ─────────────────────────────────────────────────────────────
@Entity @Table(name = "conversation_messages")
@Getter @Setter @NoArgsConstructor
public class ConversationMessage {

    public enum Direction { INBOUND, OUTBOUND }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Direction direction;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String messageText;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "node_id")
    private Node node;

    @Column(length = 100) private String intentDetected;
    @Column(precision = 5, scale = 4) private Double confidence;

    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "JSON")
    private Map<String, Object> entitiesJson;

    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "JSON")
    private Map<String, Object> rawNlpResponse;

    private LocalDateTime sentAt = LocalDateTime.now();
}
