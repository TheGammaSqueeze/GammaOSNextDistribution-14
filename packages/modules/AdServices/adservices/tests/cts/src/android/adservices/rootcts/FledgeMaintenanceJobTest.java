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

package android.adservices.rootcts;

import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.utils.FledgeScenarioTest;
import android.adservices.utils.ScenarioDispatcher;

import androidx.test.filters.FlakyTest;

import com.android.adservices.common.annotations.SetFlagEnabled;

import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.ExecutionException;

public final class FledgeMaintenanceJobTest extends FledgeScenarioTest {

    private static final int FLEDGE_MAINTENANCE_JOB_ID = 1;

    @Rule public DeviceTimeRule mDeviceTimeRule = new DeviceTimeRule();

    private final BackgroundJobHelper mBackgroundJobHelper = new BackgroundJobHelper(sContext);

    @Test
    public void testAdSelection_afterOneDay_adSelectionDataCleared() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-default.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();

        try {
            overrideBiddingLogicVersionToV3(true);
            joinCustomAudience(SHOES_CA);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            mDeviceTimeRule.overrideDeviceTimeToPlus25Hours();
            assertThat(mBackgroundJobHelper.runJob(FLEDGE_MAINTENANCE_JOB_ID)).isTrue();
            Exception exception =
                    assertThrows(
                            ExecutionException.class,
                            () -> doReportImpression(result.getAdSelectionId(), adSelectionConfig));
            assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
        } finally {
            overrideBiddingLogicVersionToV3(false);
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
    }

    @Test
    @FlakyTest(bugId = 315327390)
    @SetFlagEnabled(KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED)
    public void testAdSelection_afterOneDay_adInteractionsIsCleared() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-beacon-no-interactions.json",
                        getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();

        try {
            overrideBiddingLogicVersionToV3(true);

            joinCustomAudience(SHOES_CA);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            mDeviceTimeRule.overrideDeviceTimeToPlus25Hours();
            doReportImpression(result.getAdSelectionId(), adSelectionConfig);
            assertThat(mBackgroundJobHelper.runJob(FLEDGE_MAINTENANCE_JOB_ID)).isTrue();
            Exception exception =
                    assertThrows(
                            ExecutionException.class,
                            () -> doReportEvent(result.getAdSelectionId(), "click"));
            assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
    }
}
