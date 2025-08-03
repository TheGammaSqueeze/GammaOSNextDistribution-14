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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.CommonFixture;
import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.RequiresLowRamDevice;
import com.android.adservices.common.SdkLevelSupportRule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class AdSelectionTest {

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor sCallbackExecutor = Executors.newCachedThreadPool();

    // TODO(b/291488819) - Remove SDK Level check if Fledge is enabled on R.
    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    // Skip the test if it runs on unsupported platforms.
    @Rule(order = 1)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Test
    @RequiresLowRamDevice
    public void testGetAdSelectionService_lowRamDevice_throwsIllegalStateException() {
        AdSelectionClient client =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(sCallbackExecutor)
                        .setUseGetMethodToCreateManagerInstance(true)
                        .build();

        AdSelectionSignals signals =
                AdSelectionSignals.fromString(
                        String.format(
                                "{\"valid\": true, \"publisher\": \"%s\"}",
                                CommonFixture.TEST_PACKAGE_NAME));
        AdSelectionConfig config =
                new AdSelectionConfig.Builder()
                        .setSeller(CommonFixture.VALID_BUYER_1)
                        .setPerBuyerSignals(ImmutableMap.of(CommonFixture.VALID_BUYER_1, signals))
                        .setCustomAudienceBuyers(ImmutableList.of(CommonFixture.VALID_BUYER_1))
                        .setAdSelectionSignals(signals)
                        .setSellerSignals(signals)
                        .setDecisionLogicUri(Uri.parse("http://example.com"))
                        .setTrustedScoringSignalsUri(Uri.parse("http://example.com"))
                        .build();

        Exception exception =
                assertThrows(ExecutionException.class, () -> client.selectAds(config).get());
        assertThat(exception).hasCauseThat().isInstanceOf(IllegalStateException.class);
        assertThat(exception).hasMessageThat().contains("Unable to find the AdSelection service");
    }
}
