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

package com.android.car.settings.common;

import static com.google.common.truth.Truth.assertThat;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.DualPaneTestActivity;
import com.android.car.settings.testutils.TestSettingsFragment1;
import com.android.car.settings.testutils.TestSettingsFragment2;
import com.android.car.settings.testutils.TestTopLevelMenuFragment;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class TopLevelMenuFragmentTest {

    private TopLevelMenuFragment mFragment;
    private DualPaneTestActivity mActivity;
    private FragmentManager mFragmentManager;

    @Rule
    public ActivityTestRule<DualPaneTestActivity> mActivityTestRule =
            new ActivityTestRule<>(DualPaneTestActivity.class);

    @Before
    public void setUp() throws Throwable {
        MockitoAnnotations.initMocks(this);
        mActivity = mActivityTestRule.getActivity();
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();
        setUpFragment();
    }


    @Test
    public void onPreferenceTapped_launchesFragment() throws Throwable {
        mActivityTestRule.runOnUiThread(() ->
                mFragment.getPreferenceScreen().getPreference(0).performClick());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertThat(getCurrentFragment().getClass()).isEqualTo(TestSettingsFragment1.class);
    }

    @Test
    public void onPreferenceTapped_preferenceHighlightUpdated() throws Throwable {
        mActivityTestRule.runOnUiThread(() ->
                mFragment.getPreferenceScreen().getPreference(0).performClick());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertThat(mFragment.getSelectedPreferenceKey()).isEqualTo("pk_test_fragment_1");
    }

    @Test
    public void onSamePreferenceTapped_noDuplicateEntry() throws Throwable {
        mActivityTestRule.runOnUiThread(() -> {
            mFragment.getPreferenceScreen().getPreference(0).performClick();
            mFragment.getPreferenceScreen().getPreference(0).performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertThat(getCurrentFragment().getClass()).isEqualTo(TestSettingsFragment1.class);
        assertThat(mFragmentManager.getBackStackEntryCount()).isEqualTo(1);
    }

    @Test
    public void onDifferentPreferenceTapped_clearsBackStack() throws Throwable {
        mActivityTestRule.runOnUiThread(() -> {
            mFragment.getPreferenceScreen().getPreference(0).performClick();
            mFragment.getPreferenceScreen().getPreference(1).performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertThat(getCurrentFragment().getClass()).isEqualTo(TestSettingsFragment2.class);
        assertThat(mFragmentManager.getBackStackEntryCount()).isEqualTo(1);
    }

    private void setUpFragment() throws Throwable {
        String topLevelMenuTag = "top_level_menu";
        mActivityTestRule.runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(R.id.top_level_menu, new TestTopLevelMenuFragment(), topLevelMenuTag)
                    .commitNow();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mFragment = (TopLevelMenuFragment) mFragmentManager.findFragmentByTag(topLevelMenuTag);
    }

    private Fragment getCurrentFragment() {
        return mFragmentManager.findFragmentById(R.id.fragment_container);
    }
}
