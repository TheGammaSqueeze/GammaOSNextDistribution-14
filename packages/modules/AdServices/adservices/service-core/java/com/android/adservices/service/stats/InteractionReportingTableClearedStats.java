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

package com.android.adservices.service.stats;

import com.google.auto.value.AutoValue;

/** Class for beacon level reporting for clearing interaction reporting table stats. */
@AutoValue
public abstract class InteractionReportingTableClearedStats {
    private static final int NUM_UNREPORTED_URIS_UNSET = -1;

    /** @return number of registered URIs cleared every 24 hours. */
    public abstract int getNumUrisCleared();

    /** @return number of unreported URIs before clearing. */
    public abstract int getNumUnreportedUris();

    /** @return generic builder. */
    public static Builder builder() {
        // TODO(b/314210005): Update db schema to support "number of unreported URIs" metric
        return new AutoValue_InteractionReportingTableClearedStats.Builder()
                .setNumUnreportedUris(NUM_UNREPORTED_URIS_UNSET);
    }

    /** Builder class for InteractionReportingTableClearedStats. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setNumUrisCleared(int value);

        public abstract Builder setNumUnreportedUris(int value);

        public abstract InteractionReportingTableClearedStats build();
    }
}
