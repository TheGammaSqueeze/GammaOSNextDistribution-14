/*
 * Copyright (C) 2024 The Android Open Source Project
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
import static org.junit.Assume.assumeTrue;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdCompatibleManager;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.FetchAndJoinCustomAudienceRequest;
import android.adservices.utils.FledgeScenarioTest;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.Scenarios;
import android.net.Uri;
import android.util.Log;

import androidx.test.filters.FlakyTest;

import com.android.adservices.common.AdServicesOutcomeReceiverForTests;
import com.android.adservices.common.annotations.SetFlagDisabled;
import com.android.adservices.common.annotations.SetFlagEnabled;
import com.android.compatibility.common.util.ShellUtils;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SetFlagDisabled(KEY_FLEDGE_HTTP_CACHE_ENABLE)
public class AdSelectionTest extends FledgeScenarioTest {

    /**
     * End-to-end test for ad selection.
     *
     * <p>Covers the following Remarketing CUJs:
     *
     * <ul>
     *   <li><b>001</b>: A buyer can provide bidding logic using JS
     *   <li><b>002</b>: A seller can provide scoring logic using JS
     *   <li><b>035</b>: A buyer can provide the trusted signals to be used during ad selection
     * </ul>
     */
    @Test
    public void testAdSelection_withBiddingAndScoringLogic_happyPath() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-default.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHIRTS_CA);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            assertThat(result.hasOutcome()).isTrue();
        } finally {
            leaveCustomAudience(SHIRTS_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * Test for ad selection with V3 bidding logic.
     *
     * <p>Covers the following Remarketing CUJs:
     *
     * <ul>
     *   <li><b>119</b>: A ad selection can be run with V3 bidding logic without override
     * </ul>
     */
    @Test
    public void testAdSelection_withBiddingLogicV3_happyPath() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-119.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHOES_CA);
            overrideBiddingLogicVersionToV3(true);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            assertThat(result.hasOutcome()).isTrue();
            assertThat(result.getRenderUri()).isNotNull();
        } finally {
            overrideBiddingLogicVersionToV3(false);
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * Test that buyers can specify an adCost in generateBid that is found in the buyer impression
     * reporting URI (Remarketing CUJ 160).
     */
    @Test
    @FlakyTest(bugId = 325344134)
    public void testAdSelection_withAdCostInUrl_happyPath() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-160.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();
        long adSelectionId;

        try {
            overrideCpcBillingEnabled(true);
            joinCustomAudience(SHOES_CA);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            adSelectionId = result.getAdSelectionId();
            assertThat(result.hasOutcome()).isTrue();
            assertThat(result.getRenderUri()).isNotNull();
        } finally {
            overrideCpcBillingEnabled(false);
            leaveCustomAudience(SHOES_CA);
        }
        doReportImpression(adSelectionId, adSelectionConfig);

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * Test that buyers can specify an adCost in generateBid that reported (Remarketing CUJ 161).
     */
    @FlakyTest(bugId = 299871209)
    @Test
    @SetFlagEnabled(KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED)
    public void testAdSelection_withAdCostInUrl_adCostIsReported() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-161.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();
        long adSelectionId;

        try {
            overrideCpcBillingEnabled(true);
            joinCustomAudience(SHOES_CA);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            adSelectionId = result.getAdSelectionId();
            doReportImpression(adSelectionId, adSelectionConfig);
            doReportEvent(adSelectionId, "click");
        } finally {
            overrideCpcBillingEnabled(false);
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * Test that custom audience can be successfully fetched from a server and joined to participate
     * in a successful ad selection (Remarketing CUJ 169).
     */
    @Test
    public void testAdSelection_withFetchCustomAudience_fetchesAndReturnsSuccessfully()
            throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-fetchCA.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();
        String customAudienceName = "hats";

        try {
            CustomAudience customAudience = makeCustomAudience(customAudienceName).build();
            ShellUtils.runShellCommand(
                    "device_config put adservices fledge_fetch_custom_audience_enabled true");
            mCustomAudienceClient
                    .fetchAndJoinCustomAudience(
                            new FetchAndJoinCustomAudienceRequest.Builder(
                                            Uri.parse(
                                                    getServerBaseAddress()
                                                            + Scenarios.FETCH_CA_PATH))
                                    .setActivationTime(customAudience.getActivationTime())
                                    .setExpirationTime(customAudience.getExpirationTime())
                                    .setName(customAudience.getName())
                                    .setUserBiddingSignals(customAudience.getUserBiddingSignals())
                                    .build())
                    .get(5, TimeUnit.SECONDS);
            Log.d(TAG, "Joined Custom Audience: " + customAudienceName);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            assertThat(result.hasOutcome()).isTrue();
            assertThat(result.getRenderUri()).isNotNull();
        } finally {
            ShellUtils.runShellCommand(
                    "device_config put adservices fledge_fetch_custom_audience_enabled false");
            leaveCustomAudience(customAudienceName);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /** Test that ad selection fails with an expired custom audience. */
    @Test
    public void testAdSelection_withShortlyExpiringCustomAudience_selectAdsThrowsException()
            throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-default.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig config = makeAdSelectionConfig();
        CustomAudience customAudience =
                makeCustomAudience(SHOES_CA)
                        .setExpirationTime(Instant.now().plus(5, ChronoUnit.SECONDS))
                        .build();

        joinCustomAudience(customAudience);
        Log.d(TAG, "Joined custom audience");
        // Make a call to verify ad selection succeeds before timing out.
        mAdSelectionClient.selectAds(config).get(TIMEOUT, TimeUnit.SECONDS);
        Thread.sleep(7000);

        Exception selectAdsException =
                assertThrows(
                        ExecutionException.class,
                        () -> mAdSelectionClient.selectAds(config).get(TIMEOUT, TimeUnit.SECONDS));
        assertThat(selectAdsException.getCause()).isInstanceOf(IllegalStateException.class);
    }

    /**
     * Test that not providing any ad selection Ids to selectAds with ad selection outcomes should
     * result in failure (Remarketing CUJ 071).
     */
    @Test
    public void testAdSelectionOutcomes_withNoAdSelectionId_throwsException() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-default.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionFromOutcomesConfig config =
                new AdSelectionFromOutcomesConfig.Builder()
                        .setSeller(mAdTechIdentifier)
                        .setAdSelectionIds(List.of())
                        .setSelectionLogicUri(
                                Uri.parse(getServerBaseAddress() + Scenarios.MEDIATION_LOGIC_PATH))
                        .setSelectionSignals(makeAdSelectionSignals())
                        .build();

        try {
            Exception selectAdsException =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    mAdSelectionClient
                                            .selectAds(config)
                                            .get(TIMEOUT, TimeUnit.SECONDS));
            assertThat(selectAdsException.getCause()).isInstanceOf(IllegalArgumentException.class);
        } finally {
            leaveCustomAudience(SHIRTS_CA);
        }
    }

    /** Test that buyer and seller receive win and loss debug reports (Remarketing CUJ 164). */
    @FlakyTest(bugId = 300421625)
    @Test
    public void testAdSelection_withDebugReporting_happyPath() throws Exception {
        assumeTrue(isAdIdSupported());
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-164.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHOES_CA);
            setDebugReportingEnabledForTesting(true);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            assertThat(result.hasOutcome()).isTrue();
        } finally {
            setDebugReportingEnabledForTesting(false);
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * Test that buyer and seller do not receive win and loss debug reports if the feature is
     * disabled (Remarketing CUJ 165).
     */
    @Test
    public void testAdSelection_withDebugReportingDisabled_doesNotSend() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-165.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHOES_CA);
            overrideBiddingLogicVersionToV3(true);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            assertThat(result.hasOutcome()).isTrue();
        } finally {
            overrideBiddingLogicVersionToV3(false);
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    /**
     * Test that buyer and seller receive win and loss debug reports with reject reason (Remarketing
     * CUJ 170).
     */
    @FlakyTest(bugId = 301334790)
    @Test
    public void testAdSelection_withDebugReportingAndRejectReason_happyPath() throws Exception {
        assumeTrue(isAdIdSupported());
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-170.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHOES_CA);
            joinCustomAudience(SHIRTS_CA);
            setDebugReportingEnabledForTesting(true);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            assertThat(result.hasOutcome()).isTrue();
        } finally {
            setDebugReportingEnabledForTesting(false);
            leaveCustomAudience(SHOES_CA);
            leaveCustomAudience(SHIRTS_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    @Test
    public void testAdSelection_withHighLatencyBackend_doesNotWinAuction() throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-053.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig config = makeAdSelectionConfig();

        try {
            joinCustomAudience(SHIRTS_CA);
            Exception selectAdsException =
                    assertThrows(
                            ExecutionException.class,
                            () ->
                                    mAdSelectionClient
                                            .selectAds(config)
                                            .get(TIMEOUT, TimeUnit.SECONDS));
            assertThat(selectAdsException.getCause()).isInstanceOf(TimeoutException.class);
        } finally {
            leaveCustomAudience(SHIRTS_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }

    private boolean isAdIdSupported() {
        AdIdCompatibleManager adIdCompatibleManager;
        AdServicesOutcomeReceiverForTests<AdId> callback =
                new AdServicesOutcomeReceiverForTests<>();
        try {
            adIdCompatibleManager = new AdIdCompatibleManager(sContext);
            adIdCompatibleManager.getAdId(MoreExecutors.directExecutor(), callback);
        } catch (IllegalStateException e) {
            Log.d(TAG, "isAdIdAvailable(): IllegalStateException detected in AdId manager.");
            return false;
        }

        boolean isAdIdAvailable;
        try {
            AdId result = callback.assertSuccess();
            isAdIdAvailable =
                    !Objects.isNull(result)
                            && !result.isLimitAdTrackingEnabled()
                            && !result.getAdId().equals(AdId.ZERO_OUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.d(TAG, "isAdIdSupported(): failed to get AdId due to InterruptedException.");
            isAdIdAvailable = false;
        }

        Log.d(TAG, String.format("isAdIdSupported(): %b", isAdIdAvailable));
        return isAdIdAvailable;
    }
}
