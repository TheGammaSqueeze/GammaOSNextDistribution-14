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

package com.android.adservices.tests.permissions;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfigFixture;
import android.adservices.adselection.ReportEventRequest;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.adselection.UpdateAdCounterHistogramRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.FetchAndJoinCustomAudienceRequest;
import android.adservices.topics.GetTopicsResponse;
import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.service.js.JSScriptEngine;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
// TODO: Add tests for measurement (b/238194122).
public class PermissionsValidTest {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String PERMISSION_NOT_REQUESTED =
            "Caller is not authorized to call this API. Permission was not requested.";

    @Rule(order = 0)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Rule(order = 1)
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forAllApisEnabledTests()
                    .setCompatModeFlags()
                    .setPpapiAppAllowList(sContext.getPackageName());

    @Before
    public void setup() {
        // Kill AdServices process
        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    @Test
    public void testValidPermissions_topics() throws Exception {
        AdvertisingTopicsClient advertisingTopicsClient1 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk1")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        GetTopicsResponse sdk1Result = advertisingTopicsClient1.getTopics().get();
        // Not getting an error here indicates that permissions are valid. The valid case is also
        // tested in TopicsManagerTest.
        assertThat(sdk1Result.getTopics()).isEmpty();
    }

    @Test
    public void testValidPermissions_fledgeJoinCustomAudience()
            throws ExecutionException, InterruptedException {
        AdvertisingCustomAudienceClient customAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        CustomAudience customAudience =
                new CustomAudience.Builder()
                        .setBuyer(AdTechIdentifier.fromString("test.com"))
                        .setName("exampleCustomAudience")
                        .setDailyUpdateUri(Uri.parse("https://test.com/daily-update"))
                        .setBiddingLogicUri(Uri.parse("https://test.com/bidding-logic"))
                        .build();

        customAudienceClient.joinCustomAudience(customAudience).get();
    }

    @Test
    public void testValidPermissions_fledgeFetchAndJoinCustomAudience()
            throws ExecutionException, InterruptedException {
        AdvertisingCustomAudienceClient customAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(
                                Uri.parse("https://buyer.example.com/fetch/ca"))
                        .setName("exampleCustomAudience")
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> customAudienceClient.fetchAndJoinCustomAudience(request).get());
        // We only need to get past the permissions check for this test to be valid
        assertThat(exception.getMessage()).isNotEqualTo(PERMISSION_NOT_REQUESTED);
    }

    @Test
    public void testValidPermissions_selectAds_adSelectionConfig() {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        AdSelectionClient mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mAdSelectionClient.selectAds(adSelectionConfig).get());
        // We only need to get past the permissions check for this test to be valid
        assertThat(exception.getMessage()).isNotEqualTo(PERMISSION_NOT_REQUESTED);
    }

    @Test
    public void testValidPermissions_selectAds_adSelectionFromOutcomesConfig() {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        AdSelectionClient mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class, () -> mAdSelectionClient.selectAds(config).get());
        // We only need to get past the permissions check for this test to be valid
        assertThat(exception.getMessage()).isNotEqualTo(PERMISSION_NOT_REQUESTED);
    }

    @Test
    public void testValidPermissions_reportImpression()
            throws ExecutionException, InterruptedException {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        long adSelectionId = 1;

        AdSelectionClient mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ReportImpressionRequest request =
                new ReportImpressionRequest(adSelectionId, adSelectionConfig);

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mAdSelectionClient.reportImpression(request).get());
        // We only need to get past the permissions check for this test to be valid
        assertThat(exception.getMessage()).isNotEqualTo(PERMISSION_NOT_REQUESTED);
    }

    @Test
    public void testValidPermissions_reportEvent() {
        long adSelectionId = 1;
        String eventKey = "click";
        String eventData = "{\"key\":\"value\"}";

        AdSelectionClient mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ReportEventRequest request =
                new ReportEventRequest.Builder(
                                adSelectionId,
                                eventKey,
                                eventData,
                                ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER
                                        | ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mAdSelectionClient.reportEvent(request).get());
        // We only need to get past the permissions check for this test to be valid
        assertThat(exception.getMessage()).isNotEqualTo(PERMISSION_NOT_REQUESTED);
    }

    @Test
    public void testValidPermissions_updateAdCounterHistogram() {
        long adSelectionId = 1;

        AdSelectionClient mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        UpdateAdCounterHistogramRequest request =
                new UpdateAdCounterHistogramRequest.Builder(
                                adSelectionId,
                                FrequencyCapFilters.AD_EVENT_TYPE_IMPRESSION,
                                AdTechIdentifier.fromString("test.com"))
                        .build();
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mAdSelectionClient.updateAdCounterHistogram(request).get());

        // We only need to get past the permissions check for this test to be valid
        assertThat(exception.getMessage()).isNotEqualTo(PERMISSION_NOT_REQUESTED);
    }

    @Test
    public void testValidPermissions_fledgeLeaveCustomAudience()
            throws ExecutionException, InterruptedException {
        AdvertisingCustomAudienceClient customAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        customAudienceClient
                .leaveCustomAudience(
                        AdTechIdentifier.fromString("test.com"), "exampleCustomAudience")
                .get();
    }
}
