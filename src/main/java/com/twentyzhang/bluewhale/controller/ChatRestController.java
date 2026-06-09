package com.twentyzhang.bluewhale.controller;

import com.twentyzhang.bluewhale.common.AuthUser;
import com.twentyzhang.bluewhale.common.Result;
import com.twentyzhang.bluewhale.dto.chat.ChatMessageResponse;
import com.twentyzhang.bluewhale.dto.chat.ChatSessionItemResponse;
import com.twentyzhang.bluewhale.dto.chat.ClaimResponse;
import com.twentyzhang.bluewhale.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatRestController {

    private final ChatService chatService;

    @GetMapping("/sessions")
    public Result<List<ChatSessionItemResponse>> listSessions() {
        return Result.success(chatService.listSessions(currentUser()));
    }

    @GetMapping("/sessions/{id}/messages")
    public Result<List<ChatMessageResponse>> getMessages(
            @PathVariable Long id,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(chatService.getMessages(currentUser(), id, before, size));
    }

    @PostMapping("/sessions/{id}/claim")
    public Result<ClaimResponse> claim(@PathVariable Long id) {
        return Result.success(chatService.claim(currentUser(), id));
    }

    @PostMapping("/sessions/{id}/release")
    public Result<Void> release(@PathVariable Long id) {
        chatService.release(currentUser(), id);
        return Result.success();
    }

    private AuthUser currentUser() {
        return (AuthUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
