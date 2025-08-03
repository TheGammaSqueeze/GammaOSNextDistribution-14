/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.phone.callcomposer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.Uri;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.provider.CallLog;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.telephony.gba.TlsParams;
import android.telephony.gba.UaSecurityProtocolIdentifier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PictureManagerTest {
    private static final String FAKE_URL_BASE = "https://www.example.com";
    private static final String FAKE_URL = "https://www.example.com/AAAAA";
    private static final long TIMEOUT_MILLIS = 1000;
    private static final Uri FAKE_CALLLOG_URI = Uri.parse("content://asdf");

    @Mock CallComposerPictureManager.CallLogProxy mockCallLogProxy;
    @Mock CallComposerPictureTransfer mockPictureTransfer;
    @Mock Context context;
    @Mock TelephonyManager telephonyManager;

    private boolean originalTestMode = false;
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        originalTestMode = CallComposerPictureManager.sTestMode;
        // Even though this is a test, we want test mode off so we can actually exercise the logic
        // in the class.
        CallComposerPictureManager.sTestMode = false;
        when(context.getSystemService(Context.TELEPHONY_SERVICE)).thenReturn(telephonyManager);
        when(context.getSystemServiceName(TelephonyManager.class))
                .thenReturn(Context.TELEPHONY_SERVICE);
        when(telephonyManager.createForSubscriptionId(anyInt())).thenReturn(telephonyManager);
        PersistableBundle b = new PersistableBundle();
        b.putString(CarrierConfigManager.KEY_CALL_COMPOSER_PICTURE_SERVER_URL_STRING,
                FAKE_URL_BASE);
        b.putInt(CarrierConfigManager.KEY_GBA_MODE_INT,
                CarrierConfigManager.GBA_ME);
        b.putInt(CarrierConfigManager.KEY_GBA_UA_SECURITY_ORGANIZATION_INT,
                UaSecurityProtocolIdentifier.ORG_3GPP);
        b.putInt(CarrierConfigManager.KEY_GBA_UA_SECURITY_PROTOCOL_INT,
                UaSecurityProtocolIdentifier.UA_SECURITY_PROTOCOL_3GPP_TLS_DEFAULT);
        b.putInt(CarrierConfigManager.KEY_GBA_UA_TLS_CIPHER_SUITE_INT,
                TlsParams.TLS_RSA_WITH_AES_128_CBC_SHA);
        when(telephonyManager.getCarrierConfig()).thenReturn(b);
    }

    @After
    public void tearDown() throws Exception {
        CallComposerPictureManager.sTestMode = originalTestMode;
        CallComposerPictureManager.clearInstances();
    }

    @Test
    public void testPictureUpload() throws Exception {
        CallComposerPictureManager manager = CallComposerPictureManager.getInstance(context, 0);
        manager.setCallLogProxy(mockCallLogProxy);
        ImageData imageData = new ImageData(new byte[] {1,2,3,4},
                "image/png", null);

        CompletableFuture<UUID> uploadedUuidFuture = new CompletableFuture<>();
        manager.handleUploadToServer(new CallComposerPictureTransfer.Factory() {
            @Override
            public CallComposerPictureTransfer create(Context context, int subscriptionId,
                    String url, ExecutorService executorService) {
                return mockPictureTransfer;
            }
        }, imageData, (pair) -> uploadedUuidFuture.complete(pair.first));

        // Get the callback for later manipulation
        ArgumentCaptor<CallComposerPictureTransfer.PictureCallback> callbackCaptor =
                ArgumentCaptor.forClass(CallComposerPictureTransfer.PictureCallback.class);
        verify(mockPictureTransfer).setCallback(callbackCaptor.capture());

        // Make sure the upload method is called
        ArgumentCaptor<GbaCredentialsSupplier> credSupplierCaptor =
                ArgumentCaptor.forClass(GbaCredentialsSupplier.class);
        ArgumentCaptor<ImageData> imageDataCaptor =
                ArgumentCaptor.forClass(ImageData.class);
        verify(mockPictureTransfer).uploadPicture(imageDataCaptor.capture(),
                credSupplierCaptor.capture());

        // Make sure the id field on the image data got filled in
        ImageData sentData = imageDataCaptor.getValue();
        assertArrayEquals(imageData.getImageBytes(), sentData.getImageBytes());
        assertNotNull(sentData.getId());
        String imageId = sentData.getId();

        testGbaCredLookup(credSupplierCaptor.getValue(), false);

        // Trigger upload success, make sure that the internal state is consistent after the upload.
        callbackCaptor.getValue().onUploadSuccessful(FAKE_URL);
        UUID id = uploadedUuidFuture.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertEquals(imageId, id.toString());
        assertEquals(FAKE_URL, manager.getServerUrlForImageId(id));

        // Test the call log upload
        CompletableFuture<Uri> callLogUriFuture = new CompletableFuture<>();
        manager.storeUploadedPictureToCallLog(id, callLogUriFuture::complete);

        ArgumentCaptor<OutcomeReceiver<Uri, CallLog.CallComposerLoggingException>>
                callLogCallbackCaptor = ArgumentCaptor.forClass(OutcomeReceiver.class);

        verify(mockCallLogProxy).storeCallComposerPictureAsUser(nullable(Context.class),
                nullable(UserHandle.class), nullable(InputStream.class), nullable(Executor.class),
                callLogCallbackCaptor.capture());
        callLogCallbackCaptor.getValue().onResult(FAKE_CALLLOG_URI);
        Uri receivedUri = callLogUriFuture.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertEquals(FAKE_CALLLOG_URI, receivedUri);
    }

    @Test
    public void testPictureUploadWithAuthRefresh() throws Exception {
        CallComposerPictureManager manager = CallComposerPictureManager.getInstance(context, 0);
        manager.setCallLogProxy(mockCallLogProxy);
        ImageData imageData = new ImageData(new byte[] {1,2,3,4},
                "image/png", null);

        CompletableFuture<UUID> uploadedUuidFuture = new CompletableFuture<>();
        manager.handleUploadToServer(new CallComposerPictureTransfer.Factory() {
            @Override
            public CallComposerPictureTransfer create(Context context, int subscriptionId,
                    String url, ExecutorService executorService) {
                return mockPictureTransfer;
            }
        }, imageData, (pair) -> uploadedUuidFuture.complete(pair.first));

        // Get the callback for later manipulation
        ArgumentCaptor<CallComposerPictureTransfer.PictureCallback> callbackCaptor =
                ArgumentCaptor.forClass(CallComposerPictureTransfer.PictureCallback.class);
        verify(mockPictureTransfer).setCallback(callbackCaptor.capture());

        // Make sure the upload method is called
        verify(mockPictureTransfer).uploadPicture(nullable(ImageData.class),
                nullable(GbaCredentialsSupplier.class));

        // Simulate a auth-needed retry request
        callbackCaptor.getValue().onRetryNeeded(true, 0);
        waitForExecutorAction(CallComposerPictureManager.getExecutor(), TIMEOUT_MILLIS);

        // Make sure upload gets called again immediately, and make sure that the new GBA creds
        // are requested with a force-refresh.
        ArgumentCaptor<GbaCredentialsSupplier> credSupplierCaptor =
                ArgumentCaptor.forClass(GbaCredentialsSupplier.class);
        verify(mockPictureTransfer, times(2)).uploadPicture(nullable(ImageData.class),
                credSupplierCaptor.capture());

        testGbaCredLookup(credSupplierCaptor.getValue(), true);
    }

    @Test
    public void testPictureDownload() throws Exception {
        ImageData imageData = new ImageData(new byte[] {1,2,3,4},
                "image/png", null);
        CallComposerPictureManager manager = CallComposerPictureManager.getInstance(context, 0);
        manager.setCallLogProxy(mockCallLogProxy);

        CompletableFuture<Uri> callLogUriFuture = new CompletableFuture<>();
        manager.handleDownloadFromServer(new CallComposerPictureTransfer.Factory() {
            @Override
            public CallComposerPictureTransfer create(Context context, int subscriptionId,
                    String url, ExecutorService executorService) {
                return mockPictureTransfer;
            }
        }, FAKE_URL, (p) -> callLogUriFuture.complete(p.first));

        // Get the callback for later manipulation
        ArgumentCaptor<CallComposerPictureTransfer.PictureCallback> callbackCaptor =
                ArgumentCaptor.forClass(CallComposerPictureTransfer.PictureCallback.class);
        verify(mockPictureTransfer).setCallback(callbackCaptor.capture());

        // Make sure the download method is called
        ArgumentCaptor<GbaCredentialsSupplier> credSupplierCaptor =
                ArgumentCaptor.forClass(GbaCredentialsSupplier.class);
        verify(mockPictureTransfer).downloadPicture(credSupplierCaptor.capture());

        testGbaCredLookup(credSupplierCaptor.getValue(), false);

        // Trigger download success, make sure that the call log is called into next.
        callbackCaptor.getValue().onDownloadSuccessful(imageData);
        ArgumentCaptor<OutcomeReceiver<Uri, CallLog.CallComposerLoggingException>>
                callLogCallbackCaptor = ArgumentCaptor.forClass(OutcomeReceiver.class);
        verify(mockCallLogProxy).storeCallComposerPictureAsUser(nullable(Context.class),
                nullable(UserHandle.class), nullable(InputStream.class), nullable(Executor.class),
                callLogCallbackCaptor.capture());

        callLogCallbackCaptor.getValue().onResult(FAKE_CALLLOG_URI);
        Uri receivedUri = callLogUriFuture.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertEquals(FAKE_CALLLOG_URI, receivedUri);
    }

    @Test
    public void testPictureDownloadWithAuthRefresh() throws Exception {
        CallComposerPictureManager manager = CallComposerPictureManager.getInstance(context, 0);
        manager.setCallLogProxy(mockCallLogProxy);

        CompletableFuture<Uri> callLogUriFuture = new CompletableFuture<>();
        manager.handleDownloadFromServer(new CallComposerPictureTransfer.Factory() {
            @Override
            public CallComposerPictureTransfer create(Context context, int subscriptionId,
                    String url, ExecutorService executorService) {
                return mockPictureTransfer;
            }
        }, FAKE_URL, (p) -> callLogUriFuture.complete(p.first));

        // Get the callback for later manipulation
        ArgumentCaptor<CallComposerPictureTransfer.PictureCallback> callbackCaptor =
                ArgumentCaptor.forClass(CallComposerPictureTransfer.PictureCallback.class);
        verify(mockPictureTransfer).setCallback(callbackCaptor.capture());

        // Make sure the download method is called
        verify(mockPictureTransfer).downloadPicture(nullable(GbaCredentialsSupplier.class));

        // Simulate a auth-needed retry request
        callbackCaptor.getValue().onRetryNeeded(true, 0);
        waitForExecutorAction(CallComposerPictureManager.getExecutor(), TIMEOUT_MILLIS);

        // Make sure download gets called again immediately, and make sure that the new GBA creds
        // are requested with a force-refresh.
        ArgumentCaptor<GbaCredentialsSupplier> credSupplierCaptor =
                ArgumentCaptor.forClass(GbaCredentialsSupplier.class);
        verify(mockPictureTransfer, times(2)).downloadPicture(credSupplierCaptor.capture());

        testGbaCredLookup(credSupplierCaptor.getValue(), true);
    }


    public void testGbaCredLookup(GbaCredentialsSupplier supplier, boolean forceExpected)
            throws Exception {
        String fakeNafId = "https://3GPP-bootstrapping@www.example.com";
        byte[] fakeKey = new byte[] {1, 2, 3, 4, 5};
        String fakeTxId = "89sdfjggf";

        ArgumentCaptor<TelephonyManager.BootstrapAuthenticationCallback> authCallbackCaptor =
                ArgumentCaptor.forClass(TelephonyManager.BootstrapAuthenticationCallback.class);

        CompletableFuture<GbaCredentials> credsFuture =
                supplier.getCredentials(fakeNafId, CallComposerPictureManager.getExecutor());
        verify(telephonyManager).bootstrapAuthenticationRequest(anyInt(),
                eq(Uri.parse(fakeNafId)),
                nullable(UaSecurityProtocolIdentifier.class), eq(forceExpected),
                nullable(Executor.class),
                authCallbackCaptor.capture());
        authCallbackCaptor.getValue().onKeysAvailable(fakeKey, fakeTxId);
        GbaCredentials creds = credsFuture.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertEquals(fakeTxId, creds.getTransactionId());
        assertArrayEquals(fakeKey, creds.getKey());


        // Do it again and see if we make another request, then make sure that matches up with what
        // we expected.
        CompletableFuture<GbaCredentials> credsFuture1 =
                supplier.getCredentials(fakeNafId, CallComposerPictureManager.getExecutor());
        verify(telephonyManager, times(forceExpected ? 2 : 1))
                .bootstrapAuthenticationRequest(anyInt(), eq(Uri.parse(fakeNafId)),
                        nullable(UaSecurityProtocolIdentifier.class),
                        eq(forceExpected),
                        nullable(Executor.class),
                        authCallbackCaptor.capture());
        authCallbackCaptor.getValue().onKeysAvailable(fakeKey, fakeTxId);
        GbaCredentials creds1 = credsFuture1.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertEquals(fakeTxId, creds1.getTransactionId());
        assertArrayEquals(fakeKey, creds1.getKey());
    }

    private static boolean waitForExecutorAction(
            ExecutorService executorService, long timeoutMillis) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        executorService.execute(() -> f.complete(null));
        try {
            f.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return false;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        return true;
    }
}
