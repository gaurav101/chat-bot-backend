package com.chatbot.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
