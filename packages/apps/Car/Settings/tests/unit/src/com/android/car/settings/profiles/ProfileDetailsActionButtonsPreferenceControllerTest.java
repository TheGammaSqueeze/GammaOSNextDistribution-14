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

package com.android.car.settings.profiles;

import static android.content.pm.UserInfo.FLAG_ADMIN;
import static android.content.pm.UserInfo.FLAG_INITIALIZED;

import static com.android.car.settings.common.ActionButtonsPreference.ActionButtons;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_PROFILE;
import static com.android.car.settings.profiles.ProfileDetailsActionButtonsPreferenceController.MAKE_ADMIN_DIALOG_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.UserManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.internal.user.UserHelper;
import com.android.car.settings.R;
import com.android.car.settings.common.ActionButtonInfo;
import com.android.car.settings.common.ActionButtonsPreference;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ProfileDetailsActionButtonsPreferenceControllerTest {
    private static final String TEST_PROFILE_NAME = "Test Profile Name";

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private ActionButtonsPreference mPreference;
    private CarUxRestrictions mCarUxRestrictions;
    private ProfileDetailsActionButtonsPreferenceController mPreferenceController;
    private MockitoSession mSession;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private ProfileHelper mMockProfileHelper;
    @Mock
    private UserManager mMockUserManager;
    @Mock
    private RemoveProfileHandler mRemoveProfileHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreference = new ActionButtonsPreference(mContext);
        mPreferenceController = new ProfileDetailsActionButtonsPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions,
                mMockProfileHelper, mMockUserManager, mRemoveProfileHandler);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void onStart_userIsViewingSelf_renameButtonShown() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(getRenameButton().isVisible()).isTrue();
    }

    @Test
    public void onStart_userIsNotViewingSelf_renameButtonHidden() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(getRenameButton().isVisible()).isFalse();
    }

    @Test
    public void onStart_isAdminViewingNonAdmin_makeAdminButtonShown() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        when(mMockUserManager.isAdminUser()).thenReturn(true);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(getMakeAdminButton().isVisible()).isTrue();
    }

    @Test
    public void onStart_isAdminViewingAdmin_makeAdminButtonHidden() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME,
                FLAG_INITIALIZED | FLAG_ADMIN);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        when(mMockUserManager.isAdminUser()).thenReturn(true);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(getMakeAdminButton().isVisible()).isFalse();
    }

    @Test
    public void onStart_isAdminViewingSelf_manageOtherProfilesButtonShown() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME,
                FLAG_INITIALIZED | FLAG_ADMIN);
        List<UserInfo> otherUsers = new ArrayList<>();
        otherUsers.add(userInfo);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        when(mMockUserManager.isAdminUser()).thenReturn(true);
        when(mMockProfileHelper.getAllLivingProfiles(any())).thenReturn(otherUsers);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        when(mRemoveProfileHandler.canRemoveProfile(userInfo)).thenReturn(true);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(getProfilesButton().isVisible()).isTrue();
        assertThat(getProfilesButton().getText())
                .isEqualTo(mContext.getString(R.string.manage_other_profiles_button_text));
    }

    @Test
    public void onStart_isAdminViewingOther_manageOtherProfilesButtonHidden() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME,
                FLAG_INITIALIZED | FLAG_ADMIN);
        List<UserInfo> otherUsers = new ArrayList<>();
        otherUsers.add(userInfo);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        when(mMockUserManager.isAdminUser()).thenReturn(true);
        when(mMockProfileHelper.getAllLivingProfiles(any())).thenReturn(otherUsers);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(getProfilesButton().isVisible()).isFalse();
    }

    @Test
    public void onStart_isNonAdmin_addAProfileButtonShown() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME,
                FLAG_INITIALIZED | FLAG_ADMIN);
        List<UserInfo> otherUsers = new ArrayList<>();
        otherUsers.add(userInfo);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        when(mMockUserManager.isAdminUser()).thenReturn(false);
        when(mMockProfileHelper.getAllLivingProfiles(any())).thenReturn(otherUsers);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(getProfilesButton().isVisible()).isTrue();
        assertThat(getProfilesButton().getText())
                .isEqualTo(mContext.getString(R.string.add_a_profile_button_text));
    }

    @Test
    public void onStart_isAdmin_isOnlyProfile_makeAddAProfileButtonShown() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME,
                FLAG_INITIALIZED | FLAG_ADMIN);
        List<UserInfo> otherUsers = new ArrayList<>();
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        when(mMockUserManager.isAdminUser()).thenReturn(true);
        when(mMockProfileHelper.getAllLivingProfiles(any())).thenReturn(otherUsers);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(getProfilesButton().isVisible()).isTrue();
        assertThat(getProfilesButton().getText())
                .isEqualTo(mContext.getString(R.string.add_a_profile_button_text));
    }

    @Test
    public void onStart_profileIsRemovable_deleteButtonShown() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        when(mRemoveProfileHandler.canRemoveProfile(userInfo)).thenReturn(true);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(getDeleteButton().isVisible()).isTrue();
    }

    @Test
    public void onStart_profileIsNotRemovable_deleteButtonHidden() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        when(mMockUserManager.hasUserRestriction(UserManager.DISALLOW_REMOVE_USER))
                .thenReturn(true);
        when(mRemoveProfileHandler.getAvailabilityStatus(mContext, userInfo,
                /* allowRemoveCurrentProcessUser */ false)).thenReturn(DISABLED_FOR_PROFILE);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(getDeleteButton().isVisible()).isFalse();
    }

    @Test
    public void onStart_userIsViewingSelf_deleteButtonHidden() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        when(mRemoveProfileHandler.getAvailabilityStatus(mContext, userInfo,
                /* allowRemoveCurrentProcessUser */ false)).thenReturn(DISABLED_FOR_PROFILE);
        when(mRemoveProfileHandler.canRemoveProfile(userInfo)).thenReturn(true);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(getDeleteButton().isVisible()).isFalse();
    }

    @Test
    public void onStart_userIsNotViewingSelf_deleteButtonShown() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        when(mRemoveProfileHandler.canRemoveProfile(userInfo)).thenReturn(true);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        assertThat(getDeleteButton().isVisible()).isTrue();
    }

    @Test
    public void onCreate_hasPreviousDeleteDialog_dialogListenerSet() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        when(mMockUserManager.isAdminUser()).thenReturn(true);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        verify(mRemoveProfileHandler).resetListeners();
    }

    @Test
    public void onRenameButtonClicked_launchEditProfileNameFragment() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        getRenameButton().getOnClickListener().onClick(/* view= */ null);

        verify(mFragmentController).launchFragment(any(EditProfileNameFragment.class));
    }

    @Test
    public void onMakeAdminButtonClicked_showsConfirmationDialog() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        when(mMockUserManager.isAdminUser()).thenReturn(true);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        getMakeAdminButton().getOnClickListener().onClick(/* view= */ null);

        verify(mFragmentController).showDialog(any(ConfirmationDialogFragment.class),
                eq(MAKE_ADMIN_DIALOG_TAG));
    }

    @Test
    public void onDeleteButtonClicked_showsConfirmationDialog() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        when(mRemoveProfileHandler.canRemoveProfile(userInfo)).thenReturn(true);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        getDeleteButton().getOnClickListener().onClick(/* view= */ null);

        verify(mRemoveProfileHandler).showConfirmRemoveProfileDialog();
    }

    @Test
    public void onMakeAdminConfirmed_makeProfileAdmin() {
        mSession = ExtendedMockito.mockitoSession().mockStatic(
                UserHelper.class).startMocking();

        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        when(mMockUserManager.isAdminUser()).thenReturn(true);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        Bundle arguments = new Bundle();
        arguments.putParcelable(ProfilesDialogProvider.KEY_PROFILE_TO_MAKE_ADMIN, userInfo);
        mPreferenceController.mMakeAdminConfirmListener.onConfirm(arguments);

        ExtendedMockito.verify(
                () -> UserHelper.grantAdminPermissions(mContext, userInfo.getUserHandle()));
    }

    @Test
    public void onMakeAdminConfirmed_goBack() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        when(mMockUserManager.isAdminUser()).thenReturn(true);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        Bundle arguments = new Bundle();
        arguments.putParcelable(ProfilesDialogProvider.KEY_PROFILE_TO_MAKE_ADMIN, userInfo);
        mPreferenceController.mMakeAdminConfirmListener.onConfirm(arguments);

        verify(mFragmentController).goBack();
    }

    @Test
    @UiThreadTest
    public void onManageOtherProfilesButtonClicked_launchProfilesListFragment() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME, FLAG_INITIALIZED);
        List<UserInfo> otherUsers = new ArrayList<>();
        otherUsers.add(userInfo);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        when(mMockUserManager.isAdminUser()).thenReturn(true);
        when(mMockProfileHelper.getAllLivingProfiles(any())).thenReturn(otherUsers);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        getProfilesButton().getOnClickListener().onClick(/* view= */ null);

        verify(mFragmentController).launchFragment(any(ProfilesListFragment.class));
    }

    private ActionButtonInfo getRenameButton() {
        return mPreference.getButton(ActionButtons.BUTTON1);
    }

    private ActionButtonInfo getMakeAdminButton() {
        return mPreference.getButton(ActionButtons.BUTTON2);
    }

    private ActionButtonInfo getProfilesButton() {
        return mPreference.getButton(ActionButtons.BUTTON3);
    }

    private ActionButtonInfo getDeleteButton() {
        return mPreference.getButton(ActionButtons.BUTTON4);
    }
}
