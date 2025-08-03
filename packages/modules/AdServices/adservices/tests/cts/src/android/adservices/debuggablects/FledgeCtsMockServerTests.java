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

import static android.adservices.adselection.CustomAudienceBiddingInfoFixture.DATA_VERSION_1;
import static android.adservices.adselection.CustomAudienceBiddingInfoFixture.DATA_VERSION_2;

import static com.android.adservices.service.adselection.DataVersionFetcher.DATA_VERSION_HEADER_BIDDING_KEY;
import static com.android.adservices.service.adselection.DataVersionFetcher.DATA_VERSION_HEADER_SCORING_KEY;

import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingData;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.adservices.utils.CtsWebViewSupportUtil;
import android.adservices.utils.MockWebServerRule;
import android.net.Uri;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.common.SupportedByConditionRule;
import com.android.adservices.service.FlagsConstants;
import com.android.adservices.service.PhFlagsFixture;
import com.android.compatibility.common.util.ShellUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RequiresSdkLevelAtLeastS
public final class FledgeCtsMockServerTests extends ForegroundDebuggableCtsTest {
    public static final String TAG = "adservices";
    // Time allowed by current test setup for APIs to respond
    private static final int API_RESPONSE_TIMEOUT_SECONDS = 120;

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final String AD_URI_PREFIX = "/adverts/123/";

    private static final String SELLER_DECISION_LOGIC_URI_PATH = "/ssp/decision/logic/";
    private static final String BUYER_BIDDING_LOGIC_URI_PATH = "/buyer/bidding/logic/";
    private static final String SELLER_TRUSTED_SIGNAL_URI_PATH = "/kv/seller/signals/";
    private static final String SELLER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION =
            "/kv/seller/data/version/signals/";
    private static final String BUYER_TRUSTED_SIGNAL_URI_PATH = "/kv/buyer/signals/";
    private static final String BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION =
            "/kv/buyer/data/version/signals/";

    private static final String SELLER_REPORTING_PATH = "/reporting/seller";
    private static final String BUYER_REPORTING_PATH = "/reporting/buyer";

    public static final String CUSTOM_AUDIENCE_SEQ_1 = "ca1";
    public static final String CUSTOM_AUDIENCE_SEQ_2 = "ca2";

    private static final AdSelectionSignals TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_uri_1\": \"signals_for_1\",\n"
                            + "\t\"render_uri_2\": \"signals_for_2\"\n"
                            + "}");
    private static final AdSelectionSignals TRUSTED_BIDDING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"example\": \"example\",\n"
                            + "\t\"valid\": \"Also valid\",\n"
                            + "\t\"list\": \"list\",\n"
                            + "\t\"of\": \"of\",\n"
                            + "\t\"keys\": \"trusted bidding signal Values\"\n"
                            + "}");

    private Uri mLocalhostBuyerDomain;

    private AdSelectionClient mAdSelectionClient;
    private AdvertisingCustomAudienceClient mCustomAudienceClient;
    private MockWebServer mMockWebServer;

    private final ArrayList<CustomAudience> mCustomAudiencesToCleanUp = new ArrayList<>();
    private MockWebServerRule.RequestMatcher<String> mRequestMatcherPrefixMatch;

    // Prefix added to all requests to bust cache.
    private int mCacheBuster;
    private final Random mCacheBusterRandom = new Random();

    private String mServerBaseAddress;
    private AdTechIdentifier mAdTechIdentifier;

    // Every test in this class requires that the JS Sandbox be available. The JS Sandbox
    // availability depends on an external component (the system webview) being higher than a
    // certain minimum version.
    @Rule(order = 11)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            CtsWebViewSupportUtil.createJSSandboxAvailableRule(sContext);

    @Rule(order = 12)
    public MockWebServerRule mMockWebServerRule =
            MockWebServerRule.forHttps(
                    ApplicationProvider.getApplicationContext(),
                    "adservices_untrusted_test_server.p12",
                    "adservices_test");

    @Before
    public void setup() throws InterruptedException {
        if (sdkLevel.isAtLeastT()) {
            assertForegroundActivityStarted();
            flags.setFlag(
                    FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH,
                    FlagsConstants.PPAPI_AND_SYSTEM_SERVER);
        }

        mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        mCustomAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(MoreExecutors.directExecutor())
                        .build();

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);

        PhFlagsFixture.overrideFledgeAdSelectionFilteringEnabled(false);

        // Enable CTS to be run with versions of WebView < M105
        PhFlagsFixture.overrideEnforceIsolateMaxHeapSize(false);
        PhFlagsFixture.overrideIsolateMaxHeapSizeBytes(0);

        // Disable registerAdBeacon by default
        PhFlagsFixture.overrideFledgeRegisterAdBeaconEnabled(false);

        // Disable cpc billing by default
        PhFlagsFixture.overrideFledgeCpcBillingEnabled(false);

        // Disable ad selection prebuilt by default
        PhFlagsFixture.overrideFledgeAdSelectionPrebuiltUriEnabled(false);

        // Make sure the flags are picked up cold
        AdservicesTestHelper.killAdservicesProcess(sContext);

        // Disable data version header by default
        PhFlagsFixture.overrideFledgeDataVersionHeaderEnabled(false);

        // Ensure HTTP Cache is disabled
        PhFlagsFixture.overrideHttpClientCacheEnabled(false);

        mRequestMatcherPrefixMatch = (a, b) -> !b.isEmpty() && a.startsWith(b);
        mCacheBuster = mCacheBusterRandom.nextInt();
    }

    @After
    public void tearDown() throws Exception {
        if (!CtsWebViewSupportUtil.isJSSandboxAvailable(sContext)) {
            return;
        }

        leaveJoinedCustomAudiences();

        // Reset the filtering flag
        PhFlagsFixture.overrideFledgeAdSelectionFilteringEnabled(false);
        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    private String getCacheBusterPrefix() {
        return String.format("/%s", mCacheBuster);
    }

    private String getServerBaseAddress() {
        return String.format(
                "https://%s:%s%s",
                mMockWebServer.getHostName(), mMockWebServer.getPort(), getCacheBusterPrefix());
    }

    private void setupMockServer(MockWebServer mockWebServer) throws Exception {
        if (mMockWebServer != null) {
            mMockWebServer.shutdown();
        }
        mMockWebServer = mockWebServer;
        mServerBaseAddress = getServerBaseAddress();
        mLocalhostBuyerDomain = Uri.parse(mServerBaseAddress);
        mAdTechIdentifier = AdTechIdentifier.fromString(mMockWebServer.getHostName());
        Log.d(TAG, "Started default MockWebServer.");
    }

    @Test
    public void testFledgeAuctionSelectionFlow_happy_Path() throws Exception {
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        Semaphore impressionReportingSemaphore = new Semaphore(0);
        setupMockServer(
                getMockWebServer(
                        getDefaultDecisionLogicJs(),
                        getDefaultBiddingLogicJs(),
                        impressionReportingSemaphore));

        CustomAudience customAudience1 =
                createCustomAudience(mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, bidsForBuyer1);

        CustomAudience customAudience2 =
                createCustomAudience(mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, bidsForBuyer2);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        AdSelectionConfig adSelectionConfig = getDefaultAdSelectionConfig();

        Log.i(
                TAG,
                "Running ad selection with logic URI " + adSelectionConfig.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + adSelectionConfig.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(adSelectionConfig)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is rendered, since it had the highest bid and score
        Assert.assertEquals(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3"),
                outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), adSelectionConfig);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertTrue(impressionReportingSemaphore.tryAcquire(2, 10, TimeUnit.SECONDS));

        String prefix = getCacheBusterPrefix();

        mMockWebServerRule.verifyMockServerRequests(
                mMockWebServer,
                8,
                ImmutableList.of(
                        prefix + SELLER_DECISION_LOGIC_URI_PATH, // this call is done twice
                        prefix + BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1,
                        prefix + BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2,
                        prefix + BUYER_TRUSTED_SIGNAL_URI_PATH,
                        prefix + SELLER_TRUSTED_SIGNAL_URI_PATH,
                        prefix + SELLER_REPORTING_PATH,
                        prefix + BUYER_REPORTING_PATH),
                mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeAuctionSelectionFlowSuccessWithDataVersionHeader() throws Exception {
        PhFlagsFixture.overrideFledgeDataVersionHeaderEnabled(true);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        Semaphore impressionReportingSemaphore = new Semaphore(0);
        setupMockServer(
                getMockWebServer(
                        getDecisionLogicJsWithDataVersionHeader(),
                        getBiddingLogicJsWithDataVersionHeader(),
                        impressionReportingSemaphore));

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_1,
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION);

        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_2,
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        AdSelectionConfig adSelectionConfig =
                getAdSelectionConfig(SELLER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + adSelectionConfig.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + adSelectionConfig.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(adSelectionConfig)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is rendered, since it had the highest bid and score
        Assert.assertEquals(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3"),
                outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), adSelectionConfig);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertTrue(impressionReportingSemaphore.tryAcquire(2, 10, TimeUnit.SECONDS));

        String prefix = getCacheBusterPrefix();

        mMockWebServerRule.verifyMockServerRequests(
                mMockWebServer,
                8,
                ImmutableList.of(
                        prefix + SELLER_DECISION_LOGIC_URI_PATH, // this call is done twice
                        prefix + BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1,
                        prefix + BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2,
                        prefix + BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION,
                        prefix + SELLER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION,
                        prefix + SELLER_REPORTING_PATH + "?dataVersion=" + DATA_VERSION_2,
                        prefix + BUYER_REPORTING_PATH + "?dataVersion=" + DATA_VERSION_1),
                mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeAuctionSelectionFlowSuccessWithDataVersionHeader_unifiedTable()
            throws Exception {
        overrideShouldUseUnifiedTable(true);
        try {
            testFledgeAuctionSelectionFlowSuccessWithDataVersionHeader();
        } finally {
            overrideShouldUseUnifiedTable(false);
        }
    }

    @Test
    public void testFledgeAuctionSelectionFlowSuccessWithDataVersionHeaderSkipsSellerExceeds8Bits()
            throws Exception {
        PhFlagsFixture.overrideFledgeDataVersionHeaderEnabled(true);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        Semaphore impressionReportingSemaphore = new Semaphore(0);

        // Set seller data version to number greater than 8 bits
        setupMockServer(
                getMockWebServer(
                        getDecisionLogicJsWithDataVersionHeader(),
                        getBiddingLogicJsWithDataVersionHeader(),
                        impressionReportingSemaphore,
                        DATA_VERSION_1,
                        300));

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_1,
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION);

        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_2,
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        AdSelectionConfig adSelectionConfig =
                getAdSelectionConfig(SELLER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + adSelectionConfig.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + adSelectionConfig.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(adSelectionConfig)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is rendered, since it had the highest bid and score
        Assert.assertEquals(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3"),
                outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), adSelectionConfig);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertTrue(impressionReportingSemaphore.tryAcquire(2, 10, TimeUnit.SECONDS));

        String prefix = getCacheBusterPrefix();

        mMockWebServerRule.verifyMockServerRequests(
                mMockWebServer,
                8,
                ImmutableList.of(
                        prefix + SELLER_DECISION_LOGIC_URI_PATH, // this call is done twice
                        prefix + BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1,
                        prefix + BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2,
                        prefix + BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION,
                        prefix + SELLER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION,
                        prefix + SELLER_REPORTING_PATH,
                        prefix + BUYER_REPORTING_PATH + "?dataVersion=" + DATA_VERSION_1),
                mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeAuctionSelectionFlowSuccessWithDataVersionHeaderSkipsBuyerExceeds8Bits()
            throws Exception {
        PhFlagsFixture.overrideFledgeDataVersionHeaderEnabled(true);

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        Semaphore impressionReportingSemaphore = new Semaphore(0);

        // Set buyer data version to number greater than 8 bits
        setupMockServer(
                getMockWebServer(
                        getDecisionLogicJsWithDataVersionHeader(),
                        getBiddingLogicJsWithDataVersionHeader(),
                        impressionReportingSemaphore,
                        300,
                        DATA_VERSION_1));

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_1,
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION);

        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_2,
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION);

        // Joining custom audiences, no result to do assertion on. Failures will generate an
        // exception."
        joinCustomAudience(customAudience1);
        joinCustomAudience(customAudience2);

        AdSelectionConfig adSelectionConfig =
                getAdSelectionConfig(SELLER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION);

        Log.i(
                TAG,
                "Running ad selection with logic URI " + adSelectionConfig.getDecisionLogicUri());
        Log.i(
                TAG,
                "Decision logic URI domain is "
                        + adSelectionConfig.getDecisionLogicUri().getHost());

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(adSelectionConfig)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Assert that the ad3 from buyer 2 is rendered, since it had the highest bid and score
        Assert.assertEquals(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3"),
                outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), adSelectionConfig);

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertTrue(impressionReportingSemaphore.tryAcquire(2, 10, TimeUnit.SECONDS));

        String prefix = getCacheBusterPrefix();

        mMockWebServerRule.verifyMockServerRequests(
                mMockWebServer,
                8,
                ImmutableList.of(
                        prefix + SELLER_DECISION_LOGIC_URI_PATH, // this call is done twice
                        prefix + BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1,
                        prefix + BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2,
                        prefix + BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION,
                        prefix + SELLER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION,
                        prefix + SELLER_REPORTING_PATH + "?dataVersion=" + DATA_VERSION_1,
                        prefix + BUYER_REPORTING_PATH),
                mRequestMatcherPrefixMatch);
    }

    private void leaveJoinedCustomAudiences()
            throws ExecutionException, InterruptedException, TimeoutException {
        try {
            for (CustomAudience customAudience : mCustomAudiencesToCleanUp) {
                Log.i(
                        TAG,
                        "Cleanup: leaving custom audience "
                                + customAudience.getName()
                                + " for buyer"
                                + customAudience.getBuyer());
                mCustomAudienceClient
                        .leaveCustomAudience(customAudience.getBuyer(), customAudience.getName())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            mCustomAudiencesToCleanUp.clear();
        }
    }

    private void joinCustomAudience(CustomAudience customAudience)
            throws ExecutionException, InterruptedException, TimeoutException {
        mCustomAudiencesToCleanUp.add(customAudience);
        Log.i(
                TAG,
                "Joining custom audience "
                        + customAudience.getName()
                        + " for buyer"
                        + customAudience.getBuyer());
        mCustomAudienceClient
                .joinCustomAudience(customAudience)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private MockWebServer getMockWebServer(
            String decisionLogicJs, String biddingLogicJs, Semaphore impressionReportingSemaphore)
            throws Exception {
        return getMockWebServer(
                decisionLogicJs,
                biddingLogicJs,
                impressionReportingSemaphore,
                DATA_VERSION_1,
                DATA_VERSION_2);
    }

    private MockWebServer getMockWebServer(
            String decisionLogicJs,
            String biddingLogicJs,
            Semaphore impressionReportingSemaphore,
            int buyerDataVersion,
            int sellerDataVersion)
            throws Exception {
        return mMockWebServerRule.startMockWebServer(
                request -> {
                    // remove cache buster prefix
                    String requestPath = request.getPath().replace(getCacheBusterPrefix(), "");
                    if (requestPath.startsWith(SELLER_DECISION_LOGIC_URI_PATH)) {
                        return new MockResponse().setBody(decisionLogicJs);
                    }

                    if (requestPath.startsWith(BUYER_BIDDING_LOGIC_URI_PATH)) {
                        return new MockResponse().setBody(biddingLogicJs);
                    }

                    if (requestPath.startsWith(SELLER_REPORTING_PATH)
                            || requestPath.startsWith(BUYER_REPORTING_PATH)) {
                        impressionReportingSemaphore.release();
                        return new MockResponse().setResponseCode(200);
                    }

                    // The seller params vary based on runtime, so we are returning trusted
                    // signals based on correct path prefix
                    if (requestPath.startsWith(SELLER_TRUSTED_SIGNAL_URI_PATH)) {
                        return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                    }

                    if (requestPath.startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                        return new MockResponse().setBody(TRUSTED_BIDDING_SIGNALS.toString());
                    }

                    // Add seller trusted scoring uri path with data version header
                    if (requestPath.startsWith(SELLER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION)) {
                        return new MockResponse()
                                .setBody(TRUSTED_SCORING_SIGNALS.toString())
                                .addHeader(DATA_VERSION_HEADER_SCORING_KEY, sellerDataVersion);
                    }

                    // Add buyer trusted scoring uri path with data version header
                    if (requestPath.startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION)) {
                        return new MockResponse()
                                .setBody(TRUSTED_BIDDING_SIGNALS.toString())
                                .addHeader(DATA_VERSION_HEADER_BIDDING_KEY, buyerDataVersion);
                    }

                    return new MockResponse().setResponseCode(404);
                });
    }

    private CustomAudience createCustomAudience(
            final Uri buyerDomain, final String customAudienceSeq, List<Double> bids) {
        return createCustomAudience(
                buyerDomain, customAudienceSeq, bids, BUYER_TRUSTED_SIGNAL_URI_PATH);
    }

    private CustomAudience createCustomAudience(
            final Uri buyerDomain,
            final String customAudienceSeq,
            List<Double> bids,
            final String trustedBiddingUriPath) {

        // Generate ads for with bids provided
        List<AdData> ads = new ArrayList<>();

        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            AdData.Builder builder =
                    new AdData.Builder()
                            .setRenderUri(
                                    CommonFixture.getUri(
                                            buyerDomain.getAuthority(),
                                            AD_URI_PREFIX + customAudienceSeq + "/ad" + (i + 1)))
                            .setMetadata("{\"result\":" + bids.get(i) + "}");
            ads.add(builder.build());
        }

        return new CustomAudience.Builder()
                .setBuyer(AdTechIdentifier.fromString(buyerDomain.getHost()))
                .setName(
                        buyerDomain.getHost()
                                + customAudienceSeq
                                + CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setDailyUpdateUri(
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                AdTechIdentifier.fromString(buyerDomain.getAuthority())))
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        new TrustedBiddingData.Builder()
                                .setTrustedBiddingUri(
                                        Uri.parse(mServerBaseAddress + trustedBiddingUriPath))
                                .setTrustedBiddingKeys(
                                        TrustedBiddingDataFixture.getValidTrustedBiddingKeys())
                                .build())
                .setBiddingLogicUri(
                        Uri.parse(
                                mServerBaseAddress
                                        + BUYER_BIDDING_LOGIC_URI_PATH
                                        + customAudienceSeq))
                .setAds(ads)
                .build();
    }

    private String getDefaultDecisionLogicJs() {
        return "function scoreAd(ad, bid, auction_config, seller_signals,"
                + " trusted_scoring_signals, contextual_signal, user_signal,"
                + " custom_audience_signal) { \n"
                + "  return {'status': 0, 'score': bid };\n"
                + "}\n"
                + "function reportResult(ad_selection_config, render_uri, bid,"
                + " contextual_signals) { \n"
                + " return {'status': 0, 'results': {'signals_for_buyer':"
                + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                + mMockWebServerRule.uriForPath(getCacheBusterPrefix() + SELLER_REPORTING_PATH)
                + "' } };\n"
                + "}";
    }

    private String getDecisionLogicJsWithDataVersionHeader() {
        return "function scoreAd(ad, bid, auction_config, seller_signals, "
                + "trusted_scoring_signals,\n"
                + "  contextual_signals, user_signal, custom_audience_scoring_signals) {\n"
                + "  //return error if data version does not exist\n"
                + "  if(contextual_signals.dataVersion===null) {\n"
                + "      return {'status': -1};\n"
                + "  }\n"
                + "  return {'status': 0, 'score': bid };\n"
                + "}\n"
                + "function reportResult(ad_selection_config, render_uri, bid, "
                + "contextual_signals) {\n"
                + "  // Add the address of your reporting server here\n"
                + "  let reporting_address = '"
                + mMockWebServerRule.uriForPath(getCacheBusterPrefix() + SELLER_REPORTING_PATH)
                + "';\n"
                + "  return {'status': 0, 'results': {'signals_for_buyer': "
                + "'{\"signals_for_buyer\" : 1}'\n"
                + "          , 'reporting_uri': reporting_address + '?dataVersion=' "
                + "+ contextual_signals.dataVersion } };\n"
                + "}";
    }

    private String getDefaultBiddingLogicJs() {
        return "function generateBid(ad, auction_signals, per_buyer_signals,"
                + " trusted_bidding_signals, contextual_signals, custom_audience_signals) { \n"
                + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                + "}\n"
                + "function reportWin(ad_selection_signals, per_buyer_signals,"
                + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                + " return {'status': 0, 'results': {'reporting_uri': '"
                + mMockWebServerRule.uriForPath(getCacheBusterPrefix() + BUYER_REPORTING_PATH)
                + "' } };\n"
                + "}";
    }

    private String getBiddingLogicJsWithDataVersionHeader() {
        return "function generateBid(ad, auction_signals, per_buyer_signals, "
                + "trusted_bidding_signals, contextual_signals, custom_audience_signals) {\n"
                + "  //return error if data version does not exist\n"
                + "  if(contextual_signals.dataVersion===null) {\n"
                + "      return {'status': -1};\n"
                + "  }\n"
                + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result};\n"
                + "}"
                + "\n"
                + "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,\n"
                + " contextual_signals, custom_audience_signals) {\n"
                + "  let reporting_address = '"
                + mMockWebServerRule.uriForPath(getCacheBusterPrefix() + BUYER_REPORTING_PATH)
                + "';\n"
                + "  return {'status': 0, 'results': {'reporting_uri':\n"
                + "         reporting_address + '?dataVersion=' + contextual_signals.dataVersion}"
                + " };\n"
                + "}";
    }

    private AdSelectionConfig getDefaultAdSelectionConfig() {
        return AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                .setCustomAudienceBuyers(
                        ImmutableList.of(
                                AdTechIdentifier.fromString(mLocalhostBuyerDomain.getHost())))
                .setSeller(mAdTechIdentifier)
                .setDecisionLogicUri(Uri.parse(mServerBaseAddress + SELLER_DECISION_LOGIC_URI_PATH))
                .setTrustedScoringSignalsUri(
                        Uri.parse(mServerBaseAddress + SELLER_TRUSTED_SIGNAL_URI_PATH))
                .setPerBuyerSignals(
                        ImmutableMap.of(
                                AdTechIdentifier.fromString(mLocalhostBuyerDomain.getHost()),
                                AdSelectionSignals.fromString("{\"buyer_signals\":0}")))
                .build();
    }

    private AdSelectionConfig getAdSelectionConfig(String trustedScoringUriPath) {
        return AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                .setCustomAudienceBuyers(
                        ImmutableList.of(
                                AdTechIdentifier.fromString(mLocalhostBuyerDomain.getHost())))
                .setSeller(mAdTechIdentifier)
                .setDecisionLogicUri(Uri.parse(mServerBaseAddress + SELLER_DECISION_LOGIC_URI_PATH))
                .setTrustedScoringSignalsUri(Uri.parse(mServerBaseAddress + trustedScoringUriPath))
                .setPerBuyerSignals(
                        ImmutableMap.of(
                                AdTechIdentifier.fromString(mLocalhostBuyerDomain.getHost()),
                                AdSelectionSignals.fromString("{\"buyer_signals\":0}")))
                .build();
    }

    void overrideShouldUseUnifiedTable(boolean shouldUse) {
        ShellUtils.runShellCommand(
                String.format(
                        "device_config put adservices"
                                + " fledge_on_device_auction_should_use_unified_tables %s",
                        shouldUse ? "true" : "false"));
    }
}
