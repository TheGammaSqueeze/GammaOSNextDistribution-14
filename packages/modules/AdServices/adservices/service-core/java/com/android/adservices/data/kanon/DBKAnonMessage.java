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

package com.android.adservices.data.kanon;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.android.adservices.data.common.FledgeRoomConverters;

import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;

import java.time.Instant;

@Entity(tableName = DBKAnonMessage.TABLE_NAME)
@TypeConverters({FledgeRoomConverters.class})
@AutoValue
@CopyAnnotations
public abstract class DBKAnonMessage {
    public static final String TABLE_NAME = "kanon_messages";

    /**
     * @return the message_id for this KAnonMessage.
     */
    @Nullable
    @CopyAnnotations
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "message_id")
    public abstract Long getMessageId();

    /**
     * @return the createdAt instant for this message.
     */
    @NonNull
    @CopyAnnotations
    @ColumnInfo(name = "created_at")
    public abstract Instant getCreatedAt();

    /**
     * @return the expiry_instant for this message.
     */
    @NonNull
    @CopyAnnotations
    @ColumnInfo(name = "expiry_instant")
    public abstract Instant getExpiryInstant();

    /**
     * Corresponding client parameters id. This field is initially null and once the message is
     * joined/signed, this field is updated to match the id of the client parameters used to make
     * the sign/join calls.
     *
     * @return the Corresponding client parameters id for this message.
     */
    @Nullable
    @CopyAnnotations
    @ColumnInfo(name = "corresponding_client_parameters_id")
    public abstract Long getCorrespondingClientParametersId();

    /**
     * Corresponding client parameters expiry instant. This field is initially null and once the
     * message is joined/signed, this field is updated to match the expiry instant of the client
     * parameters used to make the sign/join calls. We need this field to keep track of expiry
     * instant of the client parameters used for signing/joining this message.
     *
     * @return the Corresponding client parameters expiry instant for this message.
     */
    @Nullable
    @CopyAnnotations
    @ColumnInfo(name = "corresponding_client_parameters_expiry_instant")
    public abstract Instant getCorrespondingClientParametersExpiryInstant();

    /**
     * @return the ad selection id for this message.
     */
    @NonNull
    @CopyAnnotations
    @ColumnInfo(name = "ad_selection_id")
    public abstract long getAdSelectionId();

    /**
     * Hash set corresponding to this message. This is the set that needs to be signed/joined.
     *
     * @return the kanon hash set for this message.
     */
    @NonNull
    @CopyAnnotations
    @ColumnInfo(name = "kanon_hash_set")
    public abstract String getKanonHashSet();

    /**
     * @return the {@link KAnonMessageConstants.MessageStatus} for this message.
     */
    @NonNull
    @CopyAnnotations
    @ColumnInfo(name = "status")
    @KAnonMessageConstants.MessageStatus
    public abstract int getStatus();

    /** Returns a {@link DBKAnonMessage.Builder} for {@link DBKAnonMessage} */
    public static DBKAnonMessage.Builder builder() {
        return new AutoValue_DBKAnonMessage.Builder();
    }

    /** Creates and returns a {@link DBKAnonMessage} object. */
    public static DBKAnonMessage create(
            @Nullable Long messageId,
            @NonNull Instant createdAt,
            @NonNull Instant expiryInstant,
            @Nullable Long correspondingClientParametersId,
            @Nullable Instant correspondingClientParametersExpiryInstant,
            @KAnonMessageConstants.MessageStatus int status,
            @NonNull String kanonHashSet,
            long adSelectionId) {
        return builder()
                .setAdSelectionId(adSelectionId)
                .setCreatedAt(createdAt)
                .setMessageId(messageId)
                .setExpiryInstant(expiryInstant)
                .setStatus(status)
                .setCorrespondingClientParametersExpiryInstant(
                        correspondingClientParametersExpiryInstant)
                .setKanonHashSet(kanonHashSet)
                .setCorrespondingClientParametersId(correspondingClientParametersId)
                .build();
    }

    /** Builder class for {@link DBKAnonMessage} */
    @AutoValue.Builder
    public abstract static class Builder {

        /** Sets the message id. */
        @NonNull
        public abstract Builder setMessageId(@Nullable Long messageId);

        /** Sets the createdAt field */
        @NonNull
        public abstract Builder setCreatedAt(Instant createdAt);

        /** Sets the expiry instant. */
        @NonNull
        public abstract Builder setExpiryInstant(Instant expiryInstant);

        /** Sets the Corresponding client parameters id */
        @NonNull
        public abstract Builder setCorrespondingClientParametersId(
                Long correspondingClientParametersId);

        /** Sets the Corresponding client parameters expiry instant */
        @NonNull
        public abstract Builder setCorrespondingClientParametersExpiryInstant(
                Instant correspondingClientParametersExpiryInstant);

        /** Sets the {@link KAnonMessageConstants.MessageStatus} for the message */
        @NonNull
        public abstract Builder setStatus(@KAnonMessageConstants.MessageStatus int status);

        /** Sets the kanon hash set string */
        @NonNull
        public abstract Builder setKanonHashSet(String kanonHashSet);

        /** Sets the ad selection id corresponding to this kanon message */
        @NonNull
        public abstract Builder setAdSelectionId(long adSelectionId);

        /** Builds and return a {@link DBKAnonMessage} message. */
        public abstract DBKAnonMessage build();
    }
}
