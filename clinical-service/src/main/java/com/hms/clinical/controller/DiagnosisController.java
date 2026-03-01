package com.hms.clinical.controller;

import com.hms.clinical.dto.*;
import com.hms.clinical.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/sessions/{id}/images")
@RequiredArgsConstructor
public class DiagnosisController {

    private final ImageService imageService;

    @PostMapping
    public ResponseEntity<ApiResponse<SessionImageDto>> uploadImage(
            @PathVariable UUID id,
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "caption", required = false) String caption,
            @RequestParam(value = "imageType", defaultValue = "scan") String imageType) {

        SessionImageDto result = imageService.uploadImage(id, image, caption, imageType);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<SessionImageDto>builder().success(true).data(result).build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SessionImageDto>>> getImages(@PathVariable UUID id) {
        List<SessionImageDto> images = imageService.getImages(id);
        return ResponseEntity.ok(ApiResponse.<List<SessionImageDto>>builder().success(true).data(images).build());
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID id, @PathVariable UUID imageId) {
        imageService.deleteImage(id, imageId);
        return ResponseEntity.noContent().build();
    }
}
