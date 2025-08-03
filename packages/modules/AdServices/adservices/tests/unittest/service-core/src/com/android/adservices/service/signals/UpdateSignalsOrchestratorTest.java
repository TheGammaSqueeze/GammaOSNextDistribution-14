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

package com.android.adservices.service.signals;

import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME_1;

import static com.android.adservices.service.signals.SignalsFixture.DEV_CONTEXT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public class UpdateSignalsOrchestratorTest {

    private static final long TEST_TIMEOUT_SECONDS = 10L;
    private static final Uri URI = Uri.parse("https://example.com");
    private static final String JSON = "{\"a\":\"b\"}";
    @Mock private UpdatesDownloader mUpdatesDownloader;
    @Mock private UpdateProcessingOrchestrator mUpdateProcessingOrchestrator;
    @Mock private AdTechUriValidator mAdTechUriValidator;

    private UpdateSignalsOrchestrator mUpdateSignalsOrchestrator;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Before
    public void setup() {
        mUpdateSignalsOrchestrator =
                new UpdateSignalsOrchestrator(
                        AdServicesExecutors.getBackgroundExecutor(),
                        mUpdatesDownloader,
                        mUpdateProcessingOrchestrator,
                        mAdTechUriValidator,
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI);
    }

    @Test
    public void testOrchestrateUpdate() throws Exception {
        SettableFuture future = SettableFuture.create();
        future.set(new JSONObject(JSON));
        FluentFuture<JSONObject> returnValue = FluentFuture.from(future);
        when(mUpdatesDownloader.getUpdateJson(URI, TEST_PACKAGE_NAME_1, DEV_CONTEXT))
                .thenReturn(returnValue);

        mUpdateSignalsOrchestrator
                .orchestrateUpdate(
                        URI, CommonFixture.VALID_BUYER_1, TEST_PACKAGE_NAME_1, DEV_CONTEXT)
                .get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        verify(mUpdatesDownloader).getUpdateJson(eq(URI), eq(TEST_PACKAGE_NAME_1), eq(DEV_CONTEXT));
        verify(mUpdateProcessingOrchestrator)
                .processUpdates(
                        any(AdTechIdentifier.class),
                        anyString(),
                        any(Instant.class),
                        any(JSONObject.class),
                        any(DevContext.class));
        verify(mAdTechUriValidator).addValidation(eq(URI), any());
    }
}
