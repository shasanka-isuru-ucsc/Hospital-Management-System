package com.hms.finance.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.buckets.prescriptions}")
    private String prescriptionsBucket;

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

    public void uploadPdf(String objectName, byte[] pdfBytes) {
        try {
            ensureBucketExists(prescriptionsBucket);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(prescriptionsBucket)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(pdfBytes), pdfBytes.length, -1)
                            .contentType("application/pdf")
                            .build());
            log.info("Uploaded PDF to MinIO: {}/{}", prescriptionsBucket, objectName);
        } catch (Exception e) {
            log.error("Failed to upload PDF to MinIO: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload prescription PDF", e);
        }
    }

    public String generatePresignedUrl(String objectName, int expiryMinutes) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(prescriptionsBucket)
                            .object(objectName)
                            .method(Method.GET)
                            .expiry(expiryMinutes, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for {}: {}", objectName, e.getMessage(), e);
            throw new RuntimeException("Failed to generate prescription download URL", e);
        }
    }
}
