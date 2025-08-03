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
package com.android.adservices.measurement;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.MeasurementCompatibleManager;
import android.adservices.measurement.MeasurementManager;
import android.adservices.measurement.SourceRegistrationRequest;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.content.Context;
import android.net.Uri;
import android.os.OutcomeReceiver;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MeasurementManagerTest {
    private static final long TIMEOUT = 5000L;

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private MeasurementManager getMeasurementManager() {
        return MeasurementManager.get(sContext);
    }

    @Rule
    public final AdServicesFlagsSetterRule flags =
            AdServicesFlagsSetterRule.forGlobalKillSwitchDisabledTests()
                    .setCompatModeFlags()
                    .setMsmtApiAppAllowList(sContext.getPackageName());

    @After
    public void tearDown() {
        resetOverrideConsentManagerDebugMode();
    }

    @Test
    public void testRegisterSource_executorAndCallbackCalled() throws Exception {
        Assume.assumeTrue(SdkLevel.isAtLeastS());
        final MeasurementManager mm = getMeasurementManager();
        final CountDownLatch anyCountDownLatch = new CountDownLatch(1);

        mm.registerSource(
                Uri.parse("https://registration-source"),
                /* inputEvent = */ null,
                CALLBACK_EXECUTOR,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Object result) {
                        anyCountDownLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        anyCountDownLatch.countDown();
                    }
                });

        assertTrue(anyCountDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testRegisterSource_executorAndCallbackCalled_customReceiver() throws Exception {
        final MeasurementManager mm = getMeasurementManager();
        final CountDownLatch anyCountDownLatch = new CountDownLatch(1);

        mm.registerSource(
                Uri.parse("https://registration-source"),
                /* inputEvent = */ null,
                CALLBACK_EXECUTOR,
                new AdServicesOutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Object result) {
                        anyCountDownLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        anyCountDownLatch.countDown();
                    }
                });

        assertTrue(anyCountDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    private WebSourceRegistrationRequest buildDefaultWebSourceRegistrationRequest() {
        WebSourceParams webSourceParams =
                new WebSourceParams.Builder(Uri.parse("https://example.com"))
                        .setDebugKeyAllowed(false)
                        .build();

        return new WebSourceRegistrationRequest.Builder(
                Collections.singletonList(webSourceParams),
                Uri.parse("https://example.com"))
                .setInputEvent(null)
                .setAppDestination(Uri.parse("android-app://com.example"))
                .setWebDestination(Uri.parse("https://example.com"))
                .setVerifiedDestination(null)
                .build();
    }

    @Test
    public void testRegisterWebSource_executorAndCallbackCalled() throws Exception {
        Assume.assumeTrue(SdkLevel.isAtLeastS());
        final MeasurementManager mm = getMeasurementManager();
        final CountDownLatch anyCountDownLatch = new CountDownLatch(1);

        mm.registerWebSource(
                buildDefaultWebSourceRegistrationRequest(),
                CALLBACK_EXECUTOR,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Object result) {
                        anyCountDownLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        anyCountDownLatch.countDown();
                    }
                });

        assertTrue(anyCountDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testRegisterWebSource_executorAndCallbackCalled_customReceiver() throws Exception {
        final MeasurementManager mm = getMeasurementManager();
        final CountDownLatch anyCountDownLatch = new CountDownLatch(1);

        mm.registerWebSource(
                buildDefaultWebSourceRegistrationRequest(),
                CALLBACK_EXECUTOR,
                new AdServicesOutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Object result) {
                        anyCountDownLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        anyCountDownLatch.countDown();
                    }
                });

        assertTrue(anyCountDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    private WebTriggerRegistrationRequest buildDefaultWebTriggerRegistrationRequest() {
        WebTriggerParams webTriggerParams =
                new WebTriggerParams.Builder(Uri.parse("https://example.com"))
                        .setDebugKeyAllowed(false)
                        .build();
        return new WebTriggerRegistrationRequest.Builder(
                        Collections.singletonList(webTriggerParams),
                        Uri.parse("https://example.com"))
                .build();
    }

    @Test
    public void testRegisterWebTrigger_executorAndCallbackCalled() throws Exception {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        final MeasurementManager mm = getMeasurementManager();
        final CountDownLatch anyCountDownLatch = new CountDownLatch(1);

        mm.registerWebTrigger(
                buildDefaultWebTriggerRegistrationRequest(),
                CALLBACK_EXECUTOR,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Object result) {
                        anyCountDownLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        anyCountDownLatch.countDown();
                    }
                });

        assertTrue(anyCountDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testRegisterWebTrigger_executorAndCallbackCalled_customReceiver() throws Exception {
        final MeasurementManager mm = getMeasurementManager();
        final CountDownLatch anyCountDownLatch = new CountDownLatch(1);

        mm.registerWebTrigger(
                buildDefaultWebTriggerRegistrationRequest(),
                CALLBACK_EXECUTOR,
                new AdServicesOutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Object result) {
                        anyCountDownLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        anyCountDownLatch.countDown();
                    }
                });

        assertTrue(anyCountDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testRegisterTrigger_executorAndCallbackCalled() throws Exception {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        final MeasurementManager mm = getMeasurementManager();
        final CountDownLatch anyCountDownLatch = new CountDownLatch(1);

        mm.registerTrigger(
                Uri.parse("https://registration-trigger"),
                CALLBACK_EXECUTOR,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Object result) {
                        anyCountDownLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        anyCountDownLatch.countDown();
                    }
                });

        assertTrue(anyCountDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testRegisterTrigger_executorAndCallbackCalled_customReceiver() throws Exception {
        final MeasurementManager mm = getMeasurementManager();
        final CountDownLatch anyCountDownLatch = new CountDownLatch(1);

        mm.registerTrigger(
                Uri.parse("https://registration-trigger"),
                CALLBACK_EXECUTOR,
                new AdServicesOutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull Object result) {
                        anyCountDownLatch.countDown();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        anyCountDownLatch.countDown();
                    }
                });

        assertTrue(anyCountDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testDeleteRegistrations_nullExecutor_throwNullPointerException() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        MeasurementManager mm = getMeasurementManager();
        assertThrows(
                NullPointerException.class,
                () ->
                        mm.deleteRegistrations(
                                new DeletionRequest.Builder().build(),
                                /* executor */ null,
                                (OutcomeReceiver<Object, Exception>)
                                        i -> new CompletableFuture<>().complete(i)));
    }

    @Test
    public void testDeleteRegistrations_nullExecutor_throwNullPointerException_customReceiver() {
        MeasurementManager mm = getMeasurementManager();

        assertThrows(
                NullPointerException.class,
                () ->
                        mm.deleteRegistrations(
                                new DeletionRequest.Builder().build(),
                                /* executor */ null,
                                (AdServicesOutcomeReceiver<Object, Exception>)
                                        i -> new CompletableFuture<>().complete(i)));
    }

    @Test
    public void testDeleteRegistrations_nullCallback_throwNullPointerException() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        MeasurementManager mm = getMeasurementManager();
        assertThrows(
                NullPointerException.class,
                () ->
                        mm.deleteRegistrations(
                                new DeletionRequest.Builder().build(),
                                CALLBACK_EXECUTOR,
                                /* callback */ (OutcomeReceiver<Object, Exception>) null));
    }

    @Test
    public void testDeleteRegistrations_nullCallback_throwNullPointerException_customReceiver() {
        MeasurementManager mm = getMeasurementManager();

        assertThrows(
                NullPointerException.class,
                () ->
                        mm.deleteRegistrations(
                                new DeletionRequest.Builder().build(),
                                CALLBACK_EXECUTOR,
                                /* callback */ (AdServicesOutcomeReceiver<Object, Exception>)
                                        null));
    }

    @Test
    public void testGetMeasurementApiStatus() throws Exception {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        final MeasurementManager mm = getMeasurementManager();
        overrideConsentNotifiedDebugMode();
        overrideConsentManagerDebugMode();
        CompletableFuture<Integer> future = new CompletableFuture<>();
        OutcomeReceiver<Integer, Exception> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Integer result) {
                        future.complete(result);
                    }

                    @Override
                    public void onError(Exception error) {
                        Assert.fail();
                    }
                };

        mm.getMeasurementApiStatus(CALLBACK_EXECUTOR, callback);
        final int response = future.get();
        Assert.assertEquals(MeasurementManager.MEASUREMENT_API_STATE_ENABLED, response);
    }

    @Test
    public void testGetMeasurementApiStatus_customReceiver() throws Exception {
        final MeasurementManager mm = getMeasurementManager();
        overrideConsentNotifiedDebugMode();
        overrideConsentManagerDebugMode();
        CompletableFuture<Integer> future = new CompletableFuture<>();
        AdServicesOutcomeReceiver<Integer, Exception> callback =
                new AdServicesOutcomeReceiver<>() {
                    @Override
                    public void onResult(Integer result) {
                        future.complete(result);
                    }

                    @Override
                    public void onError(Exception error) {
                        Assert.fail();
                    }
                };

        mm.getMeasurementApiStatus(CALLBACK_EXECUTOR, callback);
        final int response = future.get();
        Assert.assertEquals(MeasurementManager.MEASUREMENT_API_STATE_ENABLED, response);
    }

    @Test
    public void testGetMeasurementApiStatus_nullExecutor_throwNullPointerException() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        MeasurementManager mm = getMeasurementManager();
        overrideConsentManagerDebugMode();

        assertThrows(
                NullPointerException.class,
                () ->
                        mm.getMeasurementApiStatus(
                                /* executor */ null,
                                (OutcomeReceiver<Integer, Exception>) result -> {}));
    }

    @Test
    public void
            testGetMeasurementApiStatus_nullExecutor_throwNullPointerException_customReceiver() {
        MeasurementManager mm = getMeasurementManager();
        overrideConsentManagerDebugMode();

        assertThrows(
                NullPointerException.class,
                () ->
                        mm.getMeasurementApiStatus(
                                /* executor */ null,
                                (AdServicesOutcomeReceiver<Integer, Exception>) result -> {}));
    }

    @Test
    public void testGetMeasurementApiStatus_nullCallback_throwNullPointerException() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        MeasurementManager mm = getMeasurementManager();
        overrideConsentManagerDebugMode();

        assertThrows(
                NullPointerException.class,
                () ->
                        mm.getMeasurementApiStatus(
                                CALLBACK_EXECUTOR, /* callback */
                                (OutcomeReceiver<Integer, Exception>) null));
    }

    @Test
    public void
            testGetMeasurementApiStatus_nullCallback_throwNullPointerException_customReceiver() {
        MeasurementManager mm = getMeasurementManager();
        overrideConsentManagerDebugMode();

        assertThrows(
                NullPointerException.class,
                () ->
                        mm.getMeasurementApiStatus(
                                CALLBACK_EXECUTOR, /* callback */
                                (AdServicesOutcomeReceiver<Integer, Exception>) null));
    }

    // The remaining tests validate that the MeasurementManager invokes the underlying
    // implementation object correctly. They all mock the implementation.

    @Test
    public void testRegisterSource() {
        MeasurementCompatibleManager impl = mock(MeasurementCompatibleManager.class);
        MeasurementManager mm = new MeasurementManager(impl);
        Uri uri = Uri.parse("http://www.example.com");
        mm.registerSource(
                uri,
                /* inputEvent= */ null,
                /* executor= */ null,
                (AdServicesOutcomeReceiver<Object, Exception>) null);
        verify(impl).registerSource(eq(uri), isNull(), isNull(), isNull());
        verifyNoMoreInteractions(impl);
    }

    @Test
    public void testRegisterSource_SPlus() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());
        MeasurementCompatibleManager impl = mock(MeasurementCompatibleManager.class);
        MeasurementManager mm = new MeasurementManager(impl);
        Uri uri = Uri.parse("http://www.example.com");
        mm.registerSource(
                uri,
                /* inputEvent= */ null,
                /* executor= */ null,
                (OutcomeReceiver<Object, Exception>) null);
        verify(impl).registerSource(eq(uri), isNull(), isNull(), isNull());
        verifyNoMoreInteractions(impl);
    }

    @Test
    public void testRegisterSource_propagatesExecutor() {
        MeasurementCompatibleManager impl = mock(MeasurementCompatibleManager.class);
        MeasurementManager mm = new MeasurementManager(impl);
        Uri uri = Uri.parse("http://www.example.com");
        mm.registerSource(
                uri,
                /* inputEvent= */ null,
                CALLBACK_EXECUTOR,
                (AdServicesOutcomeReceiver<Object, Exception>) null);
        verify(impl).registerSource(eq(uri), isNull(), eq(CALLBACK_EXECUTOR), isNull());
        verifyNoMoreInteractions(impl);
    }

    @Test
    public void testRegisterSource_propagatesExecutor_SPlus() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());
        MeasurementCompatibleManager impl = mock(MeasurementCompatibleManager.class);
        MeasurementManager mm = new MeasurementManager(impl);
        Uri uri = Uri.parse("http://www.example.com");
        mm.registerSource(
                uri,
                /* inputEvent= */ null,
                CALLBACK_EXECUTOR,
                (OutcomeReceiver<Object, Exception>) null);
        verify(impl).registerSource(eq(uri), isNull(), eq(CALLBACK_EXECUTOR), isNull());
        verifyNoMoreInteractions(impl);
    }

    @Test
    public void testRegisterSource_propagatesCallback() {
        MeasurementCompatibleManager impl = mock(MeasurementCompatibleManager.class);
        MeasurementManager mm = new MeasurementManager(impl);
        Uri uri = Uri.parse("http://www.example.com");
        AdServicesOutcomeReceiver<Object, Exception> callback =
                mock(AdServicesOutcomeReceiver.class);
        mm.registerSource(uri, /* inputEvent= */ null, /* executor= */ null, callback);

        verify(impl).registerSource(eq(uri), isNull(), isNull(), eq(callback));
        verifyNoMoreInteractions(impl);
    }

    @Test
    public void testRegisterSource_propagatesCallback_SPlus() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        MeasurementCompatibleManager impl = mock(MeasurementCompatibleManager.class);
        MeasurementManager mm = new MeasurementManager(impl);
        Uri uri = Uri.parse("http://www.example.com");
        OutcomeReceiver<Object, Exception> callback = mock(OutcomeReceiver.class);
        mm.registerSource(uri, /* inputEvent= */ null, /* executor= */ null, callback);

        ArgumentCaptor<AdServicesOutcomeReceiver<Object, Exception>> captor =
                ArgumentCaptor.forClass(AdServicesOutcomeReceiver.class);
        verify(impl).registerSource(eq(uri), isNull(), isNull(), captor.capture());
        verifyCallback(callback, captor.getValue());
        verifyNoMoreInteractions(impl);
    }

    @Test
    public void testRegisterWebSource() {
        MeasurementCompatibleManager impl = mock(MeasurementCompatibleManager.class);
        MeasurementManager mm = new MeasurementManager(impl);
        WebSourceRegistrationRequest request = buildDefaultWebSourceRegistrationRequest();
        AdServicesOutcomeReceiver<Object, Exception> callback =
                mock(AdServicesOutcomeReceiver.class);

        mm.registerWebSource(request, CALLBACK_EXECUTOR, callback);

        verify(impl).registerWebSource(eq(request), eq(CALLBACK_EXECUTOR), eq(callback));
        verifyNoMoreInteractions(impl);
    }

    @Test
    public void testRegisterWebSource_SPlus() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        MeasurementCompatibleManager impl = mock(MeasurementCompatibleManager.class);
        MeasurementManager mm = new MeasurementManager(impl);
        WebSourceRegistrationRequest request = buildDefaultWebSourceRegistrationRequest();
        OutcomeReceiver<Object, Exception> callback = mock(OutcomeReceiver.class);

        mm.registerWebSource(request, CALLBACK_EXECUTOR, callback);

        ArgumentCaptor<AdServicesOutcomeReceiver<Object, Exception>> captor =
                ArgumentCaptor.forClass(AdServicesOutcomeReceiver.class);
        verify(impl).registerWebSource(eq(request), eq(CALLBACK_EXECUTOR), captor.capture());
        verifyCallback(callback, captor.getValue());
        verifyNoMoreInteractions(impl);
    }

    @Test
    public void testRegisterWebTrigger() {
        MeasurementCompatibleManager impl = mock(MeasurementCompatibleManager.class);
        MeasurementManager mm = new MeasurementManager(impl);
        WebTriggerRegistrationRequest request = buildDefaultWebTriggerRegistrationRequest();
        AdServicesOutcomeReceiver<Object, Exception> callback =
                mock(AdServicesOutcomeReceiver.class);

        mm.registerWebTrigger(request, CALLBACK_EXECUTOR, callback);

        verify(impl).registerWebTrigger(eq(request), eq(CALLBACK_EXECUTOR), eq(callback));
        verifyNoMoreInteractions(impl);
    }

    @Test
    public void testRegisterWebTrigger_SPlus() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        MeasurementCompatibleManager impl = mock(MeasurementCompatibleManager.class);
        MeasurementManager mm = new MeasurementManager(impl);
        WebTriggerRegistrationRequest request = buildDefaultWebTriggerRegistrationRequest();
        OutcomeReceiver<Object, Exception> callback = mock(OutcomeReceiver.class);

        mm.registerWebTrigger(request, CALLBACK_EXECUTOR, callback);

        ArgumentCaptor<AdServicesOutcomeReceiver<Object, Exception>> captor =
                ArgumentCaptor.forClass(AdServicesOutcomeReceiver.class);
        verify(impl).registerWebTrigger(eq(request), eq(CALLBACK_EXECUTOR), captor.capture());
        verifyCallback(callback, captor.getValue());
        verifyNoMoreInteractions(impl);
    }

    @Test
    public void testRegisterTrigger() {
        MeasurementCompatibleManager impl = mock(MeasurementCompatibleManager.class);
        MeasurementManager mm = new MeasurementManager(impl);
        Uri uri = Uri.parse("https://www.example.com");
        AdServicesOutcomeReceiver<Object, Exception> callback =
                mock(AdServicesOutcomeReceiver.class);

        mm.registerTrigger(uri, CALLBACK_EXECUTOR, callback);

        verify(impl).registerTrigger(eq(uri), eq(CALLBACK_EXECUTOR), eq(callback));
        verifyNoMoreInteractions(impl);
    }

    @Test
    public void testRegisterTrigger_SPlus() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        MeasurementCompatibleManager impl = mock(MeasurementCompatibleManager.class);
        MeasurementManager mm = new MeasurementManager(impl);
        Uri uri = Uri.parse("https://www.example.com");
        OutcomeReceiver<Object, Exception> callback = mock(OutcomeReceiver.class);

        mm.registerTrigger(uri, CALLBACK_EXECUTOR, callback);

        ArgumentCaptor<AdServicesOutcomeReceiver<Object, Exception>> captor =
                ArgumentCaptor.forClass(AdServicesOutcomeReceiver.class);
        verify(impl).registerTrigger(eq(uri), eq(CALLBACK_EXECUTOR), captor.capture());
        verifyCallback(callback, captor.getValue());
        verifyNoMoreInteractions(impl);
    }

    @Test
    public void testDeleteRegistrations() {
        MeasurementCompatibleManager impl = mock(MeasurementCompatibleManager.class);
        MeasurementManager mm = new MeasurementManager(impl);
        DeletionRequest request = new DeletionRequest.Builder().build();
        AdServicesOutcomeReceiver<Object, Exception> callback =
                mock(AdServicesOutcomeReceiver.class);

        mm.deleteRegistrations(request, CALLBACK_EXECUTOR, callback);

        verify(impl).deleteRegistrations(eq(request), eq(CALLBACK_EXECUTOR), eq(callback));
        verifyNoMoreInteractions(impl);
    }

    @Test
    public void testDeleteRegistrations_SPlus() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        MeasurementCompatibleManager impl = mock(MeasurementCompatibleManager.class);
        MeasurementManager mm = new MeasurementManager(impl);
        DeletionRequest request = new DeletionRequest.Builder().build();
        OutcomeReceiver<Object, Exception> callback = mock(OutcomeReceiver.class);

        mm.deleteRegistrations(request, CALLBACK_EXECUTOR, callback);

        ArgumentCaptor<AdServicesOutcomeReceiver<Object, Exception>> captor =
                ArgumentCaptor.forClass(AdServicesOutcomeReceiver.class);
        verify(impl).deleteRegistrations(eq(request), eq(CALLBACK_EXECUTOR), captor.capture());
        verifyCallback(callback, captor.getValue());
        verifyNoMoreInteractions(impl);
    }

    @Test
    public void testGetMeasurementApiStatus_MockImpl() {
        MeasurementCompatibleManager impl = mock(MeasurementCompatibleManager.class);
        MeasurementManager mm = new MeasurementManager(impl);
        AdServicesOutcomeReceiver<Integer, Exception> callback =
                mock(AdServicesOutcomeReceiver.class);

        mm.getMeasurementApiStatus(CALLBACK_EXECUTOR, callback);

        verify(impl).getMeasurementApiStatus(eq(CALLBACK_EXECUTOR), eq(callback));
        verifyNoMoreInteractions(impl);
    }

    @Test
    public void testGetMeasurementApiStatus_MockImpl_SPlus() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        MeasurementCompatibleManager impl = mock(MeasurementCompatibleManager.class);
        MeasurementManager mm = new MeasurementManager(impl);
        OutcomeReceiver<Integer, Exception> callback = mock(OutcomeReceiver.class);

        mm.getMeasurementApiStatus(CALLBACK_EXECUTOR, callback);

        ArgumentCaptor<AdServicesOutcomeReceiver<Integer, Exception>> captor =
                ArgumentCaptor.forClass(AdServicesOutcomeReceiver.class);
        verify(impl).getMeasurementApiStatus(eq(CALLBACK_EXECUTOR), captor.capture());

        AdServicesOutcomeReceiver<Integer, Exception> invoked = captor.getValue();
        invoked.onResult(1);
        verify(callback).onResult(1);

        Exception ex = new Exception("TestException");
        invoked.onError(ex);

        verifyNoMoreInteractions(impl);
    }

    @Test
    public void testRegisterSourceMultiple() {
        MeasurementCompatibleManager impl = mock(MeasurementCompatibleManager.class);
        MeasurementManager mm = new MeasurementManager(impl);
        SourceRegistrationRequest request = buildDefaultAppSourcesRegistrationRequest();
        AdServicesOutcomeReceiver<Object, Exception> callback =
                mock(AdServicesOutcomeReceiver.class);

        mm.registerSource(request, CALLBACK_EXECUTOR, callback);

        verify(impl).registerSource(eq(request), eq(CALLBACK_EXECUTOR), eq(callback));
        verifyNoMoreInteractions(impl);
    }

    @Test
    public void testRegisterSourceMultiple_SPlus() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        MeasurementCompatibleManager impl = mock(MeasurementCompatibleManager.class);
        MeasurementManager mm = new MeasurementManager(impl);
        SourceRegistrationRequest request = buildDefaultAppSourcesRegistrationRequest();
        OutcomeReceiver<Object, Exception> callback = mock(OutcomeReceiver.class);

        mm.registerSource(request, CALLBACK_EXECUTOR, callback);

        ArgumentCaptor<AdServicesOutcomeReceiver<Object, Exception>> captor =
                ArgumentCaptor.forClass(AdServicesOutcomeReceiver.class);
        verify(impl).registerSource(eq(request), eq(CALLBACK_EXECUTOR), captor.capture());
        verifyCallback(callback, captor.getValue());
        verifyNoMoreInteractions(impl);
    }

    private SourceRegistrationRequest buildDefaultAppSourcesRegistrationRequest() {
        return new SourceRegistrationRequest.Builder(
                        List.of(
                                Uri.parse("https://example1.com"),
                                Uri.parse("https://example2.com")))
                .setInputEvent(null)
                .build();
    }

    @Test
    public void testUnbindFromService() {
        MeasurementCompatibleManager impl = mock(MeasurementCompatibleManager.class);
        MeasurementManager mm = new MeasurementManager(impl);

        mm.unbindFromService();

        verify(impl).unbindFromService();
        verifyNoMoreInteractions(impl);
    }

    // Mockito crashes on Android R if there are any methods that take unknown types, such as
    // OutcomeReceiver. So, declaring the parameter as Object and then casting to the
    // correct type.
    private void verifyCallback(
            Object expected, AdServicesOutcomeReceiver<Object, Exception> invoked) {
        OutcomeReceiver<Object, Exception> callback = (OutcomeReceiver<Object, Exception>) expected;
        invoked.onResult("Test");
        verify(callback).onResult("Test");

        Exception ex = new Exception("TestException");
        invoked.onError(ex);
        verify(callback).onError(eq(ex));
    }

    // Override the Consent Manager behaviour - Consent Given
    private void overrideConsentNotifiedDebugMode() {
        ShellUtils.runShellCommand("setprop debug.adservices.consent_notified_debug_mode true");
    }

    // Override the Consent Manager behaviour - Consent Given
    private void overrideConsentManagerDebugMode() {
        ShellUtils.runShellCommand("setprop debug.adservices.consent_manager_debug_mode true");
    }

    private void resetOverrideConsentManagerDebugMode() {
        ShellUtils.runShellCommand("setprop debug.adservices.consent_manager_debug_mode null");
    }
}
