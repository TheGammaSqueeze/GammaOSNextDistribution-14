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

package com.android.systemui.car.users;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.car.test.mocks.AndroidMockitoHelper;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.testing.AndroidTestingRunner;

import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.settings.UserTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@SmallTest
public class CarSystemUIUserUtilTest extends SysuiTestCase {

    private final UserHandle mUserHandle = UserHandle.of(1000);
    private final int mActivityManagerTestUser = 1001;
    private final int mContextTestUser = 1002;
    private MockitoSession mSession;

    @Mock
    private UserTracker mUserTracker;

    @Before
    public void setUp() {
        mSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .spyStatic(UserManager.class)
                .spyStatic(ActivityManager.class)
                .spyStatic(Process.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        mContext = spy(mContext);
        when(mUserTracker.getUserHandle()).thenReturn(mUserHandle);
        AndroidMockitoHelper.mockUmIsHeadlessSystemUserMode(true);
        AndroidMockitoHelper.mockAmGetCurrentUser(mActivityManagerTestUser);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
            mSession = null;
        }
    }

    @Test
    public void getCurrentUserHandle_userTrackerNotNull_returnsUserHandle() {
        UserHandle userHandle = CarSystemUIUserUtil.getCurrentUserHandle(mContext, mUserTracker);

        assertThat(userHandle).isEqualTo(mUserHandle);
    }

    @Test
    public void getCurrentUserHandle_userTrackerNull_headlessSystemUser_returnsAmUser() {
        when(mContext.getUserId()).thenReturn(UserHandle.USER_SYSTEM);

        UserHandle userHandle = CarSystemUIUserUtil.getCurrentUserHandle(mContext, null);

        assertThat(userHandle.getIdentifier()).isEqualTo(mActivityManagerTestUser);
    }

    @Test
    public void getCurrentUserHandle_userTrackerNull_nonHeadlessSystemUser_returnsContextUser() {
        when(mContext.getUserId()).thenReturn(UserHandle.USER_SYSTEM);
        AndroidMockitoHelper.mockUmIsHeadlessSystemUserMode(false);

        UserHandle userHandle = CarSystemUIUserUtil.getCurrentUserHandle(mContext, null);

        assertThat(userHandle.getIdentifier()).isEqualTo(UserHandle.USER_SYSTEM);
    }

    @Test
    public void getCurrentUserHandle_userTrackerNull_nonSystemUser_returnsContextUser() {
        when(mContext.getUserId()).thenReturn(mContextTestUser);

        UserHandle userHandle = CarSystemUIUserUtil.getCurrentUserHandle(mContext, null);

        assertThat(userHandle.getIdentifier()).isEqualTo(mContextTestUser);
    }

    @Test
    public void isSecondaryMUMDSystemUI_usersOnSecondaryDisplaysNotSupported_returnsFalse() {
        when(UserManager.isVisibleBackgroundUsersEnabled()).thenReturn(false);

        assertThat(CarSystemUIUserUtil.isSecondaryMUMDSystemUI()).isFalse();
    }

    @Test
    public void isSecondaryMUMDSystemUI_systemUser_returnsFalse() {
        when(UserManager.isVisibleBackgroundUsersEnabled()).thenReturn(true);
        when(Process.myUserHandle()).thenReturn(UserHandle.SYSTEM);

        assertThat(CarSystemUIUserUtil.isSecondaryMUMDSystemUI()).isFalse();
    }

    @Test
    public void isSecondaryMUMDSystemUI_currentUser_returnsFalse() {
        when(UserManager.isVisibleBackgroundUsersEnabled()).thenReturn(true);
        when(Process.myUserHandle()).thenReturn(UserHandle.of(mActivityManagerTestUser));

        assertThat(CarSystemUIUserUtil.isSecondaryMUMDSystemUI()).isFalse();
    }

    @Test
    public void isSecondaryMUMDSystemUI_isSecondaryUser_returnsTrue() {
        when(UserManager.isVisibleBackgroundUsersEnabled()).thenReturn(true);
        when(Process.myUserHandle()).thenReturn(mUserHandle);

        assertThat(CarSystemUIUserUtil.isSecondaryMUMDSystemUI()).isTrue();
    }
}
