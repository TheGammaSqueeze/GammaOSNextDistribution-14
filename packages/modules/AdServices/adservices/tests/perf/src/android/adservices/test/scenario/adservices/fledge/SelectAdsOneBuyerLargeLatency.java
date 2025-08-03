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

import android.platform.test.scenario.annotation.Scenario;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@Scenario
@RunWith(JUnit4.class)
public class SelectAdsOneBuyerLargeLatency extends AbstractSelectAdsLatencyTest {
    @Test
    public void selectAds_oneBuyer71CAs_hotStart_jsCacheEnabled() throws Exception {
        // 1 Seller, 1 Buyer, 71 Custom Audiences
        enableJsCache();
        warmupSingleBuyerProcess();
        runSelectAds(
                "CustomAudiencesOneBuyerLargeCAs.json",
                "AdSelectionConfigOneBuyerLargeCAs.json",
                getClass().getSimpleName(),
                "selectAds_oneBuyer71CAs_hotStart_jsCacheEnabled");
    }

    @Test
    public void selectAds_oneBuyer71CAs_hotStart_noJsCache() throws Exception {
        // 1 Seller, 1 Buyer, 71 Custom Audiences
        disableJsCache();
        warmupSingleBuyerProcess();
        runSelectAds(
                "CustomAudiencesOneBuyerLargeCAs.json",
                "AdSelectionConfigOneBuyerLargeCAs.json",
                getClass().getSimpleName(),
                "selectAds_oneBuyer71CAs_hotStart_noJsCache");
    }

    @Test
    public void selectAds_oneBuyer71CAs_coldStart_noJsCache() throws Exception {
        // 1 Seller, 1 Buyer, 71 Custom Audiences
        disableJsCache();
        runSelectAds(
                "CustomAudiencesOneBuyerLargeCAs.json",
                "AdSelectionConfigOneBuyerLargeCAs.json",
                getClass().getSimpleName(),
                "selectAds_oneBuyer71CAs_coldStart_noJsCache");
    }
}
