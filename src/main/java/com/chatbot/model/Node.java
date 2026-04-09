package com.chatbot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @OneToMany(mappedBy = "fromNode", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @OrderBy("priority DESC")
    private Set<Edge> outgoingEdges ;

    @OneToOne(mappedBy = "node", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private NodeAction action;

    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
}
