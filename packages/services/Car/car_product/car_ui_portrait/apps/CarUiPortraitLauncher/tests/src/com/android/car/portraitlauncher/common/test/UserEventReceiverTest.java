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

package com.android.car.portraitlauncher.common.test;

import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import android.car.Car;
import android.car.user.CarUserManager;
import android.content.Context;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.car.portraitlauncher.common.UserEventReceiver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class UserEventReceiverTest {
    private static final int MY_USER_ID = 1000;
    private static final int OTHER_USER_ID = 1001;
    private MockitoSession mSession;
    private UserEventReceiver mUserEventReceiver;
    @Mock
    private Context mContext;
    @Mock
    private Car mCar;
    @Mock
    private CarUserManager mCarUserManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mSession = mockitoSession().mockStatic(Car.class,
                withSettings().lenient()).startMocking();
        doReturn(MY_USER_ID).when(mContext).getUserId();
        doReturn(mCar).when(() -> Car.createCar(any()));
        doReturn(mCarUserManager).when(mCar).getCarManager(CarUserManager.class);
        mUserEventReceiver = new UserEventReceiver();
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void onRegister_addsUserListener() {
        TestCallback callback = new TestCallback();

        mUserEventReceiver.register(mContext, callback);

        verifyAndGetUserListener();
    }

    @Test
    public void onUnregister_removesListeners() {
        TestCallback callback = new TestCallback();
        mUserEventReceiver.register(mContext, callback);
        CarUserManager.UserLifecycleListener listener = verifyAndGetUserListener();

        mUserEventReceiver.unregister();

        verify(mCarUserManager).removeListener(listener);
        verify(mCar).disconnect();
    }

    @Test
    public void onUserSwitch_notPreviousUser_noCallback() {
        TestCallback callback = new TestCallback();
        mUserEventReceiver.register(mContext, callback);
        CarUserManager.UserLifecycleListener listener = verifyAndGetUserListener();
        CarUserManager.UserLifecycleEvent event = new CarUserManager.UserLifecycleEvent(
                USER_LIFECYCLE_EVENT_TYPE_SWITCHING, OTHER_USER_ID, MY_USER_ID);

        listener.onEvent(event);

        assertThat(callback.mUserSwitchingCalled).isFalse();
    }

    @Test
    public void onUserSwitch_wasPreviousUser_triggersCallback() {
        TestCallback callback = new TestCallback();
        mUserEventReceiver.register(mContext, callback);
        CarUserManager.UserLifecycleListener listener = verifyAndGetUserListener();
        CarUserManager.UserLifecycleEvent event = new CarUserManager.UserLifecycleEvent(
                USER_LIFECYCLE_EVENT_TYPE_SWITCHING, MY_USER_ID, OTHER_USER_ID);

        listener.onEvent(event);

        assertThat(callback.mUserSwitchingCalled).isTrue();
    }

    @Test
    public void onUserUnlock_otherUser_noCallback() {
        TestCallback callback = new TestCallback();
        mUserEventReceiver.register(mContext, callback);
        CarUserManager.UserLifecycleListener listener = verifyAndGetUserListener();
        CarUserManager.UserLifecycleEvent event = new CarUserManager.UserLifecycleEvent(
                USER_LIFECYCLE_EVENT_TYPE_UNLOCKED, OTHER_USER_ID);

        listener.onEvent(event);

        assertThat(callback.mUserUnlockCalled).isFalse();
    }

    @Test
    public void onUserUnlock_currentUser_triggersCallback() {
        TestCallback callback = new TestCallback();
        mUserEventReceiver.register(mContext, callback);
        CarUserManager.UserLifecycleListener listener = verifyAndGetUserListener();
        CarUserManager.UserLifecycleEvent event = new CarUserManager.UserLifecycleEvent(
                USER_LIFECYCLE_EVENT_TYPE_UNLOCKED, MY_USER_ID);

        listener.onEvent(event);

        assertThat(callback.mUserUnlockCalled).isTrue();
    }

    private CarUserManager.UserLifecycleListener verifyAndGetUserListener() {
        ArgumentCaptor<CarUserManager.UserLifecycleListener> captor = ArgumentCaptor.forClass(
                CarUserManager.UserLifecycleListener.class);
        verify(mCarUserManager).addListener(any(), any(), captor.capture());
        assertThat(captor.getValue()).isNotNull();
        return captor.getValue();
    }

    private static class TestCallback implements UserEventReceiver.Callback {
        boolean mUserSwitchingCalled = false;
        boolean mUserUnlockCalled = false;

        @Override
        public void onUserSwitching() {
            mUserSwitchingCalled = true;
        }

        @Override
        public void onUserUnlock() {
            mUserUnlockCalled = true;
        }
    }
}
