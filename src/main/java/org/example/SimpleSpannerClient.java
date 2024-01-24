package org.example;

import com.google.cloud.spanner.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimpleSpannerClient {
    private static final Logger LOGGER = Logger.getLogger(SimpleSpannerClient.class.getName());
    private static final String COLUMN_NAME = "field0";
    private static final String COLUMN_VALUE = "1000";
    private static final String PRIMARY_KEY_COLUMN = "id";
    private static final AtomicLong keyCounter = new AtomicLong(0);
    private static Spanner spanner;
    private static DatabaseClient dbClient;
    private static String tableName;
    private static Integer threadCount;
    private static Integer opsCount;
    private static Integer batchInserts;

    public static void main(String[] args) {
        try {
            init();
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                Thread t = new Thread(SimpleSpannerClient::runClientOperations);
                threads.add(t);
                t.start();
            }
            for (Thread t : threads) {
                t.join();
            }
            LOGGER.info("All operations completed.");
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Thread interrupted", e);
        } finally {
            if (spanner != null) {
                spanner.close();
            }
        }
    }

    private static void init() {
        Properties properties = new Properties();
        try (InputStream inputStream = SimpleSpannerClient.class.getResourceAsStream("/application.properties")) {
            properties.load(inputStream);
            tableName = properties.getProperty("cloudspanner.table", "usertable");
            threadCount = Integer.parseInt(properties.getProperty("threadcount"));
            opsCount = Integer.parseInt(properties.getProperty("opsCount", "1"));
            batchInserts = Integer.parseInt(properties.getProperty("cloudspanner.batchinserts", "1"));
            String instanceId = properties.getProperty("cloudspanner.instance");
            String databaseId = properties.getProperty("cloudspanner.database");
            SpannerOptions options = SpannerOptions.newBuilder().setSessionPoolOption(SessionPoolOptions.newBuilder()
                    .setMinSessions(threadCount)
                    .build()).build();
            spanner = options.getService();
            dbClient = spanner.getDatabaseClient(DatabaseId.of(options.getProjectId(), instanceId, databaseId));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load properties", e);
        }
    }

    private static void runClientOperations() {
        int opsDone = 0;
        int abortedExceptionCount = 0;
        List<Mutation> remainingMutations = new ArrayList<>();

        while (opsDone < opsCount) {
            List<Mutation> mutations = null;
            try (TransactionManager transactionManager = dbClient.transactionManager()) {
                TransactionContext tx = transactionManager.begin();
                mutations = new ArrayList<>(remainingMutations);
                remainingMutations.clear();

                while (mutations.size() < batchInserts && opsDone < opsCount) {
                    String key = "user" + keyCounter.getAndIncrement();
                    mutations.add(Mutation.newInsertOrUpdateBuilder(tableName)
                            .set(PRIMARY_KEY_COLUMN).to(key)
                            .set(COLUMN_NAME).to(COLUMN_VALUE)
                            .build());
                    opsDone++;
                }

                tx.buffer(mutations);
                transactionManager.commit();
            } catch (AbortedException e) {
                abortedExceptionCount++;
                remainingMutations.addAll(mutations); // Save mutations for retry
                // Implement retry logic or handle the exception as needed
            }
        }

        if (!remainingMutations.isEmpty()) {
            // Handle any remaining mutations
            try (TransactionManager transactionManager = dbClient.transactionManager()) {
                TransactionContext tx = transactionManager.begin();
                tx.buffer(remainingMutations);
                transactionManager.commit();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error during cleanup phase", e);
            }
        }

        LOGGER.info(Thread.currentThread().getName() + " completed operations. Aborted exceptions: " + abortedExceptionCount);
    }
}
