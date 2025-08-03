/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.user;

import static android.car.test.mocks.AndroidMockitoHelper.mockContextGetService;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.car.AbstractExtendedMockitoCarServiceTestCase;
import android.car.builtin.app.KeyguardManagerHelper;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.CarPowerManager.CarPowerStateListener;
import android.car.settings.CarSettings;
import android.car.test.mocks.MockSettings;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.CarLocalServices;
import com.android.car.R;
import com.android.car.power.CarPowerManagementService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

public class CarUserNoticeServiceTest extends AbstractExtendedMockitoCarServiceTestCase {

    private static final String TAG = CarUserNoticeServiceTest.class.getSimpleName();

    private static final long TIMEOUT_MS = 15_000;
    private static final int USER_ID = 101;

    @Mock
    private Context mMockContext;
    @Mock
    private Context mOtherMockContext;
    @Mock
    private Resources mMockedResources;
    @Mock
    private CarPowerManagementService mMockCarPowerManagementService;
    @Mock
    private CarUserService mMockCarUserService;
    @Mock
    private PowerManager mMockPowerManager;
    @Mock
    private AppOpsManager mMockAppOpsManager;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private CarPowerManager mCarPowerManager;

    @Captor
    private ArgumentCaptor<BroadcastReceiver> mDisplayBroadcastReceiver;

    @Captor
    private ArgumentCaptor<UserLifecycleListener> mUserLifecycleListenerArgumentCaptor;

    @Captor
    private ArgumentCaptor<CarPowerStateListener> mPowerStateListener;

    private CarUserNoticeService mCarUserNoticeService;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private boolean mIsKeyguardLocked;

    private MockSettings mMockSettings;

    public CarUserNoticeServiceTest() {
        super(CarUserNoticeService.TAG);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        mMockSettings = new MockSettings(session);
        session
            .spyStatic(CarLocalServices.class)
            .spyStatic(ActivityManager.class)
            .spyStatic(KeyguardManagerHelper.class);
    }

    /**
     * Initialize all of the objects with the @Mock annotation.
     */
    @Before
    public void setUpMocks() throws Exception {
        doReturn(mCarPowerManager).when(() -> CarLocalServices.createCarPowerManager(mMockContext));
        mockGetCurrentUser(USER_ID);
        mockGetCarLocalService(CarPowerManagementService.class, mMockCarPowerManagementService);
        mockGetCarLocalService(CarUserService.class, mMockCarUserService);

        mMockSettings.putInt(CarSettings.Secure.KEY_ENABLE_INITIAL_NOTICE_SCREEN_TO_USER, 1);

        when(mMockContext.createContextAsUser(any(), anyInt())).thenReturn(mMockContext);
        when(mMockContext.getResources()).thenReturn(mMockedResources);
        when(mMockContext.getContentResolver())
                .thenReturn(InstrumentationRegistry.getInstrumentation().getTargetContext()
                        .getContentResolver());
        when(mMockedResources.getString(anyInt())).thenReturn("com.foo/.Blah");

        mockContextGetService(mMockContext, PowerManager.class, mMockPowerManager);
        mockContextGetService(mMockContext, AppOpsManager.class, mMockAppOpsManager);
        mockContextGetService(mMockContext, PackageManager.class, mMockPackageManager);
        when(mMockPackageManager.getPackageUidAsUser(any(), anyInt())).thenReturn(1);
        Log.d(TAG, "setUpMocks(): handler=" + mHandler + ", looper=" + mHandler.getLooper()
                + ", thread=" + mHandler.getLooper().getThread());
        mCarUserNoticeService = new CarUserNoticeService(mMockContext, mHandler);
        mCarUserNoticeService.init();
        verify(mMockCarUserService).addUserLifecycleListener(any(),
                mUserLifecycleListenerArgumentCaptor.capture());
        verify(mMockContext).registerReceiver(mDisplayBroadcastReceiver.capture(),
                any(IntentFilter.class), anyInt());
        verify(mCarPowerManager).setListener(any(), mPowerStateListener.capture());
        when(mMockContext.bindServiceAsUser(any(), any(), anyInt(), any())).thenReturn(true);
        doAnswer(invocation -> mIsKeyguardLocked)
                .when(() -> KeyguardManagerHelper.isKeyguardLocked());
    }

    @After
    public void clearHandler() {
        mCarUserNoticeService.removeCallbacks();
    }

    @Test
    public void featureDisabledTest() {
        // Feature is disabled when the string is empty
        when(mMockedResources.getString(R.string.config_userNoticeUiService)).thenReturn("");
        when(mOtherMockContext.getResources()).thenReturn(mMockedResources);

        CarUserNoticeService otherService = new CarUserNoticeService(mOtherMockContext);
        otherService.init();

        verify(mOtherMockContext, never()).registerReceiver(any(), any(), anyInt());
    }

    @Test
    public void uiHiddenWhenBroadcastOffReceived() throws Exception {
        setUser();
        // UI shown when user switched
        assertUiShownOnce();
        sendBroadcastActionOff();
        // UI hidden when broadcast off
        assertUiHidden();
        // UI shown only once, when user switched
        assertUiShownOnce();
    }

    @Test
    public void uiShownWhenBroadcastOnReceived() throws Exception {
        setUser();
        // UI shown when user switched
        assertUiShownOnce();
        sendBroadcastActionOff();
        // UI hidden when broadcast off
        assertUiHidden();
        sendBroadcastActionOn();
        // UI shown again on broadcast on
        assertUiShownTwice();
    }

    @Test
    public void uiHiddenWhenPowerShutDown() throws Exception {
        setUser();
        // UI shown when user switched
        assertUiShownOnce();
        sendPowerShutDown();
        // UI hidden when power shutdown
        assertUiHidden();
        // UI shown only once, when user switched
        assertUiShownOnce();
    }

    @Test
    public void uiShownWhenPowerOn() throws Exception {
        setUser();
        // UI shown when user switched
        assertUiShownOnce();
        sendPowerShutDown();
        // UI hidden when power shutdown
        assertUiHidden();
        sendPowerOn();
        // UI shown again on power on
        assertUiShownTwice();
    }

    @Test
    public void uiNotShownWhenPowerOnWithKeyguardLocked() throws Exception {
        setUser();
        // UI shown when user switched
        assertUiShownOnce();
        sendPowerShutDown();
        // UI hidden when power shutdown
        assertUiHidden();
        mIsKeyguardLocked = true;
        sendPowerOn();
        // UI shown only once, when user switched
        assertUiShownOnce();
    }

    @Test
    public void uiNotShownIfSettingsDisabled() throws Exception {
        setUser();
        // UI shown when user switched
        assertUiShownOnce();
        sendBroadcastActionOff();
        // UI hidden when broadcast off
        assertUiHidden();
        mMockSettings.putInt(CarSettings.Secure.KEY_ENABLE_INITIAL_NOTICE_SCREEN_TO_USER, 0);
        sendBroadcastActionOn();
        // UI shown only once, when user switched
        assertUiShownOnce();
    }

    private void setDisplayOn() {
        when(mMockPowerManager.isInteractive()).thenReturn(true);
    }

    private void setDisplayOff() {
        when(mMockPowerManager.isInteractive()).thenReturn(false);
    }

    private void sendBroadcast(String action) {
        Intent intent = new Intent();
        intent.setAction(action);
        mDisplayBroadcastReceiver.getValue().onReceive(mMockContext, intent);
    }

    private void sendPowerStateChange(int state) {
        mPowerStateListener.getValue().onStateChanged(state);
    }

    private void sendUserSwitchEvent() throws Exception {
        mUserLifecycleListenerArgumentCaptor.getValue().onEvent(new UserLifecycleEvent(
                CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING, USER_ID));
    }

    private void setUser() throws Exception {
        setDisplayOn();
        sendUserSwitchEvent();
        waitForHandlerThreadToFinish();
    }

    private void sendBroadcastActionOff() {
        setDisplayOff();
        sendBroadcast(Intent.ACTION_SCREEN_OFF);
    }

    private void sendBroadcastActionOn() {
        setDisplayOn();
        sendBroadcast(Intent.ACTION_SCREEN_ON);
    }

    private void sendPowerShutDown() {
        setDisplayOff();
        sendPowerStateChange(CarPowerManager.STATE_SHUTDOWN_PREPARE);
        waitForHandlerThreadToFinish();
    }

    private void sendPowerOn() {
        setDisplayOn();
        sendPowerStateChange(CarPowerManager.STATE_ON);
        waitForHandlerThreadToFinish();
    }

    private void assertUiShownOnce() {
        verify(mMockContext).bindServiceAsUser(any(), any(), anyInt(), any());
    }

    private void assertUiShownTwice() {
        verify(mMockContext, times(2)).bindServiceAsUser(any(), any(), anyInt(), any());
    }

    private void assertUiHidden() {
        verify(mMockContext).unbindService(any());
    }

    private void waitForHandlerThreadToFinish() {
        assertWithMessage("handler not idle in %sms", TIMEOUT_MS)
                .that(mHandler.runWithScissors(() -> {}, TIMEOUT_MS)).isTrue();
    }
}
