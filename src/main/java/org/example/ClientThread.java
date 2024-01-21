package org.example;

public class ClientThread implements Runnable {
    private final SpannerClient spannerClient;
    // number of operations
    private final int opsCount;
    // number of operations done
    private int opsDone;

    public ClientThread(SpannerClient spannerClient, int opsCount) {
        this.spannerClient = spannerClient;
        this.opsCount = opsCount;
    }

    public int getOpsDone() {
        return opsDone;
    }

    @Override
    public void run() {
        try {
            spannerClient.init();
        } catch (Exception e) {
            e.printStackTrace();
            e.printStackTrace(System.out);
            return;
        }


        while (opsDone < opsCount) {
            try {
                spannerClient.start();
                if (spannerClient.doInsert()) {
                    spannerClient.commit();
                }
            } catch (Exception e) {
                e.printStackTrace();
                spannerClient.abort();
            }
            opsDone++;
        }
        spannerClient.cleanup();
    }
}
