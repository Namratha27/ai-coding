package com.interview.catalog.simulated;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class SimulatedCosmosContainer<T extends CosmosItem> {
    private final ConcurrentHashMap<String, T> store = new ConcurrentHashMap<>();

    public T createItem(T item) {
        store.put(item.getId(), item);
        return item;
    }

    public T readItem(String id) {
        return store.get(id);
    }

    public List<T> query(Predicate<T> predicate) {
        return store.values().stream().filter(predicate).toList();
    }

    public T replaceItem(T item) {
        store.put(item.getId(), item);
        return item;
    }

    public boolean deleteItem(String id) {
        return store.remove(id) != null;
    }

    public List<T> items() {
        return new ArrayList<>(store.values());
    }
}