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
import android.adservices.adselection.GetAdSelectionDataOutcome;
import android.adservices.adselection.GetAdSelectionDataRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.test.scenario.adservices.fledge.utils.CustomAudienceTestFixture;
import android.adservices.test.scenario.adservices.utils.SelectAdsFlagRule;
import android.content.Context;
import android.platform.test.microbenchmark.Microbenchmark;
import android.platform.test.rule.CleanPackageRule;
import android.platform.test.rule.KillAppsRule;
import android.platform.test.scenario.annotation.Scenario;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.compatibility.common.util.ShellUtils;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Scenario
@RunWith(Microbenchmark.class)
public class GetAdSelectionDataLatency {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String CUSTOM_AUDIENCE_ONE_BUYER_ONE_CA_ONE_AD =
            "CustomAudienceOneBuyerOneCaOneAd.json";
    private static final String SELLER = "ba-seller-5jyy5ulagq-uc.a.run.app";

    private static final int API_RESPONSE_TIMEOUT_SECONDS = 100;
    private static final AdSelectionClient AD_SELECTION_CLIENT =
            new AdSelectionClient.Builder()
                    .setContext(CONTEXT)
                    .setExecutor(CALLBACK_EXECUTOR)
                    .build();

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
                    .around(new SelectAdsFlagRule());

    @BeforeClass
    public static void setupBeforeClass() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);
    }

    private static final String TAG = "SelectAds";

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

    private static final long NANO_TO_MILLISECONDS = 1000000;

    @Before
    public void warmup() throws Exception {
        List<CustomAudience> customAudiences =
                CustomAudienceTestFixture.readCustomAudiences(
                        CUSTOM_AUDIENCE_ONE_BUYER_ONE_CA_ONE_AD);
        CustomAudienceTestFixture.joinCustomAudiences(customAudiences);

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdTechIdentifier.fromString(SELLER))
                        .build();
        GetAdSelectionDataOutcome outcome =
                AD_SELECTION_CLIENT
                        .getAdSelectionData(request)
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);
    }

    @Test
    public void test_withoutFiltering_varyingBuyers_1() throws Exception {
        List<CustomAudience> customAudiences =
                CustomAudienceFixture.getNValidCustomAudiences(
                        /* nBuyers= */ 1, /* nCAsPerBuyer= */ 1, /* nAdsPerCA= */ 5);
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
        long endTime = System.nanoTime();
        Log.i(
                TAG,
                generateLogLabel(
                        getClass().getSimpleName(),
                        "test_withoutFiltering_varyingBuyers_1",
                        (endTime - startTime) / NANO_TO_MILLISECONDS));

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);
    }

    @Test
    public void test_withoutFiltering_varyingBuyers_10() throws Exception {
        List<CustomAudience> customAudiences =
                CustomAudienceFixture.getNValidCustomAudiences(
                        /* nBuyers= */ 10, /* nCAsPerBuyer= */ 1, /* nAdsPerCA= */ 5);
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
        long endTime = System.nanoTime();
        Log.i(
                TAG,
                generateLogLabel(
                        getClass().getSimpleName(),
                        "test_withoutFiltering_varyingBuyers_10",
                        (endTime - startTime) / NANO_TO_MILLISECONDS));

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);
    }

    @Test
    public void test_withoutFiltering_varyingBuyers_100() throws Exception {
        List<CustomAudience> customAudiences =
                CustomAudienceFixture.getNValidCustomAudiences(
                        /* nBuyers= */ 100, /* nCAsPerBuyer= */ 1, /* nAdsPerCA= */ 5);
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
        long endTime = System.nanoTime();
        Log.i(
                TAG,
                generateLogLabel(
                        getClass().getSimpleName(),
                        "test_withoutFiltering_varyingBuyers_100",
                        (endTime - startTime) / NANO_TO_MILLISECONDS));

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);
    }

    @Test
    public void test_withoutFiltering_varyingBuyers_500() throws Exception {
        increasePayloadBucketSize();
        List<CustomAudience> customAudiences =
                CustomAudienceFixture.getNValidCustomAudiences(
                        /* nBuyers= */ 500, /* nCAsPerBuyer= */ 1, /* nAdsPerCA= */ 5);
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
        long endTime = System.nanoTime();
        Log.i(
                TAG,
                generateLogLabel(
                        getClass().getSimpleName(),
                        "test_withoutFiltering_varyingBuyers_500",
                        (endTime - startTime) / NANO_TO_MILLISECONDS));

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);
        resetPayloadBucketSize();
    }

    @Test
    public void test_withoutFiltering_varyingBuyers_1000() throws Exception {
        increasePayloadBucketSize();
        List<CustomAudience> customAudiences =
                CustomAudienceFixture.getNValidCustomAudiences(
                        /* nBuyers= */ 1000, /* nCAsPerBuyer= */ 1, /* nAdsPerCA= */ 5);
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
        long endTime = System.nanoTime();
        Log.i(
                TAG,
                generateLogLabel(
                        getClass().getSimpleName(),
                        "test_withoutFiltering_varyingBuyers_1000",
                        (endTime - startTime) / NANO_TO_MILLISECONDS));

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);
        resetPayloadBucketSize();
    }

    @Test
    public void test_withoutFiltering_varyingCAs_1() throws Exception {
        List<CustomAudience> customAudiences =
                CustomAudienceFixture.getNValidCustomAudiences(
                        /* nBuyers= */ 4, /* nCAsPerBuyer= */ 1, /* nAdsPerCA= */ 5);
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
        long endTime = System.nanoTime();
        Log.i(
                TAG,
                generateLogLabel(
                        getClass().getSimpleName(),
                        "test_withoutFiltering_varyingCAs_1",
                        (endTime - startTime) / NANO_TO_MILLISECONDS));

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);
    }

    @Test
    public void test_withoutFiltering_varyingCAs_10() throws Exception {
        List<CustomAudience> customAudiences =
                CustomAudienceFixture.getNValidCustomAudiences(
                        /* nBuyers= */ 4, /* nCAsPerBuyer= */ 10, /* nAdsPerCA= */ 5);
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
        long endTime = System.nanoTime();
        Log.i(
                TAG,
                generateLogLabel(
                        getClass().getSimpleName(),
                        "test_withoutFiltering_varyingCAs_10",
                        (endTime - startTime) / NANO_TO_MILLISECONDS));

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);
    }

    @Test
    public void test_withoutFiltering_varyingCAs_100() throws Exception {
        List<CustomAudience> customAudiences =
                CustomAudienceFixture.getNValidCustomAudiences(
                        /* nBuyers= */ 4, /* nCAsPerBuyer= */ 100, /* nAdsPerCA= */ 5);
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
        long endTime = System.nanoTime();
        Log.i(
                TAG,
                generateLogLabel(
                        getClass().getSimpleName(),
                        "test_withoutFiltering_varyingBuyers_100",
                        (endTime - startTime) / NANO_TO_MILLISECONDS));

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);
    }

    @Test
    public void test_withoutFiltering_varyingCAs_500() throws Exception {
        increaseMaxCAsPerApp();
        List<CustomAudience> customAudiences =
                CustomAudienceFixture.getNValidCustomAudiences(
                        /* nBuyers= */ 4, /* nCAsPerBuyer= */ 500, /* nAdsPerCA= */ 5);
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
        long endTime = System.nanoTime();
        Log.i(
                TAG,
                generateLogLabel(
                        getClass().getSimpleName(),
                        "test_withoutFiltering_varyingCAs_500",
                        (endTime - startTime) / NANO_TO_MILLISECONDS));

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);
        resetMaxCAsPerApp();
    }

    @Test
    public void test_withoutFiltering_varyingCAs_1000() throws Exception {
        increaseMaxCAsPerApp();
        increasePayloadBucketSize();
        List<CustomAudience> customAudiences =
                CustomAudienceFixture.getNValidCustomAudiences(
                        /* nBuyers= */ 4, /* nCAsPerBuyer= */ 1000, /* nAdsPerCA= */ 5);
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
        long endTime = System.nanoTime();
        Log.i(
                TAG,
                generateLogLabel(
                        getClass().getSimpleName(),
                        "test_withoutFiltering_varyingCAs_1000",
                        (endTime - startTime) / NANO_TO_MILLISECONDS));

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);
        resetPayloadBucketSize();
        resetMaxCAsPerApp();
    }

    @Test
    public void test_withoutFiltering_varyingAds_5() throws Exception {
        List<CustomAudience> customAudiences =
                CustomAudienceFixture.getNValidCustomAudiences(
                        /* nBuyers= */ 4, /* nCAsPerBuyer= */ 10, /* nAdsPerCA= */ 5);
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
        long endTime = System.nanoTime();
        Log.i(
                TAG,
                generateLogLabel(
                        getClass().getSimpleName(),
                        "test_withoutFiltering_varyingAds_5",
                        (endTime - startTime) / NANO_TO_MILLISECONDS));

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);
    }

    @Test
    public void test_withoutFiltering_varyingAds_25() throws Exception {
        List<CustomAudience> customAudiences =
                CustomAudienceFixture.getNValidCustomAudiences(
                        /* nBuyers= */ 4, /* nCAsPerBuyer= */ 10, /* nAdsPerCA= */ 25);
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
        long endTime = System.nanoTime();
        Log.i(
                TAG,
                generateLogLabel(
                        getClass().getSimpleName(),
                        "test_withoutFiltering_varyingAds_25",
                        (endTime - startTime) / NANO_TO_MILLISECONDS));

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);
    }

    @Test
    public void test_withoutFiltering_varyingAds_125() throws Exception {
        increaseMaximumAds();
        List<CustomAudience> customAudiences =
                CustomAudienceFixture.getNValidCustomAudiences(
                        /* nBuyers= */ 4, /* nCAsPerBuyer= */ 10, /* nAdsPerCA= */ 125);
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
        long endTime = System.nanoTime();
        Log.i(
                TAG,
                generateLogLabel(
                        getClass().getSimpleName(),
                        "test_withoutFiltering_varyingAds_125",
                        (endTime - startTime) / NANO_TO_MILLISECONDS));

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);
        resetMaximumAds();
    }

    @Test
    public void test_withoutFiltering_varyingAds_500() throws Exception {
        increaseMaximumAds();
        List<CustomAudience> customAudiences =
                CustomAudienceFixture.getNValidCustomAudiences(
                        /* nBuyers= */ 4, /* nCAsPerBuyer= */ 10, /* nAdsPerCA= */ 500);
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
        long endTime = System.nanoTime();
        Log.i(
                TAG,
                generateLogLabel(
                        getClass().getSimpleName(),
                        "test_withoutFiltering_varyingAds_500",
                        (endTime - startTime) / NANO_TO_MILLISECONDS));

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);
        resetMaximumAds();
    }

    @Test
    public void test_withoutFiltering_varyingAds_1000() throws Exception {
        increaseMaximumAds();
        increasePayloadBucketSize();
        List<CustomAudience> customAudiences =
                CustomAudienceFixture.getNValidCustomAudiences(
                        /* nBuyers= */ 4, /* nCAsPerBuyer= */ 10, /* nAdsPerCA= */ 1000);
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
        long endTime = System.nanoTime();
        Log.i(
                TAG,
                generateLogLabel(
                        getClass().getSimpleName(),
                        "test_withoutFiltering_varyingAds_1000",
                        (endTime - startTime) / NANO_TO_MILLISECONDS));

        CustomAudienceTestFixture.leaveCustomAudience(customAudiences);
        resetPayloadBucketSize();
        resetMaximumAds();
    }

    void increaseMaxCAsPerApp() {
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_custom_audience_max_count 10000");
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_custom_audience_per_app_max_count 10000");
    }

    void resetMaxCAsPerApp() {
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_custom_audience_max_count 4000");
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_custom_audience_per_app_max_count 1000");
    }

    void increaseMaximumAds() {
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_custom_audience_max_num_ads 1000");
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_custom_audience_max_ads_size_b 1048576");
    }

    void resetMaximumAds() {
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_custom_audience_max_num_ads 100");
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_custom_audience_max_ads_size_b 10240");
    }

    void increasePayloadBucketSize() {
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_auction_server_payload_bucket_sizes"
                    + " 0,1024,2048,4096,8192,16384,32768,65536,131072,262174,524288,1048576");
    }

    void resetPayloadBucketSize() {
        ShellUtils.runShellCommand(
                "device_config put adservices fledge_auction_server_payload_bucket_sizes"
                    + " 0,1024,2048,4096,8192,16384,32768,65536");
    }
}
