/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.kanon;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;

/** Represents the KAnon Message sent by the server and used SIGN/JOIN KAnon calls. */
@AutoValue
@CopyAnnotations
public abstract class KAnonMessageEntity {
    @IntDef(
            value = {
                KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED,
                KAnonMessageEntity.KanonMessageEntityStatus.SIGNED,
                KAnonMessageEntity.KanonMessageEntityStatus.JOINED,
                KAnonMessageEntity.KanonMessageEntityStatus.FAILED
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface KanonMessageEntityStatus {
        int NOT_PROCESSED = 0;
        int SIGNED = 1;
        int JOINED = 2;
        int FAILED = 3;
    }

    /**
     * @return ad selection id corresponding to this {@link KAnonMessageEntity}
     */
    @NonNull
    public abstract long getAdSelectionId();

    /**
     * @return hash set for this {@link KAnonMessageEntity}
     */
    @NonNull
    public abstract String getHashSet();

    /**
     * @return the messageId for this {@link KAnonMessageEntity}
     */
    @Nullable
    public abstract Long getMessageId();

    /**
     * Corresponding client parameters expiry instant. This field is initially null and once the
     * message is joined/signed, this field is updated to match the expiry instant of the client
     * parameters used to make the sign/join calls. We need this field to keep track of expiry
     * instant of the client parameters used for signing/joining this message.
     *
     * @return the Corresponding client parameters expiry instant for this message.
     */
    @Nullable
    public abstract Instant getCorrespondingClientParametersExpiryInstant();

    @NonNull
    @KanonMessageEntityStatus
    public abstract int getStatus();

    /** Returns a {@link KAnonMessageEntity.Builder} for {@link KAnonMessageEntity} */
    public static KAnonMessageEntity.Builder builder() {
        return new AutoValue_KAnonMessageEntity.Builder();
    }

    /** Creates and returns {@link KAnonMessageEntity} object */
    public KAnonMessageEntity create(
            long adSelectionId,
            String hashSet,
            Long messageId,
            @KanonMessageEntityStatus int status,
            Instant correspondingClientParametersExpiryInstant) {
        return builder()
                .setAdSelectionId(adSelectionId)
                .setHashSet(hashSet)
                .setMessageId(messageId)
                .setCorrespondingClientParametersExpiryInstant(
                        correspondingClientParametersExpiryInstant)
                .setStatus(status)
                .build();
    }

    /** Builder class for {@link KAnonMessageEntity} */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the hash set to sign/join for this k-anon message entity. */
        public abstract Builder setHashSet(@NonNull String hashSet);

        /** Sets the ad selection id for this k-anon message entity. */
        public abstract Builder setAdSelectionId(@NonNull long adSelectionId);

        /** Sets the message id for this k-anon message entity */
        public abstract Builder setMessageId(@Nullable Long messageId);

        /** Sets the message status for this kanon message entity */
        public abstract Builder setStatus(@KanonMessageEntityStatus int status);

        /** Sets the Corresponding client parameters expiry instant */
        public abstract Builder setCorrespondingClientParametersExpiryInstant(
                Instant correspondingClientParametersExpiryInstant);

        /** Builds and returns {@link KAnonMessageEntity} */
        public abstract KAnonMessageEntity build();
    }
}
