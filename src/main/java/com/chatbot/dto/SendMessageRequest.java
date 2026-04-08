package com.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendMessageRequest {
    @NotBlank(message = "sessionToken is required")
    private String sessionToken;

    @NotBlank(message = "message is required")
    @Size(max = 2000, message = "message too long")
    private String message;
}
