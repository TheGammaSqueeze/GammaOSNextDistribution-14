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

package android.adservices.test.scenario.adservices.fledge;

import android.adservices.customaudience.CustomAudience;
import android.platform.test.scenario.annotation.Scenario;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Scenario
@RunWith(JUnit4.class)
public class SelectAdsTestServerLatency extends AbstractSelectAdsLatencyTest {

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        for (CustomAudience ca : sCustomAudiences) {
            CUSTOM_AUDIENCE_CLIENT
                    .leaveCustomAudience(ca.getBuyer(), ca.getName())
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        ShellUtils.runShellCommand("device_config set_sync_disabled_for_tests none");
    }

    @After
    public void tearDown() throws Exception {
        List<CustomAudience> removedCAs = new ArrayList<>();
        try {
            for (CustomAudience ca : sCustomAudiences) {
                CUSTOM_AUDIENCE_CLIENT
                        .leaveCustomAudience(ca.getBuyer(), ca.getName())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                removedCAs.add(ca);
            }
        } finally {
            sCustomAudiences.removeAll(removedCAs);
        }
    }

    @Test
    public void selectAds_oneBuyerOneCAOneAdPerCA_hotStart_jsCacheEnabled() throws Exception {
        // 1 Seller, 1 Buyer, 1 Custom Audience, 1 Ad
        enableJsCache();
        warmupSingleBuyerProcess();
        runSelectAds(
                "CustomAudiencesOneBuyerOneCAOneAd.json",
                "AdSelectionConfigOneBuyerOneCAOneAd.json",
                getClass().getSimpleName(),
                "selectAds_oneBuyerOneCAOneAdPerCA_hotStart_jsCacheEnabled");
    }

    @Test
    public void selectAds_oneBuyerOneCAOneAdPerCA_hotStart_noJsCache() throws Exception {
        // 1 Seller, 1 Buyer, 1 Custom Audience, 1 Ad
        disableJsCache();
        warmupSingleBuyerProcess();
        runSelectAds(
                "CustomAudiencesOneBuyerOneCAOneAd.json",
                "AdSelectionConfigOneBuyerOneCAOneAd.json",
                getClass().getSimpleName(),
                "selectAds_oneBuyerOneCAOneAdPerCA_hotStart_noJsCache");
    }

    @Test
    public void selectAds_oneBuyerOneCAOneAdPerCA_coldStart_noJsCache() throws Exception {
        // 1 Seller, 1 Buyer, 1 Custom Audience, 1 Ad
        disableJsCache();
        runSelectAds(
                "CustomAudiencesOneBuyerOneCAOneAd.json",
                "AdSelectionConfigOneBuyerOneCAOneAd.json",
                getClass().getSimpleName(),
                "selectAds_oneBuyerOneCAOneAdPerCA_coldStart_noJsCache");
    }

    @Test
    public void selectAds_fiveBuyersTwoCAsFiveAdsPerCA_hotStart_jsCacheEnabled() throws Exception {
        enableJsCache();
        warmupFiveBuyersProcess();
        runSelectAds(
                "CustomAudiencesFiveBuyersTwoCAsFiveAdsPerCA.json",
                "AdSelectionConfigFiveBuyersTwoCAsFiveAdsPerCA.json",
                getClass().getSimpleName(),
                "selectAds_fiveBuyerTwoCAsFiveAdsPerCA_hotStart_jsCacheEnabled");
    }

    @Test
    public void selectAds_fiveBuyersTwoCAsFiveAdsPerCA_hotStart_noJsCache() throws Exception {
        disableJsCache();
        warmupFiveBuyersProcess();
        runSelectAds(
                "CustomAudiencesFiveBuyersTwoCAsFiveAdsPerCA.json",
                "AdSelectionConfigFiveBuyersTwoCAsFiveAdsPerCA.json",
                getClass().getSimpleName(),
                "selectAds_fiveBuyerTwoCAsFiveAdsPerCA_hotStart_noJsCache");
    }

    @Test
    public void selectAds_fiveBuyersTwoCAsFiveAdsPerCA_coldStart_noJsCache() throws Exception {
        disableJsCache();
        runSelectAds(
                "CustomAudiencesFiveBuyersTwoCAsFiveAdsPerCA.json",
                "AdSelectionConfigFiveBuyersTwoCAsFiveAdsPerCA.json",
                getClass().getSimpleName(),
                "selectAds_fiveBuyerTwoCAsFiveAdsPerCA_coldStart_noJsCache");
    }
}
