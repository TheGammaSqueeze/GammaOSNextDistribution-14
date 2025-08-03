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

package com.android.cobalt.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import com.android.cobalt.crypto.Encrypter;
import com.android.cobalt.crypto.EncryptionFailedException;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;
import com.google.cobalt.EncryptedMessage;
import com.google.cobalt.ObservationBatch;
import com.google.cobalt.ObservationToEncrypt;
import com.google.cobalt.UnencryptedObservationBatch;

import java.util.Objects;
import java.util.Optional;

/**
 * Stores observations which have been generated, but not sent.
 *
 * <p>Observations are automatically assigned a montonically increasing id.
 */
@AutoValue
@CopyAnnotations
@Entity(tableName = "ObservationStore")
public abstract class ObservationStoreEntity {

    /** The id automatically assigned to the observation batch. */
    @CopyAnnotations
    @ColumnInfo(name = "observation_store_id")
    @PrimaryKey(autoGenerate = true)
    @NonNull
    public abstract int observationStoreId();

    /** The stored observation batch. */
    @CopyAnnotations
    @ColumnInfo(name = "unencrypted_observation_batch")
    @NonNull
    public abstract UnencryptedObservationBatch unencryptedObservationBatch();

    /**
     * Creates an {@link ObservationStoreEntity}.
     *
     * <p>Used by Room to instantiate objects.
     */
    @NonNull
    public static ObservationStoreEntity create(
            int observationStoreId, UnencryptedObservationBatch unencryptedObservationBatch) {
        return new AutoValue_ObservationStoreEntity(
                observationStoreId, unencryptedObservationBatch);
    }

    /** Creates an {@link ObservationStoreEntity} to insert. */
    @Ignore
    @NonNull
    static ObservationStoreEntity createForInsertion(
            UnencryptedObservationBatch unencryptedObservationBatch) {
        return new AutoValue_ObservationStoreEntity(0 /*unused */, unencryptedObservationBatch);
    }

    /**
     * Creates an {@link ObservationBatch} using the provided {@link Encrypter}.
     *
     * @param encrypter the {@link Encrypter} to encrypt data with
     * @return an ObservationBatch
     * @throws EncryptionFailedException if encryption failed
     */
    @NonNull
    public ObservationBatch encrypt(@NonNull Encrypter encrypter) throws EncryptionFailedException {
        Objects.requireNonNull(encrypter);

        ObservationBatch.Builder encryptedObservations =
                ObservationBatch.newBuilder()
                        .setMetaData(unencryptedObservationBatch().getMetadata());
        for (ObservationToEncrypt toEncrypt :
                unencryptedObservationBatch().getUnencryptedObservationsList()) {
            Optional<EncryptedMessage> encryptionResult = encrypter.encryptObservation(toEncrypt);
            encryptionResult.ifPresent(encryptedObservations::addEncryptedObservation);
        }
        return encryptedObservations.build();
    }
}
