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

package android.app.sdksandbox.sdkprovider;

import static android.app.sdksandbox.SdkSandboxManager.LOAD_SDK_NOT_FOUND;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.app.sdksandbox.AppOwnedSdkSandboxInterface;
import android.app.sdksandbox.ILoadSdkCallback;
import android.app.sdksandbox.ISdkToServiceCallback;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxLatencyInfo;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.SdkSandboxLocalSingleton;
import android.app.sdksandbox.StatsdUtil;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.DeviceConfig;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceConfigStateManager;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSession;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class SdkSandboxControllerUnitTest {
    private static final String EXPECTED_MESSAGE =
            "Only available from the context obtained by calling "
                    + "android.app.sdksandbox.SandboxedSdkProvider#getContext()";
    private static final String CLIENT_PACKAGE_NAME = "android.app.sdksandbox.sdkprovider";
    private static final String SDK_NAME = "testSdk";
    private static final String ERROR_MSG = "Error";

    private static boolean sCustomizedSdkContextEnabled;

    private Context mContext;
    private SandboxedSdkContext mSandboxedSdkContext;
    private SdkSandboxLocalSingleton mSdkSandboxLocalSingleton;
    private ISdkToServiceCallback mServiceCallback;
    private StaticMockitoSession mStaticMockSession;

    @BeforeClass
    public static void setUpClass() throws Exception {
        DeviceConfigStateManager stateManager =
                new DeviceConfigStateManager(
                        InstrumentationRegistry.getInstrumentation().getContext(),
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        "sdksandbox_customized_sdk_context_enabled");
        sCustomizedSdkContextEnabled = Boolean.parseBoolean(stateManager.get());
    }

    @Before
    public void setup() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mSandboxedSdkContext =
                new SandboxedSdkContext(
                        mContext,
                        getClass().getClassLoader(),
                        /*clientPackageName=*/ CLIENT_PACKAGE_NAME,
                        new ApplicationInfo(),
                        /*sdkName=*/ "",
                        /*sdkCeDataDir=*/ null,
                        /*sdkDeDataDir=*/ null,
                        sCustomizedSdkContextEnabled);

        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(SdkSandboxLocalSingleton.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        mSdkSandboxLocalSingleton = Mockito.mock(SdkSandboxLocalSingleton.class);
        mServiceCallback = Mockito.mock(ISdkToServiceCallback.class);
        Mockito.when(mSdkSandboxLocalSingleton.getSdkToServiceCallback())
                .thenReturn(mServiceCallback);
        // Populate mSdkSandboxLocalSingleton
        ExtendedMockito.doReturn(mSdkSandboxLocalSingleton)
                .when(() -> SdkSandboxLocalSingleton.getExistingInstance());
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testCreateInstance() throws Exception {
        final SdkSandboxController controller = new SdkSandboxController(mContext);
        assertThat(controller).isNotNull();
    }

    @Test
    public void testInitWithAnyContext() throws Exception {
        SdkSandboxController controller = mContext.getSystemService(SdkSandboxController.class);
        assertThat(controller).isNotNull();
        // Does not fail on initialising with same context
        controller.initialize(mContext);
        assertThat(controller).isNotNull();
    }

    @Test
    public void testGetAppOwnedSdkSandboxInterfaces() throws RemoteException {
        final SdkSandboxController controller = new SdkSandboxController(mSandboxedSdkContext);

        // Mock singleton methods
        final ISdkToServiceCallback serviceCallback = Mockito.mock(ISdkToServiceCallback.class);
        ArrayList<AppOwnedSdkSandboxInterface> appOwnedInterfacesMock = new ArrayList<>();
        appOwnedInterfacesMock.add(
                new AppOwnedSdkSandboxInterface(
                        "mockPackage", /*version=*/ 0, /*interfaceIBinder=*/ new Binder()));

        Mockito.when(serviceCallback.getAppOwnedSdkSandboxInterfaces(Mockito.anyString()))
                .thenReturn(appOwnedInterfacesMock);
        Mockito.when(mSdkSandboxLocalSingleton.getSdkToServiceCallback())
                .thenReturn(serviceCallback);
        final List<AppOwnedSdkSandboxInterface> appOwnedSdkSandboxInterfaces =
                controller.getAppOwnedSdkSandboxInterfaces();

        assertThat(appOwnedSdkSandboxInterfaces).isEqualTo(appOwnedInterfacesMock);
    }

    @Test
    public void testGetSandboxedSdks() throws RemoteException {
        final SdkSandboxController controller = new SdkSandboxController(mSandboxedSdkContext);

        // Mock singleton methods
        ISdkToServiceCallback serviceCallback = Mockito.mock(ISdkToServiceCallback.class);
        ArrayList<SandboxedSdk> sandboxedSdksMock = new ArrayList<>();
        sandboxedSdksMock.add(new SandboxedSdk(new Binder()));
        Mockito.when(serviceCallback.getSandboxedSdks(Mockito.anyString()))
                .thenReturn(sandboxedSdksMock);
        Mockito.when(mSdkSandboxLocalSingleton.getSdkToServiceCallback())
                .thenReturn(serviceCallback);

        List<SandboxedSdk> sandboxedSdks = controller.getSandboxedSdks();
        assertThat(sandboxedSdks).isEqualTo(sandboxedSdksMock);
    }

    @Test
    public void testLoadSdk() throws RemoteException {
        final SdkSandboxController controller = new SdkSandboxController(mSandboxedSdkContext);

        // Mock singleton methods
        ISdkToServiceCallback serviceCallback = Mockito.mock(ISdkToServiceCallback.class);
        Mockito.when(mSdkSandboxLocalSingleton.getSdkToServiceCallback())
                .thenReturn(serviceCallback);

        controller.loadSdk("testSdk", new Bundle(), Runnable::run, new FakeLoadSdkCallback());
        Mockito.verify(serviceCallback, Mockito.times(1))
                .loadSdk(
                        Mockito.anyString(),
                        Mockito.anyString(),
                        Mockito.any(SandboxLatencyInfo.class),
                        Mockito.any(Bundle.class),
                        Mockito.any(ILoadSdkCallback.class));
    }

    @Test
    public void testLoadSdk_callSuccessful_logLatenciesCalled() throws Exception {
        Bundle params = new Bundle();
        SdkSandboxController sdkSandboxController = new SdkSandboxController(mSandboxedSdkContext);

        sdkSandboxController.loadSdk(SDK_NAME, params, Runnable::run, new FakeLoadSdkCallback());
        ArgumentCaptor<SandboxLatencyInfo> sandboxLatencyInfoCaptor =
                ArgumentCaptor.forClass(SandboxLatencyInfo.class);
        ArgumentCaptor<ILoadSdkCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(ILoadSdkCallback.class);
        Mockito.verify(mServiceCallback)
                .loadSdk(
                        Mockito.eq(CLIENT_PACKAGE_NAME),
                        Mockito.eq(SDK_NAME),
                        sandboxLatencyInfoCaptor.capture(),
                        Mockito.eq(params),
                        callbackArgumentCaptor.capture());
        // Simulate the success callback
        callbackArgumentCaptor
                .getValue()
                .onLoadSdkSuccess(
                        new SandboxedSdk(new Binder()), sandboxLatencyInfoCaptor.getValue());

        Mockito.verify(mServiceCallback, Mockito.times(1))
                .logLatenciesFromSandbox(Mockito.eq(sandboxLatencyInfoCaptor.getValue()));
        assertThat(sandboxLatencyInfoCaptor.getValue().getMethod())
                .isEqualTo(SandboxLatencyInfo.METHOD_LOAD_SDK_VIA_CONTROLLER);
    }

    @Test
    public void testLoadSdk_callFails_logLatenciesCalled() throws Exception {
        Bundle params = new Bundle();
        SdkSandboxController sdkSandboxController = new SdkSandboxController(mSandboxedSdkContext);

        sdkSandboxController.loadSdk(SDK_NAME, params, Runnable::run, new FakeLoadSdkCallback());
        ArgumentCaptor<SandboxLatencyInfo> sandboxLatencyInfoCaptor =
                ArgumentCaptor.forClass(SandboxLatencyInfo.class);
        ArgumentCaptor<ILoadSdkCallback> callbackArgumentCaptor =
                ArgumentCaptor.forClass(ILoadSdkCallback.class);
        Mockito.verify(mServiceCallback)
                .loadSdk(
                        Mockito.eq(CLIENT_PACKAGE_NAME),
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

        Mockito.verify(mServiceCallback, Mockito.times(1))
                .logLatenciesFromSandbox(Mockito.eq(sandboxLatencyInfoCaptor.getValue()));
        assertThat(sandboxLatencyInfoCaptor.getValue().getMethod())
                .isEqualTo(SandboxLatencyInfo.METHOD_LOAD_SDK_VIA_CONTROLLER);
    }

    @Test
    public void testGetSandboxedSdksFailsWithIncorrectContext() {
        SdkSandboxController controller = mContext.getSystemService(SdkSandboxController.class);

        Exception e =
                assertThrows(UnsupportedOperationException.class, controller::getSandboxedSdks);
        assertThat(e.getMessage()).isEqualTo(EXPECTED_MESSAGE);
    }

    @Test
    public void testGetClientSharedPreferences_onlyFromSandboxedContext() {
        final SdkSandboxController controller = new SdkSandboxController(mContext);
        Exception e =
                assertThrows(
                        UnsupportedOperationException.class,
                        controller::getClientSharedPreferences);
        assertThat(e.getMessage()).isEqualTo(EXPECTED_MESSAGE);
    }

    @Test
    public void testGetClientSharedPreferences() {
        final SdkSandboxController controller = new SdkSandboxController(mSandboxedSdkContext);

        final SharedPreferences sp = controller.getClientSharedPreferences();
        // Assert same instance as a name SharedPreference on sandboxed context
        final SharedPreferences spFromSandboxedContext =
                mSandboxedSdkContext.getSharedPreferences(
                        SdkSandboxController.CLIENT_SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        assertThat(sp).isSameInstanceAs(spFromSandboxedContext);
        // Assert same instance as a name SharedPreference on original context
        final SharedPreferences spFromOriginalContext =
                mContext.getSharedPreferences(
                        SdkSandboxController.CLIENT_SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        assertThat(sp).isSameInstanceAs(spFromOriginalContext);
    }

    @Test
    public void testRegisterSdkSandboxActivityHandler() {
        final SdkSandboxController controller = new SdkSandboxController(mSandboxedSdkContext);

        assumeTrue(SdkLevel.isAtLeastU());

        SdkSandboxActivityHandler handler = activity -> {};

        IBinder token1 = controller.registerSdkSandboxActivityHandler(handler);
        IBinder token2 = controller.registerSdkSandboxActivityHandler(handler);
        assertThat(token2).isEqualTo(token1);

        // cleaning
        controller.unregisterSdkSandboxActivityHandler(handler);
    }

    @Test
    public void testRegisterSdkSandboxActivityHandler_CallsStatsd() throws RemoteException {
        final SdkSandboxController controller = new SdkSandboxController(mSandboxedSdkContext);

        assumeTrue(SdkLevel.isAtLeastU());

        SdkSandboxActivityHandler handler = activity -> {};

        controller.registerSdkSandboxActivityHandler(handler);

        Mockito.verify(mServiceCallback)
                .logSandboxActivityApiLatencyFromSandbox(
                        Mockito.eq(
                                StatsdUtil
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__REGISTER_SDK_SANDBOX_ACTIVITY_HANDLER),
                        Mockito.eq(
                                StatsdUtil.SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__SUCCESS),
                        Mockito.anyInt());

        // cleaning
        controller.unregisterSdkSandboxActivityHandler(handler);
    }

    @Test
    public void testUnregisterSdkSandboxActivityHandler() {
        SdkSandboxController controller = new SdkSandboxController(mSandboxedSdkContext);

        assumeTrue(SdkLevel.isAtLeastU());

        SdkSandboxActivityHandler handler = activity -> {};

        IBinder token1 = controller.registerSdkSandboxActivityHandler(handler);
        assertThat(token1).isNotNull();

        controller.unregisterSdkSandboxActivityHandler(handler);

        IBinder token2 = controller.registerSdkSandboxActivityHandler(handler);
        assertThat(token2).isNotEqualTo(token1);
    }

    @Test
    public void testUnregisterSdkSandboxActivityHandler_CallsStatsd() throws RemoteException {
        SdkSandboxController controller = new SdkSandboxController(mSandboxedSdkContext);

        assumeTrue(SdkLevel.isAtLeastU());

        SdkSandboxActivityHandler handler = activity -> {};

        IBinder token1 = controller.registerSdkSandboxActivityHandler(handler);
        assertThat(token1).isNotNull();

        controller.unregisterSdkSandboxActivityHandler(handler);

        Mockito.verify(mServiceCallback)
                .logSandboxActivityApiLatencyFromSandbox(
                        Mockito.eq(
                                StatsdUtil
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__UNREGISTER_SDK_SANDBOX_ACTIVITY_HANDLER),
                        Mockito.eq(
                                StatsdUtil.SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__SUCCESS),
                        Mockito.anyInt());
    }

    @Test
    public void testGetClientPackageName() {
        SdkSandboxController controller = new SdkSandboxController(mSandboxedSdkContext);

        assertThat(controller.getClientPackageName())
                .isEqualTo(mSandboxedSdkContext.getClientPackageName());
        assertThat(controller.getClientPackageName()).isEqualTo(CLIENT_PACKAGE_NAME);
    }

    @Test
    public void testGetClientPackageNameFailsWithIncorrectContext() {
        SdkSandboxController controller = mContext.getSystemService(SdkSandboxController.class);
        Exception e =
                assertThrows(UnsupportedOperationException.class, controller::getClientPackageName);
        assertThat(e.getMessage()).isEqualTo(EXPECTED_MESSAGE);
    }
}
