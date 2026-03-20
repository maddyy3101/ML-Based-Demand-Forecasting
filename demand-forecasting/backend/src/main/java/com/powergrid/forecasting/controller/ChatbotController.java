package com.powergrid.forecasting.controller;

import com.powergrid.forecasting.dto.ChatRequestDto;
import com.powergrid.forecasting.dto.ChatResponseDto;
import com.powergrid.forecasting.service.ChatbotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/message")
    public Mono<ResponseEntity<ChatResponseDto>> handleChatMessage(@RequestBody ChatRequestDto request) {
        return chatbotService.getChatbotResponse(request.getMessage(), request.getHistory())
                .map(response -> {
                    List<String> suggestions = chatbotService.generateSuggestedQuestions(response);
                    return ResponseEntity.ok(ChatResponseDto.builder()
                            .response(response)
                            .suggestions(suggestions)
                            .build());
                });
    }
}
