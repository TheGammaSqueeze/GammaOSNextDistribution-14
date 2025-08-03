/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.settings.accounts;

import static android.app.Activity.RESULT_OK;

import static com.android.car.settings.accounts.AddAccountPreferenceController.NEW_USER_DISCLAIMER_REQUEST;
import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_PROFILE;
import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.ActivityResultCallback;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment;
import com.android.car.settings.profiles.ProfileHelper;
import com.android.car.settings.testutils.EnterpriseTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.car.ui.preference.CarUiPreference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class AddAccountPreferenceControllerTest {
    private static final String TEST_RESTRICTION =
            android.os.UserManager.DISALLOW_MODIFY_ACCOUNTS;

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private CarUiPreference mPreference;
    private CarUxRestrictions mCarUxRestrictions;
    private AddAccountPreferenceController mController;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private ProfileHelper mMockProfileHelper;
    @Mock
    private AccountTypesHelper mMockAccountTypesHelper;
    @Mock
    private UserManager mMockUserManager;
    @Mock
    private DevicePolicyManager mMockDpm;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreference = new CarUiPreference(mContext);
        mController = new TestAddAccountPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        when(mContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(mContext.getSystemService(DevicePolicyManager.class)).thenReturn(mMockDpm);
        doNothing().when(mContext).startActivity(any());
    }

    @Test
    public void cannotModifyUsers_restrictedByDpm_addAccountButtonShouldBeAvailableForViewing() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void cannotModifyUsers_addAccountButtonShouldBeAvailableForViewing_zoneWrite() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void cannotModifyUsers_addAccountButtonShouldBeAvailableForViewing_zoneRead() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void cannotModifyUsers_addAccountButtonShouldBeAvailableForViewing_zoneHidden() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void newUserDisclaimerUnackowledged_addAccountButtonShouldBeAvailableForViewing() {
        when(mMockDpm.isNewUserDisclaimerAcknowledged()).thenReturn(false);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void newUserDisclaimerUnackowledged_addAccountButtonBeAvailableForViewing_zoneWrite() {
        when(mMockDpm.isNewUserDisclaimerAcknowledged()).thenReturn(false);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void newUserDisclaimerUnackowledged_addAccountButtonBeAvailableForViewing_zoneRead() {
        when(mMockDpm.isNewUserDisclaimerAcknowledged()).thenReturn(false);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void newUserDisclaimerUnackowledged_addAccountButtonBeAvailableForViewing_zoneHidden() {
        when(mMockDpm.isNewUserDisclaimerAcknowledged()).thenReturn(false);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void cannotModifyUsers_restrictedByUm_addAccountButtonShouldBeDisabled() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void cannotModifyUsers_restrictedByUm_addAccountButtonShouldBeDisabled_zoneWrite() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void cannotModifyUsers_restrictedByUm_addAccountButtonShouldBeDisabled_zoneRead() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void cannotModifyUsers_restrictedByUm_addAccountButtonShouldBeDisabled_zoneHidden() {
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void cannotModifyUsers_demoOrGuestAndRestricedByDpm_addAccountButtonShouldBeDisabled() {
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void cannotModifyUsers_addAccountButtonShouldBeDisabled_zoneWrite() {
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void cannotModifyUsers_addAccountButtonShouldBeDisabled_zoneRead() {
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void cannotModifyUsers_addAccountButtonShouldBeDisabled_zoneHidden() {
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void cannotModifyUsers_demoOrGuestUser_addAccountButtonShouldBeDisabled() {
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void cannotModifyUsers_demoOrGuestUser_addAccountButtonShouldBeDisabled_zoneWrite() {
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void cannotModifyUsers_demoOrGuestUser_addAccountButtonShouldBeDisabled_zoneRead() {
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void cannotModifyUsers_demoOrGuestUser_addAccountButtonShouldBeDisabled_zoneHidden() {
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void canModifyUsers_addAccountButtonShouldBeAvailable() {
        when(mMockProfileHelper.canCurrentProcessModifyAccounts()).thenReturn(true);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void canModifyUsers_addAccountButtonShouldBeAvailable_zoneWrite() {
        when(mMockProfileHelper.canCurrentProcessModifyAccounts()).thenReturn(true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);
    }

    @Test
    public void canModifyUsers_addAccountButtonShouldBeAvailable_zoneRead() {
        when(mMockProfileHelper.canCurrentProcessModifyAccounts()).thenReturn(true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void canModifyUsers_addAccountButtonShouldBeAvailable_zoneHidden() {
        when(mMockProfileHelper.canCurrentProcessModifyAccounts()).thenReturn(true);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    @UiThreadTest
    public void clickAddAccountButton_shouldOpenChooseAccountFragment() {
        when(mMockProfileHelper.canCurrentProcessModifyAccounts()).thenReturn(true);
        when(mMockDpm.isNewUserDisclaimerAcknowledged()).thenReturn(true);

        mController.onCreate(mLifecycleOwner);
        mPreference.performClick();

        verify(mFragmentController).launchFragment(any(ChooseAccountFragment.class));
    }

    @Test
    public void clickAddAccountButton_shouldNotOpenChooseAccountFragmentWhenOneTypeAndUnmanaged() {
        when(mMockProfileHelper.canCurrentProcessModifyAccounts()).thenReturn(true);
        Set<String> accountSet = new HashSet<>();
        accountSet.add("TEST_ACCOUNT_TYPE_1");
        when(mMockAccountTypesHelper.getAuthorizedAccountTypes()).thenReturn(accountSet);

        mController.onCreate(mLifecycleOwner);
        mPreference.performClick();

        ArgumentCaptor<Intent> intentArgumentCaptor = ArgumentCaptor.forClass(
                Intent.class);
        verify(mContext).startActivity(intentArgumentCaptor.capture());

        Intent intent = intentArgumentCaptor.getValue();
        assertThat(intent.getComponent().getClassName()).isEqualTo(
                AddAccountActivity.class.getName());
    }

    @Test
    @UiThreadTest
    public void clickAddAccountButton_shouldOpenChooseAccountFragmentWhenOneTypeAndManaged() {
        when(mMockDpm.isDeviceManaged()).thenReturn(true);
        when(mMockDpm.isNewUserDisclaimerAcknowledged()).thenReturn(true);
        when(mMockProfileHelper.canCurrentProcessModifyAccounts()).thenReturn(true);
        Set<String> accountSet = new HashSet<>();
        accountSet.add("TEST_ACCOUNT_TYPE_1");
        when(mMockAccountTypesHelper.getAuthorizedAccountTypes()).thenReturn(accountSet);

        mController.onCreate(mLifecycleOwner);
        mPreference.performClick();

        verify(mFragmentController).launchFragment(any(ChooseAccountFragment.class));
    }

    @Test
    @UiThreadTest
    public void clickAddAccountButton_shouldOpenChooseAccountFragmentWhenTwoTypes() {
        when(mMockProfileHelper.canCurrentProcessModifyAccounts()).thenReturn(true);
        Set<String> accountSet = new HashSet<>();
        accountSet.add("TEST_ACCOUNT_TYPE_1");
        accountSet.add("TEST_ACCOUNT_TYPE_2");
        when(mMockAccountTypesHelper.getAuthorizedAccountTypes()).thenReturn(accountSet);

        mController.onCreate(mLifecycleOwner);
        mPreference.performClick();

        verify(mFragmentController).launchFragment(any(ChooseAccountFragment.class));
    }

    @Test
    @UiThreadTest
    public void disabledClick_restrictedByDpm_dialog() {
        when(mMockProfileHelper.canCurrentProcessModifyAccounts()).thenReturn(false);
        when(mMockDpm.isNewUserDisclaimerAcknowledged()).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        mController.onCreate(mLifecycleOwner);

        mPreference.performClick();

        assertShowingDisabledByAdminDialog();
    }

    @Test
    @UiThreadTest
    public void disabledClick_newUserDisclaimerUnacknowledged_launchDisclaimer() {
        when(mMockDpm.isDeviceManaged()).thenReturn(true);
        when(mMockDpm.isNewUserDisclaimerAcknowledged()).thenReturn(false);
        mController.onCreate(mLifecycleOwner);

        mPreference.performClick();

        assertShowingNewUserDisclaimerActivity();
    }

    @Test
    @UiThreadTest
    public void processActivityResult_newUserDisclaimer_resultOk_chooseAccountActivity() {
        when(mMockDpm.isDeviceManaged()).thenReturn(true);
        when(mMockDpm.isNewUserDisclaimerAcknowledged()).thenReturn(true);
        mController.onCreate(mLifecycleOwner);

        mController.processActivityResult(NEW_USER_DISCLAIMER_REQUEST, RESULT_OK,
                /* data= */ null);

        verify(mFragmentController).launchFragment(any(ChooseAccountFragment.class));
    }

    private void assertShowingDisabledByAdminDialog() {
        verify(mFragmentController).showDialog(any(ActionDisabledByAdminDialogFragment.class),
                eq(DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG));
    }

    private void assertShowingNewUserDisclaimerActivity() {
        verify(mFragmentController).startActivityForResult(any(Intent.class),
                eq(NEW_USER_DISCLAIMER_REQUEST), any(ActivityResultCallback.class));
    }

    private class TestAddAccountPreferenceController extends AddAccountPreferenceController {

        TestAddAccountPreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController,
                CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        ProfileHelper getProfileHelper() {
            return mMockProfileHelper;
        }

        @Override
        AccountTypesHelper getAccountTypesHelper() {
            return mMockAccountTypesHelper;
        }
    }
}
