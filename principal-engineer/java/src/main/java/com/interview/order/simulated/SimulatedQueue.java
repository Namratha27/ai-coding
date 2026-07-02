package com.interview.order.simulated;

import java.util.ArrayList;
import java.util.List;

/** In-memory stand-in for an Azure Storage Queue. Messages are JSON strings. */
public class SimulatedQueue {
    private final String name;
    private final List<String> messages = new ArrayList<>();
    private int cursor = 0;

    public SimulatedQueue(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public synchronized void sendMessage(String json) {
        messages.add(json);
    }

    /** Returns messages enqueued since the last receive call. */
    public synchronized List<String> receive() {
        List<String> batch = new ArrayList<>(messages.subList(cursor, messages.size()));
        cursor = messages.size();
        return batch;
    }

    public synchronized List<String> messages() {
        return new ArrayList<>(messages);
    }
}
