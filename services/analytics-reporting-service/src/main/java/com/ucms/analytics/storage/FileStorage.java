package com.ucms.analytics.storage;

import java.io.InputStream;

/** Object storage for submission files and assignment briefs. */
public interface FileStorage {
    void put(String key, InputStream content, long size, String contentType);

    InputStream get(String key);
}
