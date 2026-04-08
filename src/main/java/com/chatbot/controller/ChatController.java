package com.chatbot.controller;

import com.chatbot.service.ChatService;
import com.chatbot.service.ChatService.*;
import jakarta.validation.Valid;
import lombok.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.chatbot.dto.*;
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

}
