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

package com.android.server.sdksandbox;

import static android.Manifest.permission.DUMP;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.sdksandbox.ISharedPreferencesSyncCallback.PREFERENCES_SYNC_INTERNAL_ERROR;
import static android.app.sdksandbox.SdkSandboxManager.ACTION_START_SANDBOXED_ACTIVITY;
import static android.app.sdksandbox.SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR;

import static com.android.server.sdksandbox.SdkSandboxServiceProvider.SANDBOX_INSTR_PROCESS_NAME_SUFFIX;
import static com.android.server.sdksandbox.testutils.FakeSdkSandboxProvider.FAKE_DUMP_OUTPUT;
import static com.android.server.wm.ActivityInterceptorCallback.MAINLINE_SDK_SANDBOX_ORDER_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import android.Manifest;
import android.app.ActivityManager;
import android.app.sdksandbox.AppOwnedSdkSandboxInterface;
import android.app.sdksandbox.ILoadSdkCallback;
import android.app.sdksandbox.ISharedPreferencesSyncCallback;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxLatencyInfo;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.SharedPreferencesUpdate;
import android.app.sdksandbox.testutils.FakeLoadSdkCallbackBinder;
import android.app.sdksandbox.testutils.FakeRequestSurfacePackageCallbackBinder;
import android.app.sdksandbox.testutils.FakeSdkSandboxManagerLocal;
import android.app.sdksandbox.testutils.FakeSdkSandboxProcessDeathCallbackBinder;
import android.app.sdksandbox.testutils.FakeSdkSandboxService;
import android.app.sdksandbox.testutils.FakeSharedPreferencesSyncCallback;
import android.app.sdksandbox.testutils.SdkSandboxDeviceSupportedRule;
import android.app.sdksandbox.testutils.SdkSandboxStorageManagerUtility;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.shared.testing.common.DumpHelper;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.modules.utils.build.SdkLevel;
import com.android.sdksandbox.IComputeSdkStorageCallback;
import com.android.sdksandbox.IUnloadSdkInSandboxCallback;
import com.android.server.LocalManagerRegistry;
import com.android.server.SystemService.TargetUser;
import com.android.server.am.ActivityManagerLocal;
import com.android.server.pm.PackageManagerLocal;
import com.android.server.sdksandbox.SdkSandboxStorageManager.StorageDirInfo;
import com.android.server.sdksandbox.testutils.FakeSdkSandboxProvider;
import com.android.server.wm.ActivityInterceptorCallback;
import com.android.server.wm.ActivityInterceptorCallbackRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Unit tests for {@link SdkSandboxManagerService}.
 */
public class SdkSandboxManagerServiceUnitTest {

    private static final String TAG = SdkSandboxManagerServiceUnitTest.class.getSimpleName();

    private static final String SDK_NAME = "com.android.codeprovider";
    private static final String APP_OWNED_SDK_SANDBOX_INTERFACE_NAME = "com.android.testinterface";
    private static final String SDK_PROVIDER_PACKAGE = "com.android.codeprovider_1";
    private static final String SDK_PROVIDER_RESOURCES_SDK_NAME =
            "com.android.codeproviderresources";
    private static final String TEST_PACKAGE = "com.android.server.sdksandbox.tests";
    private static final String PROPERTY_DISABLE_SANDBOX = "disable_sdk_sandbox";

    private static final String TEST_KEY = "key";
    private static final String TEST_VALUE = "value";
    private static final SharedPreferencesUpdate TEST_UPDATE =
            new SharedPreferencesUpdate(new ArrayList<>(), getTestBundle());

    private SdkSandboxManagerService mService;
    private ActivityManager mAmSpy;
    private FakeSdkSandboxService mSdkSandboxService;
    private MockitoSession mStaticMockSession;
    private Context mSpyContext;
    private SdkSandboxManagerService.Injector mInjector;
    private int mClientAppUid;
    private PackageManagerLocal mPmLocal;
    private ActivityManagerLocal mAmLocal;
    private ArgumentCaptor<ActivityInterceptorCallback> mInterceptorCallbackArgumentCaptor =
            ArgumentCaptor.forClass(ActivityInterceptorCallback.class);
    private SdkSandboxStorageManagerUtility mSdkSandboxStorageManagerUtility;
    private boolean mDisabledNetworkChecks;
    private boolean mDisabledForegroundCheck;
    private SandboxLatencyInfo mSandboxLatencyInfo;

    @Mock private IBinder mAdServicesManager;

    private static FakeSdkSandboxProvider sProvider;
    private static SdkSandboxPulledAtoms sSdkSandboxPulledAtoms;

    private static SdkSandboxSettingsListener sSdkSandboxSettingsListener;

    private SdkSandboxStorageManager mSdkSandboxStorageManager;
    private static SdkSandboxManagerLocal sSdkSandboxManagerLocal;
    private CallingInfo mCallingInfo;
    private DeviceConfigUtil mDeviceConfigUtil;

    @Rule(order = 0)
    public final SdkSandboxDeviceSupportedRule supportedRule = new SdkSandboxDeviceSupportedRule();

    @Before
    public void setup() {
        StaticMockitoSessionBuilder mockitoSessionBuilder =
                ExtendedMockito.mockitoSession()
                        .strictness(Strictness.LENIENT)
                        .mockStatic(LocalManagerRegistry.class)
                        .spyStatic(Process.class)
                        .initMocks(this);
        if (SdkLevel.isAtLeastU()) {
            mockitoSessionBuilder =
                    mockitoSessionBuilder.mockStatic(ActivityInterceptorCallbackRegistry.class);
        }
        // Required to access <sdk-library> information and DeviceConfig update.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.ACCESS_SHARED_LIBRARIES,
                        Manifest.permission.INSTALL_PACKAGES,
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
                            eq(MAINLINE_SDK_SANDBOX_ORDER_ID),
                            mInterceptorCallbackArgumentCaptor.capture());
        }

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSpyContext = Mockito.spy(context);
        ActivityManager am = context.getSystemService(ActivityManager.class);
        mAmSpy = Mockito.spy(Objects.requireNonNull(am));

        Mockito.when(mSpyContext.getSystemService(ActivityManager.class)).thenReturn(mAmSpy);

        mSdkSandboxService = Mockito.spy(FakeSdkSandboxService.class);
        sProvider = new FakeSdkSandboxProvider(mSdkSandboxService);

        // Populate LocalManagerRegistry
        mAmLocal = Mockito.mock(ActivityManagerLocal.class);
        ExtendedMockito.doReturn(mAmLocal)
                .when(() -> LocalManagerRegistry.getManager(ActivityManagerLocal.class));
        mPmLocal = Mockito.spy(PackageManagerLocal.class);

        sSdkSandboxPulledAtoms = Mockito.spy(new SdkSandboxPulledAtoms());

        String testDir = context.getDir("test_dir", Context.MODE_PRIVATE).getPath();
        mSdkSandboxStorageManager =
                new SdkSandboxStorageManager(
                        mSpyContext, new FakeSdkSandboxManagerLocal(), mPmLocal, testDir);
        mSdkSandboxStorageManagerUtility =
                new SdkSandboxStorageManagerUtility(mSdkSandboxStorageManager);

        mInjector =
                Mockito.spy(
                        new FakeInjector(
                                mSpyContext,
                                mSdkSandboxStorageManager,
                                sProvider,
                                sSdkSandboxPulledAtoms,
                                new SdkSandboxStatsdLogger()));

        mService = new SdkSandboxManagerService(mSpyContext, mInjector);
        mService.forceEnableSandbox();
        sSdkSandboxManagerLocal = mService.getLocalManager();
        assertThat(sSdkSandboxManagerLocal).isNotNull();

        sSdkSandboxSettingsListener = mService.getSdkSandboxSettingsListener();
        assertThat(sSdkSandboxSettingsListener).isNotNull();
        mDeviceConfigUtil = new DeviceConfigUtil(sSdkSandboxSettingsListener);

        mClientAppUid = Process.myUid();
        mSandboxLatencyInfo = new SandboxLatencyInfo();
        mCallingInfo = new CallingInfo(mClientAppUid, TEST_PACKAGE);
    }

    @After
    public void tearDown() {
        if (sSdkSandboxSettingsListener != null) {
            sSdkSandboxSettingsListener.unregisterPropertiesListener();
        }
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    /** Mock the ActivityManager::killUid to avoid SecurityException thrown in test. **/
    private void disableKillUid() {
        Mockito.doNothing().when(mAmSpy).killUid(Mockito.anyInt(), Mockito.anyString());
    }

    private void disableForegroundCheck() {
        if (!mDisabledForegroundCheck) {
            Mockito.doReturn(IMPORTANCE_FOREGROUND).when(mAmSpy).getUidImportance(Mockito.anyInt());
            mDisabledForegroundCheck = true;
        }
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

    @Test
    public void testOnUserUnlocking() {
        UserInfo userInfo = new UserInfo(/*id=*/ 0, /*name=*/ "tempUser", /*flags=*/ 0);
        TargetUser user = new TargetUser(userInfo);

        SdkSandboxManagerService.Lifecycle lifeCycle =
                new SdkSandboxManagerService.Lifecycle(mSpyContext);
        lifeCycle.mService = Mockito.mock(SdkSandboxManagerService.class);
        lifeCycle.onUserUnlocking(user);
        Mockito.verify(lifeCycle.mService).onUserUnlocking(ArgumentMatchers.eq(/*userId=*/ 0));
    }

    @Test
    public void testSdkSandboxManagerIsRegistered() throws Exception {
        ServiceManager.getServiceOrThrow(SdkSandboxManager.SDK_SANDBOX_SERVICE);
    }

    @Test
    public void testRegisterAndGetAppOwnedSdkSandboxInterfaceSuccess() throws Exception {
        final IBinder iBinder = new Binder();

        mService.registerAppOwnedSdkSandboxInterface(
                TEST_PACKAGE,
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                        /*version=*/ 0,
                        /*interfaceIBinder=*/ iBinder),
                mSandboxLatencyInfo);
        final List<AppOwnedSdkSandboxInterface> appOwnedSdkSandboxInterfaceList =
                mService.getAppOwnedSdkSandboxInterfaces(TEST_PACKAGE, mSandboxLatencyInfo);

        assertThat(appOwnedSdkSandboxInterfaceList).hasSize(1);
        assertThat(appOwnedSdkSandboxInterfaceList.get(0).getName())
                .isEqualTo(APP_OWNED_SDK_SANDBOX_INTERFACE_NAME);
        assertThat(appOwnedSdkSandboxInterfaceList.get(0).getVersion()).isEqualTo(0);
        assertThat(appOwnedSdkSandboxInterfaceList.get(0).getInterface()).isEqualTo(iBinder);
    }

    @Test
    public void testRegisterAppOwnedSdkSandboxInterfaceAlreadyRegistered() throws Exception {
        mService.registerAppOwnedSdkSandboxInterface(
                TEST_PACKAGE,
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                        /*version=*/ 0,
                        /*interfaceIBinder=*/ new Binder()),
                mSandboxLatencyInfo);

        assertThrows(
                IllegalStateException.class,
                () ->
                        mService.registerAppOwnedSdkSandboxInterface(
                                TEST_PACKAGE,
                                new AppOwnedSdkSandboxInterface(
                                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                                        /*version=*/ 0,
                                        /*interfaceIBinder=*/ new Binder()),
                                mSandboxLatencyInfo));
    }

    @Test
    public void testUnregisterAppOwnedSdkSandboxInterface() throws Exception {
        mService.registerAppOwnedSdkSandboxInterface(
                TEST_PACKAGE,
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                        /*version=*/ 0,
                        /*interfaceIBinder=*/ new Binder()),
                mSandboxLatencyInfo);
        mService.unregisterAppOwnedSdkSandboxInterface(
                TEST_PACKAGE, APP_OWNED_SDK_SANDBOX_INTERFACE_NAME, mSandboxLatencyInfo);

        assertThat(mService.getAppOwnedSdkSandboxInterfaces(TEST_PACKAGE, mSandboxLatencyInfo))
                .hasSize(0);
    }

    @Test
    public void testLoadSdkIsSuccessful() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(TEST_PACKAGE, null, SDK_NAME, mSandboxLatencyInfo, new Bundle(), callback);
        // Assume sdk sandbox loads successfully
        mSdkSandboxService.sendLoadSdkSuccessful();
        callback.assertLoadSdkIsSuccessful();
    }

    @Test
    public void testLoadSdkNonExistentCallingPackage() {
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();

        mService.loadSdk(
                "does.not.exist", null, SDK_NAME, mSandboxLatencyInfo, new Bundle(), callback);

        LoadSdkException thrown = callback.getLoadSdkException();
        assertEquals(LOAD_SDK_INTERNAL_ERROR, thrown.getLoadSdkErrorCode());
        assertThat(thrown).hasMessageThat().contains("does.not.exist");
    }

    // Tests the failure of attempting to load an SDK when the calling package name and calling uid
    // does not match.
    @Test
    public void testLoadSdkIncorrectCallingPackage() {
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();

        // Try loading the SDK from another installed sample app with a different uid.
        mService.loadSdk(
                "com.android.emptysampleapp",
                null,
                SDK_NAME,
                mSandboxLatencyInfo,
                new Bundle(),
                callback);

        LoadSdkException thrown = callback.getLoadSdkException();
        assertEquals(LOAD_SDK_INTERNAL_ERROR, thrown.getLoadSdkErrorCode());
        assertThat(thrown).hasMessageThat().contains("does not belong to uid");
    }

    @Test
    public void testLoadSdkPackageDoesNotExist() {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(
                TEST_PACKAGE, null, "does.not.exist", mSandboxLatencyInfo, new Bundle(), callback);

        // Verify loading failed
        callback.assertLoadSdkIsUnsuccessful();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_NOT_FOUND);
        assertThat(callback.getLoadSdkErrorMsg()).contains("does.not.exist");
    }

    @Test
    public void testLoadSdk_errorFromSdkSandbox() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();

        mService.loadSdk(TEST_PACKAGE, null, SDK_NAME, mSandboxLatencyInfo, new Bundle(), callback);
        mSdkSandboxService.sendLoadSdkError();

        // Verify loading failed
        callback.assertLoadSdkIsUnsuccessful();
        assertThat(callback.getLoadSdkErrorCode()).isEqualTo(LOAD_SDK_INTERNAL_ERROR);
    }

    @Test
    public void testLoadSdk_errorNoInternet() throws Exception {
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();

        mService.loadSdk(TEST_PACKAGE, null, SDK_NAME, mSandboxLatencyInfo, new Bundle(), callback);

        LoadSdkException thrown = callback.getLoadSdkException();
        assertEquals(LOAD_SDK_INTERNAL_ERROR, thrown.getLoadSdkErrorCode());
        assertThat(thrown).hasMessageThat().contains(android.Manifest.permission.INTERNET);
    }

    @Test
    public void testLoadSdk_errorNoAccessNetworkState() throws Exception {
        // Stub out internet permission check
        Mockito.doNothing().when(mSpyContext).enforceCallingPermission(
                Mockito.eq("android.permission.INTERNET"), Mockito.anyString());

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();

        mService.loadSdk(TEST_PACKAGE, null, SDK_NAME, mSandboxLatencyInfo, new Bundle(), callback);

        LoadSdkException thrown = callback.getLoadSdkException();
        assertEquals(LOAD_SDK_INTERNAL_ERROR, thrown.getLoadSdkErrorCode());
        assertThat(thrown).hasMessageThat().contains(
                android.Manifest.permission.ACCESS_NETWORK_STATE);
    }

    @Test
    public void testLoadSdk_successOnFirstLoad_errorOnLoadAgain() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        // Load it once
        {
            FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
            mService.loadSdk(
                    TEST_PACKAGE, null, SDK_NAME, mSandboxLatencyInfo, new Bundle(), callback);
            // Assume SdkSandbox loads successfully
            mSdkSandboxService.sendLoadSdkSuccessful();
            callback.assertLoadSdkIsSuccessful();
        }

        // Load it again
        {
            FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
            mService.loadSdk(
                    TEST_PACKAGE, null, SDK_NAME, mSandboxLatencyInfo, new Bundle(), callback);
            // Verify loading failed
            callback.assertLoadSdkIsUnsuccessful();
            assertThat(callback.getLoadSdkErrorCode())
                    .isEqualTo(SdkSandboxManager.LOAD_SDK_ALREADY_LOADED);
            assertThat(callback.getLoadSdkErrorMsg()).contains("has been loaded already");
        }
    }

    @Test
    public void testLoadSdk_firstLoadPending_errorOnLoadAgainRequest() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        // Request to load the SDK, but do not complete loading it
        {
            FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
            mService.loadSdk(
                    TEST_PACKAGE, null, SDK_NAME, mSandboxLatencyInfo, new Bundle(), callback);
        }

        // Requesting to load the SDK while the first load is still pending should throw an error
        {
            FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
            mService.loadSdk(
                    TEST_PACKAGE, null, SDK_NAME, mSandboxLatencyInfo, new Bundle(), callback);
            // Verify loading failed
            callback.assertLoadSdkIsUnsuccessful();
            assertThat(callback.getLoadSdkErrorCode())
                    .isEqualTo(SdkSandboxManager.LOAD_SDK_ALREADY_LOADED);
            assertThat(callback.getLoadSdkErrorMsg()).contains("is currently being loaded");
        }
    }

    @Test
    public void testLoadSdk_errorOnFirstLoad_canBeLoadedAgain() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        // Load SDK, but make it fail
        {
            FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
            mService.loadSdk(
                    TEST_PACKAGE, null, SDK_NAME, mSandboxLatencyInfo, new Bundle(), callback);
            // Assume sdk load fails
            mSdkSandboxService.sendLoadSdkError();
            callback.assertLoadSdkIsUnsuccessful();
        }

        // Caller should be able to retry loading the code
        loadSdk(SDK_NAME);
    }

    @Test
    public void testLoadSdk_sandboxDiesInBetween() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        // Load an sdk
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(TEST_PACKAGE, null, SDK_NAME, mSandboxLatencyInfo, new Bundle(), callback);

        // Kill the sandbox before the SDK can call the callback
        killSandbox();

        callback.assertLoadSdkIsUnsuccessful();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.SDK_SANDBOX_PROCESS_NOT_AVAILABLE);
    }

    @Test
    public void testSdkCanBeLoadedAfterSandboxDeath() throws Exception {
        loadSdk(SDK_NAME);
        killSandbox();
        loadSdk(SDK_NAME);
    }

    @Test
    public void testLoadSdk_sandboxIsInitialized() throws Exception {
        loadSdk(SDK_NAME);

        // Verify that sandbox was initialized
        assertThat(mSdkSandboxService.getInitializationCount()).isEqualTo(1);
    }

    @Test
    public void testLoadSdk_sandboxIsInitialized_onlyOnce() throws Exception {
        loadSdk(SDK_NAME);
        loadSdk(SDK_PROVIDER_RESOURCES_SDK_NAME);

        // Verify that sandbox was initialized
        assertThat(mSdkSandboxService.getInitializationCount()).isEqualTo(1);
    }

    @Test
    public void testLoadSdk_sandboxIsInitializedAfterRestart() throws Exception {
        loadSdk(SDK_NAME);
        restartAndSetSandboxService();
        // Restarting creates and sets a new sandbox service. Verify that the new one has been
        // initialized.
        assertThat(mSdkSandboxService.getInitializationCount()).isEqualTo(1);
    }

    @Test
    public void testLoadSdk_sandboxInitializationFails() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();
        disableKillUid();

        mSdkSandboxService.failInitialization = true;

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(TEST_PACKAGE, null, SDK_NAME, mSandboxLatencyInfo, new Bundle(), callback);

        // If initialization failed, the sandbox would be unbound.
        assertThat(sProvider.getSdkSandboxServiceForApp(mCallingInfo)).isNull();

        // Call binderDied() on the sandbox to apply the effects of sandbox death detection after
        // unbinding.
        killSandbox();

        mSdkSandboxService.failInitialization = false;
        // SDK loading should succeed afterwards.
        loadSdk(SDK_NAME);
        assertThat(sProvider.getSdkSandboxServiceForApp(mCallingInfo)).isNotNull();
    }

    @Test
    public void testLoadSdk_sdkDataPrepared_onlyOnce() throws Exception {
        loadSdk(SDK_NAME);
        loadSdk(SDK_PROVIDER_RESOURCES_SDK_NAME);

        // Verify that SDK data was prepared.
        Mockito.verify(mPmLocal, Mockito.times(1))
                .reconcileSdkData(
                        Mockito.nullable(String.class),
                        Mockito.anyString(),
                        Mockito.anyList(),
                        Mockito.anyInt(),
                        Mockito.anyInt(),
                        Mockito.anyInt(),
                        Mockito.anyString(),
                        Mockito.anyInt());
    }

    @Test
    public void testLoadSdk_sdkDataPreparedAfterSandboxRestart() throws Exception {
        loadSdk(SDK_NAME);
        restartAndSetSandboxService();

        // Verify that SDK data was prepared for the newly restarted sandbox.
        Mockito.verify(mPmLocal, Mockito.times(1))
                .reconcileSdkData(
                        Mockito.nullable(String.class),
                        Mockito.anyString(),
                        Mockito.anyList(),
                        Mockito.anyInt(),
                        Mockito.anyInt(),
                        Mockito.anyInt(),
                        Mockito.anyString(),
                        Mockito.anyInt());
    }

    @Test
    public void testRequestSurfacePackageSdkNotLoaded_SandboxExists() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                mSandboxLatencyInfo,
                new Bundle(),
                callback);
        // Assume SdkSandbox loads successfully
        mSdkSandboxService.sendLoadSdkSuccessful();
        callback.assertLoadSdkIsSuccessful();

        // Trying to request package with not exist SDK packageName
        String sdkName = "invalid";
        FakeRequestSurfacePackageCallbackBinder surfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                sdkName,
                new Binder(),
                0,
                500,
                500,
                mSandboxLatencyInfo,
                new Bundle(),
                surfacePackageCallback);
        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isFalse();
        assertThat(surfacePackageCallback.getSurfacePackageErrorCode())
                .isEqualTo(SdkSandboxManager.REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED);
    }

    @Test
    public void testRequestSurfacePackageSdkNotLoaded_SandboxDoesNotExist() throws Exception {
        disableForegroundCheck();

        // Trying to request package when sandbox is not there
        String sdkName = "invalid";
        FakeRequestSurfacePackageCallbackBinder callback =
                new FakeRequestSurfacePackageCallbackBinder();
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                sdkName,
                new Binder(),
                0,
                500,
                500,
                mSandboxLatencyInfo,
                new Bundle(),
                callback);
        assertThat(callback.isRequestSurfacePackageSuccessful()).isFalse();
        assertThat(callback.getSurfacePackageErrorCode())
                .isEqualTo(SdkSandboxManager.REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED);
    }

    @Test
    public void testRequestSurfacePackage() throws Exception {
        // 1. We first need to collect a proper sdkToken by calling loadCode
        loadSdk(SDK_NAME);

        // 2. Call request package with the retrieved sdkToken
        FakeRequestSurfacePackageCallbackBinder surfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                SDK_NAME,
                new Binder(),
                0,
                500,
                500,
                mSandboxLatencyInfo,
                new Bundle(),
                surfacePackageCallback);
        mSdkSandboxService.sendSurfacePackageReady(new SandboxLatencyInfo());
        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isTrue();
    }

    @Test
    public void testRequestSurfacePackageFailedAfterAppDied() throws Exception {
        disableKillUid();
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = Mockito.spy(new FakeLoadSdkCallbackBinder());
        Mockito.doReturn(Mockito.mock(Binder.class)).when(callback).asBinder();

        ArgumentCaptor<IBinder.DeathRecipient> deathRecipient =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);

        mService.loadSdk(TEST_PACKAGE, null, SDK_NAME, mSandboxLatencyInfo, new Bundle(), callback);
        mSdkSandboxService.sendLoadSdkSuccessful();
        callback.assertLoadSdkIsSuccessful();

        Mockito.verify(callback.asBinder())
                .linkToDeath(deathRecipient.capture(), ArgumentMatchers.eq(0));

        // App Died
        deathRecipient.getValue().binderDied();

        FakeRequestSurfacePackageCallbackBinder requestSurfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                SDK_NAME,
                new Binder(),
                0,
                500,
                500,
                mSandboxLatencyInfo,
                new Bundle(),
                requestSurfacePackageCallback);
        assertThat(requestSurfacePackageCallback.isRequestSurfacePackageSuccessful()).isFalse();
        assertThat(requestSurfacePackageCallback.getSurfacePackageErrorCode())
                .isEqualTo(SdkSandboxManager.REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED);
    }

    @Test
    public void testSurfacePackageError() throws Exception {
        loadSdk(SDK_NAME);

        // Assume SurfacePackage encounters an error.
        FakeRequestSurfacePackageCallbackBinder surfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        mSdkSandboxService.sendSurfacePackageError(
                SdkSandboxManager.REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR,
                "bad surface",
                surfacePackageCallback);
        assertThat(surfacePackageCallback.getSurfacePackageErrorMsg()).contains("bad surface");
        assertThat(surfacePackageCallback.getSurfacePackageErrorCode())
                .isEqualTo(SdkSandboxManager.REQUEST_SURFACE_PACKAGE_INTERNAL_ERROR);
    }

    @Test
    public void testRequestSurfacePackage_SandboxDiesInBetween() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        // Load an sdk
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(TEST_PACKAGE, null, SDK_NAME, mSandboxLatencyInfo, new Bundle(), callback);
        mSdkSandboxService.sendLoadSdkSuccessful();
        callback.assertLoadSdkIsSuccessful();

        // Request surface package from the SDK
        FakeRequestSurfacePackageCallbackBinder surfacePackageCallback =
                new FakeRequestSurfacePackageCallbackBinder();
        mService.requestSurfacePackage(
                TEST_PACKAGE,
                SDK_NAME,
                new Binder(),
                0,
                500,
                500,
                mSandboxLatencyInfo,
                new Bundle(),
                surfacePackageCallback);

        // Kill the sandbox before the SDK can call the callback
        killSandbox();

        assertThat(surfacePackageCallback.isRequestSurfacePackageSuccessful()).isFalse();
        assertThat(surfacePackageCallback.getSurfacePackageErrorCode())
                .isEqualTo(SdkSandboxManager.REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED);
    }

    @Test
    public void testAddSdkSandboxProcessDeathCallback_BeforeStartingSandbox() throws Exception {
        // Register for sandbox death event
        FakeSdkSandboxProcessDeathCallbackBinder lifecycleCallback =
                new FakeSdkSandboxProcessDeathCallbackBinder();
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, mSandboxLatencyInfo, lifecycleCallback);

        // Load SDK and start the sandbox
        loadSdk(SDK_NAME);

        killSandbox();

        // Check that death is recorded correctly
        assertThat(lifecycleCallback.waitForSandboxDeath()).isTrue();
    }

    @Test
    public void testAddSdkSandboxProcessDeathCallback_AfterStartingSandbox() throws Exception {
        // Load SDK and start the sandbox
        loadSdk(SDK_NAME);

        // Register for sandbox death event
        FakeSdkSandboxProcessDeathCallbackBinder lifecycleCallback =
                new FakeSdkSandboxProcessDeathCallbackBinder();
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, mSandboxLatencyInfo, lifecycleCallback);

        killSandbox();

        // Check that death is recorded correctly
        assertThat(lifecycleCallback.waitForSandboxDeath()).isTrue();
    }

    @Test
    public void testSdkSandboxProcessDeathCallback_AfterRestartingSandbox() throws Exception {
        loadSdk(SDK_NAME);

        // Register for sandbox death event
        FakeSdkSandboxProcessDeathCallbackBinder lifecycleCallback1 =
                new FakeSdkSandboxProcessDeathCallbackBinder();
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, mSandboxLatencyInfo, lifecycleCallback1);
        killSandbox();
        assertThat(lifecycleCallback1.waitForSandboxDeath()).isTrue();

        restartAndSetSandboxService();

        // Register for sandbox death event again and verify that death is detected.
        FakeSdkSandboxProcessDeathCallbackBinder lifecycleCallback2 =
                new FakeSdkSandboxProcessDeathCallbackBinder();
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, mSandboxLatencyInfo, lifecycleCallback2);
        assertThat(lifecycleCallback2.waitForSandboxDeath()).isFalse();
        killSandbox();
        assertThat(lifecycleCallback2.waitForSandboxDeath()).isTrue();
    }

    @Test
    public void testMultipleAddSdkSandboxProcessDeathCallbacks() throws Exception {
        // Load SDK and start the sandbox
        loadSdk(SDK_NAME);

        // Register for sandbox death event
        FakeSdkSandboxProcessDeathCallbackBinder lifecycleCallback1 =
                new FakeSdkSandboxProcessDeathCallbackBinder();
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, mSandboxLatencyInfo, lifecycleCallback1);

        // Register for sandbox death event again
        FakeSdkSandboxProcessDeathCallbackBinder lifecycleCallback2 =
                new FakeSdkSandboxProcessDeathCallbackBinder();
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, mSandboxLatencyInfo, lifecycleCallback2);

        killSandbox();

        // Check that death is recorded correctly
        assertThat(lifecycleCallback1.waitForSandboxDeath()).isTrue();
        assertThat(lifecycleCallback2.waitForSandboxDeath()).isTrue();
    }

    @Test
    public void testRemoveSdkSandboxProcessDeathCallback() throws Exception {
        // Load SDK and start the sandbox
        loadSdk(SDK_NAME);

        // Register for sandbox death event
        FakeSdkSandboxProcessDeathCallbackBinder lifecycleCallback1 =
                new FakeSdkSandboxProcessDeathCallbackBinder();
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, mSandboxLatencyInfo, lifecycleCallback1);

        // Register for sandbox death event again
        FakeSdkSandboxProcessDeathCallbackBinder lifecycleCallback2 =
                new FakeSdkSandboxProcessDeathCallbackBinder();
        mService.addSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, mSandboxLatencyInfo, lifecycleCallback2);

        // Unregister one of the lifecycle callbacks
        mService.removeSdkSandboxProcessDeathCallback(
                TEST_PACKAGE, mSandboxLatencyInfo, lifecycleCallback1);

        killSandbox();

        // Check that death is recorded correctly
        assertThat(lifecycleCallback1.waitForSandboxDeath()).isFalse();
        assertThat(lifecycleCallback2.waitForSandboxDeath()).isTrue();
    }

    @Test
    public void testSdkSandboxServiceUnbindingWhenAppDied() throws Exception {
        disableKillUid();
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        ILoadSdkCallback.Stub callback = Mockito.spy(ILoadSdkCallback.Stub.class);
        assertThat(sProvider.getSdkSandboxServiceForApp(mCallingInfo)).isNull();

        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                mSandboxLatencyInfo,
                new Bundle(),
                callback);

        ArgumentCaptor<IBinder.DeathRecipient> deathRecipient = ArgumentCaptor
                .forClass(IBinder.DeathRecipient.class);
        Mockito.verify(callback.asBinder(), Mockito.times(1))
                .linkToDeath(deathRecipient.capture(), Mockito.eq(0));

        assertThat(sProvider.getSdkSandboxServiceForApp(mCallingInfo)).isNotNull();
        deathRecipient.getValue().binderDied();
        assertThat(sProvider.getSdkSandboxServiceForApp(mCallingInfo)).isNull();
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailIfCalledFromSandboxUid()
            throws RemoteException {
        loadSdk(SDK_NAME);

        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    new Intent(),
                                    Process.toSdkSandboxUid(mClientAppUid),
                                    TEST_PACKAGE);
                        });
        assertEquals(
                "Sandbox process is not allowed to start sandbox activities.",
                exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailForNullIntents() {
        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    null, mClientAppUid, TEST_PACKAGE);
                        });
        assertEquals("Intent to start sandbox activity is null.", exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailForNullActions() {
        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    new Intent(), mClientAppUid, TEST_PACKAGE);
                        });
        assertEquals(
                "Sandbox activity intent must have an action ("
                        + ACTION_START_SANDBOXED_ACTIVITY
                        + ").",
                exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailForWrongAction() {
        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    new Intent().setAction(Intent.ACTION_VIEW),
                                    mClientAppUid,
                                    TEST_PACKAGE);
                        });
        assertEquals(
                "Sandbox activity intent must have an action ("
                        + ACTION_START_SANDBOXED_ACTIVITY
                        + ").",
                exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailForNullPackage() {
        Intent intent = new Intent().setAction(ACTION_START_SANDBOXED_ACTIVITY);
        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    intent, mClientAppUid, TEST_PACKAGE);
                        });
        assertEquals(
                "Sandbox activity intent's package must be set to the sandbox package",
                exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailForIntentsTargetingOtherPackages() {
        Intent intent =
                new Intent()
                        .setAction(ACTION_START_SANDBOXED_ACTIVITY)
                        .setPackage("com.random.package");
        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    intent, mClientAppUid, TEST_PACKAGE);
                        });
        assertEquals(
                "Sandbox activity intent's package must be set to the sandbox package",
                exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailForIntentsWithWrongComponent()
            throws Exception {
        loadSdk(SDK_NAME);

        Intent intent =
                new Intent()
                        .setAction(ACTION_START_SANDBOXED_ACTIVITY)
                        .setPackage(getSandboxPackageName())
                        .setComponent(new ComponentName("random", ""));
        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    intent, mClientAppUid, TEST_PACKAGE);
                        });
        assertEquals(
                "Sandbox activity intent's component must refer to the sandbox package",
                exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailIfNoSandboxProcees() {
        Intent intent = new Intent().setAction(ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());

        SecurityException exception =
                assertThrows(
                        SecurityException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    intent, mClientAppUid, TEST_PACKAGE);
                        });
        assertEquals(
                "There is no sandbox process running for the caller uid: " + mClientAppUid + ".",
                exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailIfIntentHasNoExtras()
            throws RemoteException {
        loadSdk(SDK_NAME);

        Intent intent = new Intent().setAction(ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    intent, mClientAppUid, TEST_PACKAGE);
                        });
        assertEquals(
                "Intent should contain an extra params with key = "
                        + mService.getSandboxedActivityHandlerKey()
                        + " and value is an IBinder that identifies a registered "
                        + "SandboxedActivityHandler.",
                exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailIfIntentHasNoHandlerExtra()
            throws RemoteException {
        loadSdk(SDK_NAME);

        Intent intent = new Intent().setAction(ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());
        intent.putExtras(new Bundle());

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    intent, mClientAppUid, TEST_PACKAGE);
                        });
        assertEquals(
                "Intent should contain an extra params with key = "
                        + mService.getSandboxedActivityHandlerKey()
                        + " and value is an IBinder that identifies a registered "
                        + "SandboxedActivityHandler.",
                exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivityFailIfIntentHasWrongTypeOfHandlerExtra()
            throws RemoteException {
        loadSdk(SDK_NAME);

        Intent intent = new Intent().setAction(ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());
        Bundle params = new Bundle();
        params.putString(mService.getSandboxedActivityHandlerKey(), "");
        intent.putExtras(params);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> {
                            sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                                    intent, mClientAppUid, TEST_PACKAGE);
                        });
        assertEquals(
                "Intent should contain an extra params with key = "
                        + mService.getSandboxedActivityHandlerKey()
                        + " and value is an IBinder that identifies a registered "
                        + "SandboxedActivityHandler.",
                exception.getMessage());
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivitySuccessWithoutComponent()
            throws Exception {
        loadSdk(SDK_NAME);

        Intent intent = new Intent().setAction(ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());
        Bundle params = new Bundle();
        params.putBinder(mService.getSandboxedActivityHandlerKey(), new Binder());
        intent.putExtras(params);
        sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                intent, mClientAppUid, TEST_PACKAGE);
    }

    @Test
    public void testEnforceAllowedToHostSandboxedActivitySuccessWithComponentReferToSandboxPackage()
            throws Exception {
        loadSdk(SDK_NAME);

        Intent intent = new Intent().setAction(ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());
        intent.setComponent(new ComponentName(getSandboxPackageName(), ""));
        Bundle params = new Bundle();
        params.putBinder(mService.getSandboxedActivityHandlerKey(), new Binder());
        intent.putExtras(params);
        sSdkSandboxManagerLocal.enforceAllowedToHostSandboxedActivity(
                intent, mClientAppUid, TEST_PACKAGE);
    }

    @Test
    public void testGetSdkSandboxProcessNameForInstrumentation() throws Exception {
        final PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        final ApplicationInfo info = pm.getApplicationInfo(TEST_PACKAGE, 0);
        final String processName =
                sSdkSandboxManagerLocal.getSdkSandboxProcessNameForInstrumentation(info);
        assertThat(processName).isEqualTo(TEST_PACKAGE + SANDBOX_INSTR_PROCESS_NAME_SUFFIX);
    }

    @Test
    public void test_getSdkInstrumentationInfo() throws Exception {
        final PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        ApplicationInfo clientAppInfo = pm.getApplicationInfo(TEST_PACKAGE, 0);

        ApplicationInfo sdkSandboxInfo =
                sSdkSandboxManagerLocal.getSdkSandboxApplicationInfoForInstrumentation(
                        clientAppInfo, /* isSdkInSandbox= */ false);

        assertThat(sdkSandboxInfo.processName)
                .isEqualTo(TEST_PACKAGE + SANDBOX_INSTR_PROCESS_NAME_SUFFIX);
        assertThat(sdkSandboxInfo.packageName).isEqualTo(pm.getSdkSandboxPackageName());
        assertThat(sdkSandboxInfo.sourceDir).startsWith("/apex/com.android.adservices");
    }

    @Test
    public void test_getSdkInstrumentationInfo_sdkInSandbox() throws Exception {
        final PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        ApplicationInfo clientAppInfo = pm.getApplicationInfo(TEST_PACKAGE, 0);

        ApplicationInfo sdkSandboxInfo =
                sSdkSandboxManagerLocal.getSdkSandboxApplicationInfoForInstrumentation(
                        clientAppInfo, /* isSdkInSandbox= */ true);

        assertThat(sdkSandboxInfo.processName)
                .isEqualTo(TEST_PACKAGE + SANDBOX_INSTR_PROCESS_NAME_SUFFIX);
        assertThat(sdkSandboxInfo.packageName).isEqualTo(pm.getSdkSandboxPackageName());
        assertThat(sdkSandboxInfo.sourceDir).startsWith("/data/app");
    }

    @Test
    public void testNotifyInstrumentationStarted_killsSandboxProcess() throws Exception {
        disableKillUid();

        // First load SDK.
        loadSdk(SDK_NAME);

        // Check that sdk sandbox for TEST_PACKAGE is bound
        assertThat(sProvider.getSdkSandboxServiceForApp(mCallingInfo)).isNotNull();

        sSdkSandboxManagerLocal.notifyInstrumentationStarted(TEST_PACKAGE, mClientAppUid);

        // Verify that sdk sandbox was killed
        Mockito.verify(mAmSpy)
                .killUid(Mockito.eq(Process.toSdkSandboxUid(mClientAppUid)), Mockito.anyString());
        assertThat(sProvider.getSdkSandboxServiceForApp(mCallingInfo)).isNull();
    }

    @Test
    public void testNotifyInstrumentationStarted_doesNotAllowLoadSdk() throws Exception {
        disableKillUid();

        // First load SDK.
        loadSdk(SDK_NAME);

        final CallingInfo callingInfo = new CallingInfo(mClientAppUid, TEST_PACKAGE);

        // Check that sdk sandbox for TEST_PACKAGE is bound
        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isNotNull();

        sSdkSandboxManagerLocal.notifyInstrumentationStarted(TEST_PACKAGE, mClientAppUid);
        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isNull();

        // Try load again, it should throw SecurityException
        FakeLoadSdkCallbackBinder callback2 = new FakeLoadSdkCallbackBinder();

        mService.loadSdk(
                TEST_PACKAGE,
                callback2.asBinder(),
                SDK_NAME,
                mSandboxLatencyInfo,
                new Bundle(),
                callback2);

        LoadSdkException thrown = callback2.getLoadSdkException();
        assertEquals(LOAD_SDK_INTERNAL_ERROR, thrown.getLoadSdkErrorCode());
        assertThat(thrown)
                .hasMessageThat()
                .contains("Currently running instrumentation of this sdk sandbox process");
    }

    @Test
    public void testNotifyInstrumentationFinished_canLoadSdk() throws Exception {
        disableKillUid();
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        sSdkSandboxManagerLocal.notifyInstrumentationStarted(TEST_PACKAGE, mClientAppUid);

        final CallingInfo callingInfo = new CallingInfo(mClientAppUid, TEST_PACKAGE);
        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isNull();

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();

        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                mSandboxLatencyInfo,
                new Bundle(),
                callback);

        LoadSdkException thrown = callback.getLoadSdkException();
        assertEquals(LOAD_SDK_INTERNAL_ERROR, thrown.getLoadSdkErrorCode());
        assertThat(thrown)
                .hasMessageThat()
                .contains("Currently running instrumentation of this sdk sandbox process");

        sSdkSandboxManagerLocal.notifyInstrumentationFinished(TEST_PACKAGE, mClientAppUid);

        FakeLoadSdkCallbackBinder callback2 = new FakeLoadSdkCallbackBinder();
        // Now loading should work
        mService.loadSdk(
                TEST_PACKAGE,
                callback2.asBinder(),
                SDK_NAME,
                mSandboxLatencyInfo,
                new Bundle(),
                callback2);
        mSdkSandboxService.sendLoadSdkSuccessful();
        callback2.assertLoadSdkIsSuccessful();
        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isNotNull();
    }

    @Test
    public void testGetEffectiveTargetSdkVersion() throws Exception {
        assertThat(
                        sSdkSandboxManagerLocal.getEffectiveTargetSdkVersion(
                                Process.toSdkSandboxUid(mClientAppUid)))
                .isEqualTo(34);
    }

    @Test
    public void testGetSandboxedSdks_afterLoadSdkSuccess() throws Exception {
        loadSdk(SDK_NAME);
        assertThat(mService.getSandboxedSdks(TEST_PACKAGE, mSandboxLatencyInfo)).hasSize(1);
        assertThat(
                        mService.getSandboxedSdks(TEST_PACKAGE, mSandboxLatencyInfo)
                                .get(0)
                                .getSharedLibraryInfo()
                                .getName())
                .isEqualTo(SDK_NAME);
    }

    @Test
    public void testGetSandboxedSdks_errorLoadingSdk() throws Exception {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();

        mService.loadSdk(TEST_PACKAGE, null, SDK_NAME, mSandboxLatencyInfo, new Bundle(), callback);
        mSdkSandboxService.sendLoadSdkError();

        // Verify sdkInfo is missing when loading failed
        callback.assertLoadSdkIsUnsuccessful();
        assertThat(callback.getLoadSdkErrorCode()).isEqualTo(LOAD_SDK_INTERNAL_ERROR);
        assertThat(mService.getSandboxedSdks(TEST_PACKAGE, mSandboxLatencyInfo)).isEmpty();
    }

    @Test
    public void testAdServicesPackageIsResolved() throws Exception {
        assertThat(mInjector.getAdServicesPackageName()).contains("adservices");
    }

    @Test
    public void testUnloadSdkThatIsNotLoaded() throws Exception {
        // Load SDK to bring up a sandbox
        loadSdk(SDK_NAME);
        // Trying to unload an SDK that is not loaded should do nothing - it's a no-op.
        mService.unloadSdk(TEST_PACKAGE, SDK_PROVIDER_PACKAGE, mSandboxLatencyInfo);
    }

    @Test
    public void testUnloadSdkThatIsLoaded() throws Exception {
        disableKillUid();
        loadSdk(SDK_NAME);

        loadSdk(SDK_PROVIDER_RESOURCES_SDK_NAME);
        mService.unloadSdk(TEST_PACKAGE, SDK_NAME, mSandboxLatencyInfo);

        // One SDK should still be loaded, therefore the sandbox should still be alive.
        assertThat(sProvider.getSdkSandboxServiceForApp(mCallingInfo)).isNotNull();

        mService.unloadSdk(TEST_PACKAGE, SDK_PROVIDER_RESOURCES_SDK_NAME, mSandboxLatencyInfo);

        // No more SDKs should be loaded at this point. Verify that the sandbox has been killed.
        if (!SdkLevel.isAtLeastU()) {
            // For T, killUid() is used to kill the sandbox.
            Mockito.verify(mAmSpy)
                    .killUid(
                            Mockito.eq(Process.toSdkSandboxUid(mClientAppUid)),
                            Mockito.anyString());
        }
        assertThat(sProvider.getSdkSandboxServiceForApp(mCallingInfo)).isNull();
    }

    @Test
    public void testUnloadSdkThatIsBeingLoaded() throws Exception {
        // Ask to load SDK, but don't finish loading it
        disableKillUid();
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                mSandboxLatencyInfo,
                new Bundle(),
                callback);

        // Trying to unload an SDK that is being loaded should fail
        assertThrows(
                IllegalArgumentException.class,
                () -> mService.unloadSdk(TEST_PACKAGE, SDK_NAME, mSandboxLatencyInfo));

        // After loading the SDK, unloading should not fail
        mSdkSandboxService.sendLoadSdkSuccessful();
        callback.assertLoadSdkIsSuccessful();
        mService.unloadSdk(TEST_PACKAGE, SDK_NAME, mSandboxLatencyInfo);
    }

    @Test
    public void testUnloadSdkAfterKillingSandboxDoesNotThrowException() throws Exception {
        loadSdk(SDK_NAME);
        killSandbox();

        // Unloading SDK should be a no-op
        mService.unloadSdk(TEST_PACKAGE, SDK_NAME, mSandboxLatencyInfo);
    }

    @Test
    public void test_syncDataFromClient_verifiesCallingPackageName() {
        FakeSharedPreferencesSyncCallback callback = new FakeSharedPreferencesSyncCallback();
        mService.syncDataFromClient("does.not.exist", mSandboxLatencyInfo, TEST_UPDATE, callback);

        assertEquals(PREFERENCES_SYNC_INTERNAL_ERROR, callback.getErrorCode());
        assertThat(callback.getErrorMsg()).contains("does.not.exist");
    }

    @Test
    public void test_syncDataFromClient_sandboxServiceIsNotBound() {
        // Sync data from client
        final FakeSharedPreferencesSyncCallback callback = new FakeSharedPreferencesSyncCallback();
        mService.syncDataFromClient(TEST_PACKAGE, mSandboxLatencyInfo, TEST_UPDATE, callback);

        // Verify when sandbox is not bound, manager service does not try to sync
        assertThat(mSdkSandboxService.getLastSyncUpdate()).isNull();
        // Verify on error was called
        assertThat(callback.hasError()).isTrue();
        assertThat(callback.getErrorCode())
                .isEqualTo(ISharedPreferencesSyncCallback.SANDBOX_NOT_AVAILABLE);
        assertThat(callback.getErrorMsg()).contains("Sandbox not available");
    }

    @Test
    public void test_syncDataFromClient_sandboxServiceIsNotBound_sandboxStartedLater()
            throws Exception {
        // Sync data from client
        final FakeSharedPreferencesSyncCallback callback = new FakeSharedPreferencesSyncCallback();
        mService.syncDataFromClient(TEST_PACKAGE, mSandboxLatencyInfo, TEST_UPDATE, callback);

        // Verify on error was called
        assertThat(callback.hasError()).isTrue();
        callback.resetLatch();

        // Now loadSdk so that sandbox is created
        loadSdk(SDK_NAME);

        // Verify that onSandboxStart was called
        assertThat(callback.hasSandboxStarted()).isTrue();
    }

    @Test
    public void test_syncDataFromClient_sandboxServiceIsAlreadyBound_forwardsToSandbox()
            throws Exception {
        // Ensure a sandbox service is already bound for the client
        sProvider.bindService(mCallingInfo, Mockito.mock(ServiceConnection.class));

        // Sync data from client
        final Bundle data = new Bundle();
        final FakeSharedPreferencesSyncCallback callback = new FakeSharedPreferencesSyncCallback();
        mService.syncDataFromClient(TEST_PACKAGE, mSandboxLatencyInfo, TEST_UPDATE, callback);

        // Verify that manager service calls sandbox to sync data
        assertThat(mSdkSandboxService.getLastSyncUpdate()).isSameInstanceAs(TEST_UPDATE);
    }

    @Test
    public void testStopSdkSandbox() throws Exception {
        disableKillUid();

        assertThat(mService.isSdkSandboxServiceRunning(TEST_PACKAGE)).isFalse();
        loadSdk(SDK_NAME);
        assertThat(mService.isSdkSandboxServiceRunning(TEST_PACKAGE)).isTrue();

        Mockito.doNothing()
                .when(mSpyContext)
                .enforceCallingPermission(
                        Mockito.eq("com.android.app.sdksandbox.permission.STOP_SDK_SANDBOX"),
                        Mockito.anyString());
        mService.stopSdkSandbox(TEST_PACKAGE);
        int callingUid = Binder.getCallingUid();
        final CallingInfo callingInfo = new CallingInfo(callingUid, TEST_PACKAGE);
        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isEqualTo(null);
        assertThat(mService.isSdkSandboxServiceRunning(TEST_PACKAGE)).isFalse();
    }

    @Test(expected = SecurityException.class)
    public void testStopSdkSandbox_WithoutPermission() {
        mService.stopSdkSandbox(TEST_PACKAGE);
    }

    @Test
    public void testDump_preU_notPublished() throws Exception {
        requiresAtLeastU(false);
        mockGrantedPermission(DUMP);
        mService.registerAdServicesManagerService(mAdServicesManager, /* published= */ false);

        String dump = mockAdServicesDumpAndDump("FakeAdServiceDump");

        assertThat(dump).contains(FAKE_DUMP_OUTPUT);
        assertThat(dump).contains("FakeAdServiceDump");
    }

    @Test
    public void testDump_preU_published() throws Exception {
        requiresAtLeastU(false);
        mockGrantedPermission(DUMP);
        mService.registerAdServicesManagerService(mAdServicesManager, /* published= */ true);

        String dump = dump();

        assertThat(dump).contains(FAKE_DUMP_OUTPUT);
        Mockito.verify(mAdServicesManager, Mockito.never())
                .dump(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    public void testDump_atLeastU_notPublished() throws Exception {
        requiresAtLeastU(true);
        mockGrantedPermission(DUMP);
        mService.registerAdServicesManagerService(mAdServicesManager, /* published= */ false);

        String dump = mockAdServicesDumpAndDump("FakeAdServiceDump");

        assertThat(dump).contains(FAKE_DUMP_OUTPUT);
        assertThat(dump).contains("FakeAdServiceDump");
    }

    @Test
    public void testDump_atLeastU_published() throws Exception {
        requiresAtLeastU(true);
        mockGrantedPermission(DUMP);
        mService.registerAdServicesManagerService(mAdServicesManager, /* published= */ true);

        String dump = dump();

        assertThat(dump).contains(FAKE_DUMP_OUTPUT);
        Mockito.verify(mAdServicesManager, Mockito.never())
                .dump(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    public void testDump_adServices_preU_notPublished() throws Exception {
        requiresAtLeastU(false);
        mockGrantedPermission(DUMP);
        mService.registerAdServicesManagerService(mAdServicesManager, /* published= */ false);

        String dump = mockAdServicesDumpAndDump("FakeAdServiceDump", "--AdServices");

        assertThat(dump).isEqualTo("AdServices:\n\nFakeAdServiceDump\n\n");
    }

    @Test
    public void testDump_adServices_preU_published() throws Exception {
        requiresAtLeastU(false);
        mockGrantedPermission(DUMP);
        mService.registerAdServicesManagerService(mAdServicesManager, /* published= */ true);

        String dump = dump("--AdServices");

        assertThat(dump)
                .isEqualTo(
                        SdkSandboxManagerService
                                        .DUMP_AD_SERVICES_MESSAGE_HANDLED_BY_AD_SERVICES_ITSELF
                                + "\n");
        Mockito.verify(mAdServicesManager, Mockito.never())
                .dump(ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    public void testDump_adServices_atLeastU_notPublished() throws Exception {
        requiresAtLeastU(true);
        mockGrantedPermission(DUMP);
        mService.registerAdServicesManagerService(mAdServicesManager, /* published= */ false);

        String dump = mockAdServicesDumpAndDump("FakeAdServiceDump", "--AdServices");

        assertThat(dump).isEqualTo("AdServices:\n\nFakeAdServiceDump\n\n");
    }

    @Test
    public void testDump_adServices_atLeastU_published() throws Exception {
        requiresAtLeastU(true);
        mockGrantedPermission(DUMP);
        mService.registerAdServicesManagerService(mAdServicesManager, /* published= */ true);

        String dump = mockAdServicesDumpAndDump("FakeAdServiceDump", "--AdServices");

        assertThat(dump)
                .isEqualTo(
                        SdkSandboxManagerService
                                        .DUMP_AD_SERVICES_MESSAGE_HANDLED_BY_AD_SERVICES_ITSELF
                                + "\n");
    }

    @Test(expected = SecurityException.class)
    public void testDump_WithoutPermission() {
        mService.dump(new FileDescriptor(), new PrintWriter(new StringWriter()), new String[0]);
    }

    @Test
    public void testHandleShellCommandExecutesCommand() throws Exception {
        SdkSandboxShellCommand command = Mockito.mock(SdkSandboxShellCommand.class);
        Mockito.when(
                        mInjector.createShellCommand(
                                mService, mSpyContext, /* supportsAdServicesShellCmd= */ true))
                .thenReturn(command);
        final String[] args = new String[] {"start"};
        try (ParcelFileDescriptor pfdIn = ParcelFileDescriptor.dup(FileDescriptor.in);
                ParcelFileDescriptor pfdOut = ParcelFileDescriptor.dup(FileDescriptor.out);
                ParcelFileDescriptor pfdErr = ParcelFileDescriptor.dup(FileDescriptor.err)) {

            mService.handleShellCommand(pfdIn, pfdOut, pfdErr, args);

            Mockito.verify(mInjector)
                    .createShellCommand(
                            mService, mSpyContext, /* supportsAdServicesShellCmd= */ true);
            Mockito.verify(command)
                    .exec(
                            mService,
                            pfdIn.getFileDescriptor(),
                            pfdOut.getFileDescriptor(),
                            pfdErr.getFileDescriptor(),
                            args);
        }
    }

    @Test
    public void testIsDisabled() {
        mService.forceEnableSandbox();
        assertThat(mService.isSdkSandboxDisabled()).isFalse();
    }

    @Test
    public void testSdkSandboxEnabledForEmulator() {
        // SDK sandbox is enabled for an emulator, even if the killswitch is turned on provided
        // AdServices APK is present.
        Mockito.when(mInjector.isEmulator()).thenReturn(true);
        sSdkSandboxSettingsListener.setKillSwitchState(true);
        assertThat(mService.isSdkSandboxDisabled()).isFalse();

        // SDK sandbox is disabled when the killswitch is enabled if the device is not an emulator.
        mService.clearSdkSandboxState();
        Mockito.when(mInjector.isEmulator()).thenReturn(false);
        sSdkSandboxSettingsListener.setKillSwitchState(true);
        assertThat(mService.isSdkSandboxDisabled()).isTrue();
    }

    @Test
    public void testSdkSandboxDisabledForEmulator() {
        // SDK sandbox is disabled for an emulator, if AdServices APK is not present.
        Mockito.doReturn(false).when(mInjector).isAdServiceApkPresent();
        Mockito.when(mInjector.isEmulator()).thenReturn(true);
        sSdkSandboxSettingsListener.setKillSwitchState(true);
        assertThat(mService.isSdkSandboxDisabled()).isTrue();
    }

    @Test
    public void testSdkSandboxDisabledForAdServiceApkMissing() {
        Mockito.doReturn(true).when(mInjector).isAdServiceApkPresent();
        sSdkSandboxSettingsListener.setKillSwitchState(false);
        assertThat(mService.isSdkSandboxDisabled()).isFalse();

        Mockito.doReturn(false).when(mInjector).isAdServiceApkPresent();
        sSdkSandboxSettingsListener.setKillSwitchState(false);
        assertThat(mService.isSdkSandboxDisabled()).isTrue();
    }

    @Test
    public void testKillswitchStopsSandbox() throws Exception {
        disableKillUid();
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_DISABLE_SANDBOX, "false");
        sSdkSandboxSettingsListener.setKillSwitchState(false);
        loadSdk(SDK_NAME);
        mDeviceConfigUtil.setDeviceConfigProperty(PROPERTY_DISABLE_SANDBOX, "true");
        int callingUid = Binder.getCallingUid();
        final CallingInfo callingInfo = new CallingInfo(callingUid, TEST_PACKAGE);
        assertThat(sProvider.getSdkSandboxServiceForApp(callingInfo)).isEqualTo(null);
    }

    @Test
    public void testLoadSdkFailsWhenSandboxDisabled() {
        disableNetworkPermissionChecks();
        disableForegroundCheck();

        sSdkSandboxSettingsListener.setKillSwitchState(true);
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                mSandboxLatencyInfo,
                new Bundle(),
                callback);
        callback.assertLoadSdkIsUnsuccessful();
        assertThat(callback.getLoadSdkErrorCode())
                .isEqualTo(SdkSandboxManager.LOAD_SDK_SDK_SANDBOX_DISABLED);
        assertThat(callback.getLoadSdkErrorMsg()).isEqualTo("SDK sandbox is disabled");
    }

    @Test
    public void testRemoveAppOwnedSdkSandboxInterfacesOnAppDeath() throws Exception {
        IBinder iBinder = Mockito.mock(IBinder.class);
        mService.registerAppOwnedSdkSandboxInterface(
                TEST_PACKAGE,
                new AppOwnedSdkSandboxInterface(
                        APP_OWNED_SDK_SANDBOX_INTERFACE_NAME,
                        /*version=*/ 0,
                        /*interfaceIBinder=*/ iBinder),
                mSandboxLatencyInfo);
        ArgumentCaptor<IBinder.DeathRecipient> deathRecipient =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);

        Mockito.verify(iBinder).linkToDeath(deathRecipient.capture(), ArgumentMatchers.eq(0));

        // App Died
        deathRecipient.getValue().binderDied();

        assertThat(mService.getAppOwnedSdkSandboxInterfaces(TEST_PACKAGE, mSandboxLatencyInfo))
                .hasSize(0);
    }

    @Test
    public void testUnloadSdkNotCalledOnAppDeath() throws Exception {
        disableKillUid();
        disableForegroundCheck();
        disableNetworkPermissionChecks();
        FakeLoadSdkCallbackBinder callback = Mockito.spy(new FakeLoadSdkCallbackBinder());
        Mockito.doReturn(Mockito.mock(Binder.class)).when(callback).asBinder();

        ArgumentCaptor<IBinder.DeathRecipient> deathRecipient =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);

        mService.loadSdk(
                TEST_PACKAGE,
                null,
                SDK_NAME,
                mSandboxLatencyInfo,
                new Bundle(),
                callback);
        mSdkSandboxService.sendLoadSdkSuccessful();
        callback.assertLoadSdkIsSuccessful();

        Mockito.verify(callback.asBinder())
                .linkToDeath(deathRecipient.capture(), ArgumentMatchers.eq(0));

        // App Died
        deathRecipient.getValue().binderDied();

        Mockito.verify(mSdkSandboxService, Mockito.never())
                .unloadSdk(
                        Mockito.anyString(),
                        Mockito.any(IUnloadSdkInSandboxCallback.class),
                        Mockito.any(SandboxLatencyInfo.class));
    }

    @Test
    public void testLoadSdk_computeSdkStorage() throws Exception {
        mSdkSandboxStorageManagerUtility.createSdkStorageForTest(
                UserHandle.getUserId(mClientAppUid),
                TEST_PACKAGE,
                Arrays.asList("sdk1", "sdk2"),
                Arrays.asList(
                        SdkSandboxStorageManager.SubDirectories.SHARED_DIR,
                        SdkSandboxStorageManager.SubDirectories.SANDBOX_DIR));

        loadSdk(SDK_NAME);
        // Assume sdk storage information calculated and sent
        mSdkSandboxService.sendStorageInfoToSystemServer();

        final List<SdkSandboxStorageManager.StorageDirInfo> internalStorageDirInfo =
                mSdkSandboxStorageManager.getInternalStorageDirInfo(mCallingInfo);
        final List<SdkSandboxStorageManager.StorageDirInfo> sdkStorageDirInfo =
                mSdkSandboxStorageManager.getSdkStorageDirInfo(mCallingInfo);

        Mockito.verify(sSdkSandboxPulledAtoms, Mockito.timeout(5000))
                .logStorage(mClientAppUid, /*sharedStorage=*/ 0, /*sdkStorage=*/ 0);

        Mockito.verify(mSdkSandboxService, Mockito.times(1))
                .computeSdkStorage(
                        Mockito.eq(mService.getListOfStoragePaths(internalStorageDirInfo)),
                        Mockito.eq(mService.getListOfStoragePaths(sdkStorageDirInfo)),
                        Mockito.any(IComputeSdkStorageCallback.class));
    }

    @Test
    public void testLoadSdk_CustomizedApplicationInfoIsPopulatedProperly() throws Exception {
        final int userId = UserHandle.getUserId(mClientAppUid);

        // Create fake storage directories
        mSdkSandboxStorageManagerUtility.createSdkStorageForTest(
                userId, TEST_PACKAGE, Arrays.asList(SDK_NAME), Collections.emptyList());
        StorageDirInfo storageInfo =
                mSdkSandboxStorageManagerUtility
                        .getSdkStorageDirInfoForTest(
                                null, userId, TEST_PACKAGE, Arrays.asList(SDK_NAME))
                        .get(0);

        // Load SDK so that information is passed to sandbox service
        loadSdk(SDK_NAME);

        // Verify customized application info is overloaded with per-sdk storage paths
        ApplicationInfo ai = mSdkSandboxService.getCustomizedInfo();
        assertThat(ai.dataDir).isEqualTo(storageInfo.getCeDataDir());
        assertThat(ai.credentialProtectedDataDir).isEqualTo(storageInfo.getCeDataDir());
        assertThat(ai.deviceProtectedDataDir).isEqualTo(storageInfo.getDeDataDir());
    }

    @Test
    public void testRegisterActivityInterceptorCallbackOnServiceStart()
            throws PackageManager.NameNotFoundException {
        assumeTrue(SdkLevel.isAtLeastU());

        // Build ActivityInterceptorInfo
        int callingUid = 1000;
        Intent intent = new Intent();
        intent.setAction(SdkSandboxManager.ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());
        ActivityInfo activityInfo = new ActivityInfo();

        ActivityInterceptorCallback.ActivityInterceptResult result =
                interceptActivityLunch(intent, callingUid, activityInfo);

        assertThat(result.getIntent()).isEqualTo(intent);
        assertThat(result.getActivityOptions()).isNull();
        assertThat(result.isActivityResolved()).isTrue();
        assertThat(activityInfo.processName)
                .isEqualTo(
                        mInjector
                                .getSdkSandboxServiceProvider()
                                .toSandboxProcessName(
                                        new CallingInfo(mClientAppUid, TEST_PACKAGE)));
        assertThat(activityInfo.applicationInfo.uid).isEqualTo(Process.toSdkSandboxUid(callingUid));
    }

    @Test
    public void testRegisterActivityInterceptionWithRightComponentSuccess()
            throws PackageManager.NameNotFoundException {
        assumeTrue(SdkLevel.isAtLeastU());

        // Build ActivityInterceptorInfo
        int callingUid = 1000;
        Intent intent = new Intent();
        intent.setAction(SdkSandboxManager.ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());
        intent.setComponent(new ComponentName(getSandboxPackageName(), ""));
        ActivityInfo activityInfo = new ActivityInfo();

        ActivityInterceptorCallback.ActivityInterceptResult result =
                interceptActivityLunch(intent, callingUid, activityInfo);

        assertThat(result.getIntent()).isEqualTo(intent);
        assertThat(result.getActivityOptions()).isNull();
        assertThat(result.isActivityResolved()).isTrue();
        assertThat(activityInfo.processName)
                .isEqualTo(
                        mInjector
                                .getSdkSandboxServiceProvider()
                                .toSandboxProcessName(mCallingInfo));
        assertThat(activityInfo.applicationInfo.uid).isEqualTo(Process.toSdkSandboxUid(callingUid));
    }

    @Test
    public void testRegisterActivityInterceptionNotProceedForNullIntent() {
        assumeTrue(SdkLevel.isAtLeastU());

        ActivityInterceptorCallback.ActivityInterceptResult result = interceptActivityLunch(null);

        assertThat(result).isNull();
    }

    @Test
    public void testRegisterActivityInterceptionNotProceedForNullPackage() {
        assumeTrue(SdkLevel.isAtLeastU());

        Intent intent = new Intent();

        ActivityInterceptorCallback.ActivityInterceptResult result = interceptActivityLunch(intent);

        assertThat(result).isNull();
    }

    @Test
    public void testRegisterActivityInterceptionNotProceedForWrongPackage() {
        assumeTrue(SdkLevel.isAtLeastU());

        Intent intent = new Intent();
        intent.setPackage("com.random.package");

        ActivityInterceptorCallback.ActivityInterceptResult result = interceptActivityLunch(intent);

        assertThat(result).isNull();
    }

    @Test
    public void testRegisterActivityInterceptionCallbackReturnNullForNullAction() {
        assumeTrue(SdkLevel.isAtLeastU());

        Intent intent = new Intent();
        intent.setPackage("com.random.package");

        ActivityInterceptorCallback.ActivityInterceptResult result = interceptActivityLunch(intent);

        assertThat(result).isNull();
    }

    @Test
    public void testRegisterActivityInterceptionCallbackReturnNullForWrongAction() {
        assumeTrue(SdkLevel.isAtLeastU());

        Intent intent = new Intent();
        intent.setPackage("com.random.package");
        intent.setAction(Intent.ACTION_VIEW);

        ActivityInterceptorCallback.ActivityInterceptResult result = interceptActivityLunch(intent);

        assertThat(result).isNull();
    }

    @Test
    public void testRegisterActivityInterceptionCallbackReturnNullForWrongComponent() {
        assumeTrue(SdkLevel.isAtLeastU());

        Intent intent = new Intent();
        intent.setAction(SdkSandboxManager.ACTION_START_SANDBOXED_ACTIVITY);
        intent.setPackage(getSandboxPackageName());
        intent.setComponent(new ComponentName("random", ""));

        ActivityInterceptorCallback.ActivityInterceptResult result = interceptActivityLunch(intent);

        assertThat(result).isNull();
    }

    @Test
    public void testRegisterActivityInterceptorCallbackForInstrumentationActivities() {
        assumeTrue(SdkLevel.isAtLeastV());
        disableKillUid();
        ExtendedMockito.when(Process.isSdkSandboxUid(Mockito.anyInt())).thenReturn(true);
        sSdkSandboxManagerLocal.notifyInstrumentationStarted(TEST_PACKAGE, mClientAppUid);

        Intent intent = new Intent().setAction(Intent.ACTION_VIEW);
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.processName = TEST_PACKAGE;
        activityInfo.applicationInfo = new ApplicationInfo();
        activityInfo.applicationInfo.packageName = TEST_PACKAGE;
        activityInfo.applicationInfo.uid = mClientAppUid;

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        // Required to intercept activities during CTS-in-sandbox tests.
                        android.Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX,
                        // Required for test tearDown.
                        Manifest.permission.READ_DEVICE_CONFIG,
                        Manifest.permission.WRITE_DEVICE_CONFIG);
        ActivityInterceptorCallback.ActivityInterceptResult result =
                mInterceptorCallbackArgumentCaptor
                        .getValue()
                        .onInterceptActivityLaunch(
                                new ActivityInterceptorCallback.ActivityInterceptorInfo.Builder(
                                                mClientAppUid,
                                                Process.myPid(),
                                                /* realCallingUid= */ 0,
                                                /* realCallingPid= */ 0,
                                                /* userId= */ 0,
                                                intent,
                                                /* rInfo= */ null,
                                                activityInfo)
                                        .setCallingPackage(TEST_PACKAGE)
                                        .build());

        assertThat(result.getIntent()).isEqualTo(intent);
        assertThat(result.getActivityOptions()).isNull();
        assertThat(result.isActivityResolved()).isTrue();
        assertThat(activityInfo.processName)
                .isEqualTo(TEST_PACKAGE + SANDBOX_INSTR_PROCESS_NAME_SUFFIX);
        assertThat(activityInfo.applicationInfo.uid).isEqualTo(mClientAppUid);
    }

    private ActivityInterceptorCallback.ActivityInterceptResult interceptActivityLunch(
            Intent intent) {
        return interceptActivityLunch(intent, 1000, new ActivityInfo());
    }

    private ActivityInterceptorCallback.ActivityInterceptResult interceptActivityLunch(
            Intent intent, int callingUid, ActivityInfo activityInfo) {
        activityInfo.applicationInfo = new ApplicationInfo();
        ActivityInterceptorCallback.ActivityInterceptorInfo info =
                new ActivityInterceptorCallback.ActivityInterceptorInfo.Builder(
                                callingUid, 0, 0, 0, 0, intent, null, activityInfo)
                        .setCallingPackage(TEST_PACKAGE)
                        .build();
        return mInterceptorCallbackArgumentCaptor.getValue().onInterceptActivityLaunch(info);
    }

    private void loadSdk(String sdkName) throws RemoteException {
        disableNetworkPermissionChecks();
        disableForegroundCheck();
        FakeLoadSdkCallbackBinder callback = new FakeLoadSdkCallbackBinder();
        mService.loadSdk(TEST_PACKAGE, null, sdkName, mSandboxLatencyInfo, new Bundle(), callback);
        mSdkSandboxService.sendLoadSdkSuccessful();
        callback.assertLoadSdkIsSuccessful();
    }

    private void killSandbox() throws Exception {
        ArgumentCaptor<IBinder.DeathRecipient> deathRecipientCaptor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        Mockito.verify(mSdkSandboxService.asBinder(), Mockito.atLeastOnce())
                .linkToDeath(deathRecipientCaptor.capture(), ArgumentMatchers.eq(0));
        List<IBinder.DeathRecipient> deathRecipients = deathRecipientCaptor.getAllValues();
        for (IBinder.DeathRecipient deathRecipient : deathRecipients) {
            deathRecipient.binderDied();
        }
    }

    // Restart sandbox which creates a new sandbox service binder.
    private void restartAndSetSandboxService() throws Exception {
        mSdkSandboxService = sProvider.restartSandbox();
    }

    private String getSandboxPackageName() {
        return mSpyContext.getPackageManager().getSdkSandboxPackageName();
    }

    private void mockGrantedPermission(String permission) {
        Log.d(TAG, "mockGrantedPermission(" + permission + ")");
        Mockito.doNothing()
                .when(mSpyContext)
                .enforceCallingPermission(Mockito.eq(permission), Mockito.anyString());
    }

    private void requiresAtLeastU(boolean required) {
        Log.d(
                TAG,
                "requireAtLeastU("
                        + required
                        + "): SdkLevel.isAtLeastU()="
                        + SdkLevel.isAtLeastU());
        // TODO(b/280677793): rather than assuming it's the given version, mock it:
        //     ExtendedMockito.doReturn(required).when(() -> SdkLevel.isAtLeastU());
        if (required) {
            assumeTrue("Device must be at least U", SdkLevel.isAtLeastU());
        } else {
            assumeFalse("Device must be less than U", SdkLevel.isAtLeastU());
        }
    }

    private String dump(String... args) throws Exception {
        Log.d(TAG, "dump(): args=" + Arrays.toString(args));
        return DumpHelper.dump(pw -> mService.dump(new FileDescriptor(), pw, args));
    }

    private String mockAdServicesDumpAndDump(String adServicesDump, String... args)
            throws Exception {
        Log.d(
                TAG,
                "mockAdServicesDumpAndDump(): adServicesDump="
                        + adServicesDump
                        + ", args="
                        + Arrays.toString(args));
        return DumpHelper.dump(
                pw -> {
                    Mockito.doAnswer(
                                    inv -> {
                                        Log.d(TAG, inv.toString());
                                        pw.println(adServicesDump);
                                        return null;
                                    })
                            .when(mAdServicesManager)
                            .dump(any(), eq(args));

                    mService.dump(new FileDescriptor(), pw, args);
                });
    }

    private static Bundle getTestBundle() {
        final Bundle data = new Bundle();
        data.putString(TEST_KEY, TEST_VALUE);
        return data;
    }
}
