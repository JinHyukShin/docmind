package com.docmind.document.service;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final MinioClient minioClient;
    private final String bucketName;

    public FileStorageService(MinioClient minioClient,
                              @Value("${minio.bucket-name}") String bucketName) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
    }

    private volatile boolean bucketInitialized = false;

    /**
     * 애플리케이션 시작 시 MinIO 버킷이 없으면 자동 생성한다.
     * MinIO 연결 실패 시에도 앱이 죽지 않도록 경고 로그만 남기고 계속 진행한다.
     * 업로드 시점에 버킷 존재 여부를 재확인한다.
     */
    @PostConstruct
    public void initBucket() {
        try {
            ensureBucketExists();
            bucketInitialized = true;
        } catch (Exception e) {
            log.warn("MinIO is not available at startup. Bucket '{}' will be created on first upload. Cause: {}",
                    bucketName, e.getMessage());
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucketName).build());
            log.info("MinIO bucket '{}' created", bucketName);
        }
    }

    /**
     * 파일을 MinIO에 업로드한다.
     *
     * @param file   업로드할 파일
     * @param userId 소유자 ID (경로 분리용)
     * @return MinIO 내 저장 경로 (예: "users/1/uuid-filename.pdf")
     */
    public String upload(MultipartFile file, Long userId) {
        // 업로드 시점에 버킷 존재 여부 재확인 (시작 시 MinIO 연결 실패 대비)
        if (!bucketInitialized) {
            try {
                ensureBucketExists();
                bucketInitialized = true;
            } catch (Exception e) {
                log.error("Failed to ensure MinIO bucket '{}' exists before upload", bucketName, e);
                throw new RuntimeException("MinIO bucket initialization failed", e);
            }
        }

        String storedPath = buildStoredPath(userId, file.getOriginalFilename());
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storedPath)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());
            log.debug("File uploaded to MinIO: {}", storedPath);
            return storedPath;
        } catch (Exception e) {
            log.error("Failed to upload file to MinIO: {}", storedPath, e);
            throw new RuntimeException("File upload failed", e);
        }
    }

    /**
     * MinIO에서 파일을 다운로드한다.
     *
     * @param storedPath MinIO 내 저장 경로
     * @return 파일 InputStream (호출자가 닫아야 함)
     */
    public InputStream download(String storedPath) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storedPath)
                            .build());
        } catch (Exception e) {
            log.error("Failed to download file from MinIO: {}", storedPath, e);
            throw new RuntimeException("File download failed", e);
        }
    }

    /**
     * MinIO에서 파일을 삭제한다.
     *
     * @param storedPath MinIO 내 저장 경로
     */
    public void delete(String storedPath) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storedPath)
                            .build());
            log.debug("File deleted from MinIO: {}", storedPath);
        } catch (Exception e) {
            log.error("Failed to delete file from MinIO: {}", storedPath, e);
            throw new RuntimeException("File deletion failed", e);
        }
    }

    private String buildStoredPath(Long userId, String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        return "users/" + userId + "/" + UUID.randomUUID() + extension;
    }
}
