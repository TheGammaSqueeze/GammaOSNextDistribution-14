/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.garagemode;

import static com.android.car.garagemode.GarageMode.ACTION_GARAGE_MODE_OFF;
import static com.android.car.garagemode.GarageMode.ACTION_GARAGE_MODE_ON;
import static com.android.car.power.CarPowerManagementService.INVALID_TIMEOUT;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.hardware.power.CarPowerManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.UserHandle;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.car.CarLocalServices;
import com.android.car.power.CarPowerManagementService;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.user.CarUserService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class GarageModeControllerTest {

    private static final String TAG = "GarageModeControllerTest";
    private static final int DEFAULT_TIMEOUT_MS = 1000;

    @Rule public final MockitoRule rule = MockitoJUnit.rule();

    @Mock private Context mContextMock;
    @Mock private CarUserService mCarUserServiceMock;
    @Mock private SystemInterface mSystemInterfaceMock;
    @Mock private CarPowerManagementService mCarPowerManagementServiceMock;
    private CarUserService mCarUserServiceOriginal;
    private CarPowerManagementService mCarPowerManagementServiceOriginal;
    @Captor private ArgumentCaptor<Intent> mIntentCaptor;

    private GarageModeController mController;
    private File mTempTestDir;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private Looper mLooper;

    @Before
    public void setUp() throws IOException {
        mHandlerThread = new HandlerThread("ControllerTest");
        mHandlerThread.start();
        mLooper = mHandlerThread.getLooper();
        mHandler = new Handler(mLooper);
        mCarUserServiceOriginal = CarLocalServices.getService(CarUserService.class);
        mCarPowerManagementServiceOriginal = CarLocalServices.getService(
                CarPowerManagementService.class);
        CarLocalServices.removeServiceForTest(CarUserService.class);
        CarLocalServices.addService(CarUserService.class, mCarUserServiceMock);
        CarLocalServices.removeServiceForTest(SystemInterface.class);
        CarLocalServices.addService(SystemInterface.class, mSystemInterfaceMock);
        CarLocalServices.removeServiceForTest(CarPowerManagementService.class);
        CarLocalServices.addService(CarPowerManagementService.class,
                mCarPowerManagementServiceMock);

        mTempTestDir = Files.createTempDirectory("garagemode_test").toFile();
        when(mSystemInterfaceMock.getSystemCarDir()).thenReturn(mTempTestDir);
        Log.v(TAG, "Using temp dir: %s " + mTempTestDir.getAbsolutePath());

        mController = new GarageModeController(mContextMock, mLooper, mHandler,
                /* garageMode= */ null);

        doReturn(new ArrayList<Integer>()).when(mCarUserServiceMock)
                .startAllBackgroundUsersInGarageMode();
        doNothing().when(mSystemInterfaceMock)
                .sendBroadcastAsUser(any(Intent.class), any(UserHandle.class));
        mController.init();
    }

    @After
    public void tearDown() throws Exception {
        mHandlerThread.quitSafely();
        mHandlerThread.join();

        CarLocalServices.removeServiceForTest(CarUserService.class);
        CarLocalServices.addService(CarUserService.class, mCarUserServiceOriginal);
        CarLocalServices.removeServiceForTest(SystemInterface.class);
        CarLocalServices.removeServiceForTest(CarPowerManagementService.class);
        CarLocalServices.addService(CarPowerManagementService.class,
                mCarPowerManagementServiceOriginal);
    }

    @Test
    public void testOnShutdownPrepare_shouldInitiateGarageMode() {
        // Sending notification that state has changed
        mController.onStateChanged(CarPowerManager.STATE_SHUTDOWN_PREPARE, INVALID_TIMEOUT);

        // Assert that GarageMode has been started
        verify(mSystemInterfaceMock, timeout(DEFAULT_TIMEOUT_MS))
                .sendBroadcastAsUser(mIntentCaptor.capture(), eq(UserHandle.ALL));
        assertThat(mController.isGarageModeActive()).isTrue();
        verifyGarageModeBroadcast(mIntentCaptor.getAllValues(), 1, ACTION_GARAGE_MODE_ON);
    }

    @Test
    public void testOnShutdownCancelled_shouldCancelGarageMode() {
        Looper looper = Looper.getMainLooper();
        mController = new GarageModeController(mContextMock, looper);
        mController.init();
        // Sending notification that state has changed
        mController.onStateChanged(CarPowerManager.STATE_SHUTDOWN_PREPARE, INVALID_TIMEOUT);

        // Sending shutdown cancelled signal to controller, GarageMode should wrap up and stop
        mController.onStateChanged(CarPowerManager.STATE_SHUTDOWN_CANCELLED, INVALID_TIMEOUT);

        // Verify that OFF signal broadcasted to JobScheduler
        verify(mSystemInterfaceMock, timeout(DEFAULT_TIMEOUT_MS).times(2))
                .sendBroadcastAsUser(mIntentCaptor.capture(), eq(UserHandle.ALL));
        verifyGarageModeBroadcast(mIntentCaptor.getAllValues(), 1, ACTION_GARAGE_MODE_ON);
        verifyGarageModeBroadcast(mIntentCaptor.getAllValues(), 2, ACTION_GARAGE_MODE_OFF);

        // Verify that listener is completed due to the cancellation.
        verify(mCarPowerManagementServiceMock, timeout(DEFAULT_TIMEOUT_MS))
                .completeHandlingPowerStateChange(
                        eq(CarPowerManager.STATE_SHUTDOWN_PREPARE), eq(mController));
    }

    @Test
    public void testInitAndRelease() {
        Executor mockExecutor = mock(Executor.class);
        when(mContextMock.getMainExecutor()).thenReturn(mockExecutor);
        GarageMode garageMode = mock(GarageMode.class);
        var controller = new GarageModeController(mContextMock, mLooper, mHandler, garageMode);

        controller.init();
        controller.release();

        verify(garageMode).init();
        verify(garageMode).release();
    }

    @Test
    public void testConstructor() {
        Resources resourcesMock = mock(Resources.class);
        when(mContextMock.getResources()).thenReturn(resourcesMock);

        var controller = new GarageModeController(mContextMock, mLooper);

        assertThat(controller).isNotNull();
    }

    @Test
    public void testOnStateChanged() {
        GarageMode garageMode = mock(GarageMode.class);
        var controller = new GarageModeController(mContextMock, mLooper, mHandler, garageMode);
        controller.init();

        controller.onStateChanged(CarPowerManager.STATE_SHUTDOWN_CANCELLED, INVALID_TIMEOUT);
        verify(garageMode, timeout(DEFAULT_TIMEOUT_MS)).cancel(any());

        clearInvocations(garageMode);
        controller.onStateChanged(CarPowerManager.STATE_SHUTDOWN_ENTER, INVALID_TIMEOUT);
        verify(garageMode, timeout(DEFAULT_TIMEOUT_MS)).cancel(any());

        clearInvocations(garageMode);
        controller.onStateChanged(CarPowerManager.STATE_SUSPEND_ENTER, INVALID_TIMEOUT);
        verify(garageMode, timeout(DEFAULT_TIMEOUT_MS)).cancel(any());

        clearInvocations(garageMode);
        controller.onStateChanged(CarPowerManager.STATE_HIBERNATION_ENTER, INVALID_TIMEOUT);
        verify(garageMode, timeout(DEFAULT_TIMEOUT_MS)).cancel(any());

        clearInvocations(garageMode);
        controller.onStateChanged(CarPowerManager.STATE_INVALID , INVALID_TIMEOUT);
        verify(garageMode, never()).cancel(any());
    }

    private void verifyGarageModeBroadcast(List<Intent> intents, int times, String action) {
        // Capture sent intent and verify that it is correct
        assertWithMessage("no of intents").that(intents.size()).isAtLeast(times);
        Intent i = intents.get(times - 1);
        assertWithMessage("intent action on %s", i).that(i.getAction())
                .isEqualTo(action);

        // Verify that additional critical flags are bundled as well
        int flags = Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_NO_ABORT;
        boolean areRequiredFlagsSet = ((flags & i.getFlags()) == flags);
        assertThat(areRequiredFlagsSet).isTrue();
    }
}
