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
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.adselection.UpdateAdCounterHistogramRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.clients.topics.AdvertisingTopicsClient;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.FetchAndJoinCustomAudienceRequest;
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

/** In the ad_services_config.xml file, API access is revoked for all but a few, for this test. */
@RunWith(AndroidJUnit4.class)
// TODO: Add tests for measurement (b/238194122).
public class PermissionsAppOptOutTest {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String CALLER_NOT_AUTHORIZED =
            "java.lang.SecurityException: Caller is not authorized to call this API. "
                    + "Caller is not allowed.";

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
    public void testAppOptOut_topics() {
        AdvertisingTopicsClient advertisingTopicsClient1 =
                new AdvertisingTopicsClient.Builder()
                        .setContext(sContext)
                        .setSdkName("sdk1")
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class, () -> advertisingTopicsClient1.getTopics().get());
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testNoEnrollment_fledgeJoinCustomAudience() {
        AdvertisingCustomAudienceClient customAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        CustomAudience customAudience =
                new CustomAudience.Builder()
                        .setBuyer(AdTechIdentifier.fromString("buyer.example.com"))
                        .setName("exampleCustomAudience")
                        .setDailyUpdateUri(Uri.parse("https://buyer.example.com/daily-update"))
                        .setBiddingLogicUri(Uri.parse("https://buyer.example.com/bidding-logic"))
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> customAudienceClient.joinCustomAudience(customAudience).get());
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testNoEnrollment_fledgeFetchAndJoinCustomAudience() {
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
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testWithEnrollment_fledgeJoinCustomAudience()
            throws ExecutionException, InterruptedException {
        AdvertisingCustomAudienceClient customAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        // The "test.com" buyer is a pre-seeded enrolled ad tech
        CustomAudience customAudience =
                new CustomAudience.Builder()
                        .setBuyer(AdTechIdentifier.fromString("test.com"))
                        .setName("exampleCustomAudience")
                        .setDailyUpdateUri(Uri.parse("https://test.com/daily-update"))
                        .setBiddingLogicUri(Uri.parse("https://test.com/bidding-logic"))
                        .build();

        // When the ad tech is properly enrolled, just verify that no error is thrown
        customAudienceClient.joinCustomAudience(customAudience).get();
    }

    @Test
    public void testNoEnrollment_fledgeLeaveCustomAudience() {
        AdvertisingCustomAudienceClient customAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                customAudienceClient
                                        .leaveCustomAudience(
                                                AdTechIdentifier.fromString("buyer.example.com"),
                                                "exampleCustomAudience")
                                        .get());
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testWithEnrollment_fledgeLeaveCustomAudience()
            throws ExecutionException, InterruptedException {
        AdvertisingCustomAudienceClient customAudienceClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        // The "test.com" buyer is a pre-seeded enrolled ad tech
        // When the ad tech is properly enrolled, just verify that no error is thrown
        customAudienceClient
                .leaveCustomAudience(
                        AdTechIdentifier.fromString("test.com"), "exampleCustomAudience")
                .get();
    }

    @Test
    public void testNoEnrollment_selectAds_adSelectionConfig() {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfig(
                        AdTechIdentifier.fromString("seller.example.com"));

        AdSelectionClient mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mAdSelectionClient.selectAds(adSelectionConfig).get());
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testWithEnrollment_selectAds_adSelectionConfig() {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        // The "test.com" buyer is a pre-seeded enrolled ad tech
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfig(
                        AdTechIdentifier.fromString("test.com"));

        AdSelectionClient mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        // When the ad tech is properly enrolled, just verify that the error thrown is not due to
        // enrollment
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mAdSelectionClient.selectAds(adSelectionConfig).get());
        assertThat(exception.getMessage()).isNotEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testNoEnrollment_selectAds_adSelectionFromOutcomesConfig() {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        AdTechIdentifier.fromString("unauthorized.seller.com"));

        AdSelectionClient mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class, () -> mAdSelectionClient.selectAds(config).get());
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testWithEnrollment_selectAds_adSelectionFromOutcomesConfig() {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        // The "test.com" buyer is a pre-seeded enrolled ad tech
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        AdTechIdentifier.fromString("test.com"));

        AdSelectionClient mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        // When the ad tech is properly enrolled, just verify that the error thrown is not due to
        // enrollment
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class, () -> mAdSelectionClient.selectAds(config).get());
        assertThat(exception.getMessage()).isNotEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testNoEnrollment_reportImpression() {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfig(
                        AdTechIdentifier.fromString("seller.example.com"));

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
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testWithEnrollment_reportImpression() {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        // The "test.com" buyer is a pre-seeded enrolled ad tech
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfig(
                        AdTechIdentifier.fromString("test.com"));

        long adSelectionId = 1;

        AdSelectionClient mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ReportImpressionRequest request =
                new ReportImpressionRequest(adSelectionId, adSelectionConfig);

        // When the ad tech is properly enrolled, just verify that the error thrown is not due to
        // enrollment
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mAdSelectionClient.reportImpression(request).get());
        assertThat(exception.getMessage()).isNotEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testNoEnrollment_updateAdCounterHistogram() {
        long adSelectionId = 1;

        AdSelectionClient mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        UpdateAdCounterHistogramRequest request =
                new UpdateAdCounterHistogramRequest.Builder(
                                adSelectionId,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                AdTechIdentifier.fromString("seller.example.com"))
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mAdSelectionClient.updateAdCounterHistogram(request).get());
        assertThat(exception.getMessage()).isEqualTo(CALLER_NOT_AUTHORIZED);
    }

    @Test
    public void testWithEnrollment_updateAdCounterHistogram()
            throws ExecutionException, InterruptedException {
        long adSelectionId = 1;

        AdSelectionClient mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        UpdateAdCounterHistogramRequest request =
                new UpdateAdCounterHistogramRequest.Builder(
                                adSelectionId,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                AdTechIdentifier.fromString("test.com"))
                        .build();

        mAdSelectionClient.updateAdCounterHistogram(request).get();
    }
}
