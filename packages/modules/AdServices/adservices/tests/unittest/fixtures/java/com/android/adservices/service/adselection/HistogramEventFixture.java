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

package com.android.adservices.service.adselection;

import android.adservices.common.CommonFixture;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.common.KeyedFrequencyCapFixture;
import android.adservices.customaudience.CustomAudienceFixture;

public class HistogramEventFixture {
    public static final HistogramEvent VALID_HISTOGRAM_EVENT =
            getValidHistogramEventBuilder().build();

    public static final HistogramEvent VALID_WIN_HISTOGRAM_EVENT =
            getValidHistogramEventBuilder()
                    .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                    .build();

    public static final HistogramEvent VALID_HISTOGRAM_EVENT_DIFFERENT_NAME =
            getValidHistogramEventBuilder()
                    .setCustomAudienceName(CustomAudienceFixture.VALID_NAME + "Different")
                    .build();

    public static final HistogramEvent VALID_WIN_HISTOGRAM_EVENT_DIFFERENT_NAME =
            getValidHistogramEventBuilder()
                    .setCustomAudienceName(CustomAudienceFixture.VALID_NAME + "Different")
                    .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                    .build();

    public static final HistogramEvent VALID_HISTOGRAM_EVENT_DIFFERENT_OWNER =
            getValidHistogramEventBuilder()
                    .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME_1)
                    .build();

    public static final HistogramEvent VALID_WIN_HISTOGRAM_EVENT_DIFFERENT_OWNER =
            getValidHistogramEventBuilder()
                    .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME_1)
                    .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                    .build();

    public static final HistogramEvent VALID_HISTOGRAM_EVENT_DIFFERENT_BUYER =
            getValidHistogramEventBuilder().setBuyer(CommonFixture.VALID_BUYER_2).build();

    public static final HistogramEvent VALID_HISTOGRAM_EVENT_DIFFERENT_BUYER_EARLIER_TIME =
            getValidHistogramEventBuilder()
                    .setBuyer(CommonFixture.VALID_BUYER_2)
                    .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.minusSeconds(1))
                    .build();

    public static final HistogramEvent VALID_WIN_HISTOGRAM_EVENT_DIFFERENT_BUYER =
            getValidHistogramEventBuilder()
                    .setBuyer(CommonFixture.VALID_BUYER_2)
                    .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                    .build();

    public static final HistogramEvent VALID_HISTOGRAM_EVENT_EARLIER_TIME =
            getValidHistogramEventBuilder()
                    .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.minusSeconds(1))
                    .build();

    public static final HistogramEvent VALID_WIN_HISTOGRAM_EVENT_EARLIER_TIME =
            getValidHistogramEventBuilder()
                    .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                    .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.minusSeconds(1))
                    .build();

    public static final HistogramEvent VALID_HISTOGRAM_EVENT_LATER_TIME =
            getValidHistogramEventBuilder()
                    .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.plusSeconds(1))
                    .build();

    public static final HistogramEvent VALID_WIN_HISTOGRAM_EVENT_LATER_TIME =
            getValidHistogramEventBuilder()
                    .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                    .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI.plusSeconds(1))
                    .build();

    public static final HistogramEvent VALID_HISTOGRAM_EVENT_DIFFERENT_SOURCE_APP =
            getValidHistogramEventBuilder().setSourceApp(CommonFixture.TEST_PACKAGE_NAME_2).build();

    public static HistogramEvent.Builder getValidHistogramEventBuilder() {
        return HistogramEvent.builder()
                .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                .setBuyer(CommonFixture.VALID_BUYER_1)
                .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                .setCustomAudienceName(CustomAudienceFixture.VALID_NAME)
                .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_VIEW)
                .setTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setSourceApp(CommonFixture.TEST_PACKAGE_NAME_1);
    }
}
