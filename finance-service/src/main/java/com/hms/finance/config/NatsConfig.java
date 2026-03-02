package com.hms.finance.config;

import io.nats.client.*;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Set;

@Slf4j
@Configuration
public class NatsConfig {

    public static final String SUBJECT_BILLING_OPD   = "billing.opd";
    public static final String SUBJECT_BILLING_WOUND  = "billing.wound";
    public static final String SUBJECT_BILLING_WARD   = "billing.ward";
    public static final String SUBJECT_BILLING_LAB    = "billing.lab";

    private static final Set<String> EXPECTED_BILLING_FILTERS = Set.of(
            SUBJECT_BILLING_OPD, SUBJECT_BILLING_WOUND,
            SUBJECT_BILLING_WARD, SUBJECT_BILLING_LAB);

    @Value("${nats.url:nats://localhost:4222}")
    private String natsUrl;

    private Connection connection;

    @Bean
    public Connection natsConnection() throws IOException, InterruptedException {
        Options options = new Options.Builder()
                .server(natsUrl)
                .connectionName("finance-service")
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
                    .name("BILLING")
                    .subjects("billing.>")
                    .storageType(StorageType.File)
                    .retentionPolicy(RetentionPolicy.WorkQueue)
                    .build());
            cleanupConflictingBillingConsumers(jsm);
        } catch (Exception e) {
            log.error("Failed to setup NATS streams", e);
        }
    }

    /**
     * WorkQueue streams require every consumer to have a unique, non-overlapping
     * filter subject. If a stale consumer exists without a filter (or with a
     * filter that doesn't match an expected billing subject), delete it so that
     * the filtered durable consumers can be created cleanly on startup.
     * Messages are NOT lost — they are re-delivered to the new consumers.
     */
    private void cleanupConflictingBillingConsumers(JetStreamManagement jsm) {
        try {
            List<String> names = jsm.getConsumerNames("BILLING");
            for (String name : names) {
                try {
                    ConsumerInfo info = jsm.getConsumerInfo("BILLING", name);
                    String filter = info.getConsumerConfiguration().getFilterSubject();
                    if (filter == null || !EXPECTED_BILLING_FILTERS.contains(filter)) {
                        jsm.deleteConsumer("BILLING", name);
                        log.info("Deleted conflicting BILLING consumer '{}' (filter was: {})", name, filter);
                    }
                } catch (Exception e) {
                    log.warn("Could not inspect/delete BILLING consumer '{}': {}", name, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Could not list BILLING consumers for cleanup: {}", e.getMessage());
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
