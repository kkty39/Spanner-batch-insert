package org.example;

public class ClientThread implements Runnable {
    private final SpannerClient spannerClient; // Reference to the SpannerClient for database operations.
    private final int opsCount; // Total number of operations this thread is supposed to perform.
    private int opsDone; // Counter for the number of operations completed.

    // Constructor to initialize the SpannerClient and the operations count.
    public ClientThread(SpannerClient spannerClient, int opsCount) {
        this.spannerClient = spannerClient;
        this.opsCount = opsCount;
    }

    // Getter method to retrieve the number of completed operations.
    public int getOpsDone() {
        return opsDone;
    }

    @Override
    public void run() {
        try {
            // Initialize the SpannerClient.
            spannerClient.init();
        } catch (Exception e) {
            e.printStackTrace();
            e.printStackTrace(System.out);
            return; // Exit the thread if initialization fails.
        }

        // Main loop to perform database operations up to the specified opsCount.
        while (opsDone < opsCount) {
            try {
                // Start a new transaction.
                spannerClient.start();
                // Perform an insert operation. If successful, commit the transaction.
                if (spannerClient.doInsert() == Status.OK) {
                    spannerClient.commit();
                } else {
                    spannerClient.abort();
                }
            } catch (Exception e) {
                // Uncomment to enable stack trace printing on exception.
                // e.printStackTrace();
                // Abort the transaction in case of exception.
                spannerClient.abort();
            }
            opsDone++; // Increment the count of operations done.
        }
        // Commit all the entries that have not been added to transactionContext.buffer(),
        // and commits the insert mutations left (number not reached to a batch size yet).
        spannerClient.cleanup();
    }

    // Method to get the count of aborted exceptions from the SpannerClient.
    public int getAbortedExceptionCount() {
        return this.spannerClient.getAbortedExceptionCount();
    }
}
