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

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_PROFILE;
import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;
import static com.android.car.settings.profiles.AddProfileHandler.CONFIRM_CREATE_NEW_PROFILE_DIALOG_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.car.user.CarUserManager;
import android.car.user.UserCreationResult;
import android.car.util.concurrent.AndroidAsyncFuture;
import android.car.util.concurrent.AndroidFuture;
import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment;
import com.android.car.settings.testutils.EnterpriseTestUtils;
import com.android.car.settings.testutils.ResourceTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class AddProfileHandlerTest {
    private static final int ADD_PROFILE_TASK_TIMEOUT = 10; // in seconds
    private static final String TEST_RESTRICTION =
            android.os.UserManager.DISALLOW_ADD_USER;

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private AddProfileHandler mAddProfileHandler;
    private LifecycleOwner mLifecycleOwner;
    private TestAddProfilePreferenceController mPreferenceController;
    private Preference mPreference;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private UserManager mUserManager;
    @Mock
    private CarUserManager mCarUserManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        mLifecycleOwner = new TestLifecycleOwner();
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new TestAddProfilePreferenceController(mContext,
                "key", mMockFragmentController, mCarUxRestrictions);
        mPreference = new Preference(mContext);
        mAddProfileHandler = new AddProfileHandler(mContext,
                mMockFragmentController, mPreferenceController);
        mAddProfileHandler.setCarUserManager(mCarUserManager);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @Test
    public void userRestricted_canNotAddProfile() {
        when(mUserManager.hasUserRestriction(UserManager.DISALLOW_ADD_USER)).thenReturn(true);

        assertThat(mAddProfileHandler.canAddProfiles(mUserManager)).isFalse();
    }

    @Test
    public void userNotRestricted_canAddProfile() {
        when(mUserManager.hasUserRestriction(UserManager.DISALLOW_ADD_USER)).thenReturn(false);

        assertThat(mAddProfileHandler.canAddProfiles(mUserManager)).isTrue();
    }

    /* Test that onCreateNewProfileConfirmed invokes a creation of a new non-admin. */
    @Test
    public void newProfileConfirmed_invokesCreateNewUser()
            throws ExecutionException, InterruptedException, TimeoutException {
        AndroidFuture<UserCreationResult> future = new AndroidFuture<>();
        UserHandle newUserHandle = UserHandle.of(1001);
        future.complete(new UserCreationResult(UserCreationResult.STATUS_SUCCESSFUL,
                newUserHandle));
        when(mUserManager.getUserInfo(anyInt())).thenReturn(null);
        when(mCarUserManager.createUser(anyString(), anyInt()))
                .thenReturn(new AndroidAsyncFuture<>(future));

        mPreferenceController.onCreate(mLifecycleOwner);
        mAddProfileHandler.mConfirmCreateNewProfileListener.onConfirm(/* arguments= */ null);
        // wait for async task
        mAddProfileHandler.mAddNewProfileTask.get(ADD_PROFILE_TASK_TIMEOUT, TimeUnit.SECONDS);
        verify(mCarUserManager).createUser(
                ResourceTestUtils.getString(mContext, "user_new_user_name"), /* flags= */ 0);
    }

    /* Test that if profile can add other profiles, clicking on the button creates a dialog to
    confirm. */
    @Test
    public void testCallOnClick_showAddProfileDialog() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mAddProfileHandler.showAddProfileDialog();

        verify(mMockFragmentController).showDialog(any(ConfirmationDialogFragment.class),
                eq(CONFIRM_CREATE_NEW_PROFILE_DIALOG_TAG));
    }

    @Test
    public void getAddProfilePreferenceAvailabilityStatus_demoUser_available() {
        when(mUserManager.isDemoUser()).thenReturn(true);
        assertThat(mAddProfileHandler.getAddProfilePreferenceAvailabilityStatus(mContext))
                .isEqualTo(AVAILABLE);
    }

    @Test
    public void getAddProfilePreferenceAvailabilityStatus_notRestricted_available() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mUserManager, TEST_RESTRICTION, false);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mUserManager, TEST_RESTRICTION, false);
        assertThat(mAddProfileHandler.getAddProfilePreferenceAvailabilityStatus(mContext))
                .isEqualTo(AVAILABLE);
    }

    @Test
    public void getAddProfilePreferenceAvailabilityStatus_notDemoUser_restrictedByUm_disabled() {
        when(mUserManager.isDemoUser()).thenReturn(false);
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mUserManager, TEST_RESTRICTION, true);
        assertThat(mAddProfileHandler.getAddProfilePreferenceAvailabilityStatus(mContext))
                .isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAddProfilePreferenceAvailabilityStatus_notDemoUser_restrictedByDpm_viewing() {
        when(mUserManager.isDemoUser()).thenReturn(false);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mUserManager, TEST_RESTRICTION, true);
        assertThat(mAddProfileHandler.getAddProfilePreferenceAvailabilityStatus(mContext))
                .isEqualTo(AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAddProfilePreferenceAvailabilityStatus_notDemoUser_restrictedByBoth_disabled() {
        when(mUserManager.isDemoUser()).thenReturn(false);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mUserManager, TEST_RESTRICTION, true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mUserManager, TEST_RESTRICTION, true);
        assertThat(mAddProfileHandler.getAddProfilePreferenceAvailabilityStatus(mContext))
                .isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void runClickableWhileDisabled_restrictedByDpm_showAdminDialog() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mUserManager, TEST_RESTRICTION, true);

        mAddProfileHandler.runClickableWhileDisabled();

        assertShowingDisabledByAdminDialog();
    }

    private void assertShowingDisabledByAdminDialog() {
        verify(mMockFragmentController).showDialog(any(ActionDisabledByAdminDialogFragment.class),
                eq(DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG));
    }

    private class TestAddProfilePreferenceController
            extends PreferenceController<Preference> {

        TestAddProfilePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController,
                CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected Class<Preference> getPreferenceType() {
            return Preference.class;
        }
    }
}
