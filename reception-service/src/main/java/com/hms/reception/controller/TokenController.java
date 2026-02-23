package com.hms.reception.controller;

import com.hms.reception.dto.ApiResponse;
import com.hms.reception.dto.TokenCreateRequest;
import com.hms.reception.dto.TokenStatusUpdate;
import com.hms.reception.entity.Token;
import com.hms.reception.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/tokens")
@RequiredArgsConstructor
public class TokenController {

    private final TokenService tokenService;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> issueToken(
            @Valid @RequestBody TokenCreateRequest request,
            HttpServletRequest httpRequest) {

        String userIdStr = (String) httpRequest.getAttribute("userId");
        UUID issuedBy = userIdStr != null ? UUID.fromString(userIdStr) : null;

        Token token = tokenService.issueToken(request.getPatientId(), request.getQueueType(), request.getDoctorId(),
                issuedBy);
        long queuePosition = tokenService.getQueuePosition(token) + 1; // +1 since it's position

        Map<String, Object> responseData = Map.of(
                "id", token.getId(),
                "tokenNumber", token.getTokenNumber(),
                "patientName", token.getPatient().getFirstName() + " " + token.getPatient().getLastName(),
                "queueType", token.getQueueType(),
                "status", token.getStatus(),
                "queuePosition", queuePosition);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(responseData));
    }

    @GetMapping("/today")
    public ResponseEntity<ApiResponse<List<Token>>> getTodayTokens() {
        List<Token> tokens = tokenService.getTodayTokens();
        return ResponseEntity.ok(ApiResponse.success(tokens));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Token>> updateTokenStatus(
            @PathVariable UUID id,
            @Valid @RequestBody TokenStatusUpdate update) {

        Token updated = tokenService.updateTokenStatus(id, update.getStatus());
        return ResponseEntity.ok(ApiResponse.success(updated));
    }
}
