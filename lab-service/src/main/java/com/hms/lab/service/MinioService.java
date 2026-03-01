package com.hms.lab.service;

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
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.buckets.lab-reports}")
    private String labReportsBucket;

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

    public void uploadReport(String objectKey, InputStream inputStream, long size) {
        try {
            ensureBucketExists(labReportsBucket);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(labReportsBucket)
                            .object(objectKey)
                            .stream(inputStream, size, -1)
                            .contentType("application/pdf")
                            .build());
            log.info("Uploaded report to MinIO: {}/{}", labReportsBucket, objectKey);
        } catch (Exception e) {
            log.error("Failed to upload report to MinIO: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload lab report", e);
        }
    }

    public String generatePresignedUrl(String objectKey, int expiryMinutes) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(labReportsBucket)
                            .object(objectKey)
                            .method(Method.GET)
                            .expiry(expiryMinutes, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for {}: {}", objectKey, e.getMessage(), e);
            throw new RuntimeException("Failed to generate report download URL", e);
        }
    }
}
