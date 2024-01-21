package org.example;

import com.google.cloud.spanner.AbortedException;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.SessionPoolOptions;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import com.google.cloud.spanner.TransactionContext;
import com.google.cloud.spanner.TransactionManager;
import com.google.protobuf.ByteString;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SpannerClient {
    private static String INSTANCE_ID;
    private static String DATABASE_ID;

    // Single Spanner client per process.
    private static Spanner spanner = null;

    // Single database client per process.
    private static DatabaseClient dbClient = null;

    // Static lock for the class.
    private static final Object CLASS_LOCK = new Object();

    // an array to store the mutations to be inserted as a batch
    private final ArrayList<Mutation> bufferedMutations = new ArrayList<>();

    // Create the transaction manager in start() before operations starts.
    private TransactionManager transactionManager = null;

    // Used for executing operations in transactions.
    private TransactionContext tx = null;

    private Properties properties;

    private Integer batchInserts;
    private static final Logger LOGGER = Logger.getLogger(SpannerClient.class.getName());
    String PRIMARY_KEY_COLUMN = "id";

    public SpannerClient(Properties properties) {
        this.properties = properties;
    }

    public void init() {
        synchronized (CLASS_LOCK) {
            INSTANCE_ID = properties.getProperty("cloudspanner.instance");
            DATABASE_ID = properties.getProperty("cloudspanner.database");
            batchInserts = Integer.parseInt(properties.getProperty("cloudspanner.batchinserts", "1"));
            int threadCount = Integer.parseInt(properties.getProperty("threadcount"));

            try {
                SpannerOptions options = SpannerOptions.newBuilder().setSessionPoolOption(SessionPoolOptions.newBuilder()
                        .setMinSessions(threadCount)
                        .build()).build();
                spanner = options.getService();
                Runtime.getRuntime().addShutdownHook(new Thread("spannerShutdown") {
                    @Override
                    public void run() {
                        spanner.close();
                    }
                });

                dbClient = spanner.getDatabaseClient(DatabaseId.of(options.getProjectId(), INSTANCE_ID, DATABASE_ID));
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "init()", e);
            }

        }
    }

    public Status insert(String table, String key, Map<String, ByteString.ByteIterator> values) {
        if (bufferedMutations.size() < batchInserts) {
            Mutation.WriteBuilder m = Mutation.newInsertOrUpdateBuilder(table);
            m.set(PRIMARY_KEY_COLUMN).to(key);
            for (Map.Entry<String, ByteString.ByteIterator> e : values.entrySet()) {
                m.set(e.getKey()).to(e.getValue().toString());
            }
            bufferedMutations.add(m.build());
        } else {
            LOGGER.log(Level.INFO, "Limit of cached mutations reached. The given mutation with key " + key +
                    " is ignored. Is this a retry?");
        }
        if (bufferedMutations.size() < batchInserts) {
            return Status.BATCHED_OK;
        }
        try {
            tx.buffer(bufferedMutations);
            bufferedMutations.clear();
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "insert()", e);
            return Status.ERROR;
        }
        return Status.OK;
    }

    public void cleanup() {
        try {
            if (bufferedMutations.size() > 0) {
                transactionManager = dbClient.transactionManager();
                tx = transactionManager.begin();
                tx.buffer(bufferedMutations);
                transactionManager.commit();
                bufferedMutations.clear();
            }
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "cleanup()", e);
        }
    }

    public void start() {
        transactionManager = dbClient.transactionManager();
        tx = transactionManager.begin();
    }

    public void commit() {
        try {
            transactionManager.commit();
        } catch (AbortedException e) {
            // todo
        }
    }

    public void abort() {
        LOGGER.info("Size of mutation buffers array: " + bufferedMutations.size());
        transactionManager.close();
    }
}
