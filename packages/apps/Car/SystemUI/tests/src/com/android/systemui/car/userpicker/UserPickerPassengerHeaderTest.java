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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.systemui.car.userpicker.HeaderState.HEADER_STATE_CHANGE_USER;
import static com.android.systemui.car.userpicker.HeaderState.HEADER_STATE_LOGOUT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.clearInvocations;

import android.content.res.Configuration;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.userpicker.UserPickerController.Callbacks;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Locale;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class UserPickerPassengerHeaderTest extends UserPickerTestCase {
    @Rule
    public ActivityScenarioRule<UserPickerPassengerTestActivity> mActivityRule =
            new ActivityScenarioRule(UserPickerPassengerTestActivity.class);

    private HeaderState mHeaderstate;

    @Mock
    private Callbacks mMockCallbacks;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mHeaderstate = new HeaderState(mMockCallbacks);
    }

    @Test
    public void pressBackButton_changeUserState_finishActivity() {
        mHeaderstate.setState(HEADER_STATE_CHANGE_USER);
        mActivityRule.getScenario().onActivity(activity -> {
            activity.setupHeaderBar(mHeaderstate);

            assertThat(activity.mBackButton.getVisibility()).isEqualTo(View.VISIBLE);
            activity.mBackButton.performClick();

            assertThat(activity.mCalledFinished).isTrue();
        });
    }

    @Test
    public void checkButtonsVisibility_logoutState_invisibleButtons() {
        mHeaderstate.setState(HEADER_STATE_LOGOUT);
        mActivityRule.getScenario().onActivity(activity -> {
            activity.setupHeaderBar(mHeaderstate);

            assertThat(activity.mBackButton.getVisibility()).isEqualTo(View.GONE);
            assertThat(activity.mLogoutButton.getVisibility()).isEqualTo(View.GONE);
        });

    }

    @Test
    public void checkTextView_changeUserState_invisibleTextView() {
        mHeaderstate.setState(HEADER_STATE_CHANGE_USER);
        mActivityRule.getScenario().onActivity(activity -> {
            activity.setupHeaderBar(mHeaderstate);

            assertThat(activity.mHeaderBarTextForLogout.getVisibility())
                    .isEqualTo(View.GONE);
        });
    }

    @Test
    public void checkTextView_logoutState_visibleTextView() {
        mHeaderstate.setState(HEADER_STATE_LOGOUT);
        mActivityRule.getScenario().onActivity(activity -> {
            activity.setupHeaderBar(mHeaderstate);

            assertThat(activity.mHeaderBarTextForLogout.getVisibility())
                    .isEqualTo(View.VISIBLE);
        });
    }

    @Test
    public void logoutUser_pressLogoutButton_stopUser() {
        mHeaderstate.setState(HEADER_STATE_CHANGE_USER);
        mActivityRule.getScenario().onActivity(activity -> {
            activity.setupHeaderBar(mHeaderstate);

            assertThat(activity.mLogoutButton.getVisibility()).isEqualTo(View.VISIBLE);
            activity.mLogoutButton.performClick();

            verify(activity.mController).logoutUser();
        });

    }

    @Test
    public void pressPowerButton_screenOff() {
        mHeaderstate.setState(HEADER_STATE_CHANGE_USER);
        mActivityRule.getScenario().onActivity(activity -> {
            activity.setupHeaderBar(mHeaderstate);

            View powerBtn = activity.findViewById(R.id.power_button_icon_view);
            powerBtn.performClick();

            assertThat(powerBtn.getVisibility()).isEqualTo(View.VISIBLE);
            verify(activity.mController).screenOffDisplay();
        });

    }

    @Test
    public void onConfigurationChanged_changeConfiguration_callSetAdapter() {
        mHeaderstate.setState(HEADER_STATE_CHANGE_USER);
        mActivityRule.getScenario().onActivity(activity -> {
            activity.setupHeaderBar(mHeaderstate);
            // initial settings
            Configuration origConfiguration = activity.getResources().getConfiguration();
            Configuration newConfiguration = origConfiguration;
            newConfiguration.orientation = Configuration.ORIENTATION_LANDSCAPE;
            newConfiguration.screenLayout = Configuration.SCREENLAYOUT_LAYOUTDIR_RTL;
            newConfiguration.setLocale(new Locale("en"));
            // orientation change
            newConfiguration.orientation = Configuration.ORIENTATION_PORTRAIT;
            activity.onConfigurationChanged(newConfiguration);
            verify(activity.mAdapter).onConfigurationChanged();
            clearInvocations(activity.mAdapter);
            // screen layout change
            newConfiguration.screenLayout = Configuration.SCREENLAYOUT_LAYOUTDIR_LTR;
            activity.onConfigurationChanged(newConfiguration);
            verify(activity.mAdapter).onConfigurationChanged();
            clearInvocations(activity.mAdapter);
            // locale change
            newConfiguration.setLocale(new Locale("kr"));
            activity.onConfigurationChanged(newConfiguration);
            verify(activity.mAdapter).onConfigurationChanged();
            clearInvocations(activity.mAdapter);
            // reset configuration
            activity.onConfigurationChanged(origConfiguration);
        });
    }
}
