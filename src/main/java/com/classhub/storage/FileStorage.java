package com.classhub.storage;

import java.io.InputStream;

/**
 * Storage boundary for binary objects. LocalFileStorage is the MVP implementation;
 * later backends (S3/R2/Spaces/MinIO) can replace it without changing coursework logic.
 */
public interface FileStorage {

    void store(String storageKey, InputStream content, long contentLength);

    InputStream open(String storageKey);

    void delete(String storageKey);

    boolean exists(String storageKey);
}
