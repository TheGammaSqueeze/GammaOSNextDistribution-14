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

package com.android.car.oem;

import static com.android.compatibility.common.util.SystemUtil.eventually;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.CarVersion;
import android.car.builtin.content.pm.PackageManagerHelper;
import android.car.oem.IOemCarAudioDuckingService;
import android.car.oem.IOemCarAudioFocusService;
import android.car.oem.IOemCarAudioVolumeService;
import android.car.oem.IOemCarService;
import android.car.oem.IOemCarServiceCallback;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.test.mocks.JavaMockitoHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;

import com.android.car.R;
import com.android.internal.annotations.GuardedBy;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;

public final class CarOemProxyServiceTest extends AbstractExtendedMockitoTestCase {

    private static final String COMPONENT_NAME = "android.car.test/unittest";

    @Mock
    private Context mContext;
    @Mock
    private Resources mResources;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private CarOemProxyServiceHelper mCarOemProxyServiceHelper;
    @Captor
    ArgumentCaptor<String> mReasonCapture;
    @Captor
    ArgumentCaptor<ServiceConnection> mConnectionCapture;

    private final TestOemCarService mTestOemCarService = new TestOemCarService();

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(PackageManagerHelper.class);
    }

    @Before
    public void setUp() throws Exception {
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        mockCallTimeout(/* timeoutMs= */ 5000);
    }

    @Test
    public void testFeatureDisabled() throws Exception {
        CarOemProxyService carOemProxyService = new CarOemProxyService(mContext);

        assertThat(carOemProxyService.isOemServiceEnabled()).isFalse();
    }

    @Test
    public void testFeatureEnabled() throws Exception {
        mockOemCarServiceComponent();
        CarOemProxyService carOemProxyService = new CarOemProxyService(mContext);

        assertThat(carOemProxyService.isOemServiceEnabled()).isTrue();
        assertThat(carOemProxyService.getOemServiceName()).isEqualTo(COMPONENT_NAME);
    }

    @Test
    public void testOemServiceCalledPriorToInit() throws Exception {
        mockOemCarServiceComponent();
        CarOemProxyService carOemProxyService = new CarOemProxyService(mContext);
        mockServiceConnection();

        assertThrows(IllegalStateException.class,
                () -> carOemProxyService.getCarOemAudioFocusService());
    }

    @Test
    public void getCarOemAudioVolumeService_priorToInit_fails() throws Exception {
        mockOemCarServiceComponent();
        CarOemProxyService carOemProxyService = new CarOemProxyService(mContext);
        mockServiceConnection();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> carOemProxyService.getCarOemAudioVolumeService());

        assertWithMessage("Oem audio volume service exception").that(thrown)
                .hasMessageThat().contains("should not be call before CarService initialization");
    }

    @Test
    public void getCarOemAudioDuckingService_priorToInit_fails() throws Exception {
        mockOemCarServiceComponent();
        CarOemProxyService carOemProxyService = new CarOemProxyService(mContext);
        mockServiceConnection();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> carOemProxyService.getCarOemAudioDuckingService());

        assertWithMessage("Oem audio ducking service exception").that(thrown)
                .hasMessageThat().contains("should not be call before CarService initialization");
    }

    @Test
    public void testCarServiceCrash_oemNotConnected() throws Exception {
        mockCallTimeout(/* timeoutMs= */ 100);
        mockOemCarServiceComponent();
        CarOemProxyService carOemProxyService = new CarOemProxyService(mContext,
                mCarOemProxyServiceHelper, mHandler);

        //call will wait for OEM service connected
        carOemProxyService.onInitComplete();
        waitForHandlerThreadToFinish();

        // First it will call crash with "OEM Service not connected", as it is mocked, it would
        // continue with other calls.
        verify(mCarOemProxyServiceHelper, times(2)).crashCarService(mReasonCapture.capture());
        assertThat(mReasonCapture.getAllValues()).containsExactly("OEM Service not connected",
                "OEM Service not ready");
    }

    @Test
    public void testCarServiceCrash_oemNotReady() throws Exception {
        mockCallTimeout(/* timeoutMs= */ 100);
        mockOemCarServiceComponent();
        CarOemProxyService carOemProxyService = new CarOemProxyService(mContext,
                mCarOemProxyServiceHelper, mHandler);
        mockServiceConnection();

        //call will wait for OEM service ready
        carOemProxyService.onInitComplete();
        waitForHandlerThreadToFinish();

        verify(mCarOemProxyServiceHelper).crashCarService("OEM Service not ready");
    }

    @Test
    public void testOemServiceIsReady() throws Exception {
        mockOemCarServiceComponent();
        CarOemProxyService carOemProxyService = new CarOemProxyService(mContext);
        mockServiceConnection();
        mockServiceReady();
        carOemProxyService.onInitComplete();

        eventually(() -> assertWithMessage("Oem Service not ready.")
                .that(carOemProxyService.isOemServiceReady()).isTrue());

        assertThat(carOemProxyService.getCarOemAudioFocusService()).isNull();
    }

    @Test
    public void getCarOemAudioVolumeService_withServiceReady() throws Exception {
        mockOemCarServiceComponent();
        CarOemProxyService carOemProxyService = new CarOemProxyService(mContext);
        mockServiceConnection();
        mockServiceReady();
        carOemProxyService.onInitComplete();

        eventually(() -> assertWithMessage("Oem Service not ready.")
                .that(carOemProxyService.isOemServiceReady()).isTrue());

        assertWithMessage("Oem audio volume service")
                .that(carOemProxyService.getCarOemAudioVolumeService()).isNull();
    }

    @Test
    public void getCarOemAudioDuckingService_withServiceReady() throws Exception {
        mockOemCarServiceComponent();
        CarOemProxyService carOemProxyService = new CarOemProxyService(mContext);
        mockServiceConnection();
        mockServiceReady();
        carOemProxyService.onInitComplete();

        eventually(() -> assertWithMessage("Oem Service not ready.")
                .that(carOemProxyService.isOemServiceReady()).isTrue());

        assertWithMessage("Oem audio ducking service")
                .that(carOemProxyService.getCarOemAudioDuckingService()).isNull();
    }

    @Test
    public void testCallbackWhenOemServiceIsReady() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        mockOemCarServiceComponent();
        CarOemProxyService carOemProxyService = new CarOemProxyService(mContext);
        mockServiceConnection();
        mockServiceReady();

        carOemProxyService.registerCallback(() -> latch.countDown());
        carOemProxyService.onInitComplete();

        JavaMockitoHelper.await(latch, 5000);
    }

    private void waitForHandlerThreadToFinish() {
        int timeoutMs = 2000;
        assertWithMessage("handler not idle in %sms", timeoutMs)
                .that(mHandler.runWithScissors(() -> {}, timeoutMs)).isTrue();
    }

    private void mockServiceConnection() {
        verify(mContext).bindServiceAsUser(any(), mConnectionCapture.capture(), anyInt(), any());
        ServiceConnection connection = mConnectionCapture.getValue();
        connection.onServiceConnected(ComponentName.unflattenFromString(COMPONENT_NAME),
                mTestOemCarService);
    }

    private void mockOemCarServiceComponent() throws Exception {
        when(mResources.getString(R.string.config_oemCarService)).thenReturn(COMPONENT_NAME);
        // make it a valid component.
        String packageName = ComponentName.unflattenFromString(COMPONENT_NAME).getPackageName();
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.applicationInfo = new ApplicationInfo();
        when(mPackageManager.getPackageInfo(packageName, 0)).thenReturn(packageInfo);
        doReturn(true).when(() -> PackageManagerHelper.isSystemApp(any()));
        when(mContext.bindServiceAsUser(any(), any(), anyInt(), any())).thenReturn(true);
    }

    private void mockCallTimeout(int timeoutMs) {
        when(mResources.getInteger(R.integer.config_oemCarService_connection_timeout_ms))
                .thenReturn(timeoutMs);
        when(mResources.getInteger(R.integer.config_oemCarService_serviceReady_timeout_ms))
                .thenReturn(timeoutMs);
    }

    private void mockServiceReady() throws Exception {
        mTestOemCarService.mockServiceReady();
    }

    private final class TestOemCarService extends IOemCarService.Stub {

        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private boolean mMockedServiceReady;

        public void mockServiceReady() {
            synchronized (mLock) {
                mMockedServiceReady = true;
            }
        }

        @Override
        public IOemCarAudioFocusService getOemAudioFocusService() {
            return null;
        }

        @Override
        public IOemCarAudioVolumeService getOemAudioVolumeService() {
            return null;
        }

        @Override
        public IOemCarAudioDuckingService getOemAudioDuckingService() {
            return null;
        }

        @Override
        public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        }

        @Override
        public CarVersion getSupportedCarVersion() {
            return CarVersion.VERSION_CODES.TIRAMISU_2;
        }

        @Override
        public void onCarServiceReady(IOemCarServiceCallback callback) throws RemoteException {
            boolean mockedServiceReady;
            synchronized (mLock) {
                mockedServiceReady = mMockedServiceReady;
            }
            if (mockedServiceReady) {
                callback.sendOemCarServiceReady();
            }
        }

        @Override
        public String getAllStackTraces() {
            return "";
        }
    }
}
