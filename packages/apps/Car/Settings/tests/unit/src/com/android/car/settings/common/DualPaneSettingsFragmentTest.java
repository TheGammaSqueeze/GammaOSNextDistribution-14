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

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.DualPaneTestActivity;

import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

public class DualPaneSettingsFragmentTest extends SettingsFragmentTestCase<DualPaneTestActivity> {

    @Rule
    public ActivityTestRule<DualPaneTestActivity> mActivityTestRule =
            new ActivityTestRule<>(DualPaneTestActivity.class);

    @Override
    ActivityTestRule<DualPaneTestActivity> getActivityTestRule() {
        return mActivityTestRule;
    }

    @Test
    public void launchFragment_otherFragment_opensFragment() throws Throwable {
        AtomicReference<TestSettingsFragment> otherFragment = new AtomicReference<>();
        getActivityTestRule().runOnUiThread(() -> {
            otherFragment.set(new TestSettingsFragment());
            mFragment.onCreate(null);
            mFragment.launchFragment(otherFragment.get());
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertThat(
                mFragment.getFragmentManager().findFragmentById(R.id.fragment_container)).isEqualTo(
                otherFragment.get());
    }
}
