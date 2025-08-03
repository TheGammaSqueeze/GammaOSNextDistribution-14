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
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.testutils.RootTestSettingsFragment;
import com.android.car.settings.testutils.SinglePaneTestActivity;
import com.android.car.ui.toolbar.NavButtonMode;
import com.android.car.ui.toolbar.ToolbarController;

import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

public class SinglePaneSettingsFragmentTest
        extends SettingsFragmentTestCase<SinglePaneTestActivity> {

    @Rule
    public ActivityTestRule<SinglePaneTestActivity> mActivityTestRule =
            new ActivityTestRule<>(SinglePaneTestActivity.class);

    @Override
    ActivityTestRule<SinglePaneTestActivity> getActivityTestRule() {
        return mActivityTestRule;
    }

    @Test
    public void onActivityCreated_hasAppIconIfRoot() throws Throwable {
        AtomicReference<Fragment> fragment = new AtomicReference<>();
        getActivityTestRule().runOnUiThread(() -> {
            fragment.set(new RootTestSettingsFragment());
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        setUpFragment(fragment.get());

        ToolbarController toolbar = mActivity.getToolbar();

        assertThat(toolbar.getNavButtonMode()).isEquivalentAccordingToCompareTo(
                NavButtonMode.DISABLED);
    }

    @Test
    public void onActivityCreated_hasBackArrowIconIfNotRoot() {
        ToolbarController toolbar = mActivity.getToolbar();

        assertThat(toolbar.getNavButtonMode()).isEquivalentAccordingToCompareTo(
                NavButtonMode.BACK);
    }
}
