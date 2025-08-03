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

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.adservices.common.FrequencyCapFilters;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class AdCounterHistogramUpdaterNoOpImplTest {
    private static final long AD_SELECTION_ID = 10;

    private AdCounterHistogramUpdater mAdCounterHistogramUpdater;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mAdCounterHistogramUpdater = new AdCounterHistogramUpdaterNoOpImpl();
    }

    @Test
    public void testUpdateWinHistogram_nullDbAdSelectionDataThrows() {
        assertThrows(
                NullPointerException.class,
                () -> mAdCounterHistogramUpdater.updateWinHistogram(/* dbAdSelection= */ null));
    }

    @Test
    public void testUpdateNonWinHistogram_nullCallerPackageNameThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mAdCounterHistogramUpdater.updateNonWinHistogram(
                                AD_SELECTION_ID,
                                /* callerPackageName= */ null,
                                FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION,
                                CommonFixture.FIXED_NOW));
    }

    @Test
    public void testUpdateNonWinHistogram_nullTimestampThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mAdCounterHistogramUpdater.updateNonWinHistogram(
                                AD_SELECTION_ID,
                                CommonFixture.TEST_PACKAGE_NAME,
                                FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION,
                                /* eventTimestamp= */ null));
    }
}
