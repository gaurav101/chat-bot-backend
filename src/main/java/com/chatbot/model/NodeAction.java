package com.chatbot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

// ─────────────────────────────────────────────────────────────
// NodeAction
// ─────────────────────────────────────────────────────────────
@Entity @Table(name = "node_actions")
@Getter @Setter @NoArgsConstructor
public class NodeAction {

    public enum ActionType { HTTP_CALL, DB_QUERY, SEND_EMAIL, SEND_SMS, CUSTOM_BEAN }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false, unique = true)
    private Node node;

    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private ActionType actionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSON")
    private Map<String, Object> configJson;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "on_success_node")
    private Node onSuccessNode;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "on_failure_node")
    private Node onFailureNode;

    private int timeoutMs  = 5000;
    private int retryCount = 0;
    private LocalDateTime createdAt = LocalDateTime.now();
}
