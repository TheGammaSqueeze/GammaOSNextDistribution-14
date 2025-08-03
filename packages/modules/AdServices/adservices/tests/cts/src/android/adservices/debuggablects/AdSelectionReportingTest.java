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

package android.adservices.debuggablects;

import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_HTTP_CACHE_ENABLE;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.common.AdTechIdentifier;
import android.adservices.utils.FledgeScenarioTest;
import android.adservices.utils.ScenarioDispatcher;

import androidx.test.filters.FlakyTest;

import com.android.adservices.common.annotations.SetFlagDisabled;
import com.android.adservices.common.annotations.SetFlagEnabled;

import org.junit.Test;

import java.util.concurrent.ExecutionException;

/** End-to-end test for report impression. */
@SetFlagEnabled(KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED)
@SetFlagDisabled(KEY_FLEDGE_HTTP_CACHE_ENABLE)
public final class AdSelectionReportingTest extends FledgeScenarioTest {

    @FlakyTest(bugId = 303534327)
    @Test
    public void testReportImpression_defaultAdSelection_happyPath() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-reportimpression.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHOES_CA);
            doReportImpression(
                    doSelectAds(adSelectionConfig).getAdSelectionId(), adSelectionConfig);
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    @FlakyTest(bugId = 303534327)
    @Test
    public void testReportImpression_buyerRequestFails_sellerRequestSucceeds() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-008.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHOES_CA);
            doReportImpression(
                    doSelectAds(adSelectionConfig).getAdSelectionId(), adSelectionConfig);
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
    }

    @Test
    public void testReportImpression_buyerLogicTimesOut_reportingFails() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-060.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig config = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHOES_CA);
            AdSelectionOutcome adSelectionOutcome = doSelectAds(config);
            Exception exception =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    doReportImpression(
                                            adSelectionOutcome.getAdSelectionId(), config));
            assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
    }

    @Test
    public void testReportImpression_withMismatchedAdTechUri_sellerRequestFails() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-068.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig config =
                makeAdSelectionConfig()
                        .cloneToBuilder()
                        .setSeller(AdTechIdentifier.fromString("localhost:12345"))
                        .build();

        try {
            joinCustomAudience(SHOES_CA);
            Exception selectAdsException =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    doReportImpression(
                                            doSelectAds(config).getAdSelectionId(), config));
            assertThat(selectAdsException.getCause()).isInstanceOf(IllegalArgumentException.class);
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
    }

    @FlakyTest(bugId = 303534327)
    @Test
    public void testReportImpression_registerBuyerAndSellerBeacons_happyPath() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-beacon.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig config = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHOES_CA);
            long adSelectionId = doSelectAds(config).getAdSelectionId();
            doReportImpression(adSelectionId, config);
            doReportEvent(adSelectionId, "click");
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    @Test
    public void testReportImpression_failToRegisterBuyerBeacon_sellerBeaconSucceeds()
            throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-beacon-buyer-failure.json",
                        getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig config = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHOES_CA);
            long adSelectionId = doSelectAds(config).getAdSelectionId();
            doReportImpression(adSelectionId, config);
            doReportEvent(adSelectionId, "click");
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
    }

    @Test
    public void testReportImpression_failToRegisterSellerBeacon_buyerBeaconSucceeds()
            throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-beacon-seller-failure.json",
                        getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig config = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHOES_CA);
            long adSelectionId = doSelectAds(config).getAdSelectionId();
            doReportImpression(adSelectionId, config);
            doReportEvent(adSelectionId, "click");
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
    }

    @Test
    public void testReportImpression_withMismatchedSellerAdTech_buyerStillCalled()
            throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-beacon-seller-failure.json",
                        getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig config = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHOES_CA);
            long adSelectionId = doSelectAds(config).getAdSelectionId();
            doReportImpression(adSelectionId, config);
            doReportEvent(adSelectionId, "click");
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
    }

    @Test
    public void testReportImpression_withMismatchedBuyerAdTech_sellerStillCalled()
            throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-beacon-buyer-failure.json",
                        getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig config = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHOES_CA);
            long adSelectionId = doSelectAds(config).getAdSelectionId();
            doReportImpression(adSelectionId, config);
            doReportEvent(adSelectionId, "click");
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
    }

    @FlakyTest(bugId = 303534327)
    @Test
    public void testReportImpression_withBuyerBeacon_onlyReportsForViewInteraction()
            throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-101.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig config = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHOES_CA);
            long adSelectionId = doSelectAds(config).getAdSelectionId();
            doReportImpression(adSelectionId, config);
            doReportEvent(adSelectionId, "click");
            doReportEvent(adSelectionId, "view");
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
    }

    @Test
    public void testReportImpression_biddingLogicDownloadTimesOut_throwsException()
            throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-061.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig config = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHOES_CA);
            Exception exception =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    doReportImpression(
                                            doSelectAds(config).getAdSelectionId(), config));
            assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
        } finally {
            leaveCustomAudience(SHOES_CA);
        }
    }
}
