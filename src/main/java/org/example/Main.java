package org.example;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class Main {


    private static Integer threadCount;
    // number of operations
    private static Integer opsCount;

    private static Integer opsDone;

    private static Properties properties  = new Properties();

    private static void initProperties() {
        try {
            String path = Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                    .getResource("application.properties")).getPath();
            FileInputStream fileInputStream = new FileInputStream(path);
            properties.load(fileInputStream);
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        threadCount = Integer.parseInt(properties.getProperty("threadcount"));
        opsCount = Integer.parseInt(properties.getProperty("opsdCount", "1"));
    }

    public static void main(String[] args) {
        // get properties
        initProperties();

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
        opsDone = 0;
        for (Map.Entry<Thread, ClientThread> entry : threads.entrySet()) {
            try {
                entry.getKey().join();
                opsDone += entry.getValue().getOpsDone();
            } catch (InterruptedException ignored) {
                // ignored
            }
        }

        System.exit(0);
    }

    private static List<ClientThread> getClientThreads() {
        final List<ClientThread> clients = new ArrayList<>(threadCount);
        for (int threadid = 0; threadid < threadCount; threadid++) {
            SpannerClient spannerClient = new SpannerClient(properties);

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