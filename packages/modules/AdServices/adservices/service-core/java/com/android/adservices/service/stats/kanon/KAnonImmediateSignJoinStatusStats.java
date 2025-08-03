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

package com.android.adservices.service.stats.kanon;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class KAnonImmediateSignJoinStatusStats {
    /** Result of the KAnon background job. */
    public abstract int getKAnonJobResult();

    /** Number of messages that were attempted to be signed/joined in this background run. */
    public abstract int getTotalMessagesAttempted();

    /** Number of failed messages during the sign process in this background run. */
    public abstract int getMessagesFailedToSign();

    /** Number of failed messages during the join process in this background run. */
    public abstract int getMessagesFailedToJoin();

    /** Latency for this KAnon background run. */
    public abstract int getLatencyInMs();

    public static Builder builder() {
        return new AutoValue_KAnonImmediateSignJoinStatusStats.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the kanonJobResult field */
        public abstract Builder setKAnonJobResult(int jobResult);

        /** Sets the totalMessagesAttempted field. */
        public abstract Builder setTotalMessagesAttempted(int totalMessagesAttempted);

        /** Sets the number of messages failed in the sign process. */
        public abstract Builder setMessagesFailedToSign(int messagesFailedToSign);

        /** Sets the number of messages failed in the join process. */
        public abstract Builder setMessagesFailedToJoin(int messagesFailedToJoin);

        /** Sets the latency of the background job. */
        public abstract Builder setLatencyInMs(int latencyInMs);

        public abstract KAnonImmediateSignJoinStatusStats build();
    }
}
