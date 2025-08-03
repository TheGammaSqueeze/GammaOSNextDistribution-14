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

import static android.content.pm.UserInfo.FLAG_ADMIN;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.UserInfo;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
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
import org.mockito.quality.Strictness;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class ProfilesListPreferenceControllerTest {

    private static final UserInfo TEST_CURRENT_USER = new UserInfo(/* id= */ 10,
            "TEST_USER_NAME", FLAG_ADMIN);
    private static final UserInfo TEST_OTHER_USER = new UserInfo(/* id= */ 11,
            "TEST_OTHER_NAME", /* flags= */ 0);

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;
    private ProfilesListPreferenceController mPreferenceController;
    private LogicalPreferenceGroup mPreferenceGroup;
    private MockitoSession mSession;

    @Mock
    private FragmentController mFragmentController;
    @Mock
    private ProfileHelper mProfileHelper;

    @Before
    @UiThreadTest
    public void setUp() {
        mSession = ExtendedMockito.mockitoSession()
                .initMocks(this)
                .mockStatic(ProfileHelper.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreferenceController = new ProfilesListPreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);

        mPreferenceController.getPreferenceProvider().setIncludeCurrentProfile(true);
        mPreferenceController.getPreferenceProvider().setIncludeGuest(true);
        ExtendedMockito.when(ProfileHelper.getInstance(mContext)).thenReturn(mProfileHelper);
        when(mProfileHelper.getCurrentProcessUserInfo()).thenReturn(TEST_CURRENT_USER);
        when(mProfileHelper.isCurrentProcessUser(TEST_CURRENT_USER)).thenReturn(true);
        when(mProfileHelper.getAllSwitchableProfiles()).thenReturn(
                Collections.singletonList(TEST_OTHER_USER));
        when(mProfileHelper.getAllLivingProfiles(any())).thenReturn(
                Collections.singletonList(TEST_OTHER_USER));
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
    @UiThreadTest
    public void testPreferencePerformClick_currentAdminUser_openNewFragment() {
        mPreferenceGroup.getPreference(0).performClick();

        verify(mFragmentController).launchFragment(any(ProfileDetailsFragment.class));
    }

    @Test
    @UiThreadTest
    public void testPreferencePerformClick_otherNonAdminUser_openNewFragment() {
        mPreferenceGroup.getPreference(1).performClick();

        verify(mFragmentController).launchFragment(any(ProfileDetailsPermissionsFragment.class));
    }

    @Test
    @UiThreadTest
    public void testPreferencePerformClick_guestUser_noAction() {
        mPreferenceGroup.getPreference(2).performClick();

        verify(mFragmentController, never()).launchFragment(any());
    }
}
