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

package com.android.car.settings.system;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_PROFILE;
import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.UserManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiPreference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Unit test for {@link ResetNetworkEntryPreferenceController}. */
@RunWith(AndroidJUnit4.class)
public class ResetNetworkEntryPreferenceControllerTest {
    private static final String TEST_RESTRICTION = android.os.UserManager.DISALLOW_NETWORK_RESET;

    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private final LifecycleOwner mLifecycleOwner = new TestLifecycleOwner();
    private CarUiPreference mPreference;
    private ResetNetworkEntryPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private UserManager mMockUserManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreference = new CarUiPreference(mContext);
        mPreferenceController = new ResetNetworkEntryPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @Test
    public void getAvailabilityStatus_nonAdminUser_disabledForUser() {
        mockUserIsAdmin(false);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_nonAdminUser_disabledForUser_zoneWrite() {
        mockUserIsAdmin(false);

        mPreferenceController.setAvailabilityStatusForZone("write");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_nonAdminUser_disabledForUser_zoneRead() {
        mockUserIsAdmin(false);

        mPreferenceController.setAvailabilityStatusForZone("read");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_nonAdminUser_disabledForUser_zoneHidden() {
        mockUserIsAdmin(false);

        mPreferenceController.setAvailabilityStatusForZone("hidden");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_adminUser_restrictedByUm_disabledForUser() {
        mockUserIsAdmin(true);
        mockUserRestrictionSetByUm(true);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_adminUser_restrictedByUm_disabledForUser_zoneWrite() {
        mockUserIsAdmin(true);
        mockUserRestrictionSetByUm(true);

        mPreferenceController.setAvailabilityStatusForZone("write");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_adminUser_restrictedByUm_disabledForUser_zoneRead() {
        mockUserIsAdmin(true);
        mockUserRestrictionSetByUm(true);

        mPreferenceController.setAvailabilityStatusForZone("read");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_adminUser_restrictedByUm_disabledForUser_zoneHidden() {
        mockUserIsAdmin(true);
        mockUserRestrictionSetByUm(true);

        mPreferenceController.setAvailabilityStatusForZone("hidden");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_adminUser_restrictedByDpm_disabledForUser() {
        mockUserIsAdmin(true);
        mockUserRestrictionSetByDpm(true);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_adminUser_restrictedByDpm_disabledForUser_zoneWrite() {
        mockUserIsAdmin(true);
        mockUserRestrictionSetByDpm(true);

        mPreferenceController.setAvailabilityStatusForZone("write");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_adminUser_restrictedByDpm_disabledForUser_zoneRead() {
        mockUserIsAdmin(true);
        mockUserRestrictionSetByDpm(true);

        mPreferenceController.setAvailabilityStatusForZone("read");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_adminUser_restrictedByDpm_disabledForUser_zoneHidden() {
        mockUserIsAdmin(true);
        mockUserRestrictionSetByDpm(true);

        mPreferenceController.setAvailabilityStatusForZone("hidden");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_adminUser_notRestricted_available() {
        mockUserIsAdmin(true);
        mockUserRestrictionSetByDpm(false);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void getAvailabilityStatus_adminUser_notRestricted_available_zoneWrite() {
        mockUserIsAdmin(true);
        mockUserRestrictionSetByDpm(false);

        mPreferenceController.setAvailabilityStatusForZone("write");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void getAvailabilityStatus_adminUser_notRestricted_available_zoneRead() {
        mockUserIsAdmin(true);
        mockUserRestrictionSetByDpm(false);

        mPreferenceController.setAvailabilityStatusForZone("read");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_adminUser_notRestricted_available_zoneHidden() {
        mockUserIsAdmin(true);
        mockUserRestrictionSetByDpm(false);

        mPreferenceController.setAvailabilityStatusForZone("hidden");
        mPreferenceController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    @UiThreadTest
    public void testDisabledClick_restrictedByDpm_showDialog() {
        mockUserIsAdmin(true);
        mockUserRestrictionSetByDpm(true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreference.performClick();

        assertShowingDisabledByAdminDialog();
    }

    private void mockUserIsAdmin(boolean isAdmin) {
        when(mMockUserManager.isAdminUser()).thenReturn(isAdmin);
    }

    private void mockUserRestrictionSetByUm(boolean restricted) {
        when(mMockUserManager.hasBaseUserRestriction(eq(TEST_RESTRICTION), any()))
                .thenReturn(restricted);
    }

    private void mockUserRestrictionSetByDpm(boolean restricted) {
        mockUserRestrictionSetByUm(false);
        when(mMockUserManager.hasUserRestriction(TEST_RESTRICTION)).thenReturn(restricted);
    }

    private void assertShowingDisabledByAdminDialog() {
        verify(mFragmentController).showDialog(any(ActionDisabledByAdminDialogFragment.class),
                eq(DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG));
    }
}
