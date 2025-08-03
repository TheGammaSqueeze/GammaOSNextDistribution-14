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

package android.adservices.test.scenario.adservices.fledge;

import android.Manifest;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.TrustedBiddingData;
import android.adservices.test.scenario.adservices.utils.MockWebServerRule;
import android.adservices.test.scenario.adservices.utils.MockWebServerRuleFactory;
import android.adservices.test.scenario.adservices.utils.SelectAdsFlagRule;
import android.content.Context;
import android.net.Uri;
import android.platform.test.rule.CleanPackageRule;
import android.platform.test.rule.KillAppsRule;
import android.platform.test.scenario.annotation.Scenario;
import android.provider.DeviceConfig;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;

import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.RecordedRequest;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Scenario
@RunWith(JUnit4.class)
public class AbstractPerfTest {

    public static final Duration CUSTOM_AUDIENCE_EXPIRE_IN = Duration.ofDays(1);
    public static final Instant VALID_ACTIVATION_TIME = Instant.now();
    public static final Instant VALID_EXPIRATION_TIME =
            VALID_ACTIVATION_TIME.plus(CUSTOM_AUDIENCE_EXPIRE_IN);
    public static final String VALID_NAME = "testCustomAudienceName";
    public static final AdSelectionSignals VALID_USER_BIDDING_SIGNALS =
            AdSelectionSignals.fromString("{'valid': 'yep', 'opaque': 'definitely'}");
    public static final String VALID_TRUSTED_BIDDING_URI_PATH = "/trusted/bidding/";
    public static final ArrayList<String> VALID_TRUSTED_BIDDING_KEYS =
            new ArrayList<>(Arrays.asList("example", "valid", "list", "of", "keys"));
    public static final AdTechIdentifier SELLER = AdTechIdentifier.fromString("localhost");
    // Uri Constants
    public static final String DECISION_LOGIC_PATH = "/decisionFragment";
    public static final String TRUSTED_SCORING_SIGNAL_PATH = "/trustedScoringSignalsFragment";
    public static final String CUSTOM_AUDIENCE_SHIRT = "ca_shirt";
    public static final String CUSTOM_AUDIENCE_SHOES = "ca_shoe";
    // TODO(b/244530379) Make compatible with multiple buyers
    public static final AdTechIdentifier BUYER_1 = AdTechIdentifier.fromString("localhost");
    public static final List<AdTechIdentifier> CUSTOM_AUDIENCE_BUYERS =
            Collections.singletonList(BUYER_1);
    public static final AdSelectionSignals AD_SELECTION_SIGNALS =
            AdSelectionSignals.fromString("{\"ad_selection_signals\":1}");
    public static final AdSelectionSignals SELLER_SIGNALS =
            AdSelectionSignals.fromString("{\"test_seller_signals\":1}");
    public static final Map<AdTechIdentifier, AdSelectionSignals> PER_BUYER_SIGNALS =
            Map.of(BUYER_1, AdSelectionSignals.fromString("{\"buyer_signals\":1}"));
    protected static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    // Time allowed by current test setup for APIs to respond
    // setting a large value for perf testing, to avoid failing for large datasets
    protected static final int API_RESPONSE_TIMEOUT_SECONDS = 100;
    protected static final String BUYER_BIDDING_LOGIC_URI_PATH = "/buyer/bidding/logic/";
    protected static final String SELLER_REPORTING_PATH = "/reporting/seller";
    protected static final String BUYER_REPORTING_PATH = "/reporting/buyer";
    protected static final String DEFAULT_DECISION_LOGIC_JS =
            "function scoreAd(ad, bid, auction_config, seller_signals,"
                    + " trusted_scoring_signals, contextual_signal, user_signal,"
                    + " custom_audience_signal) { \n"
                    + "  return {'status': 0, 'score': bid };\n"
                    + "}\n"
                    + "function reportResult(ad_selection_config, render_uri, bid,"
                    + " contextual_signals) { \n"
                    + " return {'status': 0, 'results': {'signals_for_buyer':"
                    + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                    + SELLER_REPORTING_PATH
                    + "' } };\n"
                    + "}";
    protected static final String DEFAULT_BIDDING_LOGIC_JS =
            "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals,"
                    + " custom_audience_signals) { \n"
                    + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                    + "}\n"
                    + "function reportWin(ad_selection_signals, per_buyer_signals,"
                    + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                    + " return {'status': 0, 'results': {'reporting_uri': '"
                    + BUYER_REPORTING_PATH
                    + "' } };\n"
                    + "}";
    protected static final String CALCULATION_INTENSE_JS =
            "for (let i = 1; i < 1000000000; i++) {\n" + "  Math.sqrt(i);\n" + "}";
    protected static final String MEMORY_INTENSE_JS =
            "var a = []\n" + "for (let i = 0; i < 10000; i++) {\n" + " a.push(i);" + "}";

    protected static final AdSelectionSignals TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_uri_1\": \"signals_for_1\",\n"
                            + "\t\"render_uri_2\": \"signals_for_2\"\n"
                            + "}");
    protected static final AdSelectionSignals TRUSTED_BIDDING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"example\": \"example\",\n"
                            + "\t\"valid\": \"Also valid\",\n"
                            + "\t\"list\": \"list\",\n"
                            + "\t\"of\": \"of\",\n"
                            + "\t\"keys\": \"trusted bidding signal Values\"\n"
                            + "}");
    protected static final String AD_URI_PREFIX = "/adverts/123/";
    protected static final int DELAY_TO_AVOID_THROTTLE_MS = 1001;
    protected final Context mContext = ApplicationProvider.getApplicationContext();
    protected final AdSelectionClient mAdSelectionClient =
            new AdSelectionClient.Builder()
                    .setContext(mContext)
                    .setExecutor(CALLBACK_EXECUTOR)
                    .build();
    protected final AdvertisingCustomAudienceClient mCustomAudienceClient =
            new AdvertisingCustomAudienceClient.Builder()
                    .setContext(mContext)
                    .setExecutor(CALLBACK_EXECUTOR)
                    .build();
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    protected Dispatcher mDefaultDispatcher;

    // Per-test method rules, run in the given order.
    @Rule
    public RuleChain rules =
            RuleChain.outerRule(
                            new KillAppsRule(
                                    AdservicesTestHelper.getAdServicesPackageName(mContext)))
                    .around(
                            // CleanPackageRule should not execute after each test method because
                            // there's a chance it interferes with ShowmapSnapshotListener snapshot
                            // at the end of the test, impacting collection of memory metrics for
                            // AdServices process.
                            new CleanPackageRule(
                                    AdservicesTestHelper.getAdServicesPackageName(mContext),
                                    /* clearOnStarting = */ true,
                                    /* clearOnFinished = */ false))
                    .around(new SelectAdsFlagRule());

    @Rule
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests().setCompatModeFlags();

    @BeforeClass
    public static void setupBeforeClass() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);
        // TODO(b/245585645) Mark true for the heap size enforcement after installing M105 library
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "fledge_js_isolate_enforce_max_heap_size",
                "false",
                true);
    }

    public static Uri getUri(String name, String path) {
        return Uri.parse("https://" + name + path);
    }

    @Before
    public void setup() throws InterruptedException {
        mDefaultDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (DECISION_LOGIC_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(DEFAULT_DECISION_LOGIC_JS);
                        } else if (BUYER_BIDDING_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(DEFAULT_BIDDING_LOGIC_JS);
                        } else if (BUYER_REPORTING_PATH.equals(request.getPath())
                                || SELLER_REPORTING_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody("");
                        } else if (request.getPath().startsWith(TRUSTED_SCORING_SIGNAL_PATH)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        } else if (request.getPath().startsWith(VALID_TRUSTED_BIDDING_URI_PATH)) {
                            return new MockResponse().setBody(TRUSTED_BIDDING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };
    }

    protected CustomAudience createCustomAudience(
            final AdTechIdentifier buyer,
            String name,
            List<Double> bids,
            Instant activationTime,
            Instant expirationTime) {
        // Generate ads for with bids provided
        List<AdData> ads = new ArrayList<>();

        // Create ads with the custom audience name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            ads.add(
                    new AdData.Builder()
                            .setRenderUri(
                                    getUri(
                                            buyer.toString(),
                                            AD_URI_PREFIX + name + "/ad" + (i + 1)))
                            .setMetadata("{\"result\":" + bids.get(i) + "}")
                            .build());
        }

        return new CustomAudience.Builder()
                .setBuyer(buyer)
                .setName(name)
                .setActivationTime(activationTime)
                .setExpirationTime(expirationTime)
                .setDailyUpdateUri(getValidDailyUpdateUriByBuyer(buyer))
                .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(getValidTrustedBiddingDataByBuyer(buyer))
                .setBiddingLogicUri(mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH))
                .setAds(ads)
                .build();
    }

    protected CustomAudience createCustomAudience(
            final AdTechIdentifier buyer, String name, List<Double> bids) {
        return createCustomAudience(
                buyer, name, bids, VALID_ACTIVATION_TIME, VALID_EXPIRATION_TIME);
    }

    protected AdSelectionConfig createAdSelectionConfig() {
        return new AdSelectionConfig.Builder()
                .setSeller(SELLER)
                .setDecisionLogicUri(mMockWebServerRule.uriForPath(DECISION_LOGIC_PATH))
                .setCustomAudienceBuyers(CUSTOM_AUDIENCE_BUYERS)
                .setAdSelectionSignals(AD_SELECTION_SIGNALS)
                .setSellerSignals(SELLER_SIGNALS)
                .setPerBuyerSignals(PER_BUYER_SIGNALS)
                .setTrustedScoringSignalsUri(
                        mMockWebServerRule.uriForPath(TRUSTED_SCORING_SIGNAL_PATH))
                // TODO(b/244530379) Make compatible with multiple buyers
                .setCustomAudienceBuyers(Collections.singletonList(BUYER_1))
                .build();
    }

    protected Uri createExpectedWinningUri(
            AdTechIdentifier buyer, String customAudienceName, int adNumber) {
        return getUri(buyer.toString(), AD_URI_PREFIX + customAudienceName + "/ad" + adNumber);
    }

    // TODO(b/244530379) Make compatible with multiple buyers
    protected Uri getValidDailyUpdateUriByBuyer(AdTechIdentifier buyer) {
        return mMockWebServerRule.uriForPath("/update");
    }

    protected TrustedBiddingData getValidTrustedBiddingDataByBuyer(AdTechIdentifier buyer) {
        return new TrustedBiddingData.Builder()
                .setTrustedBiddingKeys(VALID_TRUSTED_BIDDING_KEYS)
                .setTrustedBiddingUri(getValidTrustedBiddingUriByBuyer(buyer))
                .build();
    }

    // TODO(b/244530379) Make compatible with multiple buyers
    protected Uri getValidTrustedBiddingUriByBuyer(AdTechIdentifier buyer) {
        return mMockWebServerRule.uriForPath(VALID_TRUSTED_BIDDING_URI_PATH);
    }

    protected void addDelayToAvoidThrottle() throws InterruptedException {
        Thread.sleep(DELAY_TO_AVOID_THROTTLE_MS);
    }
}
