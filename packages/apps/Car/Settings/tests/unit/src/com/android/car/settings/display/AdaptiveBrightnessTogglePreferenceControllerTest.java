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
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;
import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.UserManager;
import android.provider.Settings;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiSwitchPreference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class AdaptiveBrightnessTogglePreferenceControllerTest {
    private static final String TEST_RESTRICTION =
            android.os.UserManager.DISALLOW_CONFIG_BRIGHTNESS;

    private Context mContext;
    private Resources mResources;
    private LifecycleOwner mLifecycleOwner;
    private AdaptiveBrightnessTogglePreferenceController mPreferenceController;
    private CarUiSwitchPreference mPreference;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private UserManager mMockUserManager;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mContext = spy(ApplicationProvider.getApplicationContext());
        mResources = spy(mContext.getResources());
        when(mContext.getResources()).thenReturn(mResources);
        mPreference = new CarUiSwitchPreference(mContext);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);

        CarUxRestrictions carUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mPreferenceController = new AdaptiveBrightnessTogglePreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, carUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);

        mPreferenceController.onCreate(mLifecycleOwner);
    }

    @Test
    public void onStart_registersContentObserver() {
        ContentResolver resolver = spy(mContext.getContentResolver());
        when(mContext.getContentResolver()).thenReturn(resolver);
        mPreferenceController.onStart(mLifecycleOwner);
        verify(resolver).registerContentObserver(
                eq(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE)), eq(false),
                any(ContentObserver.class));
    }

    @Test
    public void onStop_unregistersContentObserver() {
        ContentResolver resolver = spy(mContext.getContentResolver());
        when(mContext.getContentResolver()).thenReturn(resolver);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.onStop(mLifecycleOwner);
        verify(resolver).unregisterContentObserver(any(ContentObserver.class));
    }

    @Test
    public void testRefreshUi_manualMode_isNotChecked() {
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

        mPreferenceController.refreshUi();
        assertThat(mPreference.isChecked()).isFalse();
    }

    @Test
    public void testRefreshUi_automaticMode_isChecked() {
        Settings.System.putInt(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);

        mPreferenceController.refreshUi();
        assertThat(mPreference.isChecked()).isTrue();
    }

    @Test
    public void testHandlePreferenceChanged_setFalse() {
        mPreference.callChangeListener(false);
        int brightnessMode = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
        assertThat(brightnessMode).isEqualTo(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
    }

    @Test
    public void testHandlePreferenceChanged_setTrue() {
        mPreference.callChangeListener(true);
        int brightnessMode = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        assertThat(brightnessMode).isEqualTo(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
    }

    @Test
    public void getAvailabilityStatus_supportedd_notRestricted_available() {
        mockSupportsAdaptiveBrightness(true);
        mockUserRestrictionSetByDpm(false);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_supportedd_notRestricted_available_zoneWrite() {
        mockSupportsAdaptiveBrightness(true);
        mockUserRestrictionSetByDpm(false);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_supportedd_notRestricted_available_zoneRead() {
        mockSupportsAdaptiveBrightness(true);
        mockUserRestrictionSetByDpm(false);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_supportedd_notRestricted_available_zoneHidden() {
        mockSupportsAdaptiveBrightness(true);
        mockUserRestrictionSetByDpm(false);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_notSupported_unsupportOnDevice() {
        mockSupportsAdaptiveBrightness(false);

        assertThat(mPreferenceController.getAvailabilityStatus())
                .isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_notSupported_unsupportOnDevice_zoneWrite() {
        mockSupportsAdaptiveBrightness(false);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_notSupported_unsupportOnDevice_zoneRead() {
        mockSupportsAdaptiveBrightness(false);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_notSupported_unsupportOnDevice_zoneHidden() {
        mockSupportsAdaptiveBrightness(false);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_supported_restricted_availableForViewing() {
        mockSupportsAdaptiveBrightness(true);
        mockUserRestrictionSetByDpm(true);

        assertThat(mPreferenceController.getAvailabilityStatus())
                .isEqualTo(AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_supported_restricted_availableForViewing_zoneWrite() {
        mockSupportsAdaptiveBrightness(true);
        mockUserRestrictionSetByDpm(true);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_supported_restricted_availableForViewing_zoneRead() {
        mockSupportsAdaptiveBrightness(true);
        mockUserRestrictionSetByDpm(true);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_supported_restricted_availableForViewing_zoneHidden() {
        mockSupportsAdaptiveBrightness(true);
        mockUserRestrictionSetByDpm(true);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void disabledClick_restrictedByDpm_showActionDisabledByAdminDialog() {
        mockSupportsAdaptiveBrightness(true);
        mockUserRestrictionSetByDpm(true);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreference.performClick();

        assertShowingDisabledByAdminDialog();
    }

    // TODO: refactor to use EnterpriseTestUtils
    private void mockUserRestrictionSetByDpm(boolean restricted) {
        when(mMockUserManager.hasBaseUserRestriction(eq(TEST_RESTRICTION), any()))
                .thenReturn(false);
        when(mMockUserManager.hasUserRestriction(TEST_RESTRICTION)).thenReturn(restricted);
    }

    private void mockSupportsAdaptiveBrightness(boolean supported) {
        when(mResources.getBoolean(R.bool.config_automatic_brightness_available))
                .thenReturn(supported);
    }

    private void assertShowingDisabledByAdminDialog() {
        verify(mFragmentController).showDialog(any(ActionDisabledByAdminDialogFragment.class),
                eq(DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG));
    }
}
