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

import static android.view.Display.INVALID_DISPLAY;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import android.content.pm.UserInfo;
import android.os.UserManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.userswitcher.UserIconProvider;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

/** Test for the RecyclerView contained in the UserPickerActivity (not a separate class) */
@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class UserPickerRecyclerViewTest extends UserPickerTestCase {
    @Rule
    public ActivityScenarioRule<UserPickerRecyclerViewTestActivity> mActivityRule =
            new ActivityScenarioRule(UserPickerRecyclerViewTestActivity.class);

    @Mock
    private UserIconProvider mMockUserIconProvider;
    @Mock
    private UserManager mMockUserManager;

    private UserRecord mDriver;
    private UserRecord mFront;
    private UserRecord mRear;
    private List<UserRecord> mUserList = null;

    class OnClickListenerCreator extends UserRecord.OnClickListenerCreatorBase {
        @Override
        View.OnClickListener createOnClickListenerWithUserRecord() {
            return holderView -> {};
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        doReturn(mDriverUserInfo).when(mMockUserManager).getUserInfo(USER_ID_DRIVER);
        doReturn(mFrontUserInfo).when(mMockUserManager).getUserInfo(USER_ID_FRONT);
        doReturn(mRearUserInfo).when(mMockUserManager).getUserInfo(USER_ID_REAR);
        mContext.addMockSystemService(UserManager.class, mMockUserManager);
        doReturn(FRONT_PASSENGER_DISPLAY_ID).when(mContext).getDisplayId();

        mDriver = UserRecord.create(mDriverUserInfo, /* mName= */ mDriverUserInfo.name,
                /* mIsStartGuestSession= */ false, /* mIsAddUser= */ false,
                /* mIsForeground= */ true,
                /* mIcon= */ mMockUserIconProvider.getRoundedUserIcon(mDriverUserInfo, mContext),
                /* OnClickListenerMaker */ new OnClickListenerCreator(), /* mIsLoggedIn= */ true,
                /* mLoggedInDisplay= */ MAIN_DISPLAY_ID,
                /* mSeatLocationName= */ USER_NAME_DRIVER, /* mIsStopping= */ false);
        mFront = UserRecord.create(mFrontUserInfo, /* mName= */ mFrontUserInfo.name,
                /* mIsStartGuestSession= */ false, /* mIsAddUser= */ false,
                /* mIsForeground= */ false,
                /* mIcon= */ mMockUserIconProvider.getRoundedUserIcon(mFrontUserInfo, mContext),
                /* OnClickListenerMaker */ new OnClickListenerCreator(), /* mIsLoggedIn= */ true,
                /* mLoggedInDisplay= */ FRONT_PASSENGER_DISPLAY_ID,
                /* mSeatLocationName= */ USER_NAME_FRONT, /* mIsStopping= */ false);
        mRear = UserRecord.create(mRearUserInfo, /* mName= */ mRearUserInfo.name,
                /* mIsStartGuestSession= */ false, /* mIsAddUser= */ false,
                /* mIsForeground= */ false,
                /* mIcon= */ mMockUserIconProvider.getRoundedUserIcon(mRearUserInfo, mContext),
                /* OnClickListenerMaker */ new OnClickListenerCreator(), /* mIsLoggedIn= */ false,
                /* mLoggedInDisplay= */ INVALID_DISPLAY,
                /* mSeatLocationName= */ "", /* mIsStopping= */ false);
    }

    void updateUsers_refreshView(UserPickerAdapter adapter, List<UserRecord> mUserList) {
        adapter.updateUsers(mUserList);
        adapter.notifyDataSetChanged();
    }

    int getPositionOnPrimaryGrid(String name) {
        if (name == null || mUserList == null) {
            return -1;
        } else if (name.equals(mGuestLabel)) {
            return mUserList.size() - 2;
        } else if (name.equals(mAddLabel)) {
            return mUserList.size() - 1;
        } else {
            for (int i = 0; i < mUserList.size(); i++) {
                if (name.equals(mUserList.get(i).mName)) {
                    return i;
                }
            }
            return -1;
        }
    }

    Boolean isHighlightingfromUserPod(ViewGroup vg) {
        ViewGroup avatar = (ViewGroup) vg.getChildAt(0);
        View v = (View) avatar.getChildAt(1);
        return v.getVisibility() == View.VISIBLE;
    }

    String getUserNamefromUserPod(ViewGroup vg) {
        TextView t = (TextView) vg.getChildAt(1);
        return t.getText().toString();
    }

    String getUserDescfromUserPod(ViewGroup vg) {
        TextView t = (TextView) vg.getChildAt(2);
        return t.getText().toString();
    }

    @Test
    @Ignore("b/281125325")
    public void checkUserList_changeUserState_frontUserLoggedIn() {
        UserRecord mGuest = UserRecord.create(/* mInfo= */ null, /* mName= */ mGuestLabel,
                /* mIsStartGuestSession= */ true, /* mIsAddUser= */ false,
                /* mIsForeground= */ false,
                mMockUserIconProvider.getRoundedGuestDefaultIcon(mContext),
                /* OnClickListenerMaker */ new OnClickListenerCreator(), false, INVALID_DISPLAY,
                /* mSeatLocationName= */"", /* mIsStopping= */ false);
        UserRecord mAddUser = UserRecord.create(/* mInfo= */ null, /* mName= */ mAddLabel,
                /* mIsStartGuestSession= */ false, /* mIsAddUser= */ true,
                /* mIsForeground= */ false,
                /* mIcon= */ mContext.getDrawable(R.drawable.car_add_circle_round),
                /* OnClickListenerMaker */ new OnClickListenerCreator());
        mUserList = List.of(mDriver, mFront, mRear, mGuest, mAddUser);
        mActivityRule.getScenario().onActivity(activity -> {
            updateUsers_refreshView(activity.mAdapter, mUserList);

            View userIcon = activity.mUserPickerView.getRecyclerViewChildAt(
                    getPositionOnPrimaryGrid(USER_NAME_DRIVER));
            assertThat(getUserDescfromUserPod(((ViewGroup) userIcon)))
                    .isEqualTo(mContext.getString(R.string.prefix_logged_in_info_for_other_seat,
                            USER_NAME_DRIVER));
            assertThat(isHighlightingfromUserPod(((ViewGroup) userIcon))).isFalse();
            userIcon = activity.mUserPickerView.getRecyclerViewChildAt(
                    getPositionOnPrimaryGrid(USER_NAME_FRONT));
            assertThat(getUserDescfromUserPod(((ViewGroup) userIcon))).isEqualTo(mLoggedinLabel);

            assertThat(isHighlightingfromUserPod(((ViewGroup) userIcon))).isTrue();
            userIcon = activity.mUserPickerView
                    .getRecyclerViewChildAt(getPositionOnPrimaryGrid(USER_NAME_REAR));
            assertThat(getUserDescfromUserPod(((ViewGroup) userIcon))).isEqualTo("");
            assertThat(isHighlightingfromUserPod(((ViewGroup) userIcon))).isFalse();
            userIcon = activity.mUserPickerView
                    .getRecyclerViewChildAt(getPositionOnPrimaryGrid(mGuestLabel));
            assertThat(getUserDescfromUserPod(((ViewGroup) userIcon))).isEqualTo("");
            assertThat(isHighlightingfromUserPod(((ViewGroup) userIcon))).isFalse();
            userIcon = activity.mUserPickerView
                    .getRecyclerViewChildAt(getPositionOnPrimaryGrid(mAddLabel));
            assertThat(getUserDescfromUserPod(((ViewGroup) userIcon))).isEqualTo("");
            assertThat(isHighlightingfromUserPod(((ViewGroup) userIcon))).isFalse();
        });
    }

    @Test
    @Ignore("b/281125325")
    public void checkUserList_logoutState_frontUserLoggedOut() {
        mFront = UserRecord.create(mFrontUserInfo, /* mName= */ mFrontUserInfo.name,
                /* mIsStartGuestSession= */ false, /* mIsAddUser= */ false,
                /* mIsForeground= */ false,
                /* mIcon= */ mMockUserIconProvider.getRoundedUserIcon(mFrontUserInfo, mContext),
                /* OnClickListenerMaker */ new OnClickListenerCreator(), /* mIsLoggedIn= */ false,
                /* mLoggedInDisplay= */ -1,
                /* mSeatLocationName= */ "Test", /* mIsStopping= */ false);
        UserRecord mGuest = UserRecord.create(/* mInfo= */ null, /* mName= */ mGuestLabel,
                /* mIsStartGuestSession= */ true, /* mIsAddUser= */ false,
                /* mIsForeground= */ false,
                mMockUserIconProvider.getRoundedGuestDefaultIcon(mContext),
                /* OnClickListenerMaker */ new OnClickListenerCreator(), false, INVALID_DISPLAY,
                /* mSeatLocationName= */"", /* mIsStopping= */ false);
        UserRecord mAddUser = UserRecord.create(/* mInfo= */ null, /* mName= */ mAddLabel,
                /* mIsStartGuestSession= */ false, /* mIsAddUser= */ true,
                /* mIsForeground= */ false,
                /* mIcon= */ mContext.getDrawable(R.drawable.car_add_circle_round),
                /* OnClickListenerMaker */ new OnClickListenerCreator());
        mUserList = List.of(mDriver, mFront, mRear, mGuest, mAddUser);
        mActivityRule.getScenario().onActivity(activity -> {
            updateUsers_refreshView(activity.mAdapter, mUserList);

            View userIcon = activity.mUserPickerView
                    .getRecyclerViewChildAt(getPositionOnPrimaryGrid(USER_NAME_DRIVER));
            assertThat(getUserDescfromUserPod(((ViewGroup) userIcon)))
                    .isEqualTo(mContext.getString(R.string.prefix_logged_in_info_for_other_seat,
                            USER_NAME_DRIVER));
            assertThat(isHighlightingfromUserPod(((ViewGroup) userIcon))).isFalse();
            userIcon = activity.mUserPickerView
                    .getRecyclerViewChildAt(getPositionOnPrimaryGrid(USER_NAME_FRONT));
            assertThat(getUserDescfromUserPod(((ViewGroup) userIcon))).isEqualTo("");
            assertThat(isHighlightingfromUserPod(((ViewGroup) userIcon))).isFalse();
            userIcon = activity.mUserPickerView
                    .getRecyclerViewChildAt(getPositionOnPrimaryGrid(USER_NAME_REAR));
            assertThat(getUserDescfromUserPod(((ViewGroup) userIcon))).isEqualTo("");
            assertThat(isHighlightingfromUserPod(((ViewGroup) userIcon))).isFalse();
            userIcon = activity.mUserPickerView
                    .getRecyclerViewChildAt(getPositionOnPrimaryGrid(mGuestLabel));
            assertThat(isHighlightingfromUserPod(((ViewGroup) userIcon))).isFalse();
            userIcon = activity.mUserPickerView
                    .getRecyclerViewChildAt(getPositionOnPrimaryGrid(mAddLabel));
            assertThat(isHighlightingfromUserPod(((ViewGroup) userIcon))).isFalse();
        });
    }

    @Test
    public void updateUserList_addUser_increaseUserCount() {
        mUserList = List.of(mDriver, mFront, mRear);
        mActivityRule.getScenario().onActivity(activity -> {
            updateUsers_refreshView(activity.mAdapter, mUserList);
            int prevRecordCount = activity.mAdapter.getItemCount();

            String newUsername = "New";
            UserInfo newUserInfo = new UserInfo(USER_ID_REAR + 1, newUsername, UserInfo.FLAG_FULL);
            UserRecord mNew = UserRecord.create(newUserInfo, /* mName= */ newUserInfo.name,
                    /* mIsStartGuestSession= */ false, /* mIsAddUser= */ false,
                    /* mIsForeground= */ false,
                    /* mIcon= */ mMockUserIconProvider.getRoundedUserIcon(newUserInfo, mContext),
                    /* OnClickListenerMaker */ new OnClickListenerCreator(), /* mIsLoggedIn= */
                    false,
                    /* mLoggedInDisplay= */ INVALID_DISPLAY,
                    /* mSeatLocationName= */ "", /* mIsStopping= */ false);
            mUserList = List.of(mDriver, mFront, mNew, mRear);
            updateUsers_refreshView(activity.mAdapter, mUserList);

            assertThat(activity.mAdapter.getItemCount()).isEqualTo(prevRecordCount + 1);
            assertThat(getPositionOnPrimaryGrid(newUsername))
                    .isEqualTo(getPositionOnPrimaryGrid(USER_NAME_FRONT) + 1);
            assertThat(getPositionOnPrimaryGrid(newUsername))
                    .isEqualTo(getPositionOnPrimaryGrid(USER_NAME_REAR) - 1);
        });
    }

    @Test
    public void updateUserList_removeUser_decreaseUserCount() {
        mUserList = List.of(mDriver, mFront, mRear);
        mActivityRule.getScenario().onActivity(activity -> {
            updateUsers_refreshView(activity.mAdapter, mUserList);
            int prevRecordCount = activity.mAdapter.getItemCount();

            mUserList = List.of(mDriver, mRear);
            updateUsers_refreshView(activity.mAdapter, mUserList);

            assertThat(activity.mAdapter.getItemCount()).isEqualTo(prevRecordCount - 1);
            assertThat(getPositionOnPrimaryGrid(USER_NAME_REAR))
                    .isEqualTo(getPositionOnPrimaryGrid(USER_NAME_DRIVER) + 1);
        });
    }

    @Test
    @Ignore("b/281125325")
    public void updateUserList_loginUser_changedRearPassengerDesc() {
        mUserList = List.of(mDriver, mFront, mRear);
        mActivityRule.getScenario().onActivity(activity -> {
            updateUsers_refreshView(activity.mAdapter, mUserList);

            View RearButton = activity.mUserPickerView.getRecyclerViewChildAt(
                    getPositionOnPrimaryGrid(USER_NAME_REAR));
            assertThat(getUserNamefromUserPod(((ViewGroup) RearButton))).isEqualTo(USER_NAME_REAR);
            assertThat(getUserDescfromUserPod(((ViewGroup) RearButton))).isEqualTo("");
            mRear = UserRecord.create(mRearUserInfo, /* mName= */ mRearUserInfo.name,
                    /* mIsStartGuestSession= */ false, /* mIsAddUser= */ false,
                    /* mIsForeground= */ false,
                    /* mIcon= */ mMockUserIconProvider.getRoundedUserIcon(mRearUserInfo, mContext),
                    /* OnClickListenerMaker */ new OnClickListenerCreator(), /* mIsLoggedIn= */
                    true,
                    /* mLoggedInDisplay= */ REAR_PASSENGER_DISPLAY_ID,
                    /* mSeatLocationName= */ USER_NAME_REAR, /* mIsStopping= */ false);
            mUserList = List.of(mDriver, mFront, mRear);
            updateUsers_refreshView(activity.mAdapter, mUserList);

            RearButton = activity.mUserPickerView.getRecyclerViewChildAt(
                    getPositionOnPrimaryGrid(USER_NAME_REAR));
            assertThat(getUserDescfromUserPod(((ViewGroup) RearButton)))
                    .isEqualTo(mContext.getString(R.string.prefix_logged_in_info_for_other_seat,
                            USER_NAME_REAR));
        });
    }

    @Test
    @Ignore("b/281125325")
    public void updateUserList_logoutUser_changedRearPassengerDesc() {
        mRear = UserRecord.create(mRearUserInfo, /* mName= */ mRearUserInfo.name,
                /* mIsStartGuestSession= */ false, /* mIsAddUser= */ false,
                /* mIsForeground= */ false,
                /* mIcon= */ mMockUserIconProvider.getRoundedUserIcon(mRearUserInfo, mContext),
                /* OnClickListenerMaker */ new OnClickListenerCreator(), /* mIsLoggedIn= */ true,
                /* mLoggedInDisplay= */ REAR_PASSENGER_DISPLAY_ID,
                /* mSeatLocationName= */ USER_NAME_REAR, /* mIsStopping= */ false);
        mUserList = List.of(mDriver, mFront, mRear);
        mActivityRule.getScenario().onActivity(activity -> {
            updateUsers_refreshView(activity.mAdapter, mUserList);

            View rearPassengerUserIcon = activity.mUserPickerView
                    .getRecyclerViewChildAt(getPositionOnPrimaryGrid(USER_NAME_REAR));
            assertThat(getUserDescfromUserPod(((ViewGroup) rearPassengerUserIcon)))
                    .isEqualTo(mContext.getString(R.string.prefix_logged_in_info_for_other_seat,
                            USER_NAME_REAR));
            mRear = UserRecord.create(mRearUserInfo, /* mName= */ mRearUserInfo.name,
                    /* mIsStartGuestSession= */ false, /* mIsAddUser= */ false,
                    /* mIsForeground= */ false,
                    /* mIcon= */ mMockUserIconProvider.getRoundedUserIcon(mRearUserInfo, mContext),
                    /* OnClickListenerMaker */ new OnClickListenerCreator(), /* mIsLoggedIn= */
                    false,
                    /* mLoggedInDisplay= */ INVALID_DISPLAY,
                    /* mSeatLocationName= */ "", /* mIsStopping= */ false);
            mUserList = List.of(mDriver, mFront, mRear);
            updateUsers_refreshView(activity.mAdapter, mUserList);

            rearPassengerUserIcon = activity.mUserPickerView
                    .getRecyclerViewChildAt(getPositionOnPrimaryGrid(USER_NAME_REAR));
            assertThat(getUserDescfromUserPod(((ViewGroup) rearPassengerUserIcon))).isEqualTo("");
        });
    }

    @Test
    @Ignore("b/281125325")
    public void updateUserList_changeName_changedUserName() {
        String changeName = "Test";
        mUserList = List.of(mDriver, mFront, mRear);
        mActivityRule.getScenario().onActivity(activity -> {
            updateUsers_refreshView(activity.mAdapter, mUserList);
            View rearPassengerUserIcon = activity.mUserPickerView
                    .getRecyclerViewChildAt(mUserList.size() - 1);
            assertThat(getUserNamefromUserPod(((ViewGroup) rearPassengerUserIcon)))
                    .isEqualTo(USER_NAME_REAR);

            mRearUserInfo.name = changeName;
            mRear = UserRecord.create(mRearUserInfo, /* mName= */ mRearUserInfo.name,
                    /* mIsStartGuestSession= */ false, /* mIsAddUser= */ false,
                    /* mIsForeground= */ false,
                    /* mIcon= */ mMockUserIconProvider.getRoundedUserIcon(mRearUserInfo, mContext),
                    /* OnClickListenerMaker */ new OnClickListenerCreator(), /* mIsLoggedIn= */
                    false,
                    /* mLoggedInDisplay= */ INVALID_DISPLAY,
                    /* mSeatLocationName= */ "", /* mIsStopping= */ false);
            mUserList = List.of(mDriver, mFront, mRear);
            updateUsers_refreshView(activity.mAdapter, mUserList);

            rearPassengerUserIcon = activity.mUserPickerView
                    .getRecyclerViewChildAt(mUserList.size() - 1);
            assertThat(getUserNamefromUserPod(((ViewGroup) rearPassengerUserIcon)))
                    .isEqualTo(changeName);
        });
    }
}
