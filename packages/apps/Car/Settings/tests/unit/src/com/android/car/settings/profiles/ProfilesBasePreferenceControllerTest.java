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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.Preference;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ProfilesBasePreferenceControllerTest {

    private static final UserInfo TEST_CURRENT_USER = new UserInfo(/* id= */ 10,
            "TEST_USER_NAME", /* flags= */ 0);
    private static final UserInfo TEST_OTHER_USER = new UserInfo(/* id= */ 11,
            "TEST_OTHER_NAME", /* flags= */ 0);

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private LifecycleOwner mLifecycleOwner;
    private CarUxRestrictions mCarUxRestrictions;
    private TestProfilesBasePreferenceController mPreferenceController;
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
                .spyStatic(ProfileHelper.class)
                .strictness(Strictness.LENIENT)
                .startMocking();
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreferenceController = new TestProfilesBasePreferenceController(mContext,
                /* preferenceKey= */ "key", mFragmentController, mCarUxRestrictions);
        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);

        ExtendedMockito.doReturn(mProfileHelper)
                .when(() -> ProfileHelper.getInstance(eq(mContext)));
        when(mProfileHelper.getCurrentProcessUserInfo()).thenReturn(TEST_CURRENT_USER);
        when(mProfileHelper.isCurrentProcessUser(TEST_CURRENT_USER)).thenReturn(true);
        when(mProfileHelper.getAllSwitchableProfiles()).thenReturn(
                Collections.singletonList(TEST_OTHER_USER));
        when(mProfileHelper.getAllLivingProfiles(any())).thenReturn(
                Collections.singletonList(TEST_OTHER_USER));
    }

    @After
    @UiThreadTest
    public void tearDown() {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    @Test
    public void onCreate_registersOnUsersUpdateListener() {
        mPreferenceController.onCreate(mLifecycleOwner);

        verify(mContext).registerReceiver(eq(mPreferenceController.mProfileUpdateReceiver), any());
    }

    @Test
    public void onCreate_populatesUsers() {
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void onCreate_populatesOtherUsers() {
        mPreferenceController.getPreferenceProvider().setIncludeCurrentProfile(true);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(2);
    }

    @Test
    public void onCreate_populatesGuestUsers() {
        mPreferenceController.getPreferenceProvider().setIncludeCurrentProfile(true);
        mPreferenceController.getPreferenceProvider().setIncludeGuest(true);
        mPreferenceController.onCreate(mLifecycleOwner);

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(3);
    }

    @Test
    public void onDestroy_unregistersOnUsersUpdateListener() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);
        mPreferenceController.onDestroy(mLifecycleOwner);

        verify(mContext).unregisterReceiver(eq(mPreferenceController.mProfileUpdateReceiver));
    }

    @Test
    public void refreshUi_userChange_updatesGroup() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        // Store the list of previous Preferences.
        List<Preference> currentPreferences = new ArrayList<>();
        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            currentPreferences.add(mPreferenceGroup.getPreference(i));
        }

        // Mock a change so that other user becomes an admin.
        UserInfo adminOtherUser = new UserInfo(/* id= */ 11, "TEST_OTHER_NAME", FLAG_ADMIN);
        when(mProfileHelper.getAllSwitchableProfiles()).thenReturn(
                Collections.singletonList(adminOtherUser));
        when(mProfileHelper.getAllLivingProfiles(any())).thenReturn(
                Arrays.asList(TEST_OTHER_USER, adminOtherUser));

        mPreferenceController.refreshUi();

        List<Preference> newPreferences = new ArrayList<>();
        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            newPreferences.add(mPreferenceGroup.getPreference(i));
        }

        assertThat(newPreferences).containsNoneIn(currentPreferences);
    }

    @Test
    public void refreshUi_noChange_doesNotUpdateGroup() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        // Store the list of previous Preferences.
        List<Preference> currentPreferences = new ArrayList<>();
        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            currentPreferences.add(mPreferenceGroup.getPreference(i));
        }

        mPreferenceController.refreshUi();

        List<Preference> newPreferences = new ArrayList<>();
        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            newPreferences.add(mPreferenceGroup.getPreference(i));
        }

        assertThat(newPreferences).containsExactlyElementsIn(currentPreferences);
    }

    @Test
    public void onUsersUpdated_updatesGroup() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.onStart(mLifecycleOwner);

        // Store the list of previous Preferences.
        List<Preference> currentPreferences = new ArrayList<>();
        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            currentPreferences.add(mPreferenceGroup.getPreference(i));
        }

        // Mock a change so that other user becomes an admin.
        UserInfo adminOtherUser = new UserInfo(/* id= */ 11, "TEST_OTHER_NAME", FLAG_ADMIN);
        when(mProfileHelper.getAllSwitchableProfiles()).thenReturn(
                Collections.singletonList(adminOtherUser));
        when(mProfileHelper.getAllLivingProfiles(any())).thenReturn(
                Arrays.asList(TEST_OTHER_USER, adminOtherUser));

        mPreferenceController.mProfileUpdateReceiver.onReceive(mContext, new Intent());

        List<Preference> newPreferences = new ArrayList<>();
        for (int i = 0; i < mPreferenceGroup.getPreferenceCount(); i++) {
            newPreferences.add(mPreferenceGroup.getPreference(i));
        }

        assertThat(newPreferences).containsNoneIn(currentPreferences);
    }

    private static class TestProfilesBasePreferenceController extends
            ProfilesBasePreferenceController {

        TestProfilesBasePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected void profileClicked(UserInfo userInfo) {
        }
    }
}
