package com.company.crm.common.crypto;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * NFR-7.2b: an entity with encrypted fields that are also looked up by value. Its *_hash columns
 * are recomputed from the plain values whenever it is saved (see {@link Listener}).
 */
public interface BlindIndexed {

    void refreshBlindIndexes(DataEncryption encryption);

    /** Register with {@code @EntityListeners(BlindIndexed.Listener.class)}. */
    class Listener {
        @PrePersist
        @PreUpdate
        void refresh(Object entity) {
            if (entity instanceof BlindIndexed indexed) {
                indexed.refreshBlindIndexes(DataEncryption.get());
            }
        }
    }
}
