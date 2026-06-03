package com.healthlife.mental.support.controller;

import com.healthlife.common.security.SecurityUtils;
import com.healthlife.mental.support.dto.SupportChatRequest;
import com.healthlife.mental.support.dto.SupportChatResponse;
import com.healthlife.mental.support.service.SupportChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Встроенная служба поддержки (Claude API). */
@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
@Validated
public class SupportController {

    private final SupportChatService supportChatService;

    @PostMapping("/chat")
    public ResponseEntity<SupportChatResponse> chat(@Valid @RequestBody SupportChatRequest request) {
        var userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(supportChatService.chat(userId, request));
    }
}
