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

package com.android.server.sdksandbox;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.sdksandbox.SdkSandboxManager.ACTION_START_SANDBOXED_ACTIVITY;

import static com.android.server.wm.ActivityInterceptorCallback.MAINLINE_SDK_SANDBOX_ORDER_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.ActivityManager;
import android.app.sdksandbox.AppOwnedSdkSandboxInterface;
import android.app.sdksandbox.ISharedPreferencesSyncCallback;
import android.app.sdksandbox.SandboxLatencyInfo;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.SharedPreferencesUpdate;
import android.app.sdksandbox.StatsdUtil;
import android.app.sdksandbox.testutils.FakeLoadSdkCallbackBinder;
import android.app.sdksandbox.testutils.FakeRequestSurfacePackageCallbackBinder;
import android.app.sdksandbox.testutils.FakeSdkSandboxManagerLocal;
import android.app.sdksandbox.testutils.FakeSdkSandboxProcessDeathCallbackBinder;
import android.app.sdksandbox.testutils.FakeSdkSandboxService;
import android.app.sdksandbox.testutils.SdkSandboxDeviceSupportedRule;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.modules.utils.build.SdkLevel;
import com.android.sdksandbox.service.stats.SdkSandboxStatsLog;
import com.android.server.pm.PackageManagerLocal;
import com.android.server.sdksandbox.testutils.FakeSdkSandboxProvider;
import com.android.server.wm.ActivityInterceptorCallback;
import com.android.server.wm.ActivityInterceptorCallbackRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/** Unit tests for {@link SdkSandboxManagerService} StatsD metrics collection. */
public class SdkSandboxStatsdMetricsUnitTest {
    private static final String SDK_NAME = "com.android.codeprovider";
    private static final String APP_OWNED_SDK_SANDBOX_INTERFACE_NAME = "com.android.testinterface";
    private static final String TEST_PACKAGE = "com.android.server.sdksandbox.tests";
    private static final long TIME_APP_CALLED_SYSTEM_SERVER = 1;
    private static final long TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP = 3;
    private static final long START_TIME_TO_LOAD_SANDBOX = 5;
    private static final long END_TIME_TO_LOAD_SANDBOX = 7;
    private static final long TIME_SYSTEM_SERVER_CALL_FINISHED = 9;
    private static final long TIME_FAILURE_HANDLED = 11;
    private static final long TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER = 18;
    private static final long TIME_SANDBOX_CALLED_SDK = 19;
    private static final long TIME_SDK_CALL_COMPLETED = 20;
    private static final long TIME_SANDBOX_CALLED_SYSTEM_SERVER = 21;
    private static final long TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX = 22;
    private static final long TIME_SYSTEM_SERVER_CALLED_APP = 23;
    private static final long TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER = 25;
    private static final long APP_TO_SYSTEM_SERVER_LATENCY =
            TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP - TIME_APP_CALLED_SYSTEM_SERVER;
    private static final long SYSTEM_SERVER_APP_TO_SANDBOX_LATENCY =
            TIME_SYSTEM_SERVER_CALL_FINISHED - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP;
    private static final long LOAD_SANDBOX_LATENCY =
            END_TIME_TO_LOAD_SANDBOX - START_TIME_TO_LOAD_SANDBOX;
    private static final long SYSTEM_SERVER_TO_SANDBOX_LATENCY =
            TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER - TIME_SYSTEM_SERVER_CALL_FINISHED;
    private static final long SANDBOX_LATENCY =
            TIME_SANDBOX_CALLED_SYSTEM_SERVER - TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER;
    private static final long SDK_LATENCY = TIME_SDK_CALL_COMPLETED - TIME_SANDBOX_CALLED_SDK;
    private static final long SANDBOX_TO_SYSTEM_SERVER_LATENCY =
            TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX - TIME_SANDBOX_CALLED_SYSTEM_SERVER;
    private static final long SYSTEM_SERVER_SANDBOX_TO_APP_LATENCY =
            TIME_SYSTEM_SERVER_CALLED_APP - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX;
    private static final long SYSTEM_SERVER_TO_APP_LATENCY =
            TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER - TIME_SYSTEM_SERVER_CALLED_APP;
    private static final long FAILED_SYSTEM_SERVER_APP_TO_SANDBOX_LATENCY =
            TIME_FAILURE_HANDLED - TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP;
    private static final long TIME_EVENT_STARTED = 1;
    private static final long TIME_EVENT_FINISHED = 3;
    private static final String TEST_KEY = "key";
    private static final String TEST_VALUE = "value";
    private static final SharedPreferencesUpdate TEST_UPDATE =
            new SharedPreferencesUpdate(new ArrayList<>(), getTestBundle());
    private final ArgumentCaptor<ActivityInterceptorCallback> mInterceptorCallbackArgumentCaptor =
            ArgumentCaptor.forClass(ActivityInterceptorCallback.class);
    private final ArgumentCaptor<SandboxLatencyInfo> mSandboxLatencyInfoCaptor =
            ArgumentCaptor.forClass(SandboxLatencyInfo.class);

    private SdkSandboxManagerService mService;
    private ActivityManager mAmSpy;
    private FakeSdkSandboxService mSdkSandboxService;
    private MockitoSession mStaticMockSession;
    private Context mSpyContext;
    private FakeInjector mInjector;
    private boolean mDisabledNetworkChecks;
    private boolean mDisabledForegroundCheck;
    private SdkSandboxManagerLocal mSdkSandboxManagerLocal;
    private SdkSandboxStatsdLogger mSdkSandboxStatsdLogger;
    private int mClientAppUid;

    private static FakeSdkSandboxProvider sProvider;

    @Rule(order = 0)
    public final SdkSandboxDeviceSupportedRule supportedRule = new SdkSandboxDeviceSupportedRule();

    @Before
    public void setup() {
        StaticMockitoSessionBuilder mockitoSessionBuilder =
                ExtendedMockito.mockitoSession()
                        .strictness(Strictness.LENIENT)
                        .initMocks(this);
        if (SdkLevel.isAtLeastU()) {
            mockitoSessionBuilder =
                    mockitoSessionBuilder.mockStatic(ActivityInterceptorCallbackRegistry.class);
        }
        // Required to access <sdk-library> information and DeviceConfig update.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.READ_DEVICE_CONFIG,
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        // for Context#registerReceiverForAllUsers
                        Manifest.permission.INTERACT_ACROSS_USERS_FULL);

        mStaticMockSession = mockitoSessionBuilder.startMocking();

        if (SdkLevel.isAtLeastU()) {
            // mock the activity interceptor registry anc capture the callback if called
            ActivityInterceptorCallbackRegistry registryMock =
                    Mockito.mock(ActivityInterceptorCallbackRegistry.class);
            ExtendedMockito.doReturn(registryMock)
                    .when(ActivityInterceptorCallbackRegistry::getInstance);
            Mockito.doNothing()
                    .when(registryMock)
                    .registerActivityInterceptorCallback(
                            Mockito.eq(MAINLINE_SDK_SANDBOX_ORDER_ID),
                            mInterceptorCallbackArgumentCaptor.capture());
        }

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSpyContext = Mockito.spy(context);
        ActivityManager am = context.getSystemService(ActivityManager.class);
        mAmSpy = Mockito.spy(Objects.requireNonNull(am));

        Mockito.when(mSpyContext.getSystemService(ActivityManager.class)).thenReturn(mAmSpy);

        mSdkSandboxService = Mockito.spy(FakeSdkSandboxService.class);
        mSdkSandboxService.setTimeValues(
                TIME_SYSTEM_SERVER_CALL_FINISHED,
                TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER,
                TIME_SANDBOX_CALLED_SDK,
                TIME_SDK_CALL_COMPLETED,
                TIME_SANDBOX_CALLED_SYSTEM_SERVER);
        sProvider = Mockito.spy(new FakeSdkSandboxProvider(mSdkSandboxService));

        mSdkSandboxStatsdLogger = Mockito.spy(new SdkSandboxStatsdLogger());
        mInjector =
                Mockito.spy(
                        new FakeInjector(
                                mSpyContext,
                                new SdkSandboxStorageManager(
                                        mSpyContext,
                                        new FakeSdkSandboxManagerLocal(),
                                        Mockito.spy(PackageManagerLocal.class),
                                        /*rootDir=*/ context.getDir(
                                                        "test_dir", Context.MODE_PRIVATE)
                                                .getPath()),
                                sProvider,
                                Mockito.spy(SdkSandboxPulledAtoms.class),
                                mSdkSandboxStatsdLogger));

        mService = new SdkSandboxManagerService(mSpyContext, mInjector);
        mService.forceEnableSandbox();
        mSdkSandboxManagerLocal = mService.getLocalManager();
        assertThat(mSdkSandboxManagerLocal).isNotNull();

        mClientAppUid = Process.myUid();
    }

    @After
    public void tearDown() {
        mInjector.resetTimeSeries();
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testLatencyMetrics_LoadSdk() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_LOAD_SDK);
        mInjector.setLatencyTimeSeries(
                Arrays.asList(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        START_TIME_TO_LOAD_SANDBOX,
                        END_TIME_TO_LOAD_SANDBOX,
                        TIME_SYSTEM_SERVER_CALL_FINISHED,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLED_APP));

        // Explicitly set times app called and received call from System Server as these
        // timestamps are set in SdkSandboxManager.
        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                sandboxLatencyInfo,
                new Bundle(),
                new FakeLoadSdkCallbackBinder());
        mSdkSandboxService.sendLoadSdkSuccessfulWithSandboxLatencies(sandboxLatencyInfo);
        sandboxLatencyInfo.setTimeAppReceivedCallFromSystemServer(
                TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER);
        mService.logSandboxApiLatency(sandboxLatencyInfo);

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxApiLatency(mSandboxLatencyInfoCaptor.capture());
        assertSuccessfulSandboxLatencyInfo(
                mSandboxLatencyInfoCaptor.getValue(),
                SandboxLatencyInfo.METHOD_LOAD_SDK,
                APP_TO_SYSTEM_SERVER_LATENCY,
                /*systemServerAppToSandboxLatency=*/ SYSTEM_SERVER_APP_TO_SANDBOX_LATENCY
                        - LOAD_SANDBOX_LATENCY,
                LOAD_SANDBOX_LATENCY,
                SYSTEM_SERVER_TO_SANDBOX_LATENCY,
                /*sandboxLatency=*/ SANDBOX_LATENCY - SDK_LATENCY,
                SDK_LATENCY,
                SANDBOX_TO_SYSTEM_SERVER_LATENCY,
                SYSTEM_SERVER_SANDBOX_TO_APP_LATENCY,
                SYSTEM_SERVER_TO_APP_LATENCY,
                /*totalCallLatency=*/ TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER
                        - TIME_APP_CALLED_SYSTEM_SERVER);
    }

    @Test
    public void testLatencyMetrics_SystemServerAppToSandbox_LoadSdk_FailureOnMultiLoad()
            throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_LOAD_SDK);

        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                new SandboxLatencyInfo(),
                new Bundle(),
                new FakeLoadSdkCallbackBinder());
        mSdkSandboxService.sendLoadSdkSuccessful();

        // There's no need to explicitly set timestamps for the first sandbox load as we only test
        // that latency is correctly reported on multiload.
        mInjector.setLatencyTimeSeries(
                Arrays.asList(TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP, TIME_FAILURE_HANDLED));
        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                sandboxLatencyInfo,
                new Bundle(),
                new FakeLoadSdkCallbackBinder());
        mService.logSandboxApiLatency(sandboxLatencyInfo);

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxApiLatency(mSandboxLatencyInfoCaptor.capture());
        assertFailedSandboxLatencyInfoAtSystemServerAppToSandbox(
                mSandboxLatencyInfoCaptor.getValue(),
                SandboxLatencyInfo.METHOD_LOAD_SDK,
                FAILED_SYSTEM_SERVER_APP_TO_SANDBOX_LATENCY);
    }

    @Test
    public void testLatencyMetrics_SystemServerAppToSandbox_LoadSdk_InvalidSdkName()
            throws RemoteException {
        disableNetworkPermissionChecks();
        disableForegroundCheck();
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_LOAD_SDK);
        mInjector.setLatencyTimeSeries(
                Arrays.asList(TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP, TIME_FAILURE_HANDLED));

        // Explicitly set times app called and received call from System Server as these
        // timestamps are set in SdkSandboxManager.
        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        mService.loadSdk(
                TEST_PACKAGE,
                null,
                "RANDOM",
                sandboxLatencyInfo,
                new Bundle(),
                new FakeLoadSdkCallbackBinder());
        sandboxLatencyInfo.setTimeAppReceivedCallFromSystemServer(
                TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER);
        mService.logSandboxApiLatency(sandboxLatencyInfo);

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxApiLatency(mSandboxLatencyInfoCaptor.capture());
        assertFailedSandboxLatencyInfoAtSystemServerAppToSandbox(
                mSandboxLatencyInfoCaptor.getValue(),
                SandboxLatencyInfo.METHOD_LOAD_SDK,
                FAILED_SYSTEM_SERVER_APP_TO_SANDBOX_LATENCY);
    }

    @Test
    public void testLatencyMetrics_SystemServerAppToSandbox_LoadSdk_FailureOnAppDeath()
            throws RemoteException {
        disableNetworkPermissionChecks();
        disableForegroundCheck();
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_LOAD_SDK);
        mInjector.setLatencyTimeSeries(
                Arrays.asList(TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP, TIME_FAILURE_HANDLED));
        FakeLoadSdkCallbackBinder callback = Mockito.mock(FakeLoadSdkCallbackBinder.class);
        IBinder binder = Mockito.mock(IBinder.class);
        Mockito.when(callback.asBinder()).thenReturn(binder);
        Mockito.doThrow(new RemoteException())
                .when(binder)
                .linkToDeath(Mockito.any(), Mockito.anyInt());

        // Explicitly set times app called and received call from System Server as these
        // timestamps are set in SdkSandboxManager.
        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        mService.loadSdk(TEST_PACKAGE, null, SDK_NAME, sandboxLatencyInfo, new Bundle(), callback);
        mService.logSandboxApiLatency(sandboxLatencyInfo);

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxApiLatency(mSandboxLatencyInfoCaptor.capture());
        assertFailedSandboxLatencyInfoAtSystemServerAppToSandbox(
                mSandboxLatencyInfoCaptor.getValue(),
                SandboxLatencyInfo.METHOD_LOAD_SDK,
                FAILED_SYSTEM_SERVER_APP_TO_SANDBOX_LATENCY);
    }

    @Test
    public void testLatencyMetrics_SystemServerAppToSandbox_LoadSdk_LoadSandboxFailure() {
        disableNetworkPermissionChecks();
        disableForegroundCheck();
        sProvider.disableBinding();
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_LOAD_SDK);
        mInjector.setLatencyTimeSeries(
                Arrays.asList(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        START_TIME_TO_LOAD_SANDBOX,
                        TIME_FAILURE_HANDLED));

        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                sandboxLatencyInfo,
                new Bundle(),
                new FakeLoadSdkCallbackBinder());
        mService.logSandboxApiLatency(sandboxLatencyInfo);

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxApiLatency(mSandboxLatencyInfoCaptor.capture());
        SandboxLatencyInfo sandboxLatencyInfoValue = mSandboxLatencyInfoCaptor.getValue();
        assertThat(sandboxLatencyInfoValue.getMethod())
                .isEqualTo(SandboxLatencyInfo.METHOD_LOAD_SDK);
        assertThat(sandboxLatencyInfoValue.isTotalCallSuccessful()).isEqualTo(false);
        assertThat(sandboxLatencyInfoValue.isSuccessfulAtLoadSandbox()).isFalse();
        assertThat(sandboxLatencyInfoValue.getLoadSandboxLatency())
                .isEqualTo(TIME_FAILURE_HANDLED - START_TIME_TO_LOAD_SANDBOX);
    }

    @Test
    public void testLatencyMetrics_RequestSurfacePackage() throws Exception {
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_REQUEST_SURFACE_PACKAGE);
        loadSdk(SDK_NAME);
        mInjector.setLatencyTimeSeries(
                Arrays.asList(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_CALL_FINISHED,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLED_APP));

        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                SDK_NAME,
                new Binder(),
                0,
                500,
                500,
                sandboxLatencyInfo,
                new Bundle(),
                new FakeRequestSurfacePackageCallbackBinder());
        setSandboxLatencies(sandboxLatencyInfo);
        mSdkSandboxService.sendSurfacePackageReady(sandboxLatencyInfo);
        sandboxLatencyInfo.setTimeAppReceivedCallFromSystemServer(
                TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER);
        mService.logSandboxApiLatency(sandboxLatencyInfo);

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxApiLatency(mSandboxLatencyInfoCaptor.capture());
        // LOAD_SANDBOX and SDK stages should not be reported for requestSurfacePackage(), so we
        // check that the default latency value is returned for them here.
        assertSuccessfulSandboxLatencyInfo(
                mSandboxLatencyInfoCaptor.getValue(),
                SandboxLatencyInfo.METHOD_REQUEST_SURFACE_PACKAGE,
                APP_TO_SYSTEM_SERVER_LATENCY,
                SYSTEM_SERVER_APP_TO_SANDBOX_LATENCY,
                /*loadSandboxLatency=*/ -1,
                SYSTEM_SERVER_TO_SANDBOX_LATENCY,
                SANDBOX_LATENCY,
                /*sdkLatency=*/ -1,
                SANDBOX_TO_SYSTEM_SERVER_LATENCY,
                SYSTEM_SERVER_SANDBOX_TO_APP_LATENCY,
                SYSTEM_SERVER_TO_APP_LATENCY,
                /*totalCallLatency=*/ TIME_APP_RECEIVED_CALL_FROM_SYSTEM_SERVER
                        - TIME_APP_CALLED_SYSTEM_SERVER);
    }

    @Test
    public void
            testLatencyMetrics_systemServerAppToSandbox_RequestSurfacePackage_sandboxNotLoaded() {
        disableForegroundCheck();
        mInjector.setLatencyTimeSeries(
                Arrays.asList(TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP, TIME_FAILURE_HANDLED));
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_REQUEST_SURFACE_PACKAGE);

        mService.requestSurfacePackage(
                TEST_PACKAGE,
                SDK_NAME,
                new Binder(),
                0,
                500,
                500,
                sandboxLatencyInfo,
                new Bundle(),
                new FakeRequestSurfacePackageCallbackBinder());
        mService.logSandboxApiLatency(sandboxLatencyInfo);

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxApiLatency(mSandboxLatencyInfoCaptor.capture());
        assertFailedSandboxLatencyInfoAtSystemServerAppToSandbox(
                mSandboxLatencyInfoCaptor.getValue(),
                SandboxLatencyInfo.METHOD_REQUEST_SURFACE_PACKAGE,
                FAILED_SYSTEM_SERVER_APP_TO_SANDBOX_LATENCY);
    }

    @Test
    public void testLatencyMetrics_GetSandboxedSdks() {
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_GET_SANDBOXED_SDKS);
        mInjector.setLatencyTimeSeries(
                Arrays.asList(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_CALL_FINISHED));

        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        mService.getSandboxedSdks(TEST_PACKAGE, sandboxLatencyInfo);

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxApiLatency(mSandboxLatencyInfoCaptor.capture());
        assertSuccessfulSandboxLatencyInfo(
                mSandboxLatencyInfoCaptor.getValue(),
                SandboxLatencyInfo.METHOD_GET_SANDBOXED_SDKS,
                APP_TO_SYSTEM_SERVER_LATENCY,
                SYSTEM_SERVER_APP_TO_SANDBOX_LATENCY,
                /*loadSandboxLatency=*/ -1,
                /*systemServerToSandboxLatency=*/ -1,
                /*sandboxLatency=*/ -1,
                /*sdkLatency=*/ -1,
                /*sandboxToSystemServerLatency=*/ -1,
                /*systemServerSandboxToAppLatency=*/ -1,
                /*systemServerToAppLatency=*/ -1,
                TIME_SYSTEM_SERVER_CALL_FINISHED - TIME_APP_CALLED_SYSTEM_SERVER);
    }

    @Test
    public void testLatencyMetrics_SyncDataFromClient() {
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_SYNC_DATA_FROM_CLIENT);
        mInjector.setLatencyTimeSeries(Arrays.asList(TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP));

        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        mService.syncDataFromClient(
                TEST_PACKAGE,
                sandboxLatencyInfo,
                TEST_UPDATE,
                Mockito.mock(ISharedPreferencesSyncCallback.class));

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxApiLatency(mSandboxLatencyInfoCaptor.capture());
        assertSuccessfulSandboxLatencyInfo(
                mSandboxLatencyInfoCaptor.getValue(),
                SandboxLatencyInfo.METHOD_SYNC_DATA_FROM_CLIENT,
                APP_TO_SYSTEM_SERVER_LATENCY,
                /*systemServerAppToSandboxLatency=*/ -1,
                /*loadSandboxLatency=*/ -1,
                /*systemServerToSandboxLatency=*/ -1,
                /*sandboxLatency=*/ -1,
                /*sdkLatency=*/ -1,
                /*sandboxToSystemServerLatency=*/ -1,
                /*systemServerSandboxToAppLatency=*/ -1,
                /*systemServerToAppLatency=*/ -1,
                TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP - TIME_APP_CALLED_SYSTEM_SERVER);
    }

    @Test
    public void testLatencyMetrics_UnloadSdk() throws Exception {
        disableKillUid();
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(SandboxLatencyInfo.METHOD_UNLOAD_SDK);
        loadSdk(SDK_NAME);
        mInjector.setLatencyTimeSeries(
                Arrays.asList(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_CALL_FINISHED,
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_SANDBOX,
                        TIME_SYSTEM_SERVER_CALLED_APP,
                        TIME_SANDBOX_CALLED_SYSTEM_SERVER));

        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        mService.unloadSdk(TEST_PACKAGE, SDK_NAME, sandboxLatencyInfo);
        mSdkSandboxService.sendUnloadSdkSuccess(sandboxLatencyInfo);

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxApiLatency(mSandboxLatencyInfoCaptor.capture());
        assertSuccessfulSandboxLatencyInfo(
                mSandboxLatencyInfoCaptor.getValue(),
                SandboxLatencyInfo.METHOD_UNLOAD_SDK,
                APP_TO_SYSTEM_SERVER_LATENCY,
                SYSTEM_SERVER_APP_TO_SANDBOX_LATENCY,
                /*loadSandboxLatency=*/ -1,
                SYSTEM_SERVER_TO_SANDBOX_LATENCY,
                /*sandboxLatency=*/ SANDBOX_LATENCY - SDK_LATENCY,
                SDK_LATENCY,
                SANDBOX_TO_SYSTEM_SERVER_LATENCY,
                SYSTEM_SERVER_SANDBOX_TO_APP_LATENCY,
                /*systemServerToAppLatency=*/ -1,
                TIME_SYSTEM_SERVER_CALLED_APP - TIME_APP_CALLED_SYSTEM_SERVER);
    }

    @Test
    public void testLatencyMetrics_addSdkSandboxProcessDeathCallback() throws Exception {
        final SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(
                        SandboxLatencyInfo.METHOD_ADD_SDK_SANDBOX_LIFECYCLE_CALLBACK);
        mInjector.setLatencyTimeSeries(
                Arrays.asList(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_CALL_FINISHED));

        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, sandboxLatencyInfo, new FakeSdkSandboxProcessDeathCallbackBinder());

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxApiLatency(mSandboxLatencyInfoCaptor.capture());
        assertSuccessfulSandboxLatencyInfo(
                mSandboxLatencyInfoCaptor.getValue(),
                SandboxLatencyInfo.METHOD_ADD_SDK_SANDBOX_LIFECYCLE_CALLBACK,
                APP_TO_SYSTEM_SERVER_LATENCY,
                SYSTEM_SERVER_APP_TO_SANDBOX_LATENCY,
                /*loadSandboxLatency=*/ -1,
                /*systemServerToSandboxLatency=*/ -1,
                /*sandboxLatency=*/ -1,
                /*sdkLatency=*/ -1,
                /*sandboxToSystemServerLatency=*/ -1,
                /*systemServerSandboxToAppLatency=*/ -1,
                /*systemServerToAppLatency=*/ -1,
                TIME_SYSTEM_SERVER_CALL_FINISHED - TIME_APP_CALLED_SYSTEM_SERVER);
    }

    @Test
    public void testLatencyMetrics_removeSdkSandboxProcessDeathCallback() {
        final SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(
                        SandboxLatencyInfo.METHOD_REMOVE_SDK_SANDBOX_LIFECYCLE_CALLBACK);
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE,
                new SandboxLatencyInfo(),
                new FakeSdkSandboxProcessDeathCallbackBinder());
        mInjector.setLatencyTimeSeries(
                Arrays.asList(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_CALL_FINISHED));

        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        mService.removeSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, sandboxLatencyInfo, new FakeSdkSandboxProcessDeathCallbackBinder());

        // logSandboxApiLatency should be called 2 times: for addSdkSandboxProcessDeathCallback() at
        // first and then for removeSdkSandboxProcessDeathCallback
        Mockito.verify(mSdkSandboxStatsdLogger, Mockito.times(2))
                .logSandboxApiLatency(mSandboxLatencyInfoCaptor.capture());
        assertSuccessfulSandboxLatencyInfo(
                mSandboxLatencyInfoCaptor.getAllValues().get(1),
                SandboxLatencyInfo.METHOD_REMOVE_SDK_SANDBOX_LIFECYCLE_CALLBACK,
                APP_TO_SYSTEM_SERVER_LATENCY,
                SYSTEM_SERVER_APP_TO_SANDBOX_LATENCY,
                /*loadSandboxLatency=*/ -1,
                /*systemServerToSandboxLatency=*/ -1,
                /*sandboxLatency=*/ -1,
                /*sdkLatency=*/ -1,
                /*sandboxToSystemServerLatency=*/ -1,
                /*systemServerSandboxToAppLatency=*/ -1,
                /*systemServerToAppLatency=*/ -1,
                TIME_SYSTEM_SERVER_CALL_FINISHED - TIME_APP_CALLED_SYSTEM_SERVER);
    }

    @Test
    public void testLatencyMetrics_RegisterAppOwnedSdkSandboxInterface() throws Exception {
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(
                        SandboxLatencyInfo.METHOD_REGISTER_APP_OWNED_SDK_SANDBOX_INTERFACE);
        mInjector.setLatencyTimeSeries(
                Arrays.asList(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_CALL_FINISHED));

        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        mService.registerAppOwnedSdkSandboxInterface(
                TEST_PACKAGE,
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                        /*version=*/ 0,
                        /*interfaceIBinder=*/ new Binder()),
                sandboxLatencyInfo);

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxApiLatency(mSandboxLatencyInfoCaptor.capture());
        assertSuccessfulSandboxLatencyInfo(
                mSandboxLatencyInfoCaptor.getValue(),
                SandboxLatencyInfo.METHOD_REGISTER_APP_OWNED_SDK_SANDBOX_INTERFACE,
                APP_TO_SYSTEM_SERVER_LATENCY,
                SYSTEM_SERVER_APP_TO_SANDBOX_LATENCY,
                /*loadSandboxLatency=*/ -1,
                /*systemServerToSandboxLatency=*/ -1,
                /*sandboxLatency=*/ -1,
                /*sdkLatency=*/ -1,
                /*sandboxToSystemServerLatency=*/ -1,
                /*systemServerSandboxToAppLatency=*/ -1,
                /*systemServerToAppLatency=*/ -1,
                TIME_SYSTEM_SERVER_CALL_FINISHED - TIME_APP_CALLED_SYSTEM_SERVER);
    }

    @Test
    public void
            testLatencyMetrics_SystemServerAppToSandbox_RegisterAppOwnedSdkSandboxInterface_FailureOnAppDeath()
                    throws RemoteException {
        IBinder binder = Mockito.mock(IBinder.class);
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(
                        SandboxLatencyInfo.METHOD_REGISTER_APP_OWNED_SDK_SANDBOX_INTERFACE);
        Mockito.doThrow(new RemoteException())
                .when(binder)
                .linkToDeath(Mockito.any(), Mockito.anyInt());
        mInjector.setLatencyTimeSeries(
                Arrays.asList(TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP, TIME_FAILURE_HANDLED));

        mService.registerAppOwnedSdkSandboxInterface(
                TEST_PACKAGE,
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                        /*version=*/ 0,
                        /*interfaceIBinder=*/ binder),
                sandboxLatencyInfo);

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxApiLatency(mSandboxLatencyInfoCaptor.capture());
        assertFailedSandboxLatencyInfoAtSystemServerAppToSandbox(
                mSandboxLatencyInfoCaptor.getValue(),
                SandboxLatencyInfo.METHOD_REGISTER_APP_OWNED_SDK_SANDBOX_INTERFACE,
                FAILED_SYSTEM_SERVER_APP_TO_SANDBOX_LATENCY);
    }

    @Test
    public void testLatencyMetrics_UnregisterAppOwnedSdkSandboxInterface() throws Exception {
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(
                        SandboxLatencyInfo.METHOD_UNREGISTER_APP_OWNED_SDK_SANDBOX_INTERFACE);
        mInjector.setLatencyTimeSeries(
                Arrays.asList(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_CALL_FINISHED));

        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        mService.unregisterAppOwnedSdkSandboxInterface(
                TEST_PACKAGE, APP_OWNED_SDK_SANDBOX_INTERFACE_NAME, sandboxLatencyInfo);

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxApiLatency(mSandboxLatencyInfoCaptor.capture());
        assertSuccessfulSandboxLatencyInfo(
                mSandboxLatencyInfoCaptor.getValue(),
                SandboxLatencyInfo.METHOD_UNREGISTER_APP_OWNED_SDK_SANDBOX_INTERFACE,
                APP_TO_SYSTEM_SERVER_LATENCY,
                SYSTEM_SERVER_APP_TO_SANDBOX_LATENCY,
                /*loadSandboxLatency=*/ -1,
                /*systemServerToSandboxLatency=*/ -1,
                /*sandboxLatency=*/ -1,
                /*sdkLatency=*/ -1,
                /*sandboxToSystemServerLatency=*/ -1,
                /*systemServerSandboxToAppLatency=*/ -1,
                /*systemServerToAppLatency=*/ -1,
                TIME_SYSTEM_SERVER_CALL_FINISHED - TIME_APP_CALLED_SYSTEM_SERVER);
    }

    @Test
    public void testLatencyMetrics_GetAppOwnedSdkSandboxInterfaces() throws Exception {
        SandboxLatencyInfo sandboxLatencyInfo =
                new SandboxLatencyInfo(
                        SandboxLatencyInfo.METHOD_GET_APP_OWNED_SDK_SANDBOX_INTERFACES);
        mInjector.setLatencyTimeSeries(
                Arrays.asList(
                        TIME_SYSTEM_SERVER_RECEIVED_CALL_FROM_APP,
                        TIME_SYSTEM_SERVER_CALL_FINISHED));

        sandboxLatencyInfo.setTimeAppCalledSystemServer(TIME_APP_CALLED_SYSTEM_SERVER);
        mService.getAppOwnedSdkSandboxInterfaces(TEST_PACKAGE, sandboxLatencyInfo);

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxApiLatency(mSandboxLatencyInfoCaptor.capture());
        assertSuccessfulSandboxLatencyInfo(
                mSandboxLatencyInfoCaptor.getValue(),
                SandboxLatencyInfo.METHOD_GET_APP_OWNED_SDK_SANDBOX_INTERFACES,
                APP_TO_SYSTEM_SERVER_LATENCY,
                SYSTEM_SERVER_APP_TO_SANDBOX_LATENCY,
                /*loadSandboxLatency=*/ -1,
                /*systemServerToSandboxLatency=*/ -1,
                /*sandboxLatency=*/ -1,
                /*sdkLatency=*/ -1,
                /*sandboxToSystemServerLatency=*/ -1,
                /*systemServerSandboxToAppLatency=*/ -1,
                /*systemServerToAppLatency=*/ -1,
                TIME_SYSTEM_SERVER_CALL_FINISHED - TIME_APP_CALLED_SYSTEM_SERVER);
    }

    @Test
    public void testlogSandboxActivityApiLatency_CallsStatsd() throws Exception {
        mService.logSandboxActivityApiLatency(
                StatsdUtil.SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__START_SDK_SANDBOX_ACTIVITY,
                StatsdUtil.SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__SUCCESS,
                /*latencyMillis=*/ 123);

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxActivityApiLatency(
                        StatsdUtil
                                .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__START_SDK_SANDBOX_ACTIVITY,
                        StatsdUtil.SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__SUCCESS,
                        /*latencyMillis=*/ 123,
                        mClientAppUid);
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivity_FailsForSandboxUid_CallsStatsd()
            throws Exception {
        loadSdk(SDK_NAME);
        mInjector.setLatencyTimeSeries(Arrays.asList(TIME_EVENT_STARTED, TIME_EVENT_FINISHED));

        assertThrows(
                SecurityException.class,
                () -> {
                    mSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                            new Intent(), Process.toSdkSandboxUid(Process.myUid()), TEST_PACKAGE);
                });

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxActivityApiLatency(
                        SdkSandboxStatsLog
                                .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__ENFORCE_ALLOWED_TO_HOST_SANDBOXED_ACTIVITY,
                        SdkSandboxStatsLog
                                .SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__FAILURE_SECURITY_EXCEPTION,
                        (int) (TIME_EVENT_FINISHED - TIME_EVENT_STARTED),
                        mClientAppUid);
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivity_ThrowsSecurityException_CallsStatsd() {
        assertThrows(
                SecurityException.class,
                () -> {
                    mSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                            null, Process.myUid(), TEST_PACKAGE);
                });

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxActivityApiLatency(
                        Mockito.eq(
                                SdkSandboxStatsLog
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__ENFORCE_ALLOWED_TO_HOST_SANDBOXED_ACTIVITY),
                        Mockito.eq(
                                SdkSandboxStatsLog
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__FAILURE_SECURITY_EXCEPTION),
                        Mockito.anyInt(),
                        Mockito.eq(mClientAppUid));
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivity_FailsIfNoSandboxProcess_CallsStatsd() {
        Intent intent = new Intent().setAction(ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());

        assertThrows(
                SecurityException.class,
                () -> {
                    mSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                            intent, Process.myUid(), TEST_PACKAGE);
                });

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxActivityApiLatency(
                        Mockito.eq(
                                SdkSandboxStatsLog
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__ENFORCE_ALLOWED_TO_HOST_SANDBOXED_ACTIVITY),
                        Mockito.eq(
                                SdkSandboxStatsLog
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__FAILURE_SECURITY_EXCEPTION_NO_SANDBOX_PROCESS),
                        Mockito.anyInt(),
                        Mockito.eq(mClientAppUid));
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivity_FailsIfIntentHasNoExtras_CallsStatsd()
            throws RemoteException {
        loadSdk(SDK_NAME);

        Intent intent = new Intent().setAction(ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                            intent, Process.myUid(), TEST_PACKAGE);
                });

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxActivityApiLatency(
                        Mockito.eq(
                                SdkSandboxStatsLog
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__ENFORCE_ALLOWED_TO_HOST_SANDBOXED_ACTIVITY),
                        Mockito.eq(
                                SdkSandboxStatsLog
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__FAILURE_ILLEGAL_ARGUMENT_EXCEPTION),
                        Mockito.anyInt(),
                        Mockito.eq(mClientAppUid));
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivity_SuccessWithoutComponent_CallsStatsd()
            throws Exception {
        loadSdk(SDK_NAME);

        Intent intent = new Intent().setAction(ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());
        Bundle params = new Bundle();
        params.putBinder(mService.getSandboxedActivityHandlerKey(), new Binder());
        intent.putExtras(params);
        mSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                intent, Process.myUid(), TEST_PACKAGE);

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxActivityApiLatency(
                        Mockito.eq(
                                SdkSandboxStatsLog
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__ENFORCE_ALLOWED_TO_HOST_SANDBOXED_ACTIVITY),
                        Mockito.eq(
                                SdkSandboxStatsLog
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__SUCCESS),
                        Mockito.anyInt(),
                        Mockito.eq(mClientAppUid));
    }

    @Test
    public void testRegisterActivityInterception_SuccessWithRightComponent_CallsStatsd() {
        assumeTrue(SdkLevel.isAtLeastU());

        Intent intent =
                new Intent()
                        .setAction(SdkSandboxManager.ACTION_START_SANDBOXED_ACTIVITY)
                        .setPackage(getSandboxPackageName())
                        .setComponent(new ComponentName(getSandboxPackageName(), ""));
        mInjector.setLatencyTimeSeries(Arrays.asList(TIME_EVENT_STARTED, TIME_EVENT_FINISHED));

        interceptActivityLaunch(intent);

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxActivityApiLatency(
                        SdkSandboxStatsLog
                                .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__INTERCEPT_SANDBOX_ACTIVITY,
                        SdkSandboxStatsLog.SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__SUCCESS,
                        (int) (TIME_EVENT_FINISHED - TIME_EVENT_STARTED),
                        mClientAppUid);
    }

    @Test
    public void testRegisterActivityInterception_NullIntent_CallsStatsd() {
        assumeTrue(SdkLevel.isAtLeastU());

        interceptActivityLaunch(null);

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxActivityApiLatency(
                        Mockito.eq(
                                SdkSandboxStatsLog
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__INTERCEPT_SANDBOX_ACTIVITY),
                        Mockito.eq(
                                SdkSandboxStatsLog
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__FAILURE),
                        Mockito.anyInt(),
                        Mockito.eq(mClientAppUid));
    }

    @Test
    public void testRegisterActivityInterception_WrongComponent_CallsStatsd() {
        assumeTrue(SdkLevel.isAtLeastU());

        Intent intent =
                new Intent()
                        .setAction(SdkSandboxManager.ACTION_START_SANDBOXED_ACTIVITY)
                        .setPackage(getSandboxPackageName())
                        .setComponent(new ComponentName("random", ""));

        interceptActivityLaunch(intent);

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxActivityApiLatency(
                        Mockito.eq(
                                SdkSandboxStatsLog
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__INTERCEPT_SANDBOX_ACTIVITY),
                        Mockito.eq(
                                SdkSandboxStatsLog
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__FAILURE),
                        Mockito.anyInt(),
                        Mockito.eq(mClientAppUid));
    }

    @Test
    public void testRegisterActivityInterception_NoSandboxProcessName_CallsStatsd()
            throws PackageManager.NameNotFoundException {
        assumeTrue(SdkLevel.isAtLeastU());

        Mockito.doThrow(new PackageManager.NameNotFoundException())
                .when(sProvider)
                .toSandboxProcessName(Mockito.any(CallingInfo.class));
        Intent intent =
                new Intent()
                        .setAction(SdkSandboxManager.ACTION_START_SANDBOXED_ACTIVITY)
                        .setPackage(getSandboxPackageName())
                        .setComponent(new ComponentName(getSandboxPackageName(), ""));

        assertThrows(
                SecurityException.class,
                () -> {
                    interceptActivityLaunch(intent);
                });

        Mockito.verify(mSdkSandboxStatsdLogger)
                .logSandboxActivityApiLatency(
                        Mockito.eq(
                                SdkSandboxStatsLog
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__INTERCEPT_SANDBOX_ACTIVITY),
                        Mockito.eq(
                                SdkSandboxStatsLog
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__FAILURE_SECURITY_EXCEPTION),
                        Mockito.anyInt(),
                        Mockito.eq(mClientAppUid));
    }

    private void disableForegroundCheck() {
        if (!mDisabledForegroundCheck) {
            Mockito.doReturn(IMPORTANCE_FOREGROUND).when(mAmSpy).getUidImportance(Mockito.anyInt());
            mDisabledForegroundCheck = true;
        }
    }

    /** Mock the ActivityManager::killUid to avoid SecurityException thrown in test. */
    private void disableKillUid() {
        Mockito.doNothing().when(mAmSpy).killUid(Mockito.anyInt(), Mockito.anyString());
    }

    /* Ignores network permission checks. */
    private void disableNetworkPermissionChecks() {
        if (!mDisabledNetworkChecks) {
            Mockito.doNothing()
                    .when(mSpyContext)
                    .enforceCallingPermission(
                            Mockito.eq("android.permission.INTERNET"), Mockito.anyString());
            Mockito.doNothing()
                    .when(mSpyContext)
                    .enforceCallingPermission(
                            Mockito.eq("android.permission.ACCESS_NETWORK_STATE"),
                            Mockito.anyString());
            mDisabledNetworkChecks = true;
        }
    }

    private static Bundle getTestBundle() {
        final Bundle data = new Bundle();
        data.putString(TEST_KEY, TEST_VALUE);
        return data;
    }

    private void loadSdk(String sdkName) throws RemoteException {
        loadSdk(sdkName, new SandboxLatencyInfo());
    }

    private void loadSdk(String sdkName, SandboxLatencyInfo sandboxLatencyInfo)
            throws RemoteException {
        disableNetworkPermissionChecks();
        disableForegroundCheck();
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(TEST_PACKAGE, null, sdkName, sandboxLatencyInfo, new Bundle(), callback);
        mSdkSandboxService.sendLoadSdkSuccessful();
        callback.assertLoadSdkIsSuccessful();
    }

    private void setSandboxLatencies(SandboxLatencyInfo sandboxLatencyInfo) {
        sandboxLatencyInfo.setTimeSandboxReceivedCallFromSystemServer(
                TIME_SANDBOX_RECEIVED_CALL_FROM_SYSTEM_SERVER);
        sandboxLatencyInfo.setTimeSandboxCalledSystemServer(TIME_SANDBOX_CALLED_SYSTEM_SERVER);
    }

    private String getSandboxPackageName() {
        return mSpyContext.getPackageManager().getSdkSandboxPackageName();
    }

    private ActivityInterceptorCallback.ActivityInterceptResult interceptActivityLaunch(
            Intent intent) {
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.applicationInfo = new ApplicationInfo();
        ActivityInterceptorCallback.ActivityInterceptorInfo info =
                new ActivityInterceptorCallback.ActivityInterceptorInfo.Builder(
                                mClientAppUid,
                                /* callingUid= */ 0,
                                /* callingPid= */ 0,
                                /* callingPackage= */ 0,
                                /* callingFeatureId= */ 0,
                                intent,
                                /* activityOptions= */ null,
                                activityInfo)
                        .setCallingPackage(TEST_PACKAGE)
                        .build();
        return mInterceptorCallbackArgumentCaptor.getValue().onInterceptActivityLaunch(info);
    }

    private void assertFailedSandboxLatencyInfoAtSystemServerAppToSandbox(
            SandboxLatencyInfo sandboxLatencyInfo,
            int method,
            long systemServerAppToSandboxLatency) {
        assertThat(sandboxLatencyInfo.getMethod()).isEqualTo(method);
        assertThat(sandboxLatencyInfo.isTotalCallSuccessful()).isEqualTo(false);
        assertThat(sandboxLatencyInfo.isSuccessfulAtSystemServerAppToSandbox()).isFalse();
        assertThat(sandboxLatencyInfo.getSystemServerAppToSandboxLatency())
                .isEqualTo(systemServerAppToSandboxLatency);
    }

    private void assertSuccessfulSandboxLatencyInfo(
            SandboxLatencyInfo sandboxLatencyInfo,
            int method,
            long appToSystemServerLatency,
            long systemServerAppToSandboxLatency,
            long loadSandboxLatency,
            long systemServerToSandboxLatency,
            long sandboxLatency,
            long sdkLatency,
            long sandboxToSystemServerLatency,
            long systemServerSandboxToAppLatency,
            long systemServerToAppLatency,
            long totalCallLatency) {
        assertThat(sandboxLatencyInfo.getMethod()).isEqualTo(method);
        assertThat(sandboxLatencyInfo.isTotalCallSuccessful()).isTrue();
        assertThat(sandboxLatencyInfo.getAppToSystemServerLatency())
                .isEqualTo(appToSystemServerLatency);
        assertThat(sandboxLatencyInfo.getSystemServerAppToSandboxLatency())
                .isEqualTo(systemServerAppToSandboxLatency);
        assertThat(sandboxLatencyInfo.getLoadSandboxLatency()).isEqualTo(loadSandboxLatency);
        assertThat(sandboxLatencyInfo.getSystemServerToSandboxLatency())
                .isEqualTo(systemServerToSandboxLatency);
        assertThat(sandboxLatencyInfo.getSandboxLatency()).isEqualTo(sandboxLatency);
        assertThat(sandboxLatencyInfo.getSdkLatency()).isEqualTo(sdkLatency);
        assertThat(sandboxLatencyInfo.getSandboxToSystemServerLatency())
                .isEqualTo(sandboxToSystemServerLatency);
        assertThat(sandboxLatencyInfo.getSystemServerSandboxToAppLatency())
                .isEqualTo(systemServerSandboxToAppLatency);
        assertThat(sandboxLatencyInfo.getSystemServerToAppLatency())
                .isEqualTo(systemServerToAppLatency);
        assertThat(sandboxLatencyInfo.getTotalCallLatency()).isEqualTo(totalCallLatency);
    }
}
