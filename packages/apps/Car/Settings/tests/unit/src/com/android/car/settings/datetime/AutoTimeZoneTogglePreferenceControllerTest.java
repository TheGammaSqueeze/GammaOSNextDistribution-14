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

package com.android.car.settings.datetime;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;
import android.provider.Settings;
import android.widget.Toast;

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
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AutoTimeZoneTogglePreferenceControllerTest {
    private static final String TEST_RESTRICTION =
            android.os.UserManager.DISALLOW_CONFIG_DATE_TIME;

    private final LifecycleOwner mLifecycleOwner = new TestLifecycleOwner();
    private final Context mContext = spy(ApplicationProvider.getApplicationContext());

    private CarUiSwitchPreference mPreference;
    private AutoTimeZoneTogglePreferenceController mController;
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

        mPreference = new CarUiSwitchPreference(mContext);

        when(mContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(Toast.makeText(any(), anyString(), anyInt())).thenReturn(mMockToast);

        CarUxRestrictions carUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();
        mController = new AutoTimeZoneTogglePreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, carUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
    }

    @After
    @UiThreadTest
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void testRefreshUi_unchecked() {
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AUTO_TIME_ZONE, 0);
        mController.refreshUi();
        assertThat(mPreference.isChecked()).isFalse();
    }

    @Test
    public void testRefreshUi_checked() {
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.AUTO_TIME_ZONE, 1);
        mController.refreshUi();
        assertThat(mPreference.isChecked()).isTrue();
    }

    @Test
    public void testOnPreferenceChange_autoTimeZoneSet_shouldSendIntent() {
        mPreference.setChecked(true);
        mController.handlePreferenceChanged(mPreference, true);

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(1)).sendBroadcast(captor.capture());
        List<Intent> intentsFired = captor.getAllValues();
        assertThat(intentsFired.size()).isEqualTo(1);
        Intent intentFired = intentsFired.get(0);
        assertThat(intentFired.getAction()).isEqualTo(Intent.ACTION_TIME_CHANGED);
    }

    @Test
    public void testOnPreferenceChange_autoTimeZoneUnset_shouldSendIntent() {
        mPreference.setChecked(false);
        mController.handlePreferenceChanged(mPreference, false);

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext, times(1)).sendBroadcast(captor.capture());
        List<Intent> intentsFired = captor.getAllValues();
        assertThat(intentsFired.size()).isEqualTo(1);
        Intent intentFired = intentsFired.get(0);
        assertThat(intentFired.getAction()).isEqualTo(Intent.ACTION_TIME_CHANGED);
    }

    @Test
    public void testGetAvailabilityStatus_restricted_availableForViewing() {
        when(mMockUserManager.hasUserRestriction(TEST_RESTRICTION)).thenReturn(true);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_restricted_availableForViewing_zoneWrite() {
        when(mMockUserManager.hasUserRestriction(TEST_RESTRICTION)).thenReturn(true);

        mController.setAvailabilityStatusForZone("write");
        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_restricted_availableForViewing_zoneRead() {
        when(mMockUserManager.hasUserRestriction(TEST_RESTRICTION)).thenReturn(true);

        mController.setAvailabilityStatusForZone("read");
        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_restricted_availableForViewing_zoneHidden() {
        when(mMockUserManager.hasUserRestriction(TEST_RESTRICTION)).thenReturn(true);

        mController.setAvailabilityStatusForZone("hidden");
        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void testGetAvailabilityStatus_notRestricted_available() {
        when(mMockUserManager.hasUserRestriction(TEST_RESTRICTION)).thenReturn(false);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void testGetAvailabilityStatus_notRestricted_available_zoneWrite() {
        when(mMockUserManager.hasUserRestriction(TEST_RESTRICTION)).thenReturn(false);

        mController.setAvailabilityStatusForZone("write");
        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);
        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void testGetAvailabilityStatus_notRestricted_available_zoneRead() {
        when(mMockUserManager.hasUserRestriction(TEST_RESTRICTION)).thenReturn(false);

        mController.setAvailabilityStatusForZone("read");
        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void testGetAvailabilityStatus_notRestricted_available_zoneHidden() {
        when(mMockUserManager.hasUserRestriction(TEST_RESTRICTION)).thenReturn(false);

        mController.setAvailabilityStatusForZone("hidden");
        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    @UiThreadTest
    public void testDisabledClick_restrictedByUm_toast() {
        mockUserRestrictionSetByUm(true);
        when(mMockUserManager.hasUserRestriction(TEST_RESTRICTION)).thenReturn(true);
        mController.onCreate(mLifecycleOwner);

        mPreference.performClick();

        assertShowingBlockedToast();
    }

    @Test
    @UiThreadTest
    public void testDisabledClick_restrictedByDpm_dialog() {
        mockUserRestrictionSetByDpm(true);
        mController.onCreate(mLifecycleOwner);

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
