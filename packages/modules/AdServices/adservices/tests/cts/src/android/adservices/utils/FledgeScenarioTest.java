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

package android.adservices.utils;

import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;
import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;

import android.Manifest;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.ReportEventRequest;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.JoinCustomAudienceRequest;
import android.adservices.customaudience.ScheduleCustomAudienceUpdateRequest;
import android.adservices.customaudience.TrustedBiddingData;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.common.SupportedByConditionRule;
import com.android.adservices.service.FlagsConstants;
import com.android.adservices.service.PhFlagsFixture;
import com.android.compatibility.common.util.ShellUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.mockwebserver.MockWebServer;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Abstract class for FLEDGE scenario tests using local servers. */
public abstract class FledgeScenarioTest {
    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    protected static final String TAG = FledgeScenarioTest.class.getSimpleName();
    protected static final int TIMEOUT = 120;
    protected static final String SHOES_CA = "shoes";
    protected static final String SHIRTS_CA = "shirts";
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final int NUM_ADS_PER_AUDIENCE = 4;
    private static final String PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final long AD_ID_FETCHER_TIMEOUT = 1000;
    private static final long AD_ID_FETCHER_TIMEOUT_DEFAULT = 50;
    private final Random mCacheBusterRandom = new Random();

    protected AdvertisingCustomAudienceClient mCustomAudienceClient;
    protected AdSelectionClient mAdSelectionClient;

    protected AdTechIdentifier mAdTechIdentifier;
    private String mServerBaseAddress;
    private MockWebServer mMockWebServer;

    // Prefix added to all requests to bust cache.
    private int mCacheBuster;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Rule(order = 1)
    public final SupportedByConditionRule devOptionsEnabled =
            DevContextUtils.createDevOptionsAvailableRule(sContext, TAG);

    @Rule(order = 5)
    public final AdServicesDeviceSupportedRule deviceSupported =
            new AdServicesDeviceSupportedRule();

    @Rule(order = 3)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            CtsWebViewSupportUtil.createJSSandboxAvailableRule(CONTEXT);

    @Rule(order = 2)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    .setCompatModeFlags()
                    .setPpapiAppAllowList(sContext.getPackageName())
                    .setFlag(FlagsConstants.KEY_ADID_KILL_SWITCH, false);

    @Rule(order = 6)
    public MockWebServerRule mMockWebServerRule =
            MockWebServerRule.forHttps(
                    CONTEXT, "adservices_untrusted_test_server.p12", "adservices_test");

    protected static void overrideBiddingLogicVersionToV3(boolean useVersion3) {
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_ad_selection_bidding_logic_js_version %s",
                useVersion3 ? "3" : "2");
    }

    protected static AdSelectionSignals makeAdSelectionSignals() {
        return AdSelectionSignals.fromString(
                String.format("{\"valid\": true, \"publisher\": \"%s\"}", PACKAGE_NAME));
    }

    @Before
    public void setUp() throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);

        PhFlagsFixture.overrideFledgeOnDeviceAdSelectionTimeouts(
                /* biddingTimeoutPerCaMs= */ 5_000,
                /* scoringTimeoutMs= */ 5_000,
                /* overallTimeoutMs= */ 10_000);

        AdservicesTestHelper.killAdservicesProcess(sContext);
        ExecutorService executor = Executors.newCachedThreadPool();
        mCustomAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(CONTEXT)
                        .setExecutor(executor)
                        .build();
        mAdSelectionClient =
                new AdSelectionClient.Builder().setContext(CONTEXT).setExecutor(executor).build();
        mCacheBuster = mCacheBusterRandom.nextInt();
    }

    @After
    public final void tearDown() throws IOException {
        if (mMockWebServer != null) {
            mMockWebServer.shutdown();
        }
    }

    protected AdSelectionOutcome doSelectAds(AdSelectionConfig adSelectionConfig)
            throws ExecutionException, InterruptedException, TimeoutException {
        AdSelectionOutcome result =
                mAdSelectionClient.selectAds(adSelectionConfig).get(TIMEOUT, TimeUnit.SECONDS);
        Log.d(TAG, "Ran ad selection.");
        return result;
    }

    protected void doReportEvent(long adSelectionId, String eventName)
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        mAdSelectionClient
                .reportEvent(
                        new ReportEventRequest.Builder(
                                        adSelectionId,
                                        eventName,
                                        new JSONObject().put("key", "value").toString(),
                                        FLAG_REPORTING_DESTINATION_SELLER
                                                | FLAG_REPORTING_DESTINATION_BUYER)
                                .build())
                .get(TIMEOUT, TimeUnit.SECONDS);
        Log.d(TAG, "Ran report ad click for ad selection id: " + adSelectionId);
    }

    protected void doReportImpression(long adSelectionId, AdSelectionConfig adSelectionConfig)
            throws ExecutionException, InterruptedException, TimeoutException {
        mAdSelectionClient
                .reportImpression(new ReportImpressionRequest(adSelectionId, adSelectionConfig))
                .get(TIMEOUT, TimeUnit.SECONDS);
        Log.d(TAG, "Ran report impression for ad selection id: " + adSelectionId);
    }

    protected void joinCustomAudience(String customAudienceName)
            throws ExecutionException, InterruptedException, TimeoutException {
        JoinCustomAudienceRequest joinCustomAudienceRequest =
                makeJoinCustomAudienceRequest(customAudienceName);
        mCustomAudienceClient
                .joinCustomAudience(joinCustomAudienceRequest.getCustomAudience())
                .get(5, TimeUnit.SECONDS);
        Log.d(TAG, "Joined Custom Audience: " + customAudienceName);
    }

    protected void joinCustomAudience(CustomAudience customAudience)
            throws ExecutionException, InterruptedException, TimeoutException {
        mCustomAudienceClient.joinCustomAudience(customAudience).get(5, TimeUnit.SECONDS);
        Log.d(TAG, "Joined Custom Audience: " + customAudience.getName());
    }

    protected void leaveCustomAudience(String customAudienceName)
            throws ExecutionException, InterruptedException, TimeoutException {
        CustomAudience customAudience = makeCustomAudience(customAudienceName).build();
        mCustomAudienceClient
                .leaveCustomAudience(customAudience.getBuyer(), customAudience.getName())
                .get(TIMEOUT, TimeUnit.SECONDS);
        Log.d(TAG, "Left Custom Audience: " + customAudienceName);
    }

    protected void doScheduleCustomAudienceUpdate(ScheduleCustomAudienceUpdateRequest request)
            throws ExecutionException, InterruptedException, TimeoutException {
        mCustomAudienceClient.scheduleCustomAudienceUpdate(request).get(TIMEOUT, TimeUnit.SECONDS);
        Log.d(TAG, "Scheduled Custom Audience Update: " + request);
    }

    protected String getServerBaseAddress() {
        return String.format(
                "https://%s:%s%s/",
                mMockWebServer.getHostName(), mMockWebServer.getPort(), getCacheBusterPrefix());
    }

    protected void overrideCpcBillingEnabled(boolean enabled) {
        ShellUtils.runShellCommand(
                String.format(
                        "device_config put adservices fledge_cpc_billing_enabled %s",
                        enabled ? "true" : "false"));
    }

    protected void overrideRegisterAdBeaconEnabled(boolean enabled) {
        ShellUtils.runShellCommand(
                String.format(
                        "device_config put adservices fledge_register_ad_beacon_enabled %s",
                        enabled ? "true" : "false"));
    }

    protected void overrideShouldUseUnifiedTable(boolean shouldUse) {
        ShellUtils.runShellCommand(
                String.format(
                        "device_config put adservices"
                                + " fledge_on_device_auction_should_use_unified_tables %s",
                        shouldUse ? "true" : "false"));
    }

    protected void setDebugReportingEnabledForTesting(boolean enabled) {
        FledgeScenarioTest.overrideBiddingLogicVersionToV3(enabled);
        PhFlagsFixture.overrideAdIdFetcherTimeoutMs(
                enabled ? AD_ID_FETCHER_TIMEOUT : AD_ID_FETCHER_TIMEOUT_DEFAULT);
        ShellUtils.runShellCommand(
                String.format(
                        "device_config put adservices fledge_event_level_debug_reporting_enabled"
                                + " %s",
                        enabled ? "true" : "false"));
        ShellUtils.runShellCommand(
                String.format(
                        "device_config put adservices"
                                + " fledge_event_level_debug_report_send_immediately %s",
                        enabled ? "true" : "false"));
    }

    protected AdSelectionConfig makeAdSelectionConfig() {
        AdSelectionSignals signals = FledgeScenarioTest.makeAdSelectionSignals();
        Log.d(TAG, "Ad tech: " + mAdTechIdentifier.toString());
        return new AdSelectionConfig.Builder()
                .setSeller(mAdTechIdentifier)
                .setPerBuyerSignals(ImmutableMap.of(mAdTechIdentifier, signals))
                .setCustomAudienceBuyers(ImmutableList.of(mAdTechIdentifier))
                .setAdSelectionSignals(signals)
                .setSellerSignals(signals)
                .setDecisionLogicUri(Uri.parse(mServerBaseAddress + Scenarios.SCORING_LOGIC_PATH))
                .setTrustedScoringSignalsUri(
                        Uri.parse(mServerBaseAddress + Scenarios.SCORING_SIGNALS_PATH))
                .build();
    }

    protected void setupDefaultMockWebServer(ScenarioDispatcher dispatcher) throws Exception {
        if (mMockWebServer != null) {
            mMockWebServer.shutdown();
        }
        mMockWebServer = mMockWebServerRule.startMockWebServer(dispatcher);
        mServerBaseAddress = getServerBaseAddress();
        mAdTechIdentifier = AdTechIdentifier.fromString(mMockWebServer.getHostName());
        Log.d(TAG, "Started default MockWebServer.");
    }

    protected String getCacheBusterPrefix() {
        return String.format("/%s", mCacheBuster);
    }

    private JoinCustomAudienceRequest makeJoinCustomAudienceRequest(String customAudienceName) {
        return new JoinCustomAudienceRequest.Builder()
                .setCustomAudience(makeCustomAudience(customAudienceName).build())
                .build();
    }

    protected CustomAudience.Builder makeCustomAudience(String customAudienceName) {
        Uri trustedBiddingUri = Uri.parse(mServerBaseAddress + Scenarios.BIDDING_SIGNALS_PATH);
        Uri dailyUpdateUri =
                Uri.parse(mServerBaseAddress + Scenarios.getDailyUpdatePath(customAudienceName));
        return new CustomAudience.Builder()
                .setName(customAudienceName)
                .setDailyUpdateUri(dailyUpdateUri)
                .setTrustedBiddingData(
                        new TrustedBiddingData.Builder()
                                .setTrustedBiddingKeys(ImmutableList.of())
                                .setTrustedBiddingUri(trustedBiddingUri)
                                .build())
                .setUserBiddingSignals(AdSelectionSignals.fromString("{}"))
                .setAds(makeAds(customAudienceName))
                .setBiddingLogicUri(
                        Uri.parse(String.format(mServerBaseAddress + Scenarios.BIDDING_LOGIC_PATH)))
                .setBuyer(mAdTechIdentifier)
                .setActivationTime(Instant.now())
                .setExpirationTime(Instant.now().plus(5, ChronoUnit.DAYS));
    }

    private ImmutableList<AdData> makeAds(String customAudienceName) {
        ImmutableList.Builder<AdData> ads = new ImmutableList.Builder<>();
        for (int i = 0; i < NUM_ADS_PER_AUDIENCE; i++) {
            ads.add(makeAd(/* adNumber= */ i, customAudienceName));
        }
        return ads.build();
    }

    private AdData makeAd(int adNumber, String customAudienceName) {
        return new AdData.Builder()
                .setMetadata(
                        String.format(
                                Locale.ENGLISH,
                                "{\"bid\": 5, \"ad_number\": %d, \"target\": \"%s\"}",
                                adNumber,
                                PACKAGE_NAME))
                .setRenderUri(
                        Uri.parse(
                                String.format(
                                        "%s/render/%s/%s",
                                        mServerBaseAddress, customAudienceName, adNumber)))
                .build();
    }
}
