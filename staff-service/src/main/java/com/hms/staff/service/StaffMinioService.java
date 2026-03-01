package com.hms.staff.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffMinioService {

    private final MinioClient minioClient;

    @Value("${minio.buckets.avatars}")
    private String avatarsBucket;

    @Value("${minio.buckets.banners}")
    private String bannersBucket;

    public void ensureBucketExists(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            log.error("Failed to ensure bucket '{}' exists: {}", bucket, e.getMessage(), e);
            throw new RuntimeException("MinIO bucket setup failed", e);
        }
    }

    public void uploadAvatar(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            ensureBucketExists(avatarsBucket);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(avatarsBucket)
                            .object(objectKey)
                            .stream(inputStream, size, -1)
                            .contentType(contentType != null ? contentType : "image/jpeg")
                            .build());
            log.info("Uploaded avatar to MinIO: {}/{}", avatarsBucket, objectKey);
        } catch (Exception e) {
            log.error("Failed to upload avatar: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload avatar", e);
        }
    }

    public void uploadBanner(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            ensureBucketExists(bannersBucket);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bannersBucket)
                            .object(objectKey)
                            .stream(inputStream, size, -1)
                            .contentType(contentType != null ? contentType : "image/jpeg")
                            .build());
            log.info("Uploaded banner to MinIO: {}/{}", bannersBucket, objectKey);
        } catch (Exception e) {
            log.error("Failed to upload banner: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload banner", e);
        }
    }

    public String generateAvatarPresignedUrl(String objectKey, int expiryMinutes) {
        return generatePresignedUrl(avatarsBucket, objectKey, expiryMinutes);
    }

    public String generateBannerPresignedUrl(String objectKey, int expiryMinutes) {
        return generatePresignedUrl(bannersBucket, objectKey, expiryMinutes);
    }

    private String generatePresignedUrl(String bucket, String objectKey, int expiryMinutes) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .method(Method.GET)
                            .expiry(expiryMinutes, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            log.warn("Failed to generate presigned URL for {}/{}: {}", bucket, objectKey, e.getMessage());
            return null;
        }
    }
}
