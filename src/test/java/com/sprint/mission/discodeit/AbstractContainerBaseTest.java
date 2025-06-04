package com.sprint.mission.discodeit;

import com.sprint.mission.discodeit.config.AppConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Import(AppConfig.class)
@ActiveProfiles("container")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AbstractContainerBaseTest.TestContainersConfiguration.class)
public abstract class AbstractContainerBaseTest {

    private static final PostgreSQLContainer<?> POSTGRES_CONTAINER = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15"))
        .withDatabaseName("testdb")
        .withUsername("testuser")
        .withPassword("testpass")
//        .withInitScript("schema.sql"); // resources 폴더 기준이어서 사용하기 어려움
        .withCopyFileToContainer(
            MountableFile.forHostPath("schema.sql"),
            "/docker-entrypoint-initdb.d/schema.sql"
        );

    private static final LocalStackContainer LOCAL_STACK_CONTAINER = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.5.0"))
        .withServices(
            LocalStackContainer.Service.S3
        );

    static {
        POSTGRES_CONTAINER.start();
        LOCAL_STACK_CONTAINER.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.driver-class-name", POSTGRES_CONTAINER::getDriverClassName);
        registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);

        registry.add("discodeit.storage.s3.access-key", LOCAL_STACK_CONTAINER::getAccessKey);
        registry.add("discodeit.storage.s3.secret-key", LOCAL_STACK_CONTAINER::getSecretKey);
        registry.add("discodeit.storage.s3.region", LOCAL_STACK_CONTAINER::getRegion);
    }

    @TestConfiguration
    static class TestContainersConfiguration {

        @Bean
        public S3Client s3Client() {
            return S3Client.builder()
                .endpointOverride(LOCAL_STACK_CONTAINER.getEndpointOverride(LocalStackContainer.Service.S3))
                .region(Region.of(LOCAL_STACK_CONTAINER.getRegion()))
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                            LOCAL_STACK_CONTAINER.getAccessKey(),
                            LOCAL_STACK_CONTAINER.getSecretKey()
                        )
                    )
                )
                .build();
        }

        /**
         * 초기 버킷 생성
         */
        @Bean
        public CommandLineRunner createInitialBucket(
            S3Client s3Client,
            @Value("${discodeit.storage.s3.bucket}") String bucket
        ) {
            return args -> s3Client.createBucket(builder -> builder.bucket(bucket));
        }
    }
}