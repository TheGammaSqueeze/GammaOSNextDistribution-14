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

package com.android.car.settings.profiles;

import static com.android.car.settings.common.TopLevelMenuFragment.FRAGMENT_MENU_PREFERENCE_KEY;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class ProfilesEntryPreferenceControllerTest {
    private static final String PREFERENCE_KEY = "key";

    private Context mContext = ApplicationProvider.getApplicationContext();
    private Preference mPreference;
    private ProfilesEntryPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;

    @Mock
    private FragmentController mFragmentController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        mPreference = new Preference(mContext);
        mPreferenceController = new ProfilesEntryPreferenceController(mContext,
                PREFERENCE_KEY, mFragmentController, mCarUxRestrictions);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreference);
    }

    @Test
    @UiThreadTest
    public void handlePreferenceClicked_returnsTrue() {
        assertThat(mPreferenceController.handlePreferenceClicked(mPreference)).isTrue();
    }

    @Test
    @UiThreadTest
    public void handlePreferenceClicked_launchesProfileDetailsFragment() {
        mPreferenceController.handlePreferenceClicked(mPreference);
        verify(mFragmentController).launchFragment(any(ProfileDetailsFragment.class));
    }

    @Test
    @UiThreadTest
    public void handlePreferenceClicked_fragmentHasKeyArgument() {
        mPreferenceController.handlePreferenceClicked(mPreference);
        ArgumentCaptor<Fragment> fragmentArgumentCaptor = ArgumentCaptor.forClass(Fragment.class);
        verify(mFragmentController).launchFragment(fragmentArgumentCaptor.capture());

        Fragment fragment = fragmentArgumentCaptor.getValue();
        assertThat(fragment.getArguments().getString(FRAGMENT_MENU_PREFERENCE_KEY)).isEqualTo(
                PREFERENCE_KEY);
    }
}
