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
public abstract class KAnonGetChallengeStatusStats {

    /** The size in bytes of the X.509 certificate chain. */
    public abstract int getCertificateSizeInBytes();

    /** Result code of the Key Attestation operation. */
    public abstract int getResultCode();

    /** Latency of the operation in milliseconds */
    public abstract int getLatencyInMs();

    public static Builder builder() {
        return new AutoValue_KAnonGetChallengeStatusStats.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the certificate size in bytes */
        public abstract Builder setCertificateSizeInBytes(int certificateSizeInBytes);

        /** Sets the result code. */
        public abstract Builder setResultCode(int resultCode);

        /** Sets the latency in milliseconds. */
        public abstract Builder setLatencyInMs(int latencyInMs);

        /** Builds and returns a {@link KAnonGetChallengeStatusStats} */
        public abstract KAnonGetChallengeStatusStats build();
    }
}
