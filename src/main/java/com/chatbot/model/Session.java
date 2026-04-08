package com.chatbot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ─────────────────────────────────────────────────────────────
// Session
// ─────────────────────────────────────────────────────────────
@Entity @Table(name = "sessions")
@Getter @Setter @NoArgsConstructor
public class Session {

    public enum State { ACTIVE, COMPLETED, EXPIRED, ESCALATED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String sessionToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tree_id", nullable = false)
    private DecisionTree tree;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_node_id")
    private Node currentNode;

    @Column(nullable = false, length = 50) private String channel = "WEB";
    @Column(length = 200)                  private String userIdentifier;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")     private Map<String, Object> contextData = new HashMap<>();

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private State state = State.ACTIVE;

    private LocalDateTime startedAt    = LocalDateTime.now();
    private LocalDateTime lastActivity = LocalDateTime.now();
    private LocalDateTime expiresAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ConversationMessage> messages = new ArrayList<>();

    @PreUpdate void onUpdate() { lastActivity = LocalDateTime.now(); }
}
