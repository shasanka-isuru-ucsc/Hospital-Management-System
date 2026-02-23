package com.hms.reception.controller;

import com.hms.reception.dto.ApiResponse;
import com.hms.reception.entity.Token;
import com.hms.reception.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/queue")
@RequiredArgsConstructor
public class QueueController {

        private final TokenService tokenService;

        @GetMapping("/{type}")
        public ResponseEntity<ApiResponse<Map<String, Object>>> getQueue(
                        @PathVariable String type,
                        @RequestParam(required = false) UUID doctorId) {

                List<Token> tokens = tokenService.getQueueTokens(type, doctorId);

                Token currentlyServing = tokens.stream()
                                .filter(t -> "called".equals(t.getStatus()) || "in_progress".equals(t.getStatus()))
                                .findFirst()
                                .orElse(null);

                List<Token> waiting = tokens.stream()
                                .filter(t -> "waiting".equals(t.getStatus()))
                                .collect(Collectors.toList());

                long completedCount = tokens.stream()
                                .filter(t -> "completed".equals(t.getStatus()))
                                .count();

                Map<String, Object> responseData = Map.of(
                                "queueType", type,
                                "date", LocalDate.now(),
                                "currentlyServing", currentlyServing != null ? currentlyServing : Map.of(),
                                "waiting", waiting,
                                "completedCount", completedCount);

                return ResponseEntity.ok(ApiResponse.success(responseData));
        }
}
