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
public abstract class KAnonJoinStatusStats {
    /** A boolean representing if the sign call was successful. */
    public abstract boolean getWasSuccessful();

    /** Total number of messages that were processed. */
    public abstract int getTotalMessages();

    /** Total number of failed messages. */
    public abstract int getNumberOfFailedMessages();

    /** Latency for the KAnon join method. */
    public abstract int getLatencyInMs();

    /** Returns a {@link Builder} class for {@link KAnonJoinStatusStats}. */
    public static Builder builder() {
        return new AutoValue_KAnonJoinStatusStats.Builder().setLatencyInMs(100);
    }

    @AutoValue.Builder
    public abstract static class Builder {

        /** Sets the wasSuccessful field. */
        public abstract Builder setWasSuccessful(boolean wasSuccessful);

        /** Sets the totalMessages field. */
        public abstract Builder setTotalMessages(int totalMessages);

        /** Sets the numberOfFailedMessages field. */
        public abstract Builder setNumberOfFailedMessages(int numberOfFailedMessages);

        /** Sets the latency for the kanon method. */
        public abstract Builder setLatencyInMs(int latencyInMs);

        /** Creates and returns a {@link KAnonJoinStatusStats} object. */
        public abstract KAnonJoinStatusStats build();
    }
}
