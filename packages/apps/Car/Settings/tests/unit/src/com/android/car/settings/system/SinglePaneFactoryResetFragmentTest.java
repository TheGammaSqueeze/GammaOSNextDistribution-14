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

package com.android.car.settings.system;

import static android.app.Activity.RESULT_OK;

import static com.android.car.settings.system.FactoryResetFragment.CHECK_LOCK_REQUEST_CODE;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.testutils.SinglePaneTestActivity;

import org.junit.Rule;
import org.junit.Test;

public class SinglePaneFactoryResetFragmentTest
        extends FactoryResetFragmentTestCase<SinglePaneTestActivity> {

    @Rule
    public ActivityTestRule<SinglePaneTestActivity> mActivityTestRule =
            new ActivityTestRule<>(SinglePaneTestActivity.class);

    @Override
    ActivityTestRule<SinglePaneTestActivity> getActivityTestRule() {
        return mActivityTestRule;
    }

    @Test
    public void processActivityResult_resultOk_launchesActivity()
            throws Throwable {
        getActivityTestRule().runOnUiThread(() -> {
            mFragment.processActivityResult(CHECK_LOCK_REQUEST_CODE, RESULT_OK, /* data= */ null);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        mActivity.getStartActivityListener().assertCalled();
    }
}
