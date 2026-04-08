package com.chatbot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

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
