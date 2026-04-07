package com.chatbot.controller;

import com.chatbot.service.ChatService;
import com.chatbot.service.ChatService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ChatController
 *
 * REST API for the Decision Tree Chat backend.
 *
 * POST /api/chat/session          → start a new session
 * POST /api/chat/message          → send a message
 * GET  /api/chat/session/{token}  → get session history
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // ─── Start Session ───────────────────────────────────────────

    @PostMapping("/session")
    public ResponseEntity<ChatResponse> startSession(@Valid @RequestBody StartSessionRequest req) {
        ChatResponse response = chatService.startSession(req.getTreeId(), req.getChannel(), req.getUserIdentifier());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─── Send Message ────────────────────────────────────────────

    @PostMapping("/message")
    public ResponseEntity<ChatResponse> sendMessage(@Valid @RequestBody SendMessageRequest req) {
        ChatResponse response = chatService.sendMessage(req.getSessionToken(), req.getMessage());
        return ResponseEntity.ok(response);
    }

    // ─── Get History ─────────────────────────────────────────────

    @GetMapping("/session/{sessionToken}/history")
    public ResponseEntity<List<MessageDto>> getHistory(@PathVariable String sessionToken) {
        return ResponseEntity.ok(chatService.getHistory(sessionToken));
    }

    // ─── Global exception handling ───────────────────────────────

    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(java.util.NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal error: " + ex.getMessage()));
    }

    // ─── DTOs ────────────────────────────────────────────────────

    @Getter @Setter
    public static class StartSessionRequest {
        @NotNull(message = "treeId is required")
        private Long   treeId;
        private String channel        = "WEB";
        private String userIdentifier;
    }

    @Getter @Setter
    public static class SendMessageRequest {
        @NotBlank(message = "sessionToken is required")
        private String sessionToken;

        @NotBlank(message = "message is required")
        @Size(max = 2000, message = "message too long")
        private String message;
    }

    @Getter @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
    }
}
