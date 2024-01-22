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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SpannerClient {
    private static String INSTANCE_ID;
    private static String DATABASE_ID;

    private static String TABLE_NAME;

    private final NumberGenerator keySequence;
    private final static String COLUMN_NAME = "field0";
    private final static String COLUMN_VALUE = "1000";

    // Single Spanner client per process.
    private Spanner spanner = null;

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

    private final Properties properties;

    private Integer batchInserts;
    AtomicInteger actualOpCount = new AtomicInteger(0);
    private static final Logger LOGGER = Logger.getLogger(SpannerClient.class.getName());
    String PRIMARY_KEY_COLUMN = "id";

    // count the number of AbortedException occurred in this thread
    private int abortedExceptionCount = 0;


    public SpannerClient(Properties properties, NumberGenerator keySequence) {
        this.properties = properties;
        this.keySequence = keySequence;
    }

    public void init() {
        synchronized (CLASS_LOCK) {
            INSTANCE_ID = properties.getProperty("cloudspanner.instance");
            DATABASE_ID = properties.getProperty("cloudspanner.database");
            TABLE_NAME = properties.getProperty("cloudspanner.table", "usertable");
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

    public boolean doInsert() {
        long keyNum = keySequence.nextValue();
        String dbKey = "user" + keyNum;
        Status status = this.insert(dbKey);
        if (status == Status.OK || status == Status.BATCHED_OK) {
            actualOpCount.addAndGet(1);
            return true;
        } else {
            LOGGER.info("doinsert() returning false...");
            return false;
        }
    }

    public Status insert(String key) {
        if (bufferedMutations.size() < batchInserts) {
            Mutation.WriteBuilder m = Mutation.newInsertOrUpdateBuilder(TABLE_NAME);
            m.set(PRIMARY_KEY_COLUMN).to(key);
            m.set(COLUMN_NAME).to(COLUMN_VALUE);
            bufferedMutations.add(m.build());
        } else {
            LOGGER.log(Level.INFO, "Limit of cached mutations reached. The given mutation with key " + key +
                    " is ignored.");
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
            // there are some mutations left and was not batched, insert them together
            if (!bufferedMutations.isEmpty()) {
                // manually start the transaction
                transactionManager = dbClient.transactionManager();
                tx = transactionManager.begin();
                tx.buffer(bufferedMutations);
                transactionManager.commit();
                bufferedMutations.clear();
            }
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "cleanup()", e);
            throw new RuntimeException("Error in init phase: ", e);
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
            abortedExceptionCount++;
//            LOGGER.info( "\n-------------------------------------\n" + Thread.currentThread().getName()
//                    + "    Aborted Exception occurred.\nStack Trace:\n"
//                    + Arrays.toString(e.getStackTrace()) + "\n");
            LOGGER.log(Level.INFO, "\n-------------------------------------\n" +
                    Thread.currentThread().getName() + " - Aborted Exception occurred.\n", e);
            throw new RuntimeException("Error in commit: ", e);
        }
    }

    public void abort() {
        LOGGER.info(Thread.currentThread().getName()+ "    aborted, Size of mutation buffers array: " + bufferedMutations.size());
        transactionManager.close();
    }
}
