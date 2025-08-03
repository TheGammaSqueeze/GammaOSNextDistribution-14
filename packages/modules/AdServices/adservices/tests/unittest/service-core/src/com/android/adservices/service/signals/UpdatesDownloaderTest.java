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

import static com.android.adservices.service.signals.SignalsFixture.DEV_CONTEXT;
import static com.android.adservices.service.signals.UpdatesDownloader.PACKAGE_NAME_HEADER;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.adservices.common.CommonFixture;
import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

@RunWith(MockitoJUnitRunner.class)
public class UpdatesDownloaderTest {

    private static final Uri URI = Uri.parse("https://example.com");
    private static final String JSON = "{\"a\":\"b\"}";

    @Mock private AdServicesHttpsClient mMockAdServicesHttpsClient;

    private UpdatesDownloader mUpdatesDownloader;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Before
    public void setup() {
        mUpdatesDownloader =
                new UpdatesDownloader(
                        AdServicesExecutors.getLightWeightExecutor(), mMockAdServicesHttpsClient);
    }

    @Test
    public void testGetValidJsonSuccess() throws Exception {
        AdServicesHttpClientResponse response =
                AdServicesHttpClientResponse.builder().setResponseBody(JSON).build();
        SettableFuture<AdServicesHttpClientResponse> returnValue = SettableFuture.create();
        returnValue.set(response);

        ImmutableMap<String, String> requestProperties =
                ImmutableMap.of(PACKAGE_NAME_HEADER, CommonFixture.TEST_PACKAGE_NAME_1);
        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(requestProperties)
                        .setUri(URI)
                        .setDevContext(DEV_CONTEXT)
                        .build();
        when(mMockAdServicesHttpsClient.fetchPayload(request)).thenReturn(returnValue);

        assertEquals(
                JSON,
                mUpdatesDownloader
                        .getUpdateJson(URI, CommonFixture.TEST_PACKAGE_NAME_1, DEV_CONTEXT)
                        .get()
                        .toString());
    }

    @Test
    public void testInvalidJsonThrowsExecutionException() {
        AdServicesHttpClientResponse response =
                AdServicesHttpClientResponse.builder().setResponseBody("{abc").build();
        SettableFuture<AdServicesHttpClientResponse> returnValue = SettableFuture.create();
        returnValue.set(response);
        ImmutableMap<String, String> requestProperties =
                ImmutableMap.of(PACKAGE_NAME_HEADER, CommonFixture.TEST_PACKAGE_NAME_1);
        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(requestProperties)
                        .setUri(URI)
                        .setDevContext(DEV_CONTEXT)
                        .build();
        when(mMockAdServicesHttpsClient.fetchPayload(request)).thenReturn(returnValue);

        assertThrows(
                ExecutionException.class,
                () ->
                        mUpdatesDownloader
                                .getUpdateJson(URI, CommonFixture.TEST_PACKAGE_NAME_1, DEV_CONTEXT)
                                .get());
    }

    @Test
    public void testPayloadSizeTooLargeThrowsIae() {
        ImmutableMap<String, String> requestProperties =
                ImmutableMap.of(PACKAGE_NAME_HEADER, CommonFixture.TEST_PACKAGE_NAME_1);
        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(requestProperties)
                        .setUri(URI)
                        .setDevContext(DEV_CONTEXT)
                        .build();
        when(mMockAdServicesHttpsClient.fetchPayload(request))
                .thenReturn(Futures.immediateFailedFuture(new IOException()));

        Exception e =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mUpdatesDownloader
                                        .getUpdateJson(
                                                URI, CommonFixture.TEST_PACKAGE_NAME_1, DEV_CONTEXT)
                                        .get());
        assertTrue(e.getCause() instanceof IOException);
    }
}
