package com.chatbot.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StartSessionRequest {
    @NotNull(message = "treeId is required")
    private Long treeId;
    private String channel = "WEB";
    private String userIdentifier;
}
