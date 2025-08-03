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

import static com.android.adservices.service.stats.AdServicesLoggerUtil.FIELD_UNSET;

import android.adservices.adselection.ReportEventRequest;
import android.annotation.NonNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Class for destinationRegisteredBeacon reported stats. */
@AutoValue
public abstract class DestinationRegisteredBeaconsReportedStats {
    /** @return the entity who registered the beacons. */
    @ReportEventRequest.ReportingDestination
    public abstract int getBeaconReportingDestinationType();

    /** @return number of beacons ad-tech tries to register during reportImpression. */
    public abstract int getAttemptedRegisteredBeacons();

    /** @return key size range for interactionKey in every registerAdBeacon call. */
    public abstract ImmutableList<InteractionKeySizeRangeType> getAttemptedKeySizesRangeType();

    /** @return size of registered_ad_interactions database after each update to it. */
    public abstract int getTableNumRows();

    /** @return the status response code in AdServices. */
    public abstract int getAdServicesStatusCode();

    // The range of key size for interaction key.
    public enum InteractionKeySizeRangeType {
        UNSET_TYPE(0),
        // The key size is smaller than 50% maximum key size.
        MUCH_SMALLER_THAN_MAXIMUM_KEY_SIZE(1),
        // The key size is equal or greater than 50% maximum key size
        // but smaller than maximum key size.
        SMALLER_THAN_MAXIMUM_KEY_SIZE(2),
        // The key size is equal to maximum key size.
        EQUAL_TO_MAXIMUM_KEY_SIZE(3),
        // The key size is greater than maximum key size.
        LARGER_THAN_MAXIMUM_KEY_SIZE(4);

        private final int mValue;

        InteractionKeySizeRangeType(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /** @return generic builder */
    public static Builder builder() {
        return new AutoValue_DestinationRegisteredBeaconsReportedStats.Builder()
                .setAdServicesStatusCode(FIELD_UNSET);
    }

    /** Builder class for DestinationRegisteredBeaconsReportedStats. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setBeaconReportingDestinationType(
                @ReportEventRequest.ReportingDestination int value);

        public abstract Builder setAttemptedRegisteredBeacons(int value);

        public abstract Builder setAttemptedKeySizesRangeType(
                List<InteractionKeySizeRangeType> value);

        public abstract Builder setTableNumRows(int value);

        public abstract Builder setAdServicesStatusCode(int value);

        public abstract DestinationRegisteredBeaconsReportedStats build();
    }

    /**
     * Converts set of interaction keys to list of interaction key size range type.
     *
     * @param interactionKeys The list of interaction keys.
     * @param maxInteractionKeySize The maximum size of interaction key.
     * @return The list of interaction key size range type.
     */
    public static List<InteractionKeySizeRangeType> getInteractionKeySizeRangeTypeList(
            Set<String> interactionKeys,
            long maxInteractionKeySize) {
        List<InteractionKeySizeRangeType> results = new ArrayList<>();
        if (interactionKeys == null || maxInteractionKeySize <= 0) {
            return results;
        }
        for (String interactionKey : interactionKeys) {
            if (interactionKey == null) {
                results.add(InteractionKeySizeRangeType.UNSET_TYPE);
            } else if (interactionKey.length() < 0.5 * maxInteractionKeySize) {
                results.add(InteractionKeySizeRangeType.MUCH_SMALLER_THAN_MAXIMUM_KEY_SIZE);
            } else if (interactionKey.length() < maxInteractionKeySize) {
                results.add(InteractionKeySizeRangeType.SMALLER_THAN_MAXIMUM_KEY_SIZE);
            } else if (interactionKey.length() == maxInteractionKeySize) {
                results.add(InteractionKeySizeRangeType.EQUAL_TO_MAXIMUM_KEY_SIZE);
            } else {
                results.add(InteractionKeySizeRangeType.LARGER_THAN_MAXIMUM_KEY_SIZE);
            }
        }
        return results;
    }
}
