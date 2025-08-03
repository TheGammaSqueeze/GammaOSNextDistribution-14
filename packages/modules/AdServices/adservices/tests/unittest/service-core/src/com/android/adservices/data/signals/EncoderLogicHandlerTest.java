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

package com.android.adservices.data.signals;

import static com.android.adservices.data.signals.EncoderLogicHandler.ENCODER_VERSION_RESPONSE_HEADER;
import static com.android.adservices.data.signals.EncoderLogicHandler.FALLBACK_VERSION;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

public class EncoderLogicHandlerTest {

    @Rule public MockitoRule mRule = MockitoJUnit.rule();

    @Mock private EncoderPersistenceDao mEncoderPersistenceDao;

    @Mock private EncoderEndpointsDao mEncoderEndpointsDao;

    @Mock private EncoderLogicMetadataDao mEncoderLogicMetadataDao;

    @Mock private AdServicesHttpsClient mAdServicesHttpsClient;

    @Mock private ProtectedSignalsDao mProtectedSignalsDao;

    @Captor ArgumentCaptor<DBEncoderLogicMetadata> mDBEncoderLogicArgumentCaptor;

    private ListeningExecutorService mExecutorService = MoreExecutors.newDirectExecutorService();

    private ExecutorService mService = Executors.newFixedThreadPool(5);
    private EncoderLogicHandler mEncoderLogicHandler;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Before
    public void setup() {
        mEncoderLogicHandler =
                new EncoderLogicHandler(
                        mEncoderPersistenceDao,
                        mEncoderEndpointsDao,
                        mEncoderLogicMetadataDao,
                        mProtectedSignalsDao,
                        mAdServicesHttpsClient,
                        mExecutorService);
    }

    @Test
    public void testDownloadAndUpdate_success()
            throws ExecutionException, InterruptedException, TimeoutException {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        Uri encoderUri = CommonFixture.getUri(buyer, "/encoder");
        DBEncoderEndpoint encoderEndpoint =
                DBEncoderEndpoint.builder()
                        .setBuyer(buyer)
                        .setCreationTime(CommonFixture.FIXED_NOW)
                        .setDownloadUri(encoderUri)
                        .build();

        when(mEncoderEndpointsDao.getEndpoint(buyer)).thenReturn(encoderEndpoint);
        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setResponseHeaderKeys(ImmutableSet.of(ENCODER_VERSION_RESPONSE_HEADER))
                        .setUseCache(false)
                        .setUri(encoderUri)
                        .setDevContext(DevContext.createForDevOptionsDisabled())
                        .build();

        String body = "function() { fake JS}";
        int version = 1;
        ImmutableMap<String, List<String>> responseHeaders =
                ImmutableMap.of(
                        ENCODER_VERSION_RESPONSE_HEADER, ImmutableList.of(String.valueOf(version)));

        AdServicesHttpClientResponse response =
                AdServicesHttpClientResponse.builder()
                        .setResponseBody(body)
                        .setResponseHeaders(responseHeaders)
                        .build();

        ListenableFuture<AdServicesHttpClientResponse> responseFuture =
                Futures.immediateFuture(response);
        when(mAdServicesHttpsClient.fetchPayload(request)).thenReturn(responseFuture);
        when(mEncoderPersistenceDao.persistEncoder(buyer, body)).thenReturn(true);

        boolean updateSucceeded =
                mEncoderLogicHandler
                        .downloadAndUpdate(buyer, DevContext.createForDevOptionsDisabled())
                        .get(5, TimeUnit.SECONDS);
        assertTrue(updateSucceeded);
    }

    @Test
    public void testDownloadAndUpdate_skipped()
            throws ExecutionException, InterruptedException, TimeoutException {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        DBEncoderEndpoint encoderEndpoint = null;

        when(mEncoderEndpointsDao.getEndpoint(buyer)).thenReturn(encoderEndpoint);

        boolean updateSucceeded =
                mEncoderLogicHandler
                        .downloadAndUpdate(buyer, DevContext.createForDevOptionsDisabled())
                        .get(5, TimeUnit.SECONDS);
        assertFalse("The call to download should have been skipped", updateSucceeded);

        verifyZeroInteractions(
                mAdServicesHttpsClient, mEncoderPersistenceDao, mEncoderLogicMetadataDao);
    }

    @Test
    public void testExtractAndPersistEncoder_Success() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        String body = "function() { fake JS}";
        int version = 1;
        ImmutableMap<String, List<String>> responseHeaders =
                ImmutableMap.of(
                        ENCODER_VERSION_RESPONSE_HEADER, ImmutableList.of(String.valueOf(version)));

        AdServicesHttpClientResponse response =
                AdServicesHttpClientResponse.builder()
                        .setResponseBody(body)
                        .setResponseHeaders(responseHeaders)
                        .build();

        when(mEncoderPersistenceDao.persistEncoder(buyer, body)).thenReturn(true);
        assertTrue(mEncoderLogicHandler.extractAndPersistEncoder(buyer, response));

        Mockito.verify(mEncoderLogicMetadataDao)
                .persistEncoderLogicMetadata(mDBEncoderLogicArgumentCaptor.capture());
        Assert.assertEquals(buyer, mDBEncoderLogicArgumentCaptor.getValue().getBuyer());
        Assert.assertEquals(version, mDBEncoderLogicArgumentCaptor.getValue().getVersion());
    }

    @Test
    public void testExtractAndPersistEncoder_MissingVersionFallback() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        String body = "function() { fake JS}";
        int fallBack = FALLBACK_VERSION;

        AdServicesHttpClientResponse response =
                AdServicesHttpClientResponse.builder().setResponseBody(body).build();
        when(mEncoderPersistenceDao.persistEncoder(buyer, body)).thenReturn(true);
        assertTrue(mEncoderLogicHandler.extractAndPersistEncoder(buyer, response));

        Mockito.verify(mEncoderLogicMetadataDao)
                .persistEncoderLogicMetadata(mDBEncoderLogicArgumentCaptor.capture());
        Assert.assertEquals(buyer, mDBEncoderLogicArgumentCaptor.getValue().getBuyer());
        Assert.assertEquals(
                "Missing version from response should have fallen back to fallback",
                fallBack,
                mDBEncoderLogicArgumentCaptor.getValue().getVersion());
    }

    @Test
    public void testExtractAndPersistEncoder_UnreadableVersionFallback() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        String body = "function() { fake JS}";
        int fallBack = FALLBACK_VERSION;

        ImmutableMap<String, List<String>> responseHeadersWithBadVersion =
                ImmutableMap.of(
                        ENCODER_VERSION_RESPONSE_HEADER,
                        ImmutableList.of(String.valueOf("Garbage version")));

        AdServicesHttpClientResponse response =
                AdServicesHttpClientResponse.builder()
                        .setResponseBody(body)
                        .setResponseHeaders(responseHeadersWithBadVersion)
                        .build();

        when(mEncoderPersistenceDao.persistEncoder(buyer, body)).thenReturn(true);
        assertTrue(mEncoderLogicHandler.extractAndPersistEncoder(buyer, response));

        Mockito.verify(mEncoderLogicMetadataDao)
                .persistEncoderLogicMetadata(mDBEncoderLogicArgumentCaptor.capture());
        Assert.assertEquals(buyer, mDBEncoderLogicArgumentCaptor.getValue().getBuyer());
        Assert.assertEquals(
                "Unreadable version from response should have fallen back to fallback",
                fallBack,
                mDBEncoderLogicArgumentCaptor.getValue().getVersion());
    }

    @Test
    public void testExtractAndPersistEncoderFailed_SkipUpdate() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        String body = "function() { fake JS}";
        int version = 1;
        ImmutableMap<String, List<String>> responseHeaders =
                ImmutableMap.of(
                        ENCODER_VERSION_RESPONSE_HEADER, ImmutableList.of(String.valueOf(version)));

        AdServicesHttpClientResponse response =
                AdServicesHttpClientResponse.builder()
                        .setResponseBody(body)
                        .setResponseHeaders(responseHeaders)
                        .build();

        // Deliberately fail the persistence on file
        when(mEncoderPersistenceDao.persistEncoder(buyer, body)).thenReturn(false);
        assertFalse(mEncoderLogicHandler.extractAndPersistEncoder(buyer, response));

        Mockito.verifyZeroInteractions(mEncoderLogicMetadataDao);
    }

    @Test
    public void testGetAllBuyersWithEncoders() {
        mEncoderLogicHandler.getBuyersWithEncoders();
        verify(mEncoderLogicMetadataDao).getAllBuyersWithRegisteredEncoders();
    }

    @Test
    public void testGetAllBuyersWithStaleEncoders() {
        Instant now = Instant.now();
        mEncoderLogicHandler.getBuyersWithStaleEncoders(now);
        verify(mEncoderLogicMetadataDao).getBuyersWithEncodersBeforeTime(now);
    }

    @Test
    public void testDeleteEncoderForBuyer() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        mEncoderLogicHandler.deleteEncoderForBuyer(buyer);

        verify(mEncoderLogicMetadataDao).deleteEncoder(buyer);
        verify(mEncoderPersistenceDao).deleteEncoder(buyer);
        verify(mEncoderEndpointsDao).deleteEncoderEndpoint(buyer);
        verify(mProtectedSignalsDao).deleteSignalsUpdateMetadata(buyer);
    }

    @Test
    public void testDeleteEncodersForBuyers() {
        AdTechIdentifier buyer1 = CommonFixture.VALID_BUYER_1;
        AdTechIdentifier buyer2 = CommonFixture.VALID_BUYER_2;
        Set<AdTechIdentifier> buyers = Set.of(buyer1, buyer2);
        mEncoderLogicHandler.deleteEncodersForBuyers(buyers);

        verify(mEncoderLogicMetadataDao).deleteEncoder(buyer1);
        verify(mEncoderLogicMetadataDao).deleteEncoder(buyer2);

        verify(mEncoderPersistenceDao).deleteEncoder(buyer1);
        verify(mEncoderPersistenceDao).deleteEncoder(buyer2);

        verify(mEncoderEndpointsDao).deleteEncoderEndpoint(buyer1);
        verify(mEncoderEndpointsDao).deleteEncoderEndpoint(buyer2);

        verify(mProtectedSignalsDao).deleteSignalsUpdateMetadata(buyer1);
        verify(mProtectedSignalsDao).deleteSignalsUpdateMetadata(buyer2);
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void testExtractAndPersistEncoder_PreventsOverwrites() throws InterruptedException {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        String body = "function() { fake JS}";
        int version = 1;
        ImmutableMap<String, List<String>> responseHeaders =
                ImmutableMap.of(
                        ENCODER_VERSION_RESPONSE_HEADER, ImmutableList.of(String.valueOf(version)));

        AdServicesHttpClientResponse response =
                AdServicesHttpClientResponse.builder()
                        .setResponseBody(body)
                        .setResponseHeaders(responseHeaders)
                        .build();

        ReentrantLock buyerLock = mEncoderLogicHandler.getBuyerLock(buyer);
        CountDownLatch writeWhileLockedLatch = new CountDownLatch(1);
        buyerLock.lock();
        try {
            mService.submit(
                    () -> {
                        assertFalse(
                                "This encoder update should have failed",
                                mEncoderLogicHandler.extractAndPersistEncoder(buyer, response));
                        Mockito.verifyZeroInteractions(mEncoderLogicMetadataDao);
                        Mockito.verifyZeroInteractions(mEncoderPersistenceDao);
                        writeWhileLockedLatch.countDown();
                    });
            Assert.assertTrue(writeWhileLockedLatch.await(5, TimeUnit.SECONDS));
        } finally {
            buyerLock.unlock();
        }

        CountDownLatch writeWhileUnLockedLatch = new CountDownLatch(1);
        mService.submit(
                () -> {
                    when(mEncoderPersistenceDao.persistEncoder(buyer, body)).thenReturn(true);
                    assertTrue(
                            "This encoder update should have succeeded",
                            mEncoderLogicHandler.extractAndPersistEncoder(buyer, response));
                    Mockito.verify(mEncoderLogicMetadataDao)
                            .persistEncoderLogicMetadata(mDBEncoderLogicArgumentCaptor.capture());
                    Assert.assertEquals(buyer, mDBEncoderLogicArgumentCaptor.getValue().getBuyer());
                    Assert.assertEquals(
                            version, mDBEncoderLogicArgumentCaptor.getValue().getVersion());
                    writeWhileUnLockedLatch.countDown();
                });
        assertTrue(writeWhileUnLockedLatch.await(5, TimeUnit.SECONDS));
    }
}
