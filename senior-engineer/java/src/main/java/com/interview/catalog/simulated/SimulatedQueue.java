package com.interview.catalog.simulated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SimulatedQueue {
    private final List<String> messages = Collections.synchronizedList(new ArrayList<>());

    public void sendMessage(String message) {
        messages.add(message);
    }

    public List<String> getMessages() {
        synchronized (messages) {
            return new ArrayList<>(messages);
        }
    }

    public int getCount() {
        return messages.size();
    }
}