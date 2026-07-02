package com.interview.catalog.simulated;

import java.util.concurrent.ConcurrentHashMap;

public class SimulatedBlobContainer {
    private static final String BASE_URL = "https://sim.blob.local/product-images";
    private final ConcurrentHashMap<String, byte[]> blobs = new ConcurrentHashMap<>();

    public String upload(String blobName, byte[] contentBytes) {
        blobs.put(blobName, contentBytes);
        return BASE_URL + "/" + blobName;
    }

    public int getCount() {
        return blobs.size();
    }
}