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

package com.android.car.settings.security;

import static com.google.common.truth.Truth.assertThat;

import android.view.View;

import androidx.fragment.app.FragmentManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.TestSettingsScreenLockActivity;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

/**
 * Tests for ConfirmLockPinPasswordFragment class.
 */
@RunWith(AndroidJUnit4.class)
public class ConfirmLockPinPasswordFragmentTest {
    private ConfirmLockPinPasswordFragment mFragment;
    private FragmentManager mFragmentManager;

    @Rule
    public ActivityTestRule<TestSettingsScreenLockActivity> mActivityTestRule =
            new ActivityTestRule<>(TestSettingsScreenLockActivity.class);

    @Before
    public void setUp() throws Throwable {
        MockitoAnnotations.initMocks(this);
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();
        setUpFragment();
    }

    /**
     * A test to verify that the Enter key is re-enabled when verification fails.
     */
    @Test
    public void testEnterKeyIsEnabledWhenCheckFails() throws Throwable {
        View enterKey = mFragment.getView().findViewById(R.id.key_enter);
        mActivityTestRule.runOnUiThread(() -> {
            enterKey.setEnabled(false);
            mFragment.onCheckCompleted(false, 0);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(enterKey.isEnabled()).isTrue();
    }

    private void setUpFragment() throws Throwable {
        String confirmLockPinPasswordFragmentTag = "confirm_lock_pin_password_fragment";
        mActivityTestRule.runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(
                            R.id.fragment_container,
                            ConfirmLockPinPasswordFragment.newPinInstance(),
                            confirmLockPinPasswordFragmentTag)
                    .commitNow();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mFragment = (ConfirmLockPinPasswordFragment) mFragmentManager
                .findFragmentByTag(confirmLockPinPasswordFragmentTag);
    }
}
