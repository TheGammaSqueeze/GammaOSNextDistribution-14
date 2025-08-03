/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.adservices.data.adselection;

import android.annotation.Nullable;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.android.adservices.data.adselection.datahandlers.DBValidator;
import com.android.adservices.data.adselection.datahandlers.EncryptionContext;
import com.android.adservices.ohttp.EncapsulatedSharedSecret;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;
import com.android.adservices.ohttp.ObliviousHttpRequestContext;

import java.time.Instant;

/** Dao to manage access to entities in Encryption Context table. */
@Dao
public abstract class EncryptionContextDao {

    /** Returns the EncryptionContext of given ad selection id if it exists. */
    @Nullable
    @Query(
            "SELECT * FROM encryption_context "
                    + "WHERE context_id = :contextId AND encryption_key_type = :encryptionKeyType")
    public abstract DBEncryptionContext getEncryptionContext(
            long contextId, @EncryptionKeyConstants.EncryptionKeyType int encryptionKeyType);

    /** Inserts the given EncryptionContext in table. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insertEncryptionContext(DBEncryptionContext context);

    /**
     * Clean up expired encryption context entries if it is older than the given timestamp.
     *
     * @param expirationTime is the cutoff time to expire the Encryption Context.
     */
    @Query("DELETE FROM encryption_context WHERE creation_instant < :expirationTime")
    public abstract void removeExpiredEncryptionContext(Instant expirationTime);

    /** Persists the encryption context in the {@link DBEncryptionContext} table. */
    public void persistEncryptionContext(
            long contextId, EncryptionContext encryptionContext, Instant creationInstant) {
        DBValidator.validateEncryptionContext(encryptionContext);

        ObliviousHttpRequestContext requestContext = encryptionContext.getOhttpRequestContext();
        insertEncryptionContext(
                DBEncryptionContext.builder()
                        .setContextId(contextId)
                        .setEncryptionKeyType(
                                EncryptionKeyConstants.from(
                                        encryptionContext.getAdSelectionEncryptionKeyType()))
                        .setCreationInstant(creationInstant)
                        .setSeed(requestContext.seed())
                        .setKeyConfig(requestContext.keyConfig().serializeKeyConfigToBytes())
                        .setSharedSecret(
                                requestContext.encapsulatedSharedSecret().serializeToBytes())
                        .build());
    }

    /**
     * Method to get {@link EncryptionContext} associated with the given adSelectionId and key type.
     */
    public EncryptionContext getEncryptionContextForIdAndKeyType(
            long contextId, @EncryptionKeyConstants.EncryptionKeyType int encryptionKeyType)
            throws Exception {
        DBEncryptionContext dbEncryptionContext =
                getEncryptionContext(contextId, encryptionKeyType);

        return EncryptionContext.builder()
                .setAdSelectionEncryptionKeyType(encryptionKeyType)
                .setOhttpRequestContext(
                        ObliviousHttpRequestContext.create(
                                ObliviousHttpKeyConfig.fromSerializedKeyConfig(
                                        dbEncryptionContext.getKeyConfig()),
                                EncapsulatedSharedSecret.create(
                                        dbEncryptionContext.getSharedSecret()),
                                dbEncryptionContext.getSeed()))
                .build();
    }
}
