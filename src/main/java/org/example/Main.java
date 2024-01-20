package org.example;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public class Main {
    private static String INSTANCE_ID;
    private static String DATABASE_ID;

    private static Integer threadcount;

    private static void initProperties() {
        Properties properties = new Properties();
        try {
            String path = Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("application.properties")).getPath();
            FileInputStream fileInputStream = new FileInputStream(path);
            properties.load(fileInputStream);
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        INSTANCE_ID = properties.getProperty("cloudspanner.instance");
        DATABASE_ID = properties.getProperty("cloudspanner.database");
        threadcount = Integer.parseInt(properties.getProperty("threadcount"));
    }

    public static void main(String[] args) {
        // get properties
        initProperties();
        final List<ClientThread> clients = new ArrayList<>(threadcount);
    }
}