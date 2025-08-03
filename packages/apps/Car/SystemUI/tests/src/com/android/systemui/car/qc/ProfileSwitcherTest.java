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

package com.android.systemui.car.qc;

import static android.car.test.mocks.AndroidMockitoHelper.mockUmGetVisibleUsers;
import static android.os.UserManager.SWITCHABILITY_STATUS_OK;
import static android.os.UserManager.SWITCHABILITY_STATUS_USER_SWITCH_DISALLOWED;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.car.SyncResultCallback;
import android.car.user.CarUserManager;
import android.car.user.UserCreationResult;
import android.car.user.UserStartRequest;
import android.car.user.UserStopRequest;
import android.car.user.UserStopResponse;
import android.car.user.UserSwitchRequest;
import android.car.user.UserSwitchResult;
import android.car.util.concurrent.AsyncFuture;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.car.userswitcher.UserIconProvider;
import com.android.systemui.settings.UserTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
public class ProfileSwitcherTest extends SysuiTestCase {

    private MockitoSession mSession;
    private ProfileSwitcher mProfileSwitcher;
    private List<UserInfo> mAliveUsers = new ArrayList<>();

    @Mock
    private UserTracker mUserTracker;
    @Mock
    private UserManager mUserManager;
    @Mock
    private DevicePolicyManager mDevicePolicyManager;
    @Mock
    private CarUserManager mCarUserManager;
    @Mock
    private UserIconProvider mUserIconProvider;

    @Before
    public void setUp() throws ExecutionException, InterruptedException, TimeoutException {
        mSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .spyStatic(CarSystemUIUserUtil.class)
                .strictness(Strictness.LENIENT)
                .startMocking();

        when(mUserTracker.getUserId()).thenReturn(1000);
        when(mUserTracker.getUserHandle()).thenReturn(UserHandle.of(1000));
        when(mUserManager.getAliveUsers()).thenReturn(mAliveUsers);
        when(mUserManager.getUserSwitchability(any())).thenReturn(SWITCHABILITY_STATUS_OK);
        when(mUserManager.isVisibleBackgroundUsersSupported()).thenReturn(false);
        mockUmGetVisibleUsers(mUserManager, 1000);
        when(mDevicePolicyManager.isDeviceManaged()).thenReturn(false);
        when(mDevicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile()).thenReturn(false);
        doReturn(false).when(() -> CarSystemUIUserUtil.isSecondaryMUMDSystemUI());
        Drawable testDrawable = mContext.getDrawable(R.drawable.ic_android);
        when(mUserIconProvider.getDrawableWithBadge(any(Context.class), any(UserInfo.class)))
                .thenReturn(testDrawable);
        when(mUserIconProvider.getDrawableWithBadge(any(Context.class), any(Drawable.class)))
                .thenReturn(testDrawable);
        when(mUserIconProvider.getRoundedGuestDefaultIcon(any())).thenReturn(testDrawable);

        AsyncFuture<UserSwitchResult> switchResultFuture = mock(AsyncFuture.class);
        UserSwitchResult switchResult = mock(UserSwitchResult.class);
        when(switchResult.isSuccess()).thenReturn(true);
        when(switchResultFuture.get(anyLong(), any())).thenReturn(switchResult);
        when(mCarUserManager.switchUser(anyInt())).thenReturn(switchResultFuture);

        mProfileSwitcher = new ProfileSwitcher(mContext, mUserTracker, mUserManager,
                mDevicePolicyManager, mCarUserManager, mUserIconProvider, new Handler());
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
            mSession = null;
        }
    }

    private void setUpLogout() {
        UserInfo user1 = generateUser(1000, "User1");
        UserInfo user2 = generateUser(1001, "User2");
        mAliveUsers.add(user1);
        mAliveUsers.add(user2);
        when(mDevicePolicyManager.isDeviceManaged()).thenReturn(true);
        when(mDevicePolicyManager.isLogoutEnabled()).thenReturn(true);
        when(mDevicePolicyManager.getLogoutUser()).thenReturn(mock(UserHandle.class));
    }

    @Test
    public void logoutAllowed_managedDevice_switchAllowed() {
        setUpLogout();
        List<QCRow> rows = getProfileRows();
        assertThat(rows).hasSize(6);
        assertThat(rows.get(0).getSubtitle()).isEqualTo(
                mContext.getString(R.string.do_disclosure_generic));
        assertThat(rows.get(1).getTitle()).isEqualTo("User1");
        assertThat(rows.get(2).getTitle()).isEqualTo("User2");
        assertThat(rows.get(3).getTitle()).isEqualTo(
                mContext.getString(com.android.internal.R.string.guest_name));
        assertThat(rows.get(4).getTitle()).isEqualTo(
                mContext.getString(R.string.car_add_user));
        assertThat(rows.get(5).getTitle()).isEqualTo(mContext.getString(R.string.end_session));
    }

    @Test
    public void logoutAllowed_managedDevice_switchDisallowed() {
        setUpLogout();
        when(mUserManager.getUserSwitchability(any()))
                .thenReturn(SWITCHABILITY_STATUS_USER_SWITCH_DISALLOWED);
        when(mUserManager.getUserInfo(mUserTracker.getUserId()))
                .thenReturn(mAliveUsers.get(0));

        List<QCRow> rows = getProfileRows();
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getSubtitle()).isEqualTo(
                mContext.getString(R.string.do_disclosure_generic));
        assertThat(rows.get(1).getTitle()).isEqualTo(mAliveUsers.get(0).name);
        assertThat(rows.get(2).getTitle()).isEqualTo(mContext.getString(R.string.end_session));
    }

    @Test
    public void switchNotAllowed_returnsOnlyCurrentUser() {
        when(mUserManager.getUserSwitchability(any()))
                .thenReturn(SWITCHABILITY_STATUS_USER_SWITCH_DISALLOWED);
        UserInfo currentUser = generateUser(mUserTracker.getUserId(), "Current User");
        mAliveUsers.add(currentUser);
        when(mUserManager.getUserInfo(mUserTracker.getUserId())).thenReturn(currentUser);
        UserInfo otherUser = generateUser(1001, "Other User");
        mAliveUsers.add(otherUser);
        List<QCRow> rows = getProfileRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTitle()).isEqualTo("Current User");
    }

    @Test
    public void switchAllowed_usersSwitchable_returnsAllRows() {
        UserInfo user1 = generateUser(1000, "User1");
        UserInfo user2 = generateUser(1001, "User2");
        mAliveUsers.add(user1);
        mAliveUsers.add(user2);
        List<QCRow> rows = getProfileRows();
        // Expect four rows - one for each user, one for the guest user, and one for add user
        assertThat(rows).hasSize(4);
        assertThat(rows.get(0).getTitle()).isEqualTo("User1");
        assertThat(rows.get(1).getTitle()).isEqualTo("User2");
        assertThat(rows.get(2).getTitle()).isEqualTo(
                mContext.getString(com.android.internal.R.string.guest_name));
        assertThat(rows.get(3).getTitle()).isEqualTo(
                mContext.getString(R.string.car_add_user));
    }

    @Test
    public void switchAllowed_orderUsersByCreationTime() {
        UserInfo user1 = generateUser(1001, "User2");
        UserInfo user2 = generateUser(1000, "User1");
        mAliveUsers.add(user1);
        mAliveUsers.add(user2);
        List<QCRow> rows = getProfileRows();
        // Expect four rows - one for each user, one for the guest user, and one for add user
        assertThat(rows).hasSize(4);
        assertThat(rows.get(0).getTitle()).isEqualTo("User2");
        assertThat(rows.get(1).getTitle()).isEqualTo("User1");
        assertThat(rows.get(2).getTitle()).isEqualTo(
                mContext.getString(com.android.internal.R.string.guest_name));
        assertThat(rows.get(3).getTitle()).isEqualTo(
                mContext.getString(R.string.car_add_user));
    }

    @Test
    public void switchAllowed_userNotSwitchable_returnsValidRows() {
        UserInfo user1 = generateUser(1000, "User1");
        UserInfo user2 = generateUser(1001, "User2", /* supportsSwitch= */ false,
                /* isFull= */ true, /* isGuest= */ false);
        mAliveUsers.add(user1);
        mAliveUsers.add(user2);
        List<QCRow> rows = getProfileRows();
        // Expect three rows - one for the valid user, one for the guest user, and one for add user
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getTitle()).isEqualTo("User1");
        assertThat(rows.get(1).getTitle()).isEqualTo(
                mContext.getString(com.android.internal.R.string.guest_name));
        assertThat(rows.get(2).getTitle()).isEqualTo(
                mContext.getString(R.string.car_add_user));
    }

    @Test
    public void switchAllowed_userGuest_returnsValidRows() {
        UserInfo user1 = generateUser(1000, "User1");
        UserInfo user2 = generateUser(1001, "User2", /* supportsSwitch= */ true,
                /* isFull= */ true, /* isGuest= */ true);
        mAliveUsers.add(user1);
        mAliveUsers.add(user2);
        List<QCRow> rows = getProfileRows();
        // Expect three rows - one for the valid user, one for the guest user, and one for add user
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getTitle()).isEqualTo("User1");
        assertThat(rows.get(1).getTitle()).isEqualTo(
                mContext.getString(com.android.internal.R.string.guest_name));
        assertThat(rows.get(2).getTitle()).isEqualTo(
                mContext.getString(R.string.car_add_user));
    }

    @Test
    public void switchAllowed_userNotFull_returnsValidRows() {
        UserInfo user1 = generateUser(1000, "User1");
        UserInfo user2 = generateUser(1001, "User2", /* supportsSwitch= */ true,
                /* isFull= */ false, /* isGuest= */ false);
        mAliveUsers.add(user1);
        mAliveUsers.add(user2);
        List<QCRow> rows = getProfileRows();
        // Expect three rows - one for the valid user, one for the guest user, and one for add user
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getTitle()).isEqualTo("User1");
        assertThat(rows.get(1).getTitle()).isEqualTo(
                mContext.getString(com.android.internal.R.string.guest_name));
        assertThat(rows.get(2).getTitle()).isEqualTo(
                mContext.getString(R.string.car_add_user));
    }

    @Test
    public void switchAllowed_addUserDisallowed_returnsValidRows() {
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_ADD_USER),
                any())).thenReturn(true);
        UserInfo user1 = generateUser(1000, "User1");
        UserInfo user2 = generateUser(1001, "User2");
        mAliveUsers.add(user1);
        mAliveUsers.add(user2);
        List<QCRow> rows = getProfileRows();
        // Expect three rows - one for each user and one for the guest user
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getTitle()).isEqualTo("User1");
        assertThat(rows.get(1).getTitle()).isEqualTo("User2");
        assertThat(rows.get(2).getTitle()).isEqualTo(
                mContext.getString(com.android.internal.R.string.guest_name));
    }

    @Test
    public void switchAllowed_deviceManaged_returnsValidRows() {
        when(mDevicePolicyManager.isDeviceManaged()).thenReturn(true);
        UserInfo user1 = generateUser(1000, "User1");
        mAliveUsers.add(user1);
        List<QCRow> rows = getProfileRows();
        // Expect four rows - one for the device owner message, one for the user,
        // one for the guest user, and one for add user
        assertThat(rows).hasSize(4);
        assertThat(rows.get(0).getSubtitle()).isEqualTo(
                mContext.getString(R.string.do_disclosure_generic));
        assertThat(rows.get(1).getTitle()).isEqualTo("User1");
        assertThat(rows.get(2).getTitle()).isEqualTo(
                mContext.getString(com.android.internal.R.string.guest_name));
        assertThat(rows.get(3).getTitle()).isEqualTo(
                mContext.getString(R.string.car_add_user));
    }

    @Test
    public void onUserPressed_triggersSwitch() {
        int currentUserId = 1000;
        int otherUserId = 1001;
        UserInfo user1 = generateUser(currentUserId, "User1");
        UserInfo user2 = generateUser(otherUserId, "User2");
        mAliveUsers.add(user1);
        mAliveUsers.add(user2);
        List<QCRow> rows = getProfileRows();
        // Expect four rows - one for each user, one for the guest user, and one for add user
        assertThat(rows).hasSize(4);
        QCRow otherUserRow = rows.get(1);
        otherUserRow.getActionHandler().onAction(otherUserRow, mContext, new Intent());

        mProfileSwitcher.mHandler.post(() -> {
            ArgumentCaptor<UserSwitchRequest> requestCaptor =
                    ArgumentCaptor.forClass(UserSwitchRequest.class);
            verify(mCarUserManager).switchUser(requestCaptor.capture(), any(), any());
            assertThat(requestCaptor.getValue().getUserHandle().getIdentifier())
                    .isEqualTo(otherUserId);
        });
    }

    @Test
    public void onGuestPressed_createsAndSwitches()
            throws ExecutionException, InterruptedException, TimeoutException {
        int currentUserId = 1000;
        int guestUserId = 1001;
        AsyncFuture<UserCreationResult> createResultFuture = mock(AsyncFuture.class);
        when(createResultFuture.get(anyLong(), any())).thenReturn(null);
        when(mCarUserManager.createGuest(any())).thenReturn(createResultFuture);

        UserInfo guestUserInfo = mock(UserInfo.class);
        guestUserInfo.id = guestUserId;
        when(mUserManager.findCurrentGuestUser()).thenReturn(guestUserInfo);

        UserInfo user1 = generateUser(currentUserId, "User1");
        mAliveUsers.add(user1);
        List<QCRow> rows = getProfileRows();
        // Expect 3 rows - one for the user, one for the guest user, and one for add user
        assertThat(rows).hasSize(3);
        QCRow guestRow = rows.get(1);
        guestRow.getActionHandler().onAction(guestRow, mContext, new Intent());
        verify(mCarUserManager).createGuest(any());

        mProfileSwitcher.mHandler.post(() -> {
            ArgumentCaptor<UserSwitchRequest> requestCaptor =
                    ArgumentCaptor.forClass(UserSwitchRequest.class);
            verify(mCarUserManager).switchUser(requestCaptor.capture(), any(), any());
            assertThat(requestCaptor.getValue().getUserHandle().getIdentifier())
                    .isEqualTo(guestUserId);
        });
    }

    @Test
    public void onUserPressed_alreadyStartedUser_doesNothing() {
        when(mUserManager.isVisibleBackgroundUsersSupported()).thenReturn(true);
        int currentUserId = 1000;
        int secondaryUserId = 1001;
        UserInfo user1 = generateUser(currentUserId, "User1");
        UserInfo user2 = generateUser(secondaryUserId, "User2");
        mAliveUsers.add(user1);
        mAliveUsers.add(user2);
        mockUmGetVisibleUsers(mUserManager, currentUserId, secondaryUserId);
        List<QCRow> rows = getProfileRows();
        // Expect four rows - one for each user, one for the guest user, and one for add user
        assertThat(rows).hasSize(4);
        QCRow otherUserRow = rows.get(1);
        otherUserRow.getActionHandler().onAction(otherUserRow, mContext, new Intent());
        mProfileSwitcher.mHandler.post(() -> {
            // Verify nothing happens
            verify(mCarUserManager, never()).switchUser(secondaryUserId);
            verify(mCarUserManager, never()).stopUser(any(), any(), any());
            verify(mCarUserManager, never()).startUser(any(), any(), any());
        });
    }

    @Test
    public void onUserPressed_secondaryUser_stopsAndStartsNewUser() {
        int currentUserId = 1000;
        int secondaryUserId = 1001;
        int newUserId = 1002;
        doReturn(true).when(() -> CarSystemUIUserUtil.isSecondaryMUMDSystemUI());
        when(mUserManager.isVisibleBackgroundUsersSupported()).thenReturn(true);
        when(mUserTracker.getUserId()).thenReturn(secondaryUserId);
        when(mUserTracker.getUserHandle()).thenReturn(UserHandle.of(secondaryUserId));
        UserInfo user1 = generateUser(currentUserId, "User1");
        UserInfo user2 = generateUser(secondaryUserId, "User2");
        UserInfo user3 = generateUser(newUserId, "User3");
        mAliveUsers.add(user1);
        mAliveUsers.add(user2);
        mAliveUsers.add(user3);
        mockUmGetVisibleUsers(mUserManager, currentUserId, secondaryUserId);
        List<QCRow> rows = getProfileRows();
        // Expect five rows - one for each user, one for the guest user, and one for add user
        assertThat(rows).hasSize(5);
        QCRow newUserRow = rows.get(2);
        // Make the stopUser() call to succeed, so it can proceed to startUser().
        doAnswer(invocation -> {
            SyncResultCallback<UserStopResponse> callback = invocation.getArgument(2);
            callback.onResult(new UserStopResponse(UserStopResponse.STATUS_SUCCESSFUL));
            return null;
        }).when(mCarUserManager).stopUser(any(), any(), any());

        newUserRow.getActionHandler().onAction(newUserRow, mContext, new Intent());

        mProfileSwitcher.mHandler.post(() -> {
            ArgumentCaptor<UserStopRequest> stopRequestCaptor =
                    ArgumentCaptor.forClass(UserStopRequest.class);
            verify(mCarUserManager).stopUser(stopRequestCaptor.capture(), any(), any());
            assertThat(stopRequestCaptor.getValue().getUserHandle().getIdentifier())
                    .isEqualTo(secondaryUserId);
            ArgumentCaptor<UserStartRequest> startRequestCaptor =
                    ArgumentCaptor.forClass(UserStartRequest.class);
            verify(mCarUserManager).startUser(startRequestCaptor.capture(), any(), any());
            assertThat(startRequestCaptor.getValue().getUserHandle().getIdentifier())
                    .isEqualTo(newUserId);
        });
    }

    private List<QCRow> getProfileRows() {
        QCItem item = mProfileSwitcher.getQCItem();
        assertThat(item).isNotNull();
        assertThat(item instanceof QCList).isTrue();
        return ((QCList) item).getRows();
    }

    private UserInfo generateUser(int id, String name) {
        return generateUser(id, name, /* supportsSwitch= */ true, /* isFull= */ true,
                /* isGuest= */ false);
    }

    private UserInfo generateUser(int id, String name, boolean supportsSwitch, boolean isFull,
            boolean isGuest) {
        UserInfo info = mock(UserInfo.class);
        info.id = id;
        info.name = name;
        info.creationTime = System.currentTimeMillis();
        when(info.supportsSwitchTo()).thenReturn(supportsSwitch);
        when(info.isFull()).thenReturn(isFull);
        when(info.isGuest()).thenReturn(isGuest);
        return info;
    }
}
