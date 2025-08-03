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

import static com.android.car.settings.system.ResetNetworkFragment.REQUEST_CODE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.content.Intent;

import androidx.fragment.app.FragmentManager;
import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.security.CheckLockActivity;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.car.ui.toolbar.MenuItem;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class ResetNetworkFragmentTest {
    private ResetNetworkFragment mFragment;
    private BaseCarSettingsTestActivity mActivity;
    private FragmentManager mFragmentManager;
    private MenuItem mResetButton;

    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BaseCarSettingsTestActivity.class);

    @Before
    public void setUp() throws Throwable {
        MockitoAnnotations.initMocks(this);
        mActivity = mActivityTestRule.getActivity();
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();
        setUpFragment();
        mResetButton = mActivity.getToolbar().getMenuItems().get(0);

        doNothing().when(mFragment).startActivityForResult(any(), anyInt());
    }

    @Test
    @UiThreadTest
    public void onResetButtonPressed_launchesCheckLockActivity() {
        mResetButton.performClick();
        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mFragment).startActivityForResult(captor.capture(), eq(REQUEST_CODE));
        Intent intent = captor.getValue();
        assertThat(intent.getComponent().getClassName())
                .isEqualTo(CheckLockActivity.class.getName());
    }

    @Test
    @UiThreadTest
    public void onActivityResult_launchesResetNetworkConfirmFragment() {
        mFragment.onActivityResult(REQUEST_CODE, RESULT_OK, new Intent());
        verify(mFragment).launchFragment(any(ResetNetworkConfirmFragment.class));
    }

    private void setUpFragment() throws Throwable {
        String resetNetworkFragmentTag = "reset_network_fragment";
        mActivityTestRule.runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, new ResetNetworkFragment(),
                            resetNetworkFragmentTag)
                    .commitNow();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mFragment = (ResetNetworkFragment)
                mFragmentManager.findFragmentByTag(resetNetworkFragmentTag);
        ExtendedMockito.spyOn(mFragment);
    }
}
