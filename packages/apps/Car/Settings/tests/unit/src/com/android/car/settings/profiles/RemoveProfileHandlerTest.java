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

package com.android.car.settings.profiles;

import static android.content.pm.UserInfo.FLAG_INITIALIZED;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_PROFILE;
import static com.android.car.settings.profiles.ProfilesDialogProvider.ANY_PROFILE;
import static com.android.car.settings.profiles.ProfilesDialogProvider.KEY_PROFILE_TYPE;
import static com.android.car.settings.profiles.ProfilesDialogProvider.LAST_ADMIN;
import static com.android.car.settings.profiles.RemoveProfileHandler.REMOVE_PROFILE_DIALOG_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.testutils.EnterpriseTestUtils;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@RunWith(AndroidJUnit4.class)
public class RemoveProfileHandlerTest {
    private static final String TEST_PROFILE_NAME = "Test Profile Name";
    private static final String TEST_RESTRICTION = UserManager.DISALLOW_REMOVE_USER;
    private static final int FOREGROUND_USER_ID = 1000;
    private static final int SECONDARY_USER_ID = 1001;

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private MockitoSession mSession;
    private RemoveProfileHandler mRemoveProfileHandler;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private ProfileHelper mMockProfileHelper;
    @Mock
    private UserManager mMockUserManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mSession = ExtendedMockito.mockitoSession()
                .spyStatic(ActivityManager.class)
                .spyStatic(UserHandle.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        ExtendedMockito.doReturn(FOREGROUND_USER_ID).when(ActivityManager::getCurrentUser);
        ExtendedMockito.doReturn(FOREGROUND_USER_ID).when(UserHandle::myUserId);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        mRemoveProfileHandler = new RemoveProfileHandler(
                mContext, mMockProfileHelper, mMockUserManager, mMockFragmentController);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void userNotRestricted_canRemoveProfile() {
        UserInfo userInfo = mockCurrentUserInfo(FOREGROUND_USER_ID, /* isCurrentProcess */ false);

        assertThat(mRemoveProfileHandler.canRemoveProfile(userInfo)).isTrue();
    }

    @Test
    public void userRestricted_cannotRemoveProfile() {
        UserInfo userInfo = mockCurrentUserInfo(FOREGROUND_USER_ID, /* isCurrentProcess */ false);
        when(mMockUserManager.hasUserRestriction(TEST_RESTRICTION)).thenReturn(true);

        assertThat(mRemoveProfileHandler.canRemoveProfile(userInfo)).isFalse();
    }

    @Test
    public void viewingForegroundUser_cannotRemoveProfile() {
        UserInfo userInfo = mockCurrentUserInfo(FOREGROUND_USER_ID, /* isCurrentProcess */ false);
        ExtendedMockito.doReturn(SECONDARY_USER_ID).when(UserHandle::myUserId);

        assertThat(mRemoveProfileHandler.canRemoveProfile(userInfo)).isFalse();
    }

    @Test
    public void viewingSystemUser_cannotRemoveProfile() {
        UserInfo userInfo = mockCurrentUserInfo(0, /* isCurrentProcess */ false);

        assertThat(mRemoveProfileHandler.canRemoveProfile(userInfo)).isFalse();
    }

    @Test
    public void isDemoUser_cannotRemoveProfile() {
        UserInfo userInfo = mockCurrentUserInfo(FOREGROUND_USER_ID, /* isCurrentProcess */ true);
        when(mMockUserManager.isDemoUser()).thenReturn(true);

        assertThat(mRemoveProfileHandler.canRemoveProfile(userInfo)).isFalse();
    }

    @Test
    public void hasPreviousDeleteDialog_dialogListenerSet() {
        UserInfo userInfo = new UserInfo(FOREGROUND_USER_ID, TEST_PROFILE_NAME, FLAG_INITIALIZED);
        mRemoveProfileHandler.setUserInfo(userInfo);
        ConfirmationDialogFragment dialog = new ConfirmationDialogFragment.Builder(
                mContext).build();
        when(mMockFragmentController.findDialogByTag(REMOVE_PROFILE_DIALOG_TAG)).thenReturn(dialog);

        mRemoveProfileHandler.resetListeners();

        assertThat(dialog.getConfirmListener()).isNotNull();
    }

    @Test
    public void showConfirmRemoveProfileDialog_showsConfirmationDialog() {
        mockCurrentUserInfo(FOREGROUND_USER_ID, /* isCurrentProcess */ false);

        mRemoveProfileHandler.showConfirmRemoveProfileDialog();

        verify(mMockFragmentController).showDialog(any(ConfirmationDialogFragment.class),
                eq(REMOVE_PROFILE_DIALOG_TAG));
    }

    @Test
    public void onDeleteConfirmed_removeProfile() {
        UserInfo userInfo = mockCurrentUserInfo(FOREGROUND_USER_ID, /* isCurrentProcess */ false);
        Bundle arguments = new Bundle();
        arguments.putString(KEY_PROFILE_TYPE, ANY_PROFILE);
        mRemoveProfileHandler.mRemoveConfirmListener.onConfirm(arguments);

        verify(mMockProfileHelper).removeProfile(mContext, userInfo);
    }

    @Test
    @UiThreadTest
    public void onDeleteConfirmed_lastAdmin_launchChooseNewAdminFragment() {
        mockCurrentUserInfo(FOREGROUND_USER_ID, /* isCurrentProcess */ false);

        Bundle arguments = new Bundle();
        arguments.putString(KEY_PROFILE_TYPE, LAST_ADMIN);
        mRemoveProfileHandler.mRemoveConfirmListener.onConfirm(arguments);

        verify(mMockFragmentController).launchFragment(any(ChooseNewAdminFragment.class));
    }

    @Test
    public void getAvailabilityStatus_currentUser_availableForProcess_available() {
        UserInfo userInfo = mockCurrentUserInfo(FOREGROUND_USER_ID, /* isCurrentProcess */ true);

        assertThat(mRemoveProfileHandler.getAvailabilityStatus(mContext, userInfo,
                /* availableForCurrentProcessUser */ true)).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_notCurrentUser_notAvailableForProcess_available() {
        UserInfo userInfo = mockCurrentUserInfo(SECONDARY_USER_ID, /* isCurrentProcess */ false);

        assertThat(mRemoveProfileHandler.getAvailabilityStatus(mContext, userInfo,
                /* availableForCurrentProcessUser */ false)).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_currentUser_notAvailableForCurrentUser_disable() {
        UserInfo userInfo = mockCurrentUserInfo(FOREGROUND_USER_ID, /* isCurrentProcess */ true);

        assertThat(mRemoveProfileHandler.getAvailabilityStatus(mContext, userInfo,
                /* availableForCurrentProcessUser */ false)).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_notCurrentUser_availableForCurrentUser_disable() {
        UserInfo userInfo = mockCurrentUserInfo(SECONDARY_USER_ID, /* isCurrentProcess */ false);

        assertThat(mRemoveProfileHandler.getAvailabilityStatus(mContext, userInfo,
                /* availableForCurrentProcessUser */ true)).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_restrictedByUm_disable() {
        UserInfo userInfo = mockCurrentUserInfo(FOREGROUND_USER_ID, /* isCurrentProcess */ true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        assertThat(mRemoveProfileHandler.getAvailabilityStatus(mContext, userInfo,
                /* availableForCurrentProcessUser */ true)).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_allowCurrentProcess_onlyRestrictedByDpm_viewing() {
        UserInfo userInfo = mockCurrentUserInfo(FOREGROUND_USER_ID, /* isCurrentProcess */ true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        assertThat(mRemoveProfileHandler.getAvailabilityStatus(mContext, userInfo,
                /* availableForCurrentProcessUser */ true)).isEqualTo(AVAILABLE_FOR_VIEWING);
    }

    private UserInfo mockCurrentUserInfo(int userId, boolean isCurrentProcess) {
        UserInfo userInfo = new UserInfo(userId, TEST_PROFILE_NAME, FLAG_INITIALIZED);
        mRemoveProfileHandler.setUserInfo(userInfo);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(isCurrentProcess);
        return userInfo;
    }
}
