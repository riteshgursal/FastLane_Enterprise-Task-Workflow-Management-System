package com.flowlane.attachment;

import java.io.IOException;
import java.io.InputStream;

/** Where attachment bytes live. Swap the local implementation for S3-compatible storage in production. */
public interface FileStorage {

    void store(String key, InputStream content) throws IOException;

    InputStream open(String key) throws IOException;

    void delete(String key) throws IOException;
}
