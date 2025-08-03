/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.car.settings.profiles;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserManager;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.TestLifecycleOwner;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;

@RunWith(AndroidJUnit4.class)
public class PermissionsPreferenceControllerTest {

    private static final String TEST_RESTRICTION = UserManager.DISALLOW_ADD_USER;
    private static final UserInfo TEST_USER = new UserInfo(/* id= */ 10,
            "TEST_USER_NAME", /* flags= */ 0);

    // Keep in sync with the number of elements in the list in PermissionsPreferenceController
    private static final int NUM_PERMISSIONS = 4;

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;
    private PermissionsPreferenceController mPreferenceController;
    private LogicalPreferenceGroup mPreferenceGroup;
    private MockitoSession mSession;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private UserManager mMockUserManager;

    @Before
    @UiThreadTest
    public void setUp() {
        mSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .mockStatic(UserManager.class, withSettings().lenient())
                .startMocking();

        ExtendedMockito.when(UserManager.get(mContext)).thenReturn(mMockUserManager);

        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);

        mPreferenceController = new PermissionsPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        mPreferenceController.setUserInfo(TEST_USER);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);
        mPreferenceController.onCreate(mLifecycleOwner);
    }

    @After
    @UiThreadTest
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void testRefreshUi_populatesGroup() {
        mPreferenceController.refreshUi();
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(NUM_PERMISSIONS);
    }

    @Test
    public void testRefreshUi_callingTwice_noDuplicates() {
        mPreferenceController.refreshUi();
        mPreferenceController.refreshUi();
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(NUM_PERMISSIONS);
    }

    @Test
    public void testRefreshUi_setToFalse() {
        SwitchPreference preference = getPreferenceForRestriction(mPreferenceGroup,
                TEST_RESTRICTION);
        preference.setChecked(true);
        when(mMockUserManager.hasUserRestriction(TEST_RESTRICTION, TEST_USER.getUserHandle()))
                .thenReturn(true);
        mPreferenceController.refreshUi();
        assertThat(preference.isChecked()).isFalse();
    }

    @Test
    public void testRefreshUi_setToTrue() {
        SwitchPreference preference = getPreferenceForRestriction(mPreferenceGroup,
                TEST_RESTRICTION);
        preference.setChecked(false);
        when(mMockUserManager.hasUserRestriction(TEST_RESTRICTION, TEST_USER.getUserHandle()))
                .thenReturn(false);
        mPreferenceController.refreshUi();
        assertThat(preference.isChecked()).isTrue();
    }

    @Test
    public void testOnPreferenceChange_changeToFalse() {
        SwitchPreference preference = getPreferenceForRestriction(mPreferenceGroup,
                TEST_RESTRICTION);
        preference.callChangeListener(true);
        verify(mMockUserManager)
                .setUserRestriction(TEST_RESTRICTION, false, TEST_USER.getUserHandle());
    }

    @Test
    public void testOnPreferenceChange_changeToTrue() {
        SwitchPreference preference = getPreferenceForRestriction(mPreferenceGroup,
                TEST_RESTRICTION);
        preference.callChangeListener(false);
        verify(mMockUserManager)
                .setUserRestriction(TEST_RESTRICTION, true, TEST_USER.getUserHandle());
    }

    private SwitchPreference getPreferenceForRestriction(
            PreferenceGroup preferenceGroup, String restriction) {
        for (int i = 0; i < preferenceGroup.getPreferenceCount(); i++) {
            SwitchPreference preference = (SwitchPreference) preferenceGroup.getPreference(i);
            if (restriction.equals(preference.getExtras().getString(
                    PermissionsPreferenceController.PERMISSION_TYPE_KEY))) {
                return preference;
            }
        }
        return null;
    }
}
