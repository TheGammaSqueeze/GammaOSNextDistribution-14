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

import static android.car.CarOccupantZoneManager.INVALID_USER_ID;
import static android.view.Display.INVALID_DISPLAY;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;

import android.app.ActivityManager;
import android.car.test.mocks.AndroidMockitoHelper;
import android.car.user.UserCreationResult;
import android.content.pm.UserInfo;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.userpicker.UserPickerController.Callbacks;
import com.android.systemui.settings.DisplayTracker;

import com.google.android.material.snackbar.Snackbar;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class UserPickerControllerTest extends UserPickerTestCase {
    private UserPickerController mUserPickerController;
    private SnackbarManager mSnackbarManager;
    private UserPickerSharedState mUserPickerSharedState;

    @Mock
    private DialogManager mMockDialogManager;
    @Mock
    private CarServiceMediator mMockCarServiceMediator;
    @Mock
    private UserEventManager mMockUserEventManager;
    @Mock
    private DisplayTracker mMockDisplayTracker;
    @Mock
    private Callbacks mMockCallbacks;
    @Mock
    private UserCreationResult mCreateResult;

    private List<UserInfo> mAliveUsers = new ArrayList<UserInfo>();
    private List<UserRecord> mUserRecords = null;
    private MockitoSession mMockingSession;

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .spyStatic(ActivityManager.class)
                .spyStatic(Snackbar.class)
                .strictness(Strictness.WARN)
                .startMocking();

        mAliveUsers.clear();
        doReturn(mAliveUsers).when(mMockUserEventManager).getAliveUsers();
        doReturn(mDriverUserInfo).when(mMockUserEventManager).getCurrentForegroundUserInfo();
    }

    @After
    public void tearDown() {
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    void displaySetup(boolean isLogoutState, int displayId) {
        mAliveUsers.add(mDriverUserInfo);
        doReturn(true).when(mMockUserEventManager).isUserRunning(USER_ID_DRIVER);
        doReturn(MAIN_DISPLAY_ID).when(mMockCarServiceMediator).getDisplayIdForUser(USER_ID_DRIVER);
        doReturn(USER_ID_DRIVER).when(mMockCarServiceMediator).getUserForDisplay(MAIN_DISPLAY_ID);

        mAliveUsers.add(mFrontUserInfo);
        if (isLogoutState) {
            doReturn(INVALID_DISPLAY).when(mMockCarServiceMediator)
                    .getDisplayIdForUser(USER_ID_FRONT);
            doReturn(INVALID_USER_ID).when(mMockCarServiceMediator)
                    .getUserForDisplay(FRONT_PASSENGER_DISPLAY_ID);
        } else {
            doReturn(true).when(mMockUserEventManager).isUserRunning(USER_ID_FRONT);
            doReturn(FRONT_PASSENGER_DISPLAY_ID).when(mMockCarServiceMediator)
                    .getDisplayIdForUser(USER_ID_FRONT);
            doReturn(USER_ID_FRONT).when(mMockCarServiceMediator)
                    .getUserForDisplay(FRONT_PASSENGER_DISPLAY_ID);
        }

        mAliveUsers.add(mRearUserInfo);
        doReturn(INVALID_DISPLAY).when(mMockCarServiceMediator).getDisplayIdForUser(USER_ID_REAR);
        doReturn(INVALID_USER_ID).when(mMockCarServiceMediator)
                .getUserForDisplay(REAR_PASSENGER_DISPLAY_ID);

        doReturn(displayId).when(mContext).getDisplayId();
        doReturn(MAIN_DISPLAY_ID).when(mMockDisplayTracker).getDefaultDisplayId();

        mSnackbarManager = new SnackbarManager();
        mUserPickerSharedState = new UserPickerSharedState();
        View rootView = mInflater.inflate(R.layout.test_empty_layout, null);
        mSnackbarManager.setRootView(rootView, R.id.test_empty_layout_frame);

        mUserPickerController = new UserPickerController(mContext, mMockUserEventManager,
                mMockCarServiceMediator, mMockDialogManager, mSnackbarManager,
                mMockDisplayTracker, mUserPickerSharedState);
        mUserPickerController.init(mMockCallbacks, displayId);
        mUserPickerController.onConfigurationChanged();
        AndroidMockitoHelper.mockAmGetCurrentUser(USER_ID_DRIVER);
        mUserPickerController.updateUsers();
        mUserRecords = mUserPickerController.createUserRecords();
    }

    UserRecord getUserRecord(String name) {
        if (name != null) {
            for (int i = 0; i < mUserRecords.size(); i++) {
                if (name.equals(mUserRecords.get(i).mName)) {
                    return mUserRecords.get(i);
                }
            }
        }
        return null;
    }

    @Test
    public void loginItself_changeUserState_finishUserPicker() {
        displaySetup(/* isLogoutState= */ false, /* displayId= */ FRONT_PASSENGER_DISPLAY_ID);

        mUserPickerController.handleUserSelected(getUserRecord(USER_NAME_FRONT));

        verify(mMockUserEventManager, after(IDLE_TIMEOUT).never())
                .startUserForDisplay(anyInt(), anyInt(), anyInt(), anyBoolean());
        verify(mMockCallbacks, after(IDLE_TIMEOUT)).onFinishRequested();
    }

    @Test
    public void checkSnackbar_loginDriverUser_showSnackbar() {
        Snackbar snackbar = mock(Snackbar.class);
        doReturn(snackbar).when(() -> Snackbar.make(any(View.class), any(String.class), anyInt()));
        displaySetup(/* isLogoutState= */ false, /* displayId= */ FRONT_PASSENGER_DISPLAY_ID);
        spyOn(mSnackbarManager);

        mUserPickerController.handleUserSelected(getUserRecord(USER_NAME_DRIVER));

        verify(mMockUserEventManager, after(IDLE_TIMEOUT).never())
                .startUserForDisplay(anyInt(), anyInt(), anyInt(), anyBoolean());
        verify(mSnackbarManager, after(IDLE_TIMEOUT)).showSnackbar(anyString());
    }

    @Test
    public void checkSwitchUser_changeUserState_switchUser() {
        displaySetup(/* isLogoutState= */ false, /* displayId= */ MAIN_DISPLAY_ID);

        mUserPickerController.handleUserSelected((getUserRecord(USER_NAME_REAR)));

        verify(mMockUserEventManager, after(IDLE_TIMEOUT).never()).createNewUser();
        verify(mMockUserEventManager, after(IDLE_TIMEOUT))
                .startUserForDisplay(anyInt(), eq(USER_ID_REAR), anyInt(), anyBoolean());
    }

    @Test
    public void checkChangeUser_changeUserState_changeUser() {
        doReturn(true).when(mMockUserEventManager).stopUserUnchecked(anyInt(), anyInt());
        displaySetup(/* isLogoutState= */ false, /* displayId= */ FRONT_PASSENGER_DISPLAY_ID);

        mUserPickerController.handleUserSelected(getUserRecord(USER_NAME_REAR));

        verify(mMockUserEventManager, after(IDLE_TIMEOUT).atLeastOnce())
                .stopUserUnchecked(USER_ID_FRONT, FRONT_PASSENGER_DISPLAY_ID);
        verify(mMockUserEventManager, after(IDLE_TIMEOUT))
                .startUserForDisplay(anyInt(), eq(USER_ID_REAR), anyInt(), anyBoolean());
    }

    @Test
    public void checkLoginUser_logoutState_startUser() {
        doReturn(true).when(mMockUserEventManager).stopUserUnchecked(anyInt(), anyInt());
        displaySetup(/* isLogoutState= */ true, /* displayId= */ FRONT_PASSENGER_DISPLAY_ID);

        mUserPickerController.handleUserSelected(getUserRecord(USER_NAME_REAR));

        verify(mMockUserEventManager, after(IDLE_TIMEOUT).never())
                .stopUserUnchecked(anyInt(), anyInt());
        verify(mMockUserEventManager, after(IDLE_TIMEOUT))
                .startUserForDisplay(anyInt(), eq(USER_ID_REAR), anyInt(), anyBoolean());
    }

    @Test
    public void checkCreateGuest_pressGuestIcon_createdGuestUser() {
        UserInfo guestInfo = new UserInfo(USER_ID_GUEST, "G", UserInfo.FLAG_GUEST);
        doReturn(true).when(mCreateResult).isSuccess();
        doReturn(mCreateResult).when(mMockUserEventManager).createGuest();
        doReturn(guestInfo.getUserHandle()).when(mCreateResult).getUser();
        doReturn(guestInfo).when(mMockUserEventManager).getUserInfo(USER_ID_GUEST);
        doReturn(true).when(mMockUserEventManager).stopUserUnchecked(anyInt(), anyInt());
        displaySetup(/* isLogoutState= */ false, /* displayId= */ FRONT_PASSENGER_DISPLAY_ID);

        mUserPickerController.handleUserSelected(getUserRecord(mGuestLabel));

        verify(mMockUserEventManager, after(IDLE_TIMEOUT)).createGuest();
        verify(mMockUserEventManager, after(IDLE_TIMEOUT).atLeastOnce())
                .stopUserUnchecked(USER_ID_FRONT, FRONT_PASSENGER_DISPLAY_ID);
        verify(mMockUserEventManager, after(IDLE_TIMEOUT))
                .startUserForDisplay(anyInt(), eq(USER_ID_GUEST), anyInt(), anyBoolean());
    }

    @Test
    public void checkAddNewUser_pressAddUserIcon_createdUser() {
        UserInfo newUserInfo = new UserInfo(USER_ID_REAR + 1, "New", UserInfo.FLAG_FULL);
        doReturn(true).when(mCreateResult).isSuccess();
        doReturn(mCreateResult).when(mMockUserEventManager).createNewUser();
        doReturn(true).when(mMockUserEventManager).stopUserUnchecked(anyInt(), anyInt());
        doReturn(newUserInfo.getUserHandle()).when(mCreateResult).getUser();
        doReturn(newUserInfo).when(mMockUserEventManager)
                .getUserInfo(newUserInfo.getUserHandle().getIdentifier());
        displaySetup(/* isLogoutState= */ false, /* displayId= */ FRONT_PASSENGER_DISPLAY_ID);

        mUserPickerController.startAddNewUser();

        verify(mMockUserEventManager, after(IDLE_TIMEOUT)).createNewUser();
        verify(mMockUserEventManager, after(IDLE_TIMEOUT).atLeastOnce())
                .stopUserUnchecked(USER_ID_FRONT, FRONT_PASSENGER_DISPLAY_ID);
        verify(mMockUserEventManager, after(IDLE_TIMEOUT))
                .startUserForDisplay(anyInt(), eq(USER_ID_REAR + 1), anyInt(), anyBoolean());
    }

    @Test
    public void checkDestroyController_callOnDestroy_destroyAll() {
        displaySetup(/* isLogoutState= */ false, /* displayId= */ FRONT_PASSENGER_DISPLAY_ID);
        spyOn(mUserPickerSharedState);

        mUserPickerController.onDestroy();

        verify(mUserPickerSharedState).resetUserLoginStarted(eq(FRONT_PASSENGER_DISPLAY_ID));
        verify(mMockUserEventManager)
                .unregisterOnUpdateUsersListener(eq(FRONT_PASSENGER_DISPLAY_ID));
    }
}
