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

import android.annotation.Nullable;
import android.car.drivingstate.CarUxRestrictions;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseTestSettingsFragment;
import com.android.car.settings.testutils.DualPaneTestActivity;
import com.android.car.settings.testutils.EmptySettingsFragment;
import com.android.car.settings.testutils.TestTopLevelMenuFragment;
import com.android.car.ui.toolbar.NavButtonMode;
import com.android.car.ui.toolbar.ToolbarController;

import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

public class DualPaneBaseCarSettingsActivityTest
        extends BaseCarSettingsActivityTestCase<DualPaneTestActivity> {

    @Rule
    public ActivityTestRule<DualPaneTestActivity> mActivityTestRule =
            new ActivityTestRule<>(DualPaneTestActivity.class);

    @Override
    ActivityTestRule<DualPaneTestActivity> getActivityTestRule() {
        return mActivityTestRule;
    }

    @Test
    public void onPreferenceStartFragment_launchesFragment() throws Throwable {
        Preference pref = new Preference(mContext);
        pref.setFragment(BaseTestSettingsFragment.class.getName());

        mActivityTestRule.runOnUiThread(() ->
                mActivity.onPreferenceStartFragment(/* caller= */ null, pref));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mActivity.getSupportFragmentManager().findFragmentById(
                R.id.fragment_container)).isInstanceOf(BaseTestSettingsFragment.class);
    }

    @Test
    public void onUxRestrictionsChanged_topFragmentInBackStackHasUpdatedUxRestrictions()
            throws Throwable {
        CarUxRestrictions oldUxRestrictions = new CarUxRestrictions.Builder(
                /* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE,
                /* timestamp= */ 0
        ).build();

        CarUxRestrictions newUxRestrictions = new CarUxRestrictions.Builder(
                /* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP,
                /* timestamp= */ 0
        ).build();

        AtomicReference<BaseTestSettingsFragment> fragmentA = new AtomicReference<>();
        AtomicReference<BaseTestSettingsFragment> fragmentB = new AtomicReference<>();

        mActivityTestRule.runOnUiThread(() -> {
            fragmentA.set(new BaseTestSettingsFragment());
            fragmentB.set(new BaseTestSettingsFragment());
            mActivity.launchFragment(fragmentA.get());
            mActivity.onUxRestrictionsChanged(oldUxRestrictions);
            mActivity.launchFragment(fragmentB.get());
            mActivity.onUxRestrictionsChanged(newUxRestrictions);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(
                fragmentB.get().getUxRestrictions().isSameRestrictions(newUxRestrictions)).isTrue();
    }

    @Test
    public void onBackStackChanged_uxRestrictionsChanged_currentFragmentHasUpdatedUxRestrictions()
            throws Throwable {
        CarUxRestrictions oldUxRestrictions = new CarUxRestrictions.Builder(
                /* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE,
                /* timestamp= */ 0
        ).build();

        CarUxRestrictions newUxRestrictions = new CarUxRestrictions.Builder(
                /* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP,
                /* timestamp= */ 0
        ).build();

        AtomicReference<BaseTestSettingsFragment> fragmentA = new AtomicReference<>();
        AtomicReference<BaseTestSettingsFragment> fragmentB = new AtomicReference<>();

        mActivityTestRule.runOnUiThread(() -> {
            fragmentA.set(new BaseTestSettingsFragment());
            fragmentB.set(new BaseTestSettingsFragment());
            mActivity.launchFragment(fragmentA.get());
            mActivity.onUxRestrictionsChanged(oldUxRestrictions);
            mActivity.launchFragment(fragmentB.get());
            mActivity.onUxRestrictionsChanged(newUxRestrictions);
            mActivity.goBack();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(
                fragmentA.get().getUxRestrictions().isSameRestrictions(newUxRestrictions)).isTrue();
    }

    @Test
    public void onBackStackChanged_toolbarUpdated() throws Throwable {
        ToolbarController toolbar = mActivity.getToolbar();

        mActivityTestRule.runOnUiThread(() -> {
            BaseTestSettingsFragment fragment1 = new BaseTestSettingsFragment();
            mActivity.launchFragment(fragment1);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(toolbar.getNavButtonMode()).isEquivalentAccordingToCompareTo(
                NavButtonMode.DISABLED);

        mActivityTestRule.runOnUiThread(() -> {
            BaseTestSettingsFragment fragment2 = new BaseTestSettingsFragment();
            mActivity.launchFragment(fragment2);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(toolbar.getNavButtonMode()).isEqualTo(NavButtonMode.BACK);

        mActivityTestRule.runOnUiThread(() -> mActivity.goBack());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(toolbar.getNavButtonMode()).isEquivalentAccordingToCompareTo(
                NavButtonMode.DISABLED);
    }

    @Test
    public void onActivityCreated_noFragment_topLevelMenuFocused() throws Throwable {
        assertThat(mActivity.findViewById(R.id.top_level_menu).hasFocus()).isTrue();
    }

    @Test
    public void onActivityCreated_homepage_topLevelMenuFocused() throws Throwable {
        ActivityTestRule<TestDualPaneHomepageActivity> activityTestRule =
                new ActivityTestRule<>(TestDualPaneHomepageActivity.class);
        activityTestRule.launchActivity(null);
        mActivity = activityTestRule.getActivity();
        mFragmentManager = activityTestRule.getActivity().getSupportFragmentManager();

        assertThat(mActivity.findViewById(R.id.top_level_menu).hasFocus()).isTrue();
    }

    @Test
    public void onActivityCreated_hasFragment_contentFocused() throws Throwable {
        ActivityTestRule<TestDualPaneFragmentActivity> activityTestRule =
                new ActivityTestRule<>(TestDualPaneFragmentActivity.class);
        activityTestRule.launchActivity(null);
        mActivity = activityTestRule.getActivity();
        mFragmentManager = activityTestRule.getActivity().getSupportFragmentManager();

        assertThat(getCurrentFragment().getView().hasFocus()).isTrue();
    }

    @Test
    public void onTopLevelPreferenceTapped_focusUpdated() throws Throwable {
        setUpTopLevelTestFragment();
        mActivityTestRule.runOnUiThread(() ->
                mTopLevelFragment.getPreferenceScreen().getPreference(0).performClick());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertThat(getCurrentFragment().getView().hasFocus()).isTrue();
    }

    @Test
    public void onFragmentLaunched_maintainContentFocus() throws Throwable {
        mActivityTestRule.runOnUiThread(() -> {
            BaseTestSettingsFragment fragment = new BaseTestSettingsFragment();
            mActivity.launchFragment(fragment);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertThat(getCurrentFragment().getView().hasFocus()).isTrue();
        mActivityTestRule.runOnUiThread(() ->
                getCurrentFragment().getPreferenceScreen().getPreference(0).performClick());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertThat(getCurrentFragment().getView().hasFocus()).isTrue();
    }

    @Test
    public void onBack_maintainContentFocus() throws Throwable {
        mActivityTestRule.runOnUiThread(() -> {
            BaseTestSettingsFragment fragment1 = new BaseTestSettingsFragment();
            mActivity.launchFragment(fragment1);
            BaseTestSettingsFragment fragment2 = new BaseTestSettingsFragment();
            mActivity.launchFragment(fragment2);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertThat(getCurrentFragment().getView().hasFocus()).isTrue();
        mActivityTestRule.runOnUiThread(() -> mActivity.goBack());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertThat(getCurrentFragment().getView().hasFocus()).isTrue();
    }

    @Test
    public void onPreferenceDisabled_maintainContentFocus() throws Throwable {
        mActivityTestRule.runOnUiThread(() -> {
            BaseTestSettingsFragment fragment = new BaseTestSettingsFragment();
            mActivity.launchFragment(fragment);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertThat(getCurrentFragment().getView().hasFocus()).isTrue();
        mActivityTestRule.runOnUiThread(() ->
                getCurrentFragment().getPreferenceScreen().getPreference(0).setEnabled(false));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertThat(getCurrentFragment().getView().hasFocus()).isTrue();
    }

    @Test
    public void onFragmentLaunched_noFocusableElements_parkingViewFocused() throws Throwable {
        mActivityTestRule.runOnUiThread(() -> {
            EmptySettingsFragment fragment = new EmptySettingsFragment();
            mActivity.launchFragment(fragment);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(mActivity.findViewById(R.id.settings_focus_parking_view).isFocused()).isTrue();
    }

    private void setUpTopLevelTestFragment() throws Throwable {
        String topLevelMenuTag = "top_level_menu";
        mActivityTestRule.runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(R.id.top_level_menu, new TestTopLevelMenuFragment(), topLevelMenuTag)
                    .commitNow();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mTopLevelFragment = (TopLevelMenuFragment) mFragmentManager.findFragmentByTag(
                topLevelMenuTag);
    }

    public static class TestDualPaneHomepageActivity extends DualPaneTestActivity {
        @Nullable
        @Override
        protected Fragment getInitialFragment() {
            return new BaseTestSettingsFragment();
        }

        @Override
        protected boolean shouldFocusContentOnLaunch() {
            return false;
        }
    }

    public static class TestDualPaneFragmentActivity extends DualPaneTestActivity {
        @Nullable
        @Override
        protected Fragment getInitialFragment() {
            return new BaseTestSettingsFragment();
        }
    }
}
