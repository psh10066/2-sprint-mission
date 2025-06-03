package com.sprint.mission.discodeit.storage;

import com.sprint.mission.discodeit.dto.binaryContent.BinaryContentDto;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "discodeit.storage.type", havingValue = "s3")
public class S3BinaryContentStorage implements BinaryContentStorage {

    @Value("${discodeit.storage.s3.bucket}")
    private String bucket;

    @Value("${discodeit.storage.s3.presigned-url-expiration}")
    private int presignedUrlExpiration;

    private final S3Client s3;
    private final S3Presigner presigner;

    @Override
    public UUID put(UUID binaryContentId, byte[] bytes) {
        String key = toKey(binaryContentId);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        s3.putObject(putRequest, RequestBody.fromBytes(bytes));

        return binaryContentId;
    }

    public InputStream get(UUID binaryContentId) {
        String key = toKey(binaryContentId);

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        return s3.getObject(getRequest);
    }

    @Override
    public UUID delete(UUID binaryContentId) {
        String key = toKey(binaryContentId);

        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        s3.deleteObject(deleteRequest);

        return binaryContentId;
    }

    @Override
    public ResponseEntity<Void> download(BinaryContentDto binaryContentDto) {
        String url = generatePresignedUrl(toKey(binaryContentDto.id()), binaryContentDto.contentType());

        HttpHeaders headers = new HttpHeaders();
        headers.add("Location", url);

        return ResponseEntity.status(302)
                .headers(headers)
                .build();
    }

    private String generatePresignedUrl(String key, String contentType) {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .responseContentType(contentType)
                    .build();

            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(
                    GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofSeconds(presignedUrlExpiration))
                            .getObjectRequest(getObjectRequest)
                            .build());

            return presignedRequest.url().toString();
    }

    private String toKey(UUID id) {
        return "binary/" + id;
    }
}
