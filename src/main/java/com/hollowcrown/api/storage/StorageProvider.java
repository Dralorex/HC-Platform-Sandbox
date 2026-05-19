package com.hollowcrown.api.storage;

/**
 * Persistence abstraction; Hub picks YAML vs SQL implementations at runtime.
 */
public interface StorageProvider {

    StorageBackend backend();

    /**
     * @return file {@code version:} field or SQL schema revision
     */
    int readSchemaVersion(String domain);

    void writeSchemaVersion(String domain, int version);
}
