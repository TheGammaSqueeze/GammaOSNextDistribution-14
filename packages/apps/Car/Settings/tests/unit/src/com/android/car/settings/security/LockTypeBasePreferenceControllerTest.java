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
import static com.android.car.settings.security.LockTypeBasePreferenceController.CHOOSE_LOCK_REQUEST;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.ActivityResultCallback;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.ResourceTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiTwoActionTextPreference;
import com.android.internal.widget.LockscreenCredential;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class LockTypeBasePreferenceControllerTest {
    private static final int MATCHING_PASSWORD_QUALITY =
            DevicePolicyManager.PASSWORD_QUALITY_NUMERIC;
    private static final int NON_MATCHING_PASSWORD_QUALITY =
            DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
    private static final LockscreenCredential NONE_LOCKSCREEN_CREDENTIAL =
            LockscreenCredential.createNone();

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private Preference mPreference;
    private LockTypeBasePreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private UserManager mMockUserManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreference = new CarUiTwoActionTextPreference(mContext);
        mPreferenceController = new TestLockTypeBasePreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions);
        mPreference.getExtras().putParcelable(
                PasswordHelper.EXTRA_CURRENT_SCREEN_LOCK, NONE_LOCKSCREEN_CREDENTIAL);
        mPreference.getExtras().putInt(
                PasswordHelper.EXTRA_CURRENT_PASSWORD_QUALITY, NON_MATCHING_PASSWORD_QUALITY);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        when(mMockUserManager.isGuestUser()).thenReturn(false);
    }

    @Test
    public void testHandlePreferenceClicked_returnsTrue() {
        assertThat(mPreferenceController.handlePreferenceClicked(mPreference)).isTrue();
    }

    @Test
    public void testHandlePreferenceClicked_launchesIntent() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreference.performClick();
        verify(mFragmentController).startActivityForResult(any(Intent.class),
                eq(CHOOSE_LOCK_REQUEST), any(ActivityResultCallback.class));
    }

    @Test
    public void testRefreshUi_isCurrentLock() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setCurrentPasswordQuality(MATCHING_PASSWORD_QUALITY);
        mPreferenceController.refreshUi();
        assertThat(mPreference.getSummary()).isEqualTo(
                ResourceTestUtils.getString(mContext, "current_screen_lock"));
    }

    @Test
    public void testRefreshUi_isNotCurrentLock() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setCurrentPasswordQuality(NON_MATCHING_PASSWORD_QUALITY);
        mPreferenceController.refreshUi();
        assertThat(mPreference.getSummary()).isNotEqualTo(
                ResourceTestUtils.getString(mContext, "current_screen_lock"));
    }

    @Test
    public void testGetAvailabilityStatus_guestUser() {
        when(mMockUserManager.isGuestUser()).thenReturn(true);
        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_guestUser_zoneWrite() {
        when(mMockUserManager.isGuestUser()).thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("write");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_guestUser_zoneRead() {
        when(mMockUserManager.isGuestUser()).thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("read");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_guestUser_zoneHidden() {
        when(mMockUserManager.isGuestUser()).thenReturn(true);
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), DISABLED_FOR_PROFILE);
    }

    @Test
    public void testGetAvailabilityStatus_otherUser() {
        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_otherUser_zoneWrite() {
        mPreferenceController.setAvailabilityStatusForZone("write");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_otherUser_zoneRead() {
        mPreferenceController.setAvailabilityStatusForZone("read");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void testGetAvailabilityStatus_otherUser_zoneHidden() {
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testControllerPassword_isSet() {
        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mPreferenceController.getCurrentPassword()).isEqualTo(
                NONE_LOCKSCREEN_CREDENTIAL);
    }

    @Test
    public void testControllerPasswordQuality_isSet() {
        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mPreferenceController.getCurrentPasswordQuality()).isEqualTo(
                NON_MATCHING_PASSWORD_QUALITY);
    }

    private class TestLockTypeBasePreferenceController extends LockTypeBasePreferenceController {

        TestLockTypeBasePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
            mUserManager = mMockUserManager;
        }

        @Override
        protected Intent activityToOpen() {
            return new Intent();
        }

        @Override
        protected int[] allowedPasswordQualities() {
            return new int[]{MATCHING_PASSWORD_QUALITY};
        }
    }
}
