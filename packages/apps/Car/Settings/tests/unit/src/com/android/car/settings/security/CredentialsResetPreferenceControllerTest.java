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

package com.android.car.settings.security;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_PROFILE;
import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;
import static com.android.car.settings.testutils.EnterpriseTestUtils.mockUserRestrictionSetByDpm;
import static com.android.car.settings.testutils.EnterpriseTestUtils.mockUserRestrictionSetByUm;

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

/** Unit test for {@link CredentialsResetPreferenceController} with mock setup. */
@RunWith(AndroidJUnit4.class)
public class CredentialsResetPreferenceControllerTest {
    private static final String TEST_RESTRICTION =
            android.os.UserManager.DISALLOW_CONFIG_CREDENTIALS;

    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private final LifecycleOwner mLifecycleOwner = new TestLifecycleOwner();
    private CarUiPreference mPreference;
    private CredentialsResetPreferenceController mPreferenceController;

    @Mock
    private CarUxRestrictions mMockCarUxRestrictions;
    @Mock
    private FragmentController mMockFragmentController;
    @Mock
    private UserManager mMockUserManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);

        mPreference = new CarUiPreference(mContext);
        mPreferenceController = new CredentialsResetPreferenceController(mContext,
                "key", mMockFragmentController, mMockCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @Test
    public void getAvailabilityStatus_restrictedByUm_disabled() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_restrictedByUm_disabled_zoneWrite() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_restrictedByUm_disabled_zoneRead() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_restrictedByUm_disabled_zoneHidden() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_restrictedByDpm_viewing() {
        mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_restrictedByDpm_viewing_zoneWrite() {
        mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("wrtie");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_restrictedByDpm_viewing_zoneRead() {
        mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_restrictedByDpm_viewing_zoneHidden() {
        mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_restrictedByBothUmDpm_disabled() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_restrictedByBothUmDpm_disabled_zoneWrite() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_restrictedByBothUmDpm_disabled_zoneRead() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_restrictedByBothUmDpm_disabled_zoneHidden() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_unrestricted_available() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, false);
        mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, false);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_unrestricted_available_zoneWrite() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, false);
        mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, false);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_unrestricted_available_zoneRead() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, false);
        mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, false);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_unrestricted_available_zoneHidden() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, false);
        mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, false);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    @UiThreadTest
    public void testDisabledClick_restrictedByDpm_showDialog() {
        mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreference.performClick();

        assertShowingDisabledByAdminDialog();
    }

    private void assertShowingDisabledByAdminDialog() {
        verify(mMockFragmentController).showDialog(any(ActionDisabledByAdminDialogFragment.class),
                eq(DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG));
    }
}
