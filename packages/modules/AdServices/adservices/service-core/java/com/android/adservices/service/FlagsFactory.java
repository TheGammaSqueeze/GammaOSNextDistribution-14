/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service;

/** Factory class to create AdServices Flags */
public class FlagsFactory {
    /** Ad Services Flags backed by Phenotype/Heterodyne. */
    public static Flags getFlags() {
        // Use the Flags backed by PH.
        return PhFlags.getInstance();
    }

    /** Ad Services Flags backed by hard coded constants. This should be used in unit tests only */
    public static Flags getFlagsForTest() {
        // Use the Flags that has constant values.
        return new TestFlags();
    }

    public static class TestFlags implements Flags {
        // Using tolerant timeouts for tests to avoid flakiness.
        // Tests that need to validate timeout behaviours will override these values too.
        @Override
        public long getAdSelectionBiddingTimeoutPerCaMs() {
            return 10000;
        }

        @Override
        public long getAdSelectionScoringTimeoutMs() {
            return 10000;
        }

        @Override
        public long getAdSelectionOverallTimeoutMs() {
            return 600000;
        }

        @Override
        public boolean getEnforceIsolateMaxHeapSize() {
            return false;
        }

        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return true;
        }

        @Override
        public boolean getFledgeRegisterAdBeaconEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeAdSelectionFilteringEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeFetchCustomAudienceEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
            return true;
        }

        @Override
        public int getFledgeScheduleCustomAudienceMinDelayMinsOverride() {
            // Lets the delay be set in past for easier testing
            return -100;
        }

        @Override
        public boolean getEnableLoggedTopic() {
            return true;
        }

        @Override
        public boolean getEnableDatabaseSchemaVersion8() {
            return true;
        }

        @Override
        public boolean getFledgeAuctionServerEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeEventLevelDebugReportingEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeBeaconReportingMetricsEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeAppPackageNameLoggingEnabled() {
            return true;
        }
    }
}
