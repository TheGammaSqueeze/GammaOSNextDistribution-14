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

import static com.google.common.truth.Truth.assertThat;

import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.statusbar.policy.Clock;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class UserPickerBottomBarTest extends UserPickerTestCase {
    @Rule
    public ActivityScenarioRule<UserPickerPassengerTestActivity> mActivityRule =
            new ActivityScenarioRule(UserPickerPassengerTestActivity.class);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void checkBottomBarHeight_validDimension() {
        float target_height = mContext.getResources()
                .getDimension(R.dimen.car_bottom_system_bar_height);
        mActivityRule.getScenario().onActivity(activity -> {
            ConstraintLayout bottombar = activity.findViewById(R.id.user_picker_bottom_bar);
            float height = bottombar.getLayoutParams().height;

            assertThat(height).isEqualTo(target_height);
        });


    }

    @Test
    public void checkClockVisibility_isClockVisible() {
        mActivityRule.getScenario().onActivity(activity -> {
            Clock clock = activity.findViewById(R.id.user_picker_bottom_bar_clock);
            assertThat(clock).isNotNull();
            assertThat(clock.getVisibility()).isEqualTo(View.VISIBLE);
        });
    }
}
