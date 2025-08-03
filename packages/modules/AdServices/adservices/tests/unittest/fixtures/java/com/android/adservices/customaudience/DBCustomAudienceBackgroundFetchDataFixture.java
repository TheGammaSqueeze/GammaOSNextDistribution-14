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

package com.android.adservices.customaudience;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.data.customaudience.DBCustomAudienceBackgroundFetchData;
import com.android.adservices.service.FlagsFactory;

public class DBCustomAudienceBackgroundFetchDataFixture {
    public static final int NUM_VALIDATION_FAILURES_POSITIVE = 10;
    public static final int NUM_TIMEOUT_FAILURES_POSITIVE = 20;

    public static DBCustomAudienceBackgroundFetchData.Builder getValidBuilderByBuyer(
            AdTechIdentifier buyer) {
        return DBCustomAudienceBackgroundFetchData.builder()
                .setOwner(CustomAudienceFixture.VALID_OWNER)
                .setBuyer(buyer)
                .setName(CustomAudienceFixture.VALID_NAME)
                .setDailyUpdateUri(CustomAudienceFixture.getValidDailyUpdateUriByBuyer(buyer))
                .setEligibleUpdateTime(
                        DBCustomAudienceBackgroundFetchData
                                .computeNextEligibleUpdateTimeAfterSuccessfulUpdate(
                                        CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                                        FlagsFactory.getFlagsForTest()))
                .setNumValidationFailures(0)
                .setNumTimeoutFailures(0);
    }
}
