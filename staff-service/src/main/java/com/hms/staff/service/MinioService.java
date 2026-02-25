package com.hms.staff.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.buckets.avatars}")
    private String avatarBucket;

    @Value("${minio.buckets.banners}")
    private String bannerBucket;

    public String uploadFile(MultipartFile file, String type) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            String bucket = "avatar".equalsIgnoreCase(type) ? avatarBucket : bannerBucket;
            String extension = getExtension(file.getOriginalFilename());
            String fileName = UUID.randomUUID() + extension;

            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(fileName)
                                .stream(inputStream, file.getSize(), -1)
                                .contentType(file.getContentType())
                                .build());
            }
            return fileName;
        } catch (Exception e) {
            log.error("Error uploading file to MinIO", e);
            throw new RuntimeException("Error uploading file to MinIO", e);
        }
    }

    public String getPresignedUrl(String objectKey, String type) {
        if (objectKey == null || objectKey.trim().isEmpty()) {
            return null;
        }
        try {
            String bucket = "avatar".equalsIgnoreCase(type) ? avatarBucket : bannerBucket;
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(15, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            log.error("Error generating presigned URL for MinIO object: {}", objectKey, e);
            return null;
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains("."))
            return "";
        return filename.substring(filename.lastIndexOf("."));
    }
}
