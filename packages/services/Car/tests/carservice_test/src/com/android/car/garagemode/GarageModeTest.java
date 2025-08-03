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

package com.android.car.garagemode;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobScheduler;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
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
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class GarageModeTest {

    private static final int DEFAULT_TIMEOUT_MS = 100;
    private static final String TAG = "GarageModeTest";

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();
    private GarageMode mGarageMode;
    @Mock
    private Context mContext;
    @Mock
    private GarageModeController mController;
    @Mock
    private JobScheduler mJobScheduler;
    @Mock
    private CarUserService mCarUserService;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final HandlerThread mBgHandlerThread = new HandlerThread("GarageModeTest");
    private Handler mBgHandler;

    @Mock
    private SystemInterface mSystemInterface;
    private File mTempTestDir;

    @Before
    public void setUp() throws IOException {
        mBgHandlerThread.start();
        mBgHandler = new Handler(mBgHandlerThread.getLooper());

        when(mController.getHandler()).thenReturn(mHandler);
        when(mContext.getSystemService(JobScheduler.class)).thenReturn(mJobScheduler);

        CarLocalServices.removeServiceForTest(CarUserService.class);
        CarLocalServices.addService(CarUserService.class, mCarUserService);

        CarLocalServices.removeServiceForTest(SystemInterface.class);
        CarLocalServices.addService(SystemInterface.class, mSystemInterface);

        mTempTestDir = Files.createTempDirectory("garagemode_test").toFile();
        when(mSystemInterface.getSystemCarDir()).thenReturn(mTempTestDir);
        Log.v(TAG, "Using temp dir: %s " + mTempTestDir.getAbsolutePath());

        mGarageMode = new GarageMode(mContext, mController);

        mGarageMode.init();
    }

    @After
    public void teardown() throws Exception {
        mBgHandlerThread.quitSafely();
        mBgHandlerThread.join();

        CarLocalServices.removeServiceForTest(CarUserService.class);
    }

    @Test
    public void test_releaseRemoveListener() {
        mGarageMode.release();

        verify(mCarUserService).removeUserLifecycleListener(any());
    }

    @Test
    public void test_backgroundUsersStopedOnGarageModeCancel() throws Exception {
        ArrayList<Integer> userToStartInBackground = new ArrayList<>(Arrays.asList(101, 102, 103));
        when(mCarUserService.startAllBackgroundUsersInGarageMode())
                .thenReturn(userToStartInBackground);
        mockCarUserServiceStopUserCall(getEventListener());

        mHandler.post(() -> {
            mGarageMode.enterGarageMode(/* completor= */ null);
        });

        verify(mCarUserService, timeout(DEFAULT_TIMEOUT_MS)).startAllBackgroundUsersInGarageMode();

        CountDownLatch latch = new CountDownLatch(1);
        mHandler.post(() -> {
            mGarageMode.cancel(() -> latch.countDown());
        });

        waitForHandlerThreadToFinish(latch);
        verify(mCarUserService).startAllBackgroundUsersInGarageMode();
        assertThat(mGarageMode.getStartedBackgroundUsers()).isEmpty();
    }

    @Test
    public void test_backgroundUsersStopedOnGarageModeCancel_beforeStartingBgUsers()
            throws Exception {
        ArrayList<Integer> userToStartInBackground = new ArrayList<>(Arrays.asList(101, 102, 103));
        when(mCarUserService.startAllBackgroundUsersInGarageMode())
                .thenReturn(userToStartInBackground);
        mockCarUserServiceStopUserCall(getEventListener());

        mHandler.post(() -> {
            mGarageMode.enterGarageMode(/* completor= */ null);
        });

        CountDownLatch latch = new CountDownLatch(1);
        // It is possible that cancel is called before the background users are started, in this
        // case the completor must still be called.
        mHandler.post(() -> {
            mGarageMode.cancel(() -> latch.countDown());
        });

        waitForHandlerThreadToFinish(latch);
        assertThat(mGarageMode.getStartedBackgroundUsers()).isEmpty();
    }


    @Test
    public void test_backgroundUsersStoppedOnGarageModeFinish() throws Exception {
        ArrayList<Integer> userToStartInBackground = new ArrayList<>(Arrays.asList(101, 102, 103));
        when(mCarUserService.startAllBackgroundUsersInGarageMode())
                .thenReturn(userToStartInBackground);
        mockCarUserServiceStopUserCall(getEventListener());

        CountDownLatch latch = new CountDownLatch(1);
        mHandler.post(() -> {
            mGarageMode.enterGarageMode(() -> latch.countDown());
        });

        verify(mCarUserService, timeout(DEFAULT_TIMEOUT_MS)).startAllBackgroundUsersInGarageMode();

        mHandler.post(() -> {
            mGarageMode.finish();
        });

        waitForHandlerThreadToFinish(latch);
        assertThat(mGarageMode.getStartedBackgroundUsers()).isEmpty();
    }

    @Test
    public void test_restartingGarageModeStorePreviouslyStartedUsers() throws Exception {
        ArrayList<Integer> userToStartInBackground = new ArrayList<>(Arrays.asList(101, 102, 103));
        CountDownLatch latch = mockCarUserServiceStartUsersCall(userToStartInBackground);
        mGarageMode.enterGarageMode(/* completor= */ null);

        waitForHandlerThreadToFinish(latch);
        assertThat(mGarageMode.getStartedBackgroundUsers()).containsExactly(101, 102, 103);

        userToStartInBackground = new ArrayList<>(Arrays.asList(103, 104, 105));
        latch = mockCarUserServiceStartUsersCall(userToStartInBackground);
        mGarageMode.enterGarageMode(/* completor= */ null);

        waitForHandlerThreadToFinish(latch);
        assertThat(mGarageMode.getStartedBackgroundUsers())
                .containsExactly(101, 102, 103, 104, 105);
    }

    @Test
    public void test_garageModeTestExitImmediately() throws Exception {
        CarPowerManagementService mockCarPowerManagementService =
                mock(CarPowerManagementService.class);

        // Mock CPMS to force Garage Mode early exit
        CarLocalServices.removeServiceForTest(CarPowerManagementService.class);
        CarLocalServices.addService(CarPowerManagementService.class, mockCarPowerManagementService);
        when(mockCarPowerManagementService.garageModeShouldExitImmediately()).thenReturn(true);

        // Check exit immediately without completor
        GarageMode garageMode = new GarageMode(mContext, mController);
        garageMode.init();
        garageMode.enterGarageMode(/* completor= */ null);
        assertThat(garageMode.isGarageModeActive()).isFalse();

        // Create new instance of GarageMode
        garageMode = new GarageMode(mContext, mController);
        garageMode.init();
        // Check exit immediately with completor
        CompletorImpl completor = new CompletorImpl();
        garageMode.enterGarageMode(completor);
        assertThat(garageMode.isGarageModeActive()).isFalse();
        assertThat(completor.isFinished()).isTrue();

        CarLocalServices.removeServiceForTest(CarPowerManagementService.class);
    }

    private void waitForHandlerThreadToFinish(CountDownLatch latch) throws Exception {
        assertWithMessage("Latch has timed out.")
                .that(latch.await(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
        mHandler.runWithScissors(() -> {}, DEFAULT_TIMEOUT_MS);
    }

    private CountDownLatch mockCarUserServiceStartUsersCall(
            ArrayList<Integer> userToStartInBackground) {
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
            latch.countDown();
            return userToStartInBackground;
        }).when(mCarUserService).startAllBackgroundUsersInGarageMode();

        return latch;
    }

    private UserLifecycleListener getEventListener() {
        ArgumentCaptor<UserLifecycleListener> listenerCaptor =
                ArgumentCaptor.forClass(UserLifecycleListener.class);
        verify(mCarUserService).addUserLifecycleListener(any(), listenerCaptor.capture());
        UserLifecycleListener listener = listenerCaptor.getValue();
        return listener;
    }

    private void mockCarUserServiceStopUserCall(UserLifecycleListener listener) {
        doAnswer(inv -> {
            int userId = (int) inv.getArguments()[0];
            mBgHandler.post(() -> listener.onEvent(new UserLifecycleEvent(
                    CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPED, userId)));
            return true;
        }).when(mCarUserService).stopBackgroundUserInGagageMode(anyInt());
    }

    private static final class CompletorImpl implements Runnable {
        private boolean mFinished;

        @Override
        public void run() {
            mFinished = true;
        }

        public boolean isFinished() {
            return mFinished;
        }
    }
}

