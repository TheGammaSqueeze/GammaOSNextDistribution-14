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

package com.android.adservices.service.stats;

import android.adservices.common.AdServicesStatusUtils;

import com.google.auto.value.AutoValue;

/** Class for GetAdSelectionData API called stats */
@AutoValue
public abstract class GetAdSelectionDataApiCalledStats {
    /** Returns the size of the payload in Kb after encryption and padding */
    public abstract int getPayloadSizeKb();

    /** Return number of buyers participating in this payload */
    public abstract int getNumBuyers();

    /** The status response code of the GetAdSelectionData API in AdServices */
    @AdServicesStatusUtils.StatusCode
    public abstract int getStatusCode();

    /** Returns a generic builder. */
    public static Builder builder() {
        return new AutoValue_GetAdSelectionDataApiCalledStats.Builder();
    }

    /** Builder class for GetAdSelectionDataApiCalledStats. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the size of the payload in KB */
        public abstract Builder setPayloadSizeKb(int payloadSizeKb);

        /** Sets the number of buyers. */
        public abstract Builder setNumBuyers(int numBuyers);

        /** Sets the status code. */
        public abstract Builder setStatusCode(@AdServicesStatusUtils.StatusCode int statusCode);

        /** Builds the {@link GetAdSelectionDataApiCalledStats} object. */
        public abstract GetAdSelectionDataApiCalledStats build();
    }
}
