package org.example;

public class ClientThread implements Runnable{
    private SpannerClient spannerClient;
    // number of operations
    private int opsCount;
    // number of operations done
    private  int opsDone;

    public ClientThread(SpannerClient spannerClient, int opsCount) {
        this.spannerClient = spannerClient;
        this.opsCount = opsCount;
    }

    public int getOpsDone() {
        return opsDone;
    }

    @Override
    public void run() {
        spannerClient.init();
    }
}
