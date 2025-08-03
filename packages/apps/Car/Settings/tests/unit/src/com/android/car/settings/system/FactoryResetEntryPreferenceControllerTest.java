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

import static android.os.UserManager.DISALLOW_FACTORY_RESET;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.UserManager;
import android.widget.Toast;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiPreference;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

/** Unit test for {@link FactoryResetEntryPreferenceController}. */
@RunWith(AndroidJUnit4.class)
public class FactoryResetEntryPreferenceControllerTest {
    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private Preference mPreference;
    private FactoryResetEntryPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private MockitoSession mSession;
    private int mDefaultDemoMode;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private UserManager mMockUserManager;
    @Mock
    private Toast mMockToast;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(UserManager.class)
                .mockStatic(Toast.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        when(UserManager.get(mContext)).thenReturn(mMockUserManager);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);

        when(Toast.makeText(any(), anyString(), anyInt())).thenReturn(mMockToast);

        mPreference = new CarUiPreference(mContext);
        mPreferenceController = new FactoryResetEntryPreferenceController(mContext,
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
    public void getAvailabilityStatus_nonAdminUser_disabledForUser() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_nonAdminUser_disabledForUser_zoneWrite() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_nonAdminUser_disabledForUser_zoneRead() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_nonAdminUser_disabledForUser_zoneHidden() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_adminUser_unrestricted_available() {
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void getAvailabilityStatus_adminUser_unrestricted_available_zoneWrite() {
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void getAvailabilityStatus_adminUser_unrestricted_available_zoneRead() {
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void getAvailabilityStatus_adminUser_unrestricted_available_zoneHidden() {
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_adminUser_baseRestricted_notDpmRestricted_disabledForUser() {
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);
        addBaseUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_adminUser_notDpmRestricted_disabledForUser_zoneWrite() {
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);
        addBaseUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_adminUser_notDpmRestricted_disabledForUser_zoneRead() {
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);
        addBaseUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_adminUser_notDpmRestricted_disabledForUser_zoneHidden() {
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);
        addBaseUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_adminUser_baseRestricted_dpmRestricted_disabledForUser() {
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);
        addBaseUserRestriction(DISALLOW_FACTORY_RESET);
        addUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_adminUser_baseRestricted_disabledForUser_zoneWrite() {
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);
        addBaseUserRestriction(DISALLOW_FACTORY_RESET);
        addUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_adminUser_baseRestricted_disabledForUser_zoneRead() {
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);
        addBaseUserRestriction(DISALLOW_FACTORY_RESET);
        addUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_adminUser_baseRestricted_disabledForUser_zoneHidden() {
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);
        addBaseUserRestriction(DISALLOW_FACTORY_RESET);
        addUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_adminUser_notBaseRestricted_dpmRestricted_disabledForUser() {
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);
        addUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_adminUser_notBaseRestricted_disabledForUser_zoneWrite() {
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);
        addUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_adminUser_notBaseRestricted_disabledForUser_zoneRead() {
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);
        addUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_adminUser_notBaseRestricted_disabledForUser_zoneHidden() {
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);
        addUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_demoMode_demoUser_available() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ true);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void getAvailabilityStatus_demoMode_demoUser_available_zoneWrite() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ true);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE);
        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void getAvailabilityStatus_demoMode_demoUser_available_zoneRead() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ true);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void getAvailabilityStatus_demoMode_demoUser_available_zoneHidden() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ true);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_demoMode_demoUser_baseRestricted_disabledForUser() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ true);
        addBaseUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_demoMode_demoUser_disabledForUser_zoneWrite() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ true);
        addBaseUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_demoMode_demoUser_disabledForUser_zoneRead() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ true);
        addBaseUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_demoMode_demoUser_disabledForUser_zoneHidden() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ true);
        addBaseUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_demoMode_demoUser_dpmRestricted_disabledForUser() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ true);
        addUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_demoMode_demoUser_dpmRestricted_disabledForUser_zoneWrite() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ true);
        addUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_demoMode_demoUser_dpmRestricted_disabledForUser_zoneRead() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ true);
        addUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), AVAILABLE_FOR_VIEWING);
        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void getAvailabilityStatus_demoMode_demoUser_dpmRestricted_disabledForUser_zoneHidden() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ true);
        addUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(
                mPreferenceController.getAvailabilityStatus(), CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    @UiThreadTest
    public void performClick_showsBlockedToast_nonAdminUser() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreference.performClick();

        assertShowingBlockedToast();
    }

    @Test
    @UiThreadTest
    public void performClick_triggersAction_adminUser_unrestricted() {
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreference.performClick();

        assertNoToastAndNoDialog();
    }

    @Test
    @UiThreadTest
    public void performClick_showsBlockedToast_adminUser_baseRestricted_notDpmRestricted() {
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);
        addBaseUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreference.performClick();

        assertShowingBlockedToast();
    }

    @Test
    @UiThreadTest
    public void performClick_showsBlockedToast_adminUser_baseRestricted_dpmRestricted() {
        switchToUser(/* isAdmin= */ true, /* isDemo= */ false);
        addBaseUserRestriction(DISALLOW_FACTORY_RESET);
        addUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreference.performClick();

        assertShowingBlockedToast();
    }

    @Test
    public void performClick_showsBlockedDialog_adminUser_notBaseRestricted_dpmRestricted() {
        switchToUser(/* isAdmin= */ true, /* false= */ false);
        addUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreference.performClick();

        assertShowingDisabledByAdminDialog();
    }

    @Test
    @UiThreadTest
    public void performClick_triggersAction_demoUser_unrestricted() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ true);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreference.performClick();

        assertNoToastAndNoDialog();
    }

    @Test
    @UiThreadTest
    public void performClick_showsBlockedToast_demoUser_baseRestricted() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ true);
        addBaseUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreference.performClick();

        assertShowingBlockedToast();
    }

    @Test
    public void performClick_showsBlockedDialog_demoUser_dpmRestricted() {
        switchToUser(/* isAdmin= */ false, /* isDemo= */ true);
        addUserRestriction(DISALLOW_FACTORY_RESET);
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreference.performClick();

        assertShowingDisabledByAdminDialog();
    }

    private void switchToUser(boolean isAdmin, boolean isDemo) {
        when(mMockUserManager.isAdminUser()).thenReturn(isAdmin);
        when(mMockUserManager.isDemoUser()).thenReturn(isDemo);
        when(UserManager.isDeviceInDemoMode(mContext)).thenReturn(isDemo);
    }

    private void addBaseUserRestriction(String restriction) {
        when(mMockUserManager.hasBaseUserRestriction(
                eq(restriction), any())).thenReturn(true);
    }

    private void addUserRestriction(String restriction) {
        when(mMockUserManager.hasUserRestriction(restriction)).thenReturn(true);
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

    private void assertNoToastAndNoDialog() {
        ExtendedMockito.verify(
                () -> Toast.makeText(any(), anyString(), anyInt()), never());
        verify(mMockToast, never()).show();
        verify(mFragmentController, never())
                .showDialog(any(ActionDisabledByAdminDialogFragment.class), anyString());
    }
}
