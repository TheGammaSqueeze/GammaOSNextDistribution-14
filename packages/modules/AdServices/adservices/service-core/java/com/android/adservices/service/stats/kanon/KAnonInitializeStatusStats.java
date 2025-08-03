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

import static com.android.adservices.service.stats.kanon.KAnonSignJoinStatsConstants.KANON_ACTION_FAILURE_REASON_UNSET;
import static com.android.adservices.service.stats.kanon.KAnonSignJoinStatsConstants.KANON_ACTION_UNSET;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class KAnonInitializeStatusStats {

    /** A boolean representing if the sign call was successful. */
    public abstract boolean getWasSuccessful();

    /** The kanon action which caused the failure of the sign call. */
    public abstract int getKAnonAction();

    /** Cause of the failure, if there was any failure. */
    public abstract int getKAnonActionFailureReason();

    /** The latency of the KAnon sign method in milliseconds. */
    public abstract int getLatencyInMs();

    public static Builder builder() {
        return new AutoValue_KAnonInitializeStatusStats.Builder()
                .setKAnonAction(KANON_ACTION_UNSET)
                .setKAnonActionFailureReason(KANON_ACTION_FAILURE_REASON_UNSET);
    }

    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the wasSuccessful field. */
        public abstract Builder setWasSuccessful(boolean wasSuccessful);

        /** Sets the batch size. */
        public abstract Builder setKAnonAction(int kAnonAction);

        /** Sets set batch field. */
        public abstract Builder setKAnonActionFailureReason(int kAnonActionFailureReason);

        /** Sets the latency field. */
        public abstract Builder setLatencyInMs(int latencyInMs);

        public abstract KAnonInitializeStatusStats build();
    }
}
