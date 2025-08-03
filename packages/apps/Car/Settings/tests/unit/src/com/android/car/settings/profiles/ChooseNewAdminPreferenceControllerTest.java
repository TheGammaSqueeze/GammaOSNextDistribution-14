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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.testng.Assert.assertThrows;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.UserInfo;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.internal.user.UserHelper;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
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

@RunWith(AndroidJUnit4.class)
public class ChooseNewAdminPreferenceControllerTest {
    private static final UserInfo TEST_ADMIN_USER = new UserInfo(/* id= */ 10,
            "TEST_USER_NAME", /* flags= */ 0);
    private static final UserInfo TEST_OTHER_USER = new UserInfo(/* id= */ 11,
            "TEST_OTHER_NAME", /* flags= */ 0);

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private LogicalPreferenceGroup mPreference;
    private CarUxRestrictions mCarUxRestrictions;
    private ChooseNewAdminPreferenceController mController;
    private ConfirmationDialogFragment mDialog;
    private MockitoSession mSession;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private ProfileHelper mProfileHelper;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mDialog = new ConfirmationDialogFragment.Builder(mContext).build();

        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(ProfileHelper.class, withSettings().lenient())
                .mockStatic(UserHelper.class)
                .startMocking();
        when(ProfileHelper.getInstance(any(Context.class))).thenReturn(mProfileHelper);

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreference = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreference);
        mController = new ChooseNewAdminPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        mController.setAdminInfo(TEST_ADMIN_USER);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);
    }

    @After
    @UiThreadTest
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void testOnCreate_hasPreviousDialog_dialogListenerSet() {
        when(mFragmentController.findDialogByTag(
                ConfirmationDialogFragment.TAG)).thenReturn(mDialog);
        mController.onCreate(mLifecycleOwner);

        assertThat(mDialog.getConfirmListener()).isNotNull();
    }

    @Test
    public void testCheckInitialized_noAdminInfoSet_throwsError() {
        ChooseNewAdminPreferenceController controller = new ChooseNewAdminPreferenceController(
                mContext, /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        assertThrows(IllegalStateException.class,
                () -> PreferenceControllerTestUtil.assignPreference(controller, mPreference));
    }

    @Test
    public void testUserClicked_opensDialog() {
        mController.profileClicked(/* userToMakeAdmin= */ TEST_OTHER_USER);

        verify(mFragmentController).showDialog(any(), eq(ConfirmationDialogFragment.TAG));
    }

    @Test
    public void testAssignNewAdminAndRemoveOldAdmin_grantAdminCalled() {
        mController.assignNewAdminAndRemoveOldAdmin(TEST_OTHER_USER);

        ExtendedMockito.verify(() -> UserHelper.grantAdminPermissions(mContext,
                TEST_OTHER_USER.getUserHandle()));
    }

    @Test
    public void testAssignNewAdminAndRemoveOldAdmin_removeUserCalled() {
        mController.assignNewAdminAndRemoveOldAdmin(TEST_OTHER_USER);

        verify(mProfileHelper).removeProfile(any(), eq(TEST_ADMIN_USER));
    }

    @Test
    public void testAssignNewAdminAndRemoveOldAdmin_success_noErrorDialog() {
        when(mProfileHelper.removeProfile(any(), eq(TEST_ADMIN_USER)))
                .thenReturn(ProfileHelper.REMOVE_PROFILE_RESULT_SUCCESS);

        mController.assignNewAdminAndRemoveOldAdmin(TEST_OTHER_USER);

        verify(mFragmentController, never()).showDialog(any(), any());
    }

    @Test
    public void testAssignNewAdminAndRemoveOldAdmin_failure_errorDialog() {
        when(mProfileHelper.removeProfile(any(), eq(TEST_ADMIN_USER)))
                .thenReturn(ProfileHelper.REMOVE_PROFILE_RESULT_FAILED);

        mController.assignNewAdminAndRemoveOldAdmin(TEST_OTHER_USER);

        verify(mFragmentController).showDialog(any(), any());
    }
}
