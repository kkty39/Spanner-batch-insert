package org.example;

import com.google.cloud.spanner.Mutation;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Properties;

public class SpannerClient {
    private static String INSTANCE_ID;
    private static String DATABASE_ID;

    // Static lock for the class.
    private static final Object CLASS_LOCK = new Object();

    // an array to store the mutations to be inserted as a batch
    private final ArrayList<Mutation> bufferedMutations = new ArrayList<>();

    private Properties properties;

    private Integer batchInserts;

    public SpannerClient(Properties properties) {
        this.properties = properties;
    }

    // todo
    public void init() {
        synchronized (CLASS_LOCK) {
            INSTANCE_ID = properties.getProperty("cloudspanner.instance");
            DATABASE_ID = properties.getProperty("cloudspanner.database");
            batchInserts = Integer.parseInt(properties.getProperty("cloudspanner.batchinserts", "1"));
        }
    }
}
