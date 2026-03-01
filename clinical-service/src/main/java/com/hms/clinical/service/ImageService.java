package com.hms.clinical.service;

import com.hms.clinical.dto.SessionImageDto;
import com.hms.clinical.entity.Session;
import com.hms.clinical.entity.SessionImage;
import com.hms.clinical.exception.BusinessException;
import com.hms.clinical.exception.ResourceNotFoundException;
import com.hms.clinical.repository.SessionImageRepository;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageService {

    private final SessionImageRepository sessionImageRepository;
    private final SessionService sessionService;
    private final MinioClient minioClient;

    @Value("${minio.bucketName}")
    private String bucketName;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png");

    @Transactional
    public SessionImageDto uploadImage(UUID sessionId, MultipartFile file, String caption, String imageType) {
        Session session = sessionService.getSessionEntity(sessionId);

        if ("completed".equals(session.getStatus())) {
            throw new BusinessException("Cannot upload images to a completed session");
        }

        // Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 10MB limit");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Only JPEG and PNG images are accepted");
        }

        String imageId = UUID.randomUUID().toString();
        String extension = file.getContentType().equals("image/png") ? ".png" : ".jpg";
        String objectKey = "clinical/" + sessionId + "/" + imageId + extension;

        try {
            // Ensure bucket exists
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }

            // Upload to MinIO
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            // Generate pre-signed URL
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(15, TimeUnit.MINUTES)
                            .build());

            // Save metadata
            SessionImage img = new SessionImage();
            img.setSession(session);
            img.setFileUrl(objectKey); // Store object key, generate URL on read
            img.setCaption(caption);
            img.setImageType(imageType != null ? imageType : "scan");

            SessionImage saved = sessionImageRepository.save(img);

            SessionImageDto dto = toDto(saved);
            dto.setFileUrl(presignedUrl);
            return dto;

        } catch (Exception e) {
            log.error("Failed to upload image to MinIO", e);
            throw new RuntimeException("Failed to upload image: " + e.getMessage());
        }
    }

    public List<SessionImageDto> getImages(UUID sessionId) {
        return sessionImageRepository.findBySessionId(sessionId).stream()
                .map(img -> {
                    SessionImageDto dto = toDto(img);
                    dto.setFileUrl(generatePresignedUrl(img.getFileUrl()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteImage(UUID sessionId, UUID imageId) {
        SessionImage img = sessionImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));

        if (!img.getSession().getId().equals(sessionId)) {
            throw new ResourceNotFoundException("Image does not belong to session: " + sessionId);
        }

        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(img.getFileUrl())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to delete image from MinIO: {}", e.getMessage());
        }

        sessionImageRepository.delete(img);
    }

    private String generatePresignedUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(15, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            log.warn("Failed to generate pre-signed URL for: {}", objectKey);
            return objectKey;
        }
    }

    private SessionImageDto toDto(SessionImage img) {
        return SessionImageDto.builder()
                .id(img.getId())
                .sessionId(img.getSession().getId())
                .fileUrl(img.getFileUrl())
                .caption(img.getCaption())
                .imageType(img.getImageType())
                .uploadedAt(img.getUploadedAt())
                .build();
    }
}
