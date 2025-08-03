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

package com.android.car.settings.accounts;

import static android.content.pm.UserInfo.FLAG_INITIALIZED;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_PROFILE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.profiles.ProfileHelper;
import com.android.car.settings.testutils.EnterpriseTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

// TODO(b/201465719) add unittest to show ActionDisabledByAdminDialog
@RunWith(AndroidJUnit4.class)
public class AccountGroupPreferenceControllerTest {
    private static final String TEST_USERNAME = "Test Username";
    private static final String TEST_RESTRICTION =
            android.os.UserManager.DISALLOW_MODIFY_ACCOUNTS;

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private LogicalPreferenceGroup mPreference;
    private CarUxRestrictions mCarUxRestrictions;
    private AccountGroupPreferenceController mController;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private ProfileHelper mMockProfileHelper;
    @Mock
    private UserManager mMockUserManager;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        when(mContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreference = new LogicalPreferenceGroup(mContext);
        mController = new TestAccountGroupPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
    }

    @Test
    public void getAvailabilityStatus_currentUser_noRestriction_available() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        when(mMockProfileHelper.canCurrentProcessModifyAccounts()).thenReturn(true);
        mController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_currentUser_noRestriction_available_zoneWrite() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        when(mMockProfileHelper.canCurrentProcessModifyAccounts()).thenReturn(true);
        mController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);
        mController.setAvailabilityStatusForZone("write");

        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_currentUser_noRestriction_available_zoneRead() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        when(mMockProfileHelper.canCurrentProcessModifyAccounts()).thenReturn(true);
        mController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);
        mController.setAvailabilityStatusForZone("read");

        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_currentUser_noRestriction_available_zoneHidden() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        when(mMockProfileHelper.canCurrentProcessModifyAccounts()).thenReturn(true);
        mController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);
        mController.setAvailabilityStatusForZone("hidden");

        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_notCurrentUser_notAvailable() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        mController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_notCurrentUser_notAvailable_zoneWrite() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        mController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);
        mController.setAvailabilityStatusForZone("write");

        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_notCurrentUser_notAvailable_zoneRead() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        mController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);
        mController.setAvailabilityStatusForZone("read");

        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_notCurrentUser_notAvailable_zoneHidden() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        mController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);
        mController.setAvailabilityStatusForZone("hidden");

        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_currentUser_restricedByUserType_notAvailable() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mController.setUserInfo(userInfo);
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_currentUser_restricedByUserType_notAvailable_zoneWrite() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mController.setUserInfo(userInfo);
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);
        mController.setAvailabilityStatusForZone("write");

        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_currentUser_restricedByUserType_notAvailable_zoneRead() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mController.setUserInfo(userInfo);
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);
        mController.setAvailabilityStatusForZone("read");

        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_currentUser_restricedByUserType_notAvailable_zoneHidden() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mController.setUserInfo(userInfo);
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);
        mController.setAvailabilityStatusForZone("hidden");

        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_currentUser_restrictedByUm_notAvailable() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mController.setUserInfo(userInfo);
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_currentUser_restrictedByUm_notAvailable_zoneWrite() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mController.setUserInfo(userInfo);
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);
        mController.setAvailabilityStatusForZone("write");

        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_currentUser_restrictedByUm_notAvailable_zoneRead() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mController.setUserInfo(userInfo);
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);
        mController.setAvailabilityStatusForZone("read");

        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_currentUser_restrictedByUm_notAvailable_zoneHidden() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mController.setUserInfo(userInfo);
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);
        mController.setAvailabilityStatusForZone("hidden");

        mController.onCreate(mLifecycleOwner);

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_currentUser_restricedByDpm_availableForViewing() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mController.setUserInfo(userInfo);
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(false);
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, false);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_currentUser_restricedByDpm_availableForViewing_zoneWrite() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mController.setUserInfo(userInfo);
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(false);
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, false);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_currentUser_restricedByDpm_availableForViewing_zoneRead() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mController.setUserInfo(userInfo);
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(false);
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, false);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                AVAILABLE_FOR_VIEWING);
    }

    @Test
    public void getAvailabilityStatus_currentUser_restricedByDpm_availableForViewing_zoneHidden() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mController.setUserInfo(userInfo);
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(false);
        EnterpriseTestUtils
                .mockUserRestrictionSetByUm(mMockUserManager, TEST_RESTRICTION, false);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_currentUser_restricedByUserTypeAndDpm_notAvailable() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mController.setUserInfo(userInfo);
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_currentUser_notAvailable_zoneWrite() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mController.setUserInfo(userInfo);
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("write");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_currentUser_notAvailable_zoneRead() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mController.setUserInfo(userInfo);
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("read");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    @Test
    public void getAvailabilityStatus_currentUser_notAvailable_zoneHidden() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_USERNAME, FLAG_INITIALIZED);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mController.setUserInfo(userInfo);
        when(mMockProfileHelper.isDemoOrGuest()).thenReturn(true);
        EnterpriseTestUtils
                .mockUserRestrictionSetByDpm(mMockUserManager, TEST_RESTRICTION, true);
        PreferenceControllerTestUtil.assignPreference(mController, mPreference);

        mController.onCreate(mLifecycleOwner);
        mController.setAvailabilityStatusForZone("hidden");

        PreferenceControllerTestUtil.assertAvailability(mController.getAvailabilityStatus(),
                DISABLED_FOR_PROFILE);
    }

    private class TestAccountGroupPreferenceController extends AccountGroupPreferenceController {

        TestAccountGroupPreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        ProfileHelper getProfileHelper() {
            return mMockProfileHelper;
        }
    }
}
