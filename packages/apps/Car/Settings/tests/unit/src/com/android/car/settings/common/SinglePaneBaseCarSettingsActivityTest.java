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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Intent;

import androidx.preference.Preference;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.car.settings.testutils.BaseTestSettingsFragment;
import com.android.car.settings.testutils.SinglePaneTestActivity;

import org.junit.Rule;
import org.junit.Test;

public class SinglePaneBaseCarSettingsActivityTest
        extends BaseCarSettingsActivityTestCase<SinglePaneTestActivity> {

    @Rule
    public ActivityTestRule<SinglePaneTestActivity> mActivityTestRule =
            new ActivityTestRule<>(SinglePaneTestActivity.class);

    @Override
    ActivityTestRule<SinglePaneTestActivity> getActivityTestRule() {
        return mActivityTestRule;
    }

    @Test
    public void onPreferenceStartFragment_launchesActivity() throws Throwable {
        Preference pref = new Preference(mContext);
        pref.setFragment(BaseTestSettingsFragment.class.getName());

        BaseCarSettingsTestActivity spiedActivity = spy(mActivity);

        mActivityTestRule.runOnUiThread(() ->
                spiedActivity.onPreferenceStartFragment(/* caller= */ null, pref));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        verify(spiedActivity).startActivity(any(Intent.class));
    }

    @Test
    public void onUxRestrictionsChanged_fragmentHasUpdatedUxRestrictions()
            throws Throwable {

        CarUxRestrictions restrictions = new CarUxRestrictions.Builder(
                /* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP,
                /* timestamp= */ 0
        ).build();

        mActivityTestRule.runOnUiThread(() -> {
            mActivity.onUxRestrictionsChanged(restrictions);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(((BaseTestSettingsFragment) getCurrentFragment()).getUxRestrictions()
                .isSameRestrictions(restrictions)).isTrue();
    }
}
