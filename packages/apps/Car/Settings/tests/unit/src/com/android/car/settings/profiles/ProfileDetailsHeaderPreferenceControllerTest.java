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

package com.android.car.settings.profiles;

import static android.content.pm.UserInfo.FLAG_ADMIN;
import static android.content.pm.UserInfo.FLAG_INITIALIZED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.UserInfo;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.testutils.ResourceTestUtils;
import com.android.car.settings.testutils.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class ProfileDetailsHeaderPreferenceControllerTest {
    private static final String TEST_PROFILE_NAME = "Test Profile Name";

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private Preference mPreference;
    private CarUxRestrictions mCarUxRestrictions;
    private ProfileDetailsHeaderPreferenceController mPreferenceController;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private ProfileHelper mMockProfileHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreference = new Preference(mContext);
        mPreferenceController = new ProfileDetailsHeaderPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        mPreferenceController.setProfileHelper(mMockProfileHelper);
    }

    @Test
    public void onCreate_elementHasTitle() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME, /* flags= */ 0);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mPreference.getTitle()).isNotNull();
    }

    @Test
    public void onCreate_profileNotSetup_setsSummary() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME, /* flags= */ 0);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mPreference.getSummary()).isEqualTo(
                ResourceTestUtils.getString(mContext, "user_summary_not_set_up"));
    }

    @Test
    public void onCreate_profileSetup_noSummary() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME, FLAG_INITIALIZED);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mPreference.getSummary()).isNull();
    }

    @Test
    public void onCreate_isAdmin_notCurrentProfile() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME,
                FLAG_INITIALIZED | FLAG_ADMIN);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(false);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mPreference.getSummary()).isEqualTo(
                ResourceTestUtils.getString(mContext, "user_admin"));
    }

    @Test
    public void onCreate_isAdmin_currentProfile() {
        UserInfo userInfo = new UserInfo(/* id= */ 10, TEST_PROFILE_NAME,
                FLAG_INITIALIZED | FLAG_ADMIN);
        when(mMockProfileHelper.isCurrentProcessUser(userInfo)).thenReturn(true);
        mPreferenceController.setUserInfo(userInfo);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
        mPreferenceController.onCreate(mLifecycleOwner);
        assertThat(mPreference.getSummary()).isEqualTo(
                ResourceTestUtils.getString(mContext, "signed_in_admin_user"));
    }
}
