package com.chatbot.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.*;

// ─────────────────────────────────────────────────────────────
// DecisionTree
// ─────────────────────────────────────────────────────────────
@Entity @Table(name = "decision_trees")
@Getter @Setter @NoArgsConstructor
public class DecisionTree {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100) private String name;
    @Column(columnDefinition = "TEXT")      private String description;
    @Column(nullable = false, length = 20)  private String version = "1.0";
    @Column(nullable = false)               private boolean isActive = true;
    @Column(nullable = false, length = 10)  private String languageCode = "en";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
    private String createdBy;

    @OneToMany(mappedBy = "tree", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Node> nodes = new ArrayList<>();

    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}

// ─────────────────────────────────────────────────────────────
// Node
// ─────────────────────────────────────────────────────────────
@Entity @Table(name = "nodes")
@Getter @Setter @NoArgsConstructor
public class Node {

    public enum NodeType { MESSAGE, QUESTION, ACTION, NLP, END }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tree_id", nullable = false)
    private DecisionTree tree;

    @Column(nullable = false, length = 100) private String nodeKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)               private NodeType nodeType = NodeType.MESSAGE;
    @Column(nullable = false, columnDefinition = "TEXT") private String messageText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")      private Map<String, Object> metadata;

    private boolean isRoot     = false;
    private boolean isFallback = false;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "fromNode", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("priority DESC")
    private List<Edge> outgoingEdges = new ArrayList<>();

    @OneToOne(mappedBy = "node", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private NodeAction action;

    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}

// ─────────────────────────────────────────────────────────────
// Edge
// ─────────────────────────────────────────────────────────────
@Entity @Table(name = "edges")
@Getter @Setter @NoArgsConstructor
public class Edge {

    public enum MatchType { EXACT, CONTAINS, REGEX, INTENT, ENTITY, CONDITION, DEFAULT }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tree_id", nullable = false)
    private DecisionTree tree;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_node_id", nullable = false)
    private Node fromNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_node_id", nullable = false)
    private Node toNode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)               private MatchType matchType = MatchType.EXACT;
    @Column(length = 500)                   private String matchValue;
    private int priority = 0;
    private boolean isActive = true;
    private LocalDateTime createdAt = LocalDateTime.now();
}

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
