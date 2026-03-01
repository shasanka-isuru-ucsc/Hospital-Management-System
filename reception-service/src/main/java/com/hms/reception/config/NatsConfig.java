package com.hms.reception.config;

import io.nats.client.*;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import java.io.IOException;

@Slf4j
@Configuration
public class NatsConfig {

    public static final String SUBJECT_QUEUE_UPDATED = "queue.updated";

    @Value("${nats.url:nats://localhost:4222}")
    private String natsUrl;

    private Connection connection;

    @Bean
    public Connection natsConnection() throws IOException, InterruptedException {
        Options options = new Options.Builder()
                .server(natsUrl)
                .connectionName("reception-service")
                .build();
        connection = Nats.connect(options);
        setupStreams(connection);
        return connection;
    }

    @Bean
    public JetStream jetStream(Connection natsConnection) throws IOException {
        return natsConnection.jetStream();
    }

    private void setupStreams(Connection conn) {
        try {
            JetStreamManagement jsm = conn.jetStreamManagement();
            createStreamIfNotExists(jsm, StreamConfiguration.builder()
                    .name("QUEUE")
                    .subjects("queue.>")
                    .storageType(StorageType.File)
                    .retentionPolicy(RetentionPolicy.WorkQueue)
                    .build());
        } catch (Exception e) {
            log.error("Failed to setup NATS streams", e);
        }
    }

    private void createStreamIfNotExists(JetStreamManagement jsm, StreamConfiguration config) {
        try {
            jsm.addStream(config);
            log.info("Created NATS stream: {}", config.getName());
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("already in use")) {
                log.debug("NATS stream {} already exists", config.getName());
            } else {
                log.warn("Could not create NATS stream {}: {}", config.getName(), e.getMessage());
            }
        }
    }

    @PreDestroy
    public void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
