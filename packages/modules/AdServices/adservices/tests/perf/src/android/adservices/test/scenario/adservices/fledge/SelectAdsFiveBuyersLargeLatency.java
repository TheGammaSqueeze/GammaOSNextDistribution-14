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
public class SelectAdsFiveBuyersLargeLatency extends AbstractSelectAdsLatencyTest {

    @Test
    public void selectAds_fiveBuyers71CAs_hotStart_jsCacheEnabled() throws Exception {
        // 1 Seller, 5 Buyer, each buyer has 71 Custom Audiences
        enableJsCache();
        warmupFiveBuyersProcess();
        runSelectAds(
                "CustomAudiencesFiveBuyersLargeCAs.json",
                "AdSelectionConfigFiveBuyersLargeCAs.json",
                getClass().getSimpleName(),
                "selectAds_fiveBuyers71CAs_hotStart_jsCacheEnabled");
    }

    @Test
    public void selectAds_fiveBuyers71CAs_hotStart_noJsCache() throws Exception {
        // 1 Seller, 5 Buyer, each buyer has 71 Custom Audiences
        disableJsCache();
        warmupFiveBuyersProcess();
        runSelectAds(
                "CustomAudiencesFiveBuyersLargeCAs.json",
                "AdSelectionConfigFiveBuyersLargeCAs.json",
                getClass().getSimpleName(),
                "selectAds_fiveBuyers71CAs_hotStart_noJsCache");
    }

    @Test
    public void selectAds_fiveBuyers71CAs_coldStart_noJsCache() throws Exception {
        // 1 Seller, 5 Buyer, each buyer has 71 Custom Audiences
        disableJsCache();
        runSelectAds(
                "CustomAudiencesFiveBuyersLargeCAs.json",
                "AdSelectionConfigFiveBuyersLargeCAs.json",
                getClass().getSimpleName(),
                "selectAds_fiveBuyers71CAs_coldStart_noJsCache");
    }
}
