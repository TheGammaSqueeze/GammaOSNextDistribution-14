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

package com.android.adservices.stats;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__FLEDGE;

import com.android.adservices.service.stats.ApiCallStats;

import org.mockito.ArgumentMatcher;

/** A FLEDGE-specific ApiCallStats matcher for use in Mockito verification. */
public class FledgeApiCallStatsMatcher implements ArgumentMatcher<ApiCallStats> {
    private final int mExpectedApiName;
    private final int mExpectedResultCode;

    /** Sets the expected result code and API name. */
    public FledgeApiCallStatsMatcher(int expectedApiName, int expectedResultCode) {
        mExpectedApiName = expectedApiName;
        mExpectedResultCode = expectedResultCode;
    }

    /** Matches on FLEDGE API stats: code, API class, API name, and result code. */
    @Override
    public boolean matches(ApiCallStats actualApiCallStats) {
        return actualApiCallStats.getCode() == AD_SERVICES_API_CALLED
                && actualApiCallStats.getApiClass() == AD_SERVICES_API_CALLED__API_CLASS__FLEDGE
                && actualApiCallStats.getApiName() == mExpectedApiName
                && actualApiCallStats.getResultCode() == mExpectedResultCode;
    }

    /** Writes out expected fields to be matched. */
    @Override
    public String toString() {
        return String.format(
                "ApiCallStats{code=%d, apiClass=%d, apiName=%d, resultCode=%d}",
                AD_SERVICES_API_CALLED,
                AD_SERVICES_API_CALLED__API_CLASS__FLEDGE,
                mExpectedApiName,
                mExpectedResultCode);
    }
}
