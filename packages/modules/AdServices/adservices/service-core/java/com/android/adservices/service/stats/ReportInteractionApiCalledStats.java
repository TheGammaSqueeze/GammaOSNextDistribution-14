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

import android.adservices.adselection.ReportEventRequest;

import com.google.auto.value.AutoValue;

import java.util.Arrays;
import java.util.Optional;

/** Class for beacon level reporting for ReportInteraction API called stats */
@AutoValue
public abstract class ReportInteractionApiCalledStats {
    /** @return the entity who registered the beacons. */
    @ReportEventRequest.ReportingDestination
    public abstract int getBeaconReportingDestinationType();

    /** @return number of matching uris for the reportInteraction request is found. */
    public abstract int getNumMatchingUris();

    /** @return generic builder. */
    public static Builder builder() {
        return new AutoValue_ReportInteractionApiCalledStats.Builder();
    }

    /** Builder class for ReportInteractionApiCalledStats. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setBeaconReportingDestinationType(
                @ReportEventRequest.ReportingDestination int value);

        public abstract Builder setNumMatchingUris(int value);

        public abstract ReportInteractionApiCalledStats build();
    }
}
