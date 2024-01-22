package org.example;

import java.util.concurrent.atomic.AtomicLong;

public class NumberGenerator {
    private final AtomicLong counter; // AtomicLong to ensure thread-safe incrementing.

    // Constructor to initialize the counter with a starting value.
    public NumberGenerator(long countstart) {
        counter = new AtomicLong(countstart);
    }

    // Method to get the next value in the sequence.
    // It increments the counter atomically and returns the previous value.
    public Long nextValue() {
        return counter.getAndIncrement();
    }
}
