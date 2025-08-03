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

import android.Manifest;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.GetAdSelectionDataOutcome;
import android.adservices.adselection.GetAdSelectionDataRequest;
import android.adservices.adselection.PersistAdSelectionResultRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.test.scenario.adservices.fledge.utils.CustomAudienceTestFixture;
import android.adservices.test.scenario.adservices.fledge.utils.FakeAdExchangeServer;
import android.adservices.test.scenario.adservices.fledge.utils.SelectAdResponse;
import android.adservices.test.scenario.adservices.utils.SelectAdsFlagRule;
import android.content.Context;
import android.platform.test.rule.CleanPackageRule;
import android.platform.test.rule.KillAppsRule;
import android.platform.test.scenario.annotation.Scenario;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdservicesTestHelper;

import com.google.common.base.Ticker;
import com.google.common.io.BaseEncoding;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Scenario
@RunWith(JUnit4.class)
public class ServerAuctionOneBuyerLargeCaLatency {
    private static final String TAG = "SelectAds";

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    private static final String AD_WINNER_DOMAIN = "https://ba-buyer-5jyy5ulagq-uc.a.run.app/";
    private static final String SELLER = "ba-seller-5jyy5ulagq-uc.a.run.app";
    private static final String SFE_ADDRESS =
            "https://seller1-paprod.sfe.ppapi.gcp.pstest.dev/v1/selectAd";

    private static final String CUSTOM_AUDIENCE_SERVER_AUCTION_ONE_BUYER_LARGE_CA =
            "ServerPerformanceCustomAudiencesOneBuyerLargeCa.json";

    private static final String CONTEXTUAL_SIGNALS_PERFORMANCE =
            "ContextualSignalsPerformance.json";

    private static final boolean SERVER_RESPONSE_LOGGING_ENABLED = false;

    private static final int NUM_WARMUP_ITERATIONS = 5;

    private static final int API_RESPONSE_TIMEOUT_SECONDS = 100;
    private static final long NANO_TO_MILLISECONDS = 1000000;
    private static final AdSelectionClient AD_SELECTION_CLIENT =
            new AdSelectionClient.Builder()
                    .setContext(CONTEXT)
                    .setExecutor(CALLBACK_EXECUTOR)
                    .build();

    protected final Ticker mTicker =
            new Ticker() {
                public long read() {
                    return android.os.SystemClock.elapsedRealtimeNanos();
                }
            };

    @Rule
    public RuleChain rules =
            RuleChain.outerRule(
                            new KillAppsRule(
                                    AdservicesTestHelper.getAdServicesPackageName(CONTEXT)))
                    .around(
                            // CleanPackageRule should not execute after each test method because
                            // there's a chance it interferes with ShowmapSnapshotListener snapshot
                            // at the end of the test, impacting collection of memory metrics for
                            // AdServices process.
                            new CleanPackageRule(
                                    AdservicesTestHelper.getAdServicesPackageName(CONTEXT),
                                    /* clearOnStarting = */ true,
                                    /* clearOnFinished = */ false))
                    .around(new SelectAdsFlagRule(/* useServerAuctionPublicCoordinator = */ true));

    /** Give the required permissions to the APK */
    @BeforeClass
    public static void setupBeforeClass() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);
    }

    @Before
    public void warmup() throws Exception {
        try {
            for (int count = 0; count < NUM_WARMUP_ITERATIONS; count++) {
                List<CustomAudience> customAudiences =
                        CustomAudienceTestFixture.readCustomAudiences(
                                CUSTOM_AUDIENCE_SERVER_AUCTION_ONE_BUYER_LARGE_CA);
                CustomAudienceTestFixture.joinCustomAudiences(customAudiences);

                GetAdSelectionDataRequest request =
                        new GetAdSelectionDataRequest.Builder()
                                .setSeller(AdTechIdentifier.fromString(SELLER))
                                .build();
                GetAdSelectionDataOutcome outcome =
                        AD_SELECTION_CLIENT
                                .getAdSelectionData(request)
                                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                SelectAdResponse selectAdResponse =
                        FakeAdExchangeServer.runServerAuction(
                                CONTEXTUAL_SIGNALS_PERFORMANCE,
                                outcome.getAdSelectionData(),
                                SFE_ADDRESS,
                                SERVER_RESPONSE_LOGGING_ENABLED);

                PersistAdSelectionResultRequest persistAdSelectionResultRequest =
                        new PersistAdSelectionResultRequest.Builder()
                                .setAdSelectionId(outcome.getAdSelectionId())
                                .setSeller(AdTechIdentifier.fromString(SELLER))
                                .setAdSelectionResult(
                                        BaseEncoding.base64()
                                                .decode(selectAdResponse.auctionResultCiphertext))
                                .build();

                AdSelectionOutcome adSelectionOutcome =
                        AD_SELECTION_CLIENT
                                .persistAdSelectionResult(persistAdSelectionResultRequest)
                                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                CustomAudienceTestFixture.leaveCustomAudience(customAudiences);

                Thread.sleep(1000L);
            }
        } catch (Exception e) {
            Log.w(
                    TAG,
                    "Exception encountered during warmup: "
                            + e.getMessage()
                            + ". Continuing execution.");
        }
    }

    @Test
    public void runAdSelection_oneBuyerLargeCa_realData_remarketingWinner() throws Exception {
        List<CustomAudience> customAudiences =
                CustomAudienceTestFixture.readCustomAudiences(
                        CUSTOM_AUDIENCE_SERVER_AUCTION_ONE_BUYER_LARGE_CA);

        CustomAudienceTestFixture.joinCustomAudiences(customAudiences);

        long startTime = System.nanoTime();

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdTechIdentifier.fromString(SELLER))
                        .build();
        GetAdSelectionDataOutcome outcome =
                AD_SELECTION_CLIENT
                        .getAdSelectionData(request)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        long adSelectionEndTime = System.nanoTime();
        Log.i(
                TAG,
                generateLogLabel(
                        getClass().getSimpleName(),
                        "adSelection",
                        (adSelectionEndTime - startTime) / NANO_TO_MILLISECONDS));

        SelectAdResponse selectAdResponse =
                FakeAdExchangeServer.runServerAuction(
                        CONTEXTUAL_SIGNALS_PERFORMANCE,
                        outcome.getAdSelectionData(),
                        SFE_ADDRESS,
                        SERVER_RESPONSE_LOGGING_ENABLED);

        long serverCallEndTime = System.nanoTime();
        Log.i(
                TAG,
                generateLogLabel(
                        getClass().getSimpleName(),
                        "b&a",
                        (serverCallEndTime - adSelectionEndTime) / NANO_TO_MILLISECONDS));

        PersistAdSelectionResultRequest persistAdSelectionResultRequest =
                new PersistAdSelectionResultRequest.Builder()
                        .setAdSelectionId(outcome.getAdSelectionId())
                        .setSeller(AdTechIdentifier.fromString(SELLER))
                        .setAdSelectionResult(
                                BaseEncoding.base64()
                                        .decode(selectAdResponse.auctionResultCiphertext))
                        .build();

        AdSelectionOutcome adSelectionOutcome =
                AD_SELECTION_CLIENT
                        .persistAdSelectionResult(persistAdSelectionResultRequest)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        long endTime = System.nanoTime();

        Log.i(
                TAG,
                generateLogLabel(
                        getClass().getSimpleName(),
                        "persist",
                        (endTime - serverCallEndTime) / NANO_TO_MILLISECONDS));
        Log.i(
                TAG,
                generateLogLabel(
                        getClass().getSimpleName(),
                        "total",
                        (endTime - startTime) / NANO_TO_MILLISECONDS));

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);

        Assert.assertFalse(adSelectionOutcome.getRenderUri().toString().isEmpty());
    }

    private String generateLogLabel(String classSimpleName, String testName, long elapsedMs) {
        return "("
                + "SELECT_ADS_LATENCY_"
                + classSimpleName
                + "#"
                + testName
                + ": "
                + elapsedMs
                + " ms)";
    }
}
