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
package com.android.systemui.car.userpicker;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;

import android.app.ActivityManager;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.car.user.UserCreationResult;
import android.car.util.concurrent.AsyncFuture;
import android.content.Intent;
import android.os.UserManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.userpicker.UserEventManager.OnUpdateUsersListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class UserEventManagerTest extends UserPickerTestCase {
    private UserEventManager mUserEventManager;

    @Mock
    private CarServiceMediator mMockCarServiceMediator;
    @Mock
    private UserPickerSharedState mMockUserPickerSharedState;
    @Mock
    private OnUpdateUsersListener mMockOnUpdateUsersListener;
    @Mock
    private UserManager mMockUserManager;
    @Mock
    private ActivityManager mMockActivityManager;
    @Mock
    private CarUserManager mMockCarUserManager;
    @Mock
    private AsyncFuture<UserCreationResult> mCreateResultFuture;
    @Mock
    private UserCreationResult mCreateResult;

    private MockitoSession mMockingSession;

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .spyStatic(ActivityManager.class)
                .strictness(Strictness.WARN)
                .startMocking();

        doReturn(MAIN_DISPLAY_ID).when(mContext).getDisplayId();
        doReturn(mMockCarUserManager).when(mMockCarServiceMediator).getCarUserManager();

        mUserEventManager =
                new UserEventManager(mContext, mMockCarServiceMediator, mMockUserPickerSharedState);
        mUserEventManager.registerOnUpdateUsersListener(mMockOnUpdateUsersListener,
                MAIN_DISPLAY_ID);
        spyOn(mUserEventManager);
    }

    @After
    public void tearDown() {
        mUserEventManager.unregisterOnUpdateUsersListener(MAIN_DISPLAY_ID);
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    @Test
    public void onUserLifecycleEvent_sendEvents_updateUsers() {
        UserLifecycleEvent event = mock(UserLifecycleEvent.class);
        doReturn(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED,
                CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPING,
                CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPED).when(event).getEventType();
        doReturn(USER_ID_DRIVER).when(event).getUserId();

        mUserEventManager.mUserLifecycleListener.onEvent(event);
        mUserEventManager.mUserLifecycleListener.onEvent(event);
        mUserEventManager.mUserLifecycleListener.onEvent(event);

        verify(mMockOnUpdateUsersListener, after(IDLE_TIMEOUT).times(3))
                .onUpdateUsers(anyInt(), anyInt());
    }

    @Test
    public void checkActionUserInfoChanged_sendBroadcast_updateUsers() {
        Intent sendIntent = new Intent(Intent.ACTION_USER_INFO_CHANGED);
        mContext.sendBroadcast(sendIntent);

        verify(mMockOnUpdateUsersListener, after(IDLE_TIMEOUT)).onUpdateUsers(anyInt(), anyInt());
    }

    @Test
    public void checkStartUser_requestStartUser_startUser() {
        mUserEventManager.startUserForDisplay(/* prevCurrentUser= */ -1,
                /* userId= */ USER_ID_FRONT, /* displayId= */ FRONT_PASSENGER_DISPLAY_ID,
                /* isFgUserStart= */ false);
        verify(mMockCarUserManager).startUser(any(), any(), any());
    }

    @Test
    public void checkStopUser_requestStopUser_StopUser() {
        mUserEventManager.stopUserUnchecked(/* userId= */ USER_ID_FRONT,
                /* displayId= */ FRONT_PASSENGER_DISPLAY_ID);

        verify(mMockCarUserManager).stopUser(any(), any(), any());
    }

    @Test
    public void checkCreateUser_requestCreate_CreateUser() throws Exception {
        doReturn(mCreateResultFuture).when(mMockCarUserManager).createUser(anyString(), anyInt());
        doReturn(mCreateResult).when(mCreateResultFuture).get(anyLong(), any());

        UserCreationResult result = mUserEventManager.createNewUser();

        assertThat(result).isEqualTo(mCreateResult);
    }

    @Test
    public void checkCreateGuest_requestCreate_CreateGuest() throws Exception {
        doReturn(mCreateResultFuture).when(mMockCarUserManager).createGuest(anyString());
        doReturn(mCreateResult).when(mCreateResultFuture).get(anyLong(), any());

        UserCreationResult result = mUserEventManager.createGuest();

        assertThat(result).isEqualTo(mCreateResult);
    }
}
