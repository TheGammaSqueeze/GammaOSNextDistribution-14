/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.rkpdapp.database;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;

/**
 * Representation of the remotely provisioned key record to be stored in Rkpd Database. Ignored
 * columns (macedPublicKey and coseKey) are not stored.
 */
@Entity(tableName = "provisioned_keys",
        indices = {@Index(value = {"client_uid", "key_id", "irpc_hal"}, unique = true)})
public class ProvisionedKey {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "key_blob")
    public byte[] keyBlob;

    @ColumnInfo(name = "irpc_hal")
    @NonNull
    public String irpcHal;

    @ColumnInfo(name = "public_key")
    @NonNull
    public byte[] publicKey;

    @ColumnInfo(name = "certificate_chain")
    @NonNull
    public byte[] certificateChain;

    @ColumnInfo(name = "expiration_time", index = true)
    @NonNull
    public Instant expirationTime;

    @ColumnInfo(name = "client_uid")
    public Integer clientUid;

    @ColumnInfo(name = "key_id")
    public Integer keyId;

    // Provide default ctor just for Room generated code
    public ProvisionedKey() {}

    @Ignore
    public ProvisionedKey(@NonNull byte[] keyBlob, @NonNull String irpcHal,
            @NonNull byte[] publicKey, @NonNull byte[] certificateChain,
            @NonNull Instant expirationTime) {
        this.keyBlob = keyBlob;
        this.irpcHal = irpcHal;
        this.publicKey = publicKey;
        this.certificateChain = certificateChain;
        this.expirationTime = expirationTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProvisionedKey)) return false;
        ProvisionedKey that = (ProvisionedKey) o;
        return Arrays.equals(keyBlob, that.keyBlob)
                && Objects.equals(irpcHal, that.irpcHal)
                && Arrays.equals(publicKey, that.publicKey)
                && Arrays.equals(certificateChain, that.certificateChain)
                && Objects.equals(truncate(expirationTime), truncate(that.expirationTime))
                && Objects.equals(clientUid, that.clientUid)
                && Objects.equals(keyId, that.keyId);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(irpcHal, truncate(expirationTime), clientUid, keyId);
        result = 31 * result + Arrays.hashCode(keyBlob);
        result = 31 * result + Arrays.hashCode(publicKey);
        result = 31 * result + Arrays.hashCode(certificateChain);
        return result;
    }

    private static Instant truncate(Instant original) {
        return original == null ? null : original.truncatedTo(ChronoUnit.MILLIS);
    }

    @Override
    public String toString() {
        return "ProvisionedKey{"
                + "keyBlob=" + Arrays.toString(keyBlob)
                + ", irpcHal='" + irpcHal + '\''
                + ", publicKey=" + Arrays.toString(publicKey)
                + ", certificateChain=" + Arrays.toString(certificateChain)
                + ", expirationTime=" + expirationTime
                + ", clientUid=" + clientUid
                + ", keyId=" + keyId
                + '}';
    }
}
