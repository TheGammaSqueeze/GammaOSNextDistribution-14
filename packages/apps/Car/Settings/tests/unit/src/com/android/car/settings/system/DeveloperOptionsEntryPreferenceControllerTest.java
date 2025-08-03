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
import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;
import android.provider.Settings;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.development.DevelopmentSettingsUtil;
import com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiPreference;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

@RunWith(AndroidJUnit4.class)
public class DeveloperOptionsEntryPreferenceControllerTest {
    private static final String TEST_RESTRICTION =
            android.os.UserManager.DISALLOW_DEBUGGING_FEATURES;

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private CarUiPreference mPreference;
    private DeveloperOptionsEntryPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;

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

        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(UserManager.class, withSettings().lenient())
                .startMocking();
        when(UserManager.get(mContext)).thenReturn(mMockUserManager);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(mMockUserManager.isAdminUser()).thenReturn(true);
        doNothing().when(mContext).startActivity(any());

        mPreference = new CarUiPreference(mContext);
        mPreference.setIntent(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        mPreferenceController = new DeveloperOptionsEntryPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @After
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void testGetAvailabilityStatus_devOptionsEnabled_isAvailable() {
        setDeveloperOptionsEnabled(true);
        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_devOptionsEnabled_isAvailable_zoneWrite() {
        mPreferenceController.setAvailabilityStatusForZone("write");
        setDeveloperOptionsEnabled(true);
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_devOptionsEnabled_isAvailable_zoneRead() {
        mPreferenceController.setAvailabilityStatusForZone("read");
        setDeveloperOptionsEnabled(true);
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void testGetAvailabilityStatus_devOptionsEnabled_isAvailable_zoneHidden() {
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        setDeveloperOptionsEnabled(true);
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_devOptionsDisabled_isUnavailable() {
        setDeveloperOptionsEnabled(false);
        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_devOptionsDisabled_isUnavailable_zoneWrite() {
        mPreferenceController.setAvailabilityStatusForZone("write");
        setDeveloperOptionsEnabled(false);
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_devOptionsDisabled_isUnavailable_zoneRead() {
        mPreferenceController.setAvailabilityStatusForZone("read");
        setDeveloperOptionsEnabled(false);
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_devOptionsDisabled_isUnavailable_zoneHidden() {
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        setDeveloperOptionsEnabled(false);
        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_devOptionsEnabled_restrictedByUm_isUnavailable() {
        setDeveloperOptionsEnabled(true);
        mockUserRestrictionSetByUm(true);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_devOptionsEnabled_isUnavailable_zoneWrite() {
        mPreferenceController.setAvailabilityStatusForZone("write");
        setDeveloperOptionsEnabled(true);
        mockUserRestrictionSetByUm(true);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_devOptionsEnabled_isUnavailable_zoneRead() {
        mPreferenceController.setAvailabilityStatusForZone("read");
        setDeveloperOptionsEnabled(true);
        mockUserRestrictionSetByUm(true);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_devOptionsEnabled_isUnavailable_zoneHidden() {
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        setDeveloperOptionsEnabled(true);
        mockUserRestrictionSetByUm(true);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_devOptionsEnabled_availableForViewing() {
        setDeveloperOptionsEnabled(true);
        mockUserRestrictionSetByDpm(true);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void testGetAvailabilityStatus_devOptionsEnabled_availableForViewing_zoneWrite() {
        mPreferenceController.setAvailabilityStatusForZone("write");
        setDeveloperOptionsEnabled(true);
        mockUserRestrictionSetByDpm(true);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void testGetAvailabilityStatus_devOptionsEnabled_availableForViewing_zoneRead() {
        mPreferenceController.setAvailabilityStatusForZone("read");
        setDeveloperOptionsEnabled(true);
        mockUserRestrictionSetByDpm(true);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void testGetAvailabilityStatus_devOptionsEnabled_availableForViewing_zoneHidden() {
        mPreferenceController.setAvailabilityStatusForZone("hidden");
        setDeveloperOptionsEnabled(true);
        mockUserRestrictionSetByDpm(true);

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void performClick_startsActivity() {
        setDeveloperOptionsEnabled(true);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreference.performClick();

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivity(captor.capture());

        Intent intent = captor.getValue();
        assertThat(intent.getAction()).isEqualTo(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
    }

    @Test
    @UiThreadTest
    public void disabledClick_restrictedByDpm_dialog() {
        setDeveloperOptionsEnabled(true);
        mockUserRestrictionSetByDpm(true);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreference.performClick();

        assertShowingDisabledByAdminDialog();
    }

    private void setDeveloperOptionsEnabled(boolean enabled) {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, enabled ? 1 : 0);
        DevelopmentSettingsUtil.setDevelopmentSettingsEnabled(mContext, enabled);
    }

    private void mockUserRestrictionSetByUm(boolean restricted) {
        when(mMockUserManager.hasBaseUserRestriction(eq(TEST_RESTRICTION), any()))
                .thenReturn(restricted);
        if (restricted) {
            when(mMockUserManager.hasUserRestriction(TEST_RESTRICTION)).thenReturn(restricted);
        }
    }

    private void mockUserRestrictionSetByDpm(boolean restricted) {
        when(mMockUserManager.hasBaseUserRestriction(eq(TEST_RESTRICTION), any()))
                .thenReturn(false);
        when(mMockUserManager.hasUserRestriction(TEST_RESTRICTION)).thenReturn(restricted);
    }

    private void assertShowingDisabledByAdminDialog() {
        verify(mFragmentController).showDialog(any(ActionDisabledByAdminDialogFragment.class),
                eq(DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG));
    }
}
