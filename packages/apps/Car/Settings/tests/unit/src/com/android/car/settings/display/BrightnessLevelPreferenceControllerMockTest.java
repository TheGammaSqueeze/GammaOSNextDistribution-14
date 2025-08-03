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

package com.android.car.settings.display;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.UserManager;
import android.widget.Toast;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.common.SeekBarPreference;
import com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@RunWith(AndroidJUnit4.class)
public final class BrightnessLevelPreferenceControllerMockTest {
    private static final String TEST_RESTRICTION =
            android.os.UserManager.DISALLOW_CONFIG_BRIGHTNESS;

    private final Context mContext = spy(ApplicationProvider.getApplicationContext());
    private final LifecycleOwner mLifecycleOwner = new TestLifecycleOwner();

    private BrightnessLevelPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private SeekBarPreference mPreference;
    private MockitoSession mSession;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private UserManager mMockUserManager;
    @Mock
    private Toast mMockToast;

    @Before
    @UiThreadTest
    public void setUp() {
        mSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .mockStatic(Toast.class)
                .strictness(Strictness.LENIENT)
                .startMocking();

        when(mContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(Toast.makeText(any(), anyString(), anyInt())).thenReturn(mMockToast);

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreference = new SeekBarPreference(mContext);
        mPreferenceController = new BrightnessLevelPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @After
    @UiThreadTest
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByUm_disabledForUser() {
        mockUserRestrictionSetByUm(true);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByUm_disabledForUser_zoneWrite() {
        mockUserRestrictionSetByUm(true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByUm_disabledForUser_zoneRead() {
        mockUserRestrictionSetByUm(true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByUm_disabledForUser_zoneHidden() {
        mockUserRestrictionSetByUm(true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByDpm_disabledForUser() {
        mockUserRestrictionSetByDpm(true);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByDpm_disabledForUser_zoneWrite() {
        mockUserRestrictionSetByDpm(true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByDpm_disabledForUser_zoneRead() {
        mockUserRestrictionSetByDpm(true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_restrictedByDpm_disabledForUser_zoneHidden() {
        mockUserRestrictionSetByDpm(true);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_notRestricted_available() {
        mockUserRestrictionSetByDpm(false);

        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void testGetAvailabilityStatus_notRestricted_available_zoneWrite() {
        mockUserRestrictionSetByDpm(false);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void testGetAvailabilityStatus_notRestricted_available_zoneRead() {
        mockUserRestrictionSetByDpm(false);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void testGetAvailabilityStatus_notRestricted_available_zoneHidden() {
        mockUserRestrictionSetByDpm(false);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    @UiThreadTest
    public void testDisabledClick_restrictedByUm_toast() {
        mockUserRestrictionSetByUm(true);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreference.performClick();

        assertShowingBlockedToast();
    }

    @Test
    @UiThreadTest
    public void testDisabledClick_restrictedByDpm_dialog() {
        mockUserRestrictionSetByDpm(true);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreference.performClick();

        assertShowingDisabledByAdminDialog();
    }

    private void mockUserRestrictionSetByUm(boolean restricted) {
        when(mMockUserManager.hasBaseUserRestriction(eq(TEST_RESTRICTION), any()))
                .thenReturn(restricted);
    }

    private void mockUserRestrictionSetByDpm(boolean restricted) {
        mockUserRestrictionSetByUm(false);
        when(mMockUserManager.hasUserRestriction(TEST_RESTRICTION)).thenReturn(restricted);
    }

    private void assertShowingBlockedToast() {
        String toastText = mContext.getResources().getString(R.string.action_unavailable);
        ExtendedMockito.verify(
                () -> Toast.makeText(any(), eq(toastText), anyInt()));
        verify(mMockToast).show();
    }

    private void assertShowingDisabledByAdminDialog() {
        verify(mFragmentController).showDialog(any(ActionDisabledByAdminDialogFragment.class),
                eq(DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG));
    }
}
