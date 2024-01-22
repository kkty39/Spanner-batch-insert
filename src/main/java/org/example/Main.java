package org.example;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static Integer threadCount;
    // number of operations
    private static Integer opsCount;

    private static final Properties properties  = new Properties();

    private static NumberGenerator keySequence;

    private static void initProperties() {
        try (InputStream inputStream = Main.class.getResourceAsStream("/application.properties")) {
            properties.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }

        threadCount = Integer.parseInt(properties.getProperty("threadcount"));
        opsCount = Integer.parseInt(properties.getProperty("opsCount", "1"));
        LOGGER.info("Thread count: " + threadCount);
        LOGGER.info("Operation count: " + opsCount);
    }


    public static void main(String[] args) {
        // get properties
        initProperties();

        keySequence = new NumberGenerator(0L);

        // init each clientThread
        final List<ClientThread> clients = getClientThreads();

        // actual operations
        final Map<Thread, ClientThread> threads = new HashMap<>(threadCount);
        for (ClientThread client : clients) {
            Thread t = new Thread(client);
            threads.put(t, client);
        }

        for (Thread t : threads.keySet()) {
            t.start();
        }

        // wrap up
        int opsDone = 0;
        for (Map.Entry<Thread, ClientThread> entry : threads.entrySet()) {
            try {
                entry.getKey().join();
                opsDone += entry.getValue().getOpsDone();
            } catch (InterruptedException ignored) {
                // ignored
            }
        }
        LOGGER.info(opsDone + " operations done...");
        LOGGER.info("Exiting application...");

//        System.exit(0);
    }

    private static List<ClientThread> getClientThreads() {
        final List<ClientThread> clients = new ArrayList<>(threadCount);
        for (int threadid = 0; threadid < threadCount; threadid++) {
            SpannerClient spannerClient = new SpannerClient(properties, keySequence);

            int threadopcount = opsCount / threadCount;

            // ensure correct number of operations, in case opsCount is not a multiple of threadCount
            if (threadid < opsCount % threadCount) {
                ++threadopcount;
            }

            ClientThread t = new ClientThread(spannerClient, threadopcount);
            clients.add(t);
        }
        return clients;
    }
}