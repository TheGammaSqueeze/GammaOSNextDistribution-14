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

package com.android.adservices.data.topics;

import android.annotation.NonNull;

import com.android.internal.annotations.Immutable;

import com.google.auto.value.AutoValue;

import java.util.Objects;

/** POJO to store unencrypted {@link Topic} and encrypted {@link EncryptedTopic}. */
@AutoValue
@Immutable
public abstract class CombinedTopic {
    /**
     * @return Unencrypted Topic object.
     */
    public abstract Topic getTopic();

    /**
     * @return Encrypted Topic object.
     */
    public abstract EncryptedTopic getEncryptedTopic();

    /**
     * Creates an instance of {@link CombinedTopic}.
     *
     * @param topic Unencrypted topic object.
     * @param encryptedTopic Encrypted topic object
     * @return Combined topic object containing both encrypted and unencrypted topic object.
     */
    @NonNull
    public static CombinedTopic create(Topic topic, EncryptedTopic encryptedTopic) {
        Objects.requireNonNull(topic);
        Objects.requireNonNull(encryptedTopic);

        return builder().setTopic(topic).setEncryptedTopic(encryptedTopic).build();
    }

    /**
     * @return generic builder
     */
    @NonNull
    public static CombinedTopic.Builder builder() {
        return new AutoValue_CombinedTopic.Builder();
    }

    /** Builder for {@link CombinedTopic}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Set unencrypted Topic */
        public abstract CombinedTopic.Builder setTopic(Topic topic);

        /** Set encrypted Topic */
        public abstract CombinedTopic.Builder setEncryptedTopic(EncryptedTopic encryptedTopic);

        /** Build a CombinedTopic instance */
        @NonNull
        public abstract CombinedTopic build();
    }

    @Override
    public final boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof CombinedTopic)) return false;
        CombinedTopic combinedTopic = (CombinedTopic) object;
        return getTopic().equals(combinedTopic.getTopic())
                && getEncryptedTopic().equals(combinedTopic.getEncryptedTopic());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(getTopic().hashCode(), getEncryptedTopic().hashCode());
    }

    @Override
    public final String toString() {
        return "CombinedTopic{"
                + "topic="
                + getTopic()
                + ", encryptedTopic="
                + getEncryptedTopic()
                + '}';
    }
}
