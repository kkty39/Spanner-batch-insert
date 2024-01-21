package org.example;

import java.util.concurrent.atomic.AtomicLong;

public class NumberGenerator {
    private final AtomicLong counter;

    public NumberGenerator(long countstart) {
        counter = new AtomicLong(countstart);
    }

    public Long nextValue() {
        return counter.getAndIncrement();
    }
}
