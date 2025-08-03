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

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import androidx.fragment.app.FragmentManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.SinglePaneTestActivity;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

/**
 * Tests for ChooseLockPatternFragment class.
 */
@RunWith(AndroidJUnit4.class)
public class ChooseLockPatternFragmentTest {
    private ChooseLockPatternFragment mFragment;
    private FragmentManager mFragmentManager;

    @Rule
    public ActivityTestRule<SinglePaneTestActivity> mActivityTestRule =
            new ActivityTestRule<>(SinglePaneTestActivity.class);

    @Before
    public void setUp() throws Throwable {
        MockitoAnnotations.initMocks(this);
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();
        setUpFragment();
    }

    /**
     * A test to verify that onComplete is called when save worker succeeds
     */
    @Test
    public void testOnCompleteIsCalledWhenSaveWorkerSucceeds() {
        ChooseLockPatternFragment spyFragment = spy(mFragment);
        doNothing().when(spyFragment).onComplete();

        spyFragment.onChosenLockSaveFinished(true);

        verify(spyFragment).onComplete();
    }

    /**
     * A test to verify that the UI stage is updated when save worker fails
     */
    @Test
    public void testStageIsUpdatedWhenSaveWorkerFails() {
        ChooseLockPatternFragment spyFragment = spy(mFragment);
        doNothing().when(spyFragment).updateStage(ChooseLockPatternFragment.Stage.SaveFailure);

        spyFragment.onChosenLockSaveFinished(false);

        verify(spyFragment, never()).onComplete();
        verify(spyFragment).updateStage(ChooseLockPatternFragment.Stage.SaveFailure);
    }

    private void setUpFragment() throws Throwable {
        String chooseLockPatternFragmentTag = "choose_lock_pattern_fragment";
        mActivityTestRule.runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(
                            R.id.fragment_container, new ChooseLockPatternFragment(),
                            chooseLockPatternFragmentTag)
                    .commitNow();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mFragment = (ChooseLockPatternFragment) mFragmentManager
                .findFragmentByTag(chooseLockPatternFragmentTag);
    }
}
