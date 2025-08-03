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

package android.app.sdksandbox;

import static android.app.sdksandbox.SdkSandboxManager.EXTRA_DISPLAY_ID;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_HEIGHT_IN_PIXELS;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_HOST_TOKEN;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_SURFACE_PACKAGE;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_WIDTH_IN_PIXELS;
import static android.app.sdksandbox.SdkSandboxManager.LOAD_SDK_NOT_FOUND;
import static android.app.sdksandbox.SdkSandboxManager.REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR;
import static android.app.sdksandbox.StatsdUtil.SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__START_SDK_SANDBOX_ACTIVITY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.sdksandbox.testutils.FakeOutcomeReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceControlViewHost.SurfacePackage;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.MockitoSession;

import java.util.List;

/** Tests {@link SdkSandboxManager} APIs. */
@RunWith(JUnit4.class)
public class SdkSandboxManagerUnitTest {

    private SdkSandboxManager mSdkSandboxManager;
    private ISdkSandboxManager mBinder;
    private Context mContext;
    private SandboxLatencyInfo mSandboxLatencyInfo;
    private static final String SDK_NAME = "com.android.codeproviderresources";
    private static final String ERROR_MSG = "Error";
    private static final long TIME_SYSTEM_SERVER_CALLED_APP = 1;
    private static final String SDK_SANDBOX_MANAGER_TAG = "SdkSandboxManager";

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        mBinder = Mockito.mock(ISdkSandboxManager.class);
        mSdkSandboxManager = new SdkSandboxManager(mContext, mBinder);

        mSandboxLatencyInfo = new SandboxLatencyInfo();
    }

    @Test
    public void testGetSdkSandboxState() {
        assertThat(SdkSandboxManager.getSdkSandboxState())
                .isEqualTo(SdkSandboxManager.SDK_SANDBOX_STATE_ENABLED_PROCESS_ISOLATION);
    }

    @Test
    public void testLoadSdkSuccess() throws Exception {
        final Bundle params = new Bundle();

        OutcomeReceiver<SandboxedSdk, LoadSdkException> outcomeReceiver =
                Mockito.spy(new FakeOutcomeReceiver<>());
        long beforeCallingTimeStamp = SystemClock.elapsedRealtime();
        mSdkSandboxManager.loadSdk(SDK_NAME, params, Runnable::run, outcomeReceiver);
        long afterCallingTimeStamp = SystemClock.elapsedRealtime();

        ArgumentCaptor<ILoadSdkCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(ILoadSdkCallback.class);
        ArgumentCaptor<SandboxLatencyInfo> sandboxLatencyInfoArgumentCaptor =
                ArgumentCaptor.forClass(SandboxLatencyInfo.class);
        Mockito.verify(mBinder)
                .loadSdk(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.nullable(IBinder.class),
                        Mockito.eq(SDK_NAME),
                        sandboxLatencyInfoArgumentCaptor.capture(),
                        Mockito.eq(params),
                        callbackArgumentCaptor.capture());

        Assert.assertTrue(
                sandboxLatencyInfoArgumentCaptor.getValue().getTimeAppCalledSystemServer()
                        >= beforeCallingTimeStamp);
        Assert.assertTrue(
                sandboxLatencyInfoArgumentCaptor.getValue().getTimeAppReceivedCallFromSystemServer()
                        <= afterCallingTimeStamp);

        // Simulate the success callback
        callbackArgumentCaptor
                .getValue()
                .onLoadSdkSuccess(new SandboxedSdk(new Binder()), mSandboxLatencyInfo);
        ArgumentCaptor<SandboxedSdk> sandboxedSdkCapture =
                ArgumentCaptor.forClass(SandboxedSdk.class);
        Mockito.verify(outcomeReceiver).onResult(sandboxedSdkCapture.capture());

        assertNotNull(sandboxedSdkCapture.getValue().getInterface());
    }

    @Test
    public void testLoadSdkFailed() throws Exception {
        final Bundle params = new Bundle();

        OutcomeReceiver<SandboxedSdk, LoadSdkException> outcomeReceiver =
                Mockito.spy(new FakeOutcomeReceiver<>());
        mSdkSandboxManager.loadSdk(SDK_NAME, params, Runnable::run, outcomeReceiver);

        ArgumentCaptor<ILoadSdkCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(ILoadSdkCallback.class);
        Mockito.verify(mBinder)
                .loadSdk(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.nullable(IBinder.class),
                        Mockito.eq(SDK_NAME),
                        Mockito.any(SandboxLatencyInfo.class),
                        Mockito.eq(params),
                        callbackArgumentCaptor.capture());

        // Simulate the error callback
        callbackArgumentCaptor
                .getValue()
                .onLoadSdkFailure(
                        new LoadSdkException(LOAD_SDK_NOT_FOUND, ERROR_MSG),
                        mSandboxLatencyInfo);
        ArgumentCaptor<LoadSdkException> exceptionCapture =
                ArgumentCaptor.forClass(LoadSdkException.class);
        Mockito.verify(outcomeReceiver).onError(exceptionCapture.capture());
        final LoadSdkException exception = exceptionCapture.getValue();

        assertThat(exception.getLoadSdkErrorCode()).isEqualTo(LOAD_SDK_NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo(ERROR_MSG);
        assertNotNull(exception.getExtraInformation());
        assertTrue(exception.getExtraInformation().isEmpty());
    }

    @Test
    public void testGetSandboxedSdks() throws Exception {
        List<SandboxedSdk> sandboxedSdks = List.of();
        Mockito.when(
                        mBinder.getSandboxedSdks(
                                Mockito.anyString(), Mockito.any(SandboxLatencyInfo.class)))
                .thenReturn(sandboxedSdks);

        assertThat(mSdkSandboxManager.getSandboxedSdks()).isSameInstanceAs(sandboxedSdks);
        Mockito.verify(mBinder)
                .getSandboxedSdks(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.any(SandboxLatencyInfo.class));
    }

    @Test
    public void testUnloadSdk() throws Exception {
        mSdkSandboxManager.unloadSdk(SDK_NAME);
        Mockito.verify(mBinder)
                .unloadSdk(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.eq(SDK_NAME),
                        Mockito.any(SandboxLatencyInfo.class));
    }

    @Test
    public void testRequestSurfacePackageSuccess() throws Exception {
        final Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 400);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());

        OutcomeReceiver<Bundle, RequestSurfacePackageException> outcomeReceiver =
                Mockito.spy(new FakeOutcomeReceiver<>());
        mSdkSandboxManager.requestSurfacePackage(SDK_NAME, params, Runnable::run, outcomeReceiver);

        ArgumentCaptor<IRequestSurfacePackageCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(IRequestSurfacePackageCallback.class);
        Mockito.verify(mBinder)
                .requestSurfacePackage(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.eq(SDK_NAME),
                        Mockito.eq(params.getBinder(EXTRA_HOST_TOKEN)),
                        Mockito.eq(params.getInt(EXTRA_DISPLAY_ID)),
                        Mockito.eq(params.getInt(EXTRA_WIDTH_IN_PIXELS)),
                        Mockito.eq(params.getInt(EXTRA_HEIGHT_IN_PIXELS)),
                        Mockito.any(SandboxLatencyInfo.class),
                        Mockito.eq(params),
                        callbackArgumentCaptor.capture());

        // Simulate the success callback
        final Bundle extraInfo = new Bundle();
        SurfacePackage surfacePackageMock = Mockito.mock(SurfacePackage.class);
        callbackArgumentCaptor
                .getValue()
                .onSurfacePackageReady(surfacePackageMock, 0, extraInfo, mSandboxLatencyInfo);
        ArgumentCaptor<Bundle> responseCapture = ArgumentCaptor.forClass(Bundle.class);
        Mockito.verify(outcomeReceiver).onResult(responseCapture.capture());

        final Bundle response = responseCapture.getValue();
        SurfacePackage surfacePackage =
                response.getParcelable(EXTRA_SURFACE_PACKAGE, SurfacePackage.class);
        assertThat(surfacePackage).isEqualTo(surfacePackageMock);
        assertThat(response).isEqualTo(extraInfo);
    }

    @Test
    public void testRequestSurfacePackageFailed() throws Exception {
        final Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 400);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());

        OutcomeReceiver<Bundle, RequestSurfacePackageException> outcomeReceiver =
                Mockito.spy(new FakeOutcomeReceiver<>());
        mSdkSandboxManager.requestSurfacePackage(SDK_NAME, params, Runnable::run, outcomeReceiver);

        ArgumentCaptor<IRequestSurfacePackageCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(IRequestSurfacePackageCallback.class);
        Mockito.verify(mBinder)
                .requestSurfacePackage(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.eq(SDK_NAME),
                        Mockito.eq(params.getBinder(EXTRA_HOST_TOKEN)),
                        Mockito.eq(params.getInt(EXTRA_DISPLAY_ID)),
                        Mockito.eq(params.getInt(EXTRA_WIDTH_IN_PIXELS)),
                        Mockito.eq(params.getInt(EXTRA_HEIGHT_IN_PIXELS)),
                        Mockito.any(SandboxLatencyInfo.class),
                        Mockito.eq(params),
                        callbackArgumentCaptor.capture());

        // Simulate the error callback
        callbackArgumentCaptor
                .getValue()
                .onSurfacePackageError(
                        REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR, ERROR_MSG, mSandboxLatencyInfo);
        ArgumentCaptor<RequestSurfacePackageException> responseCapture =
                ArgumentCaptor.forClass(RequestSurfacePackageException.class);
        Mockito.verify(outcomeReceiver).onError(responseCapture.capture());

        final RequestSurfacePackageException exception = responseCapture.getValue();
        assertThat(exception.getRequestSurfacePackageErrorCode())
                .isEqualTo(REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR);
        assertThat(exception.getMessage()).isEqualTo(ERROR_MSG);
        assertNotNull(exception.getExtraErrorInformation());
        assertTrue(exception.getExtraErrorInformation().isEmpty());
    }

    @Test
    public void testRequestSurfacePackage_callSuccessful_logSandboxApiLatencyCalled()
            throws Exception {
        final Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 400);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());

        OutcomeReceiver<Bundle, RequestSurfacePackageException> outcomeReceiver =
                Mockito.spy(new FakeOutcomeReceiver<>());
        mSdkSandboxManager.requestSurfacePackage(SDK_NAME, params, Runnable::run, outcomeReceiver);

        ArgumentCaptor<SandboxLatencyInfo> sandboxLatencyInfoCaptor =
                ArgumentCaptor.forClass(SandboxLatencyInfo.class);
        ArgumentCaptor<IRequestSurfacePackageCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(IRequestSurfacePackageCallback.class);
        Mockito.verify(mBinder)
                .requestSurfacePackage(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.eq(SDK_NAME),
                        Mockito.eq(params.getBinder(EXTRA_HOST_TOKEN)),
                        Mockito.eq(params.getInt(EXTRA_DISPLAY_ID)),
                        Mockito.eq(params.getInt(EXTRA_WIDTH_IN_PIXELS)),
                        Mockito.eq(params.getInt(EXTRA_HEIGHT_IN_PIXELS)),
                        sandboxLatencyInfoCaptor.capture(),
                        Mockito.eq(params),
                        callbackArgumentCaptor.capture());
        // Simulate the success callback
        final Bundle extraInfo = new Bundle();
        SurfacePackage surfacePackageMock = Mockito.mock(SurfacePackage.class);
        callbackArgumentCaptor
                .getValue()
                .onSurfacePackageReady(
                        surfacePackageMock, 0, extraInfo, sandboxLatencyInfoCaptor.getValue());

        Mockito.verify(mBinder, Mockito.times(1))
                .logSandboxApiLatency(Mockito.eq(sandboxLatencyInfoCaptor.getValue()));
        assertEquals(
                SandboxLatencyInfo.METHOD_REQUEST_SURFACE_PACKAGE,
                sandboxLatencyInfoCaptor.getValue().getMethod());
    }

    @Test
    public void testRequestSurfacePackage_callFails_logSandboxApiLatencyCalled() throws Exception {
        final Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 400);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());

        mSdkSandboxManager.requestSurfacePackage(
                SDK_NAME, params, Runnable::run, new FakeOutcomeReceiver<>());

        ArgumentCaptor<SandboxLatencyInfo> sandboxLatencyInfoCaptor =
                ArgumentCaptor.forClass(SandboxLatencyInfo.class);
        ArgumentCaptor<IRequestSurfacePackageCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(IRequestSurfacePackageCallback.class);
        Mockito.verify(mBinder)
                .requestSurfacePackage(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.eq(SDK_NAME),
                        Mockito.eq(params.getBinder(EXTRA_HOST_TOKEN)),
                        Mockito.eq(params.getInt(EXTRA_DISPLAY_ID)),
                        Mockito.eq(params.getInt(EXTRA_WIDTH_IN_PIXELS)),
                        Mockito.eq(params.getInt(EXTRA_HEIGHT_IN_PIXELS)),
                        sandboxLatencyInfoCaptor.capture(),
                        Mockito.eq(params),
                        callbackArgumentCaptor.capture());

        // Simulate the error callback
        callbackArgumentCaptor
                .getValue()
                .onSurfacePackageError(
                        REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR,
                        ERROR_MSG,
                        sandboxLatencyInfoCaptor.getValue());

        Mockito.verify(mBinder, Mockito.times(1))
                .logSandboxApiLatency(Mockito.eq(sandboxLatencyInfoCaptor.getValue()));
        assertEquals(
                SandboxLatencyInfo.METHOD_REQUEST_SURFACE_PACKAGE,
                sandboxLatencyInfoCaptor.getValue().getMethod());
    }

    @Test
    public void testRequestSurfacePackage_logSandboxApiLatency_remoteExceptionThrown()
            throws Exception {
        MockitoSession mStaticMockSession =
                ExtendedMockito.mockitoSession().mockStatic(Log.class).startMocking();

        final Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 400);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());

        Mockito.doThrow(new RemoteException("failed"))
                .when(mBinder)
                .logSandboxApiLatency(Mockito.any(SandboxLatencyInfo.class));

        mSdkSandboxManager.requestSurfacePackage(
                SDK_NAME, params, Runnable::run, new FakeOutcomeReceiver<>());

        ArgumentCaptor<IRequestSurfacePackageCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(IRequestSurfacePackageCallback.class);

        Mockito.verify(mBinder)
                .requestSurfacePackage(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.eq(SDK_NAME),
                        Mockito.eq(params.getBinder(EXTRA_HOST_TOKEN)),
                        Mockito.eq(params.getInt(EXTRA_DISPLAY_ID)),
                        Mockito.eq(params.getInt(EXTRA_WIDTH_IN_PIXELS)),
                        Mockito.eq(params.getInt(EXTRA_HEIGHT_IN_PIXELS)),
                        Mockito.any(SandboxLatencyInfo.class),
                        Mockito.eq(params),
                        callbackArgumentCaptor.capture());

        // Simulate the success callback
        final Bundle extraInfo = new Bundle();
        SurfacePackage surfacePackageMock = Mockito.mock(SurfacePackage.class);
        callbackArgumentCaptor
                .getValue()
                .onSurfacePackageReady(surfacePackageMock, 0, extraInfo, mSandboxLatencyInfo);
        ExtendedMockito.verify(
                () ->
                        Log.w(
                                SDK_SANDBOX_MANAGER_TAG,
                                "Remote exception while calling "
                                        + "logSandboxApiLatency.Error: failed"));
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testLoadSdk_callSuccessful_logSandboxApiLatencyCalled() throws Exception {
        final Bundle params = new Bundle();

        mSdkSandboxManager.loadSdk(SDK_NAME, params, Runnable::run, new FakeOutcomeReceiver<>());
        ArgumentCaptor<SandboxLatencyInfo> sandboxLatencyInfoCaptor =
                ArgumentCaptor.forClass(SandboxLatencyInfo.class);
        ArgumentCaptor<ILoadSdkCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(ILoadSdkCallback.class);
        Mockito.verify(mBinder)
                .loadSdk(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.nullable(IBinder.class),
                        Mockito.eq(SDK_NAME),
                        sandboxLatencyInfoCaptor.capture(),
                        Mockito.eq(params),
                        callbackArgumentCaptor.capture());
        // Simulate the success callback
        callbackArgumentCaptor
                .getValue()
                .onLoadSdkSuccess(
                        new SandboxedSdk(new Binder()), sandboxLatencyInfoCaptor.getValue());

        Mockito.verify(mBinder, Mockito.times(1))
                .logSandboxApiLatency(Mockito.eq(sandboxLatencyInfoCaptor.getValue()));
        assertEquals(
                SandboxLatencyInfo.METHOD_LOAD_SDK,
                sandboxLatencyInfoCaptor.getValue().getMethod());
    }

    @Test
    public void testLoadSdk_callFails_logSandboxApiLatencyCalled() throws Exception {
        final Bundle params = new Bundle();

        mSdkSandboxManager.loadSdk(SDK_NAME, params, Runnable::run, new FakeOutcomeReceiver<>());
        ArgumentCaptor<SandboxLatencyInfo> sandboxLatencyInfoCaptor =
                ArgumentCaptor.forClass(SandboxLatencyInfo.class);
        ArgumentCaptor<ILoadSdkCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(ILoadSdkCallback.class);
        Mockito.verify(mBinder)
                .loadSdk(
                        Mockito.eq(mContext.getPackageName()),
                        Mockito.nullable(IBinder.class),
                        Mockito.eq(SDK_NAME),
                        sandboxLatencyInfoCaptor.capture(),
                        Mockito.eq(params),
                        callbackArgumentCaptor.capture());
        // Simulate the error callback
        callbackArgumentCaptor
                .getValue()
                .onLoadSdkFailure(
                        new LoadSdkException(LOAD_SDK_NOT_FOUND, ERROR_MSG),
                        sandboxLatencyInfoCaptor.getValue());

        Mockito.verify(mBinder, Mockito.times(1))
                .logSandboxApiLatency(Mockito.eq(sandboxLatencyInfoCaptor.getValue()));
        assertEquals(
                SandboxLatencyInfo.METHOD_LOAD_SDK,
                sandboxLatencyInfoCaptor.getValue().getMethod());
    }

    @Test
    public void requestSurfacePackageWithMissingWidthParam() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_WIDTH_IN_PIXELS);
    }

    @Test
    public void requestSurfacePackageWithMissingHeightParam() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_HEIGHT_IN_PIXELS);
    }

    @Test
    public void requestSurfacePackageWithMissingDisplayIdParam() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 500);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_DISPLAY_ID);
    }

    @Test
    public void requestSurfacePackageWithMissingHostTokenParam() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 500);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_HOST_TOKEN);
    }

    @Test
    public void requestSurfacePackageWithNegativeWidthParam() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, -1);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_WIDTH_IN_PIXELS);
    }

    @Test
    public void requestSurfacePackageWithNegativeHeightParam() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 500);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, -1);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_HEIGHT_IN_PIXELS);
    }

    @Test
    public void requestSurfacePackageWithNegativeDisplayIdParam() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 500);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, -1);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_DISPLAY_ID);
    }

    @Test
    public void requestSurfacePackageWithWrongTypeWidthParam() {
        Bundle params = new Bundle();
        params.putString(EXTRA_WIDTH_IN_PIXELS, "10");
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_WIDTH_IN_PIXELS);
    }

    @Test
    public void requestSurfacePackageWithWrongTypeHeightParam() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 500);
        params.putString(EXTRA_HEIGHT_IN_PIXELS, "10");
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_HEIGHT_IN_PIXELS);
    }

    @Test
    public void requestSurfacePackageWithWrongTypeDisplayIdParam() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 500);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putString(EXTRA_DISPLAY_ID, "0");
        params.putBinder(EXTRA_HOST_TOKEN, new Binder());
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_DISPLAY_ID);
    }

    @Test
    public void requestSurfacePackageWithNullHostTokenParam() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, 500);
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, 500);
        params.putInt(EXTRA_DISPLAY_ID, 0);
        params.putBinder(EXTRA_HOST_TOKEN, null);
        ensureIllegalArgumentExceptionOnRequestSurfacePackage(params, EXTRA_HOST_TOKEN);
    }

    @Test
    public void testStartSandboxActivity() {
        assumeTrue(SdkLevel.isAtLeastU());

        final Activity fromActivitySpy = Mockito.mock(Activity.class);
        final IBinder token = new Binder();
        mSdkSandboxManager.startSdkSandboxActivity(fromActivitySpy, token);
        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(Intent.class);

        Mockito.verify(fromActivitySpy).startActivity(intentArgumentCaptor.capture());
        Intent intent = intentArgumentCaptor.getValue();
        assertThat(intent.getAction()).isNotNull();
        assertThat(intent.getAction()).isEqualTo(SdkSandboxManager.ACTION_START_SANDBOXED_ACTIVITY);

        Bundle params = intent.getExtras();
        assertThat(params.getBinder(SdkSandboxManager.EXTRA_SANDBOXED_ACTIVITY_HANDLER))
                .isNotNull();
        assertThat(params.getBinder(SdkSandboxManager.EXTRA_SANDBOXED_ACTIVITY_HANDLER))
                .isEqualTo(token);
    }

    @Test
    public void testStartSandboxActivity_logSandboxActivityApiLatencyCalled() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());

        Activity fromActivitySpy = Mockito.mock(Activity.class);
        IBinder token = new Binder();
        mSdkSandboxManager.startSdkSandboxActivity(fromActivitySpy, token);

        Mockito.verify(mBinder, Mockito.times(1))
                .logSandboxActivityApiLatency(
                        Mockito.eq(
                                SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__START_SDK_SANDBOX_ACTIVITY),
                        Mockito.anyInt(),
                        Mockito.anyInt());
    }

    private void ensureIllegalArgumentExceptionOnRequestSurfacePackage(
            Bundle params, String fieldKeyName) {
        OutcomeReceiver<Bundle, RequestSurfacePackageException> outcomeReceiver =
                Mockito.spy(new FakeOutcomeReceiver<>());
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mSdkSandboxManager.requestSurfacePackage(
                                        SDK_NAME, params, Runnable::run, outcomeReceiver));
        assertTrue(exception.getMessage().contains(fieldKeyName));
    }
}
