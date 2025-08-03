/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.car.userpicker;

import static com.android.systemui.car.userpicker.HeaderState.HEADER_STATE_CHANGE_USER;

import static com.google.common.truth.Truth.assertThat;

import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.SmallTest;

import com.android.systemui.car.CarSystemUiTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class UserPickerDriverHeaderTest extends UserPickerTestCase {
    @Rule
    public ActivityScenarioRule<UserPickerDriverTestActivity> mActivityRule =
            new ActivityScenarioRule(UserPickerDriverTestActivity.class);

    private HeaderState mHeaderstate;

    @Mock
    private UserPickerController.Callbacks mMockCallbacks;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mHeaderstate = new HeaderState(mMockCallbacks);
    }

    @Test
    public void checkLogoutButton_inDriverSeat_invisibleLogoutButton() {
        mHeaderstate.setState(HEADER_STATE_CHANGE_USER);
        mActivityRule.getScenario().onActivity(activity -> {
            activity.setupHeaderBar(mHeaderstate);

            assertThat(activity.mBackButton.getVisibility()).isEqualTo(View.VISIBLE);
            assertThat(activity.mLogoutButton.getVisibility()).isEqualTo(View.GONE);
        });
    }
}
