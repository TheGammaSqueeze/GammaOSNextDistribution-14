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

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.fragment.app.FragmentManager;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.SinglePaneTestActivity;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Tests for ChooseLockPinPasswordFragment class.
 */
@RunWith(AndroidJUnit4.class)
public class ChooseLockPinPasswordFragmentTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private ChooseLockPinPasswordFragment mFragment;
    private FragmentManager mFragmentManager;

    @Mock
    PasswordHelper mMockPasswordHelper;

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();
    @Rule
    public ActivityTestRule<SinglePaneTestActivity> mActivityTestRule =
            new ActivityTestRule<>(SinglePaneTestActivity.class);

    @Before
    public void setUp() {
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();
        when(mMockPasswordHelper.getCredentialValidationErrorMessages()).thenReturn("");
    }

    /**
     * A test to verify that onComplete is called is finished when save worker succeeds
     */
    @Test
    public void testOnCompleteIsCalledWhenSaveWorkerSucceeds() throws Throwable {
        setUpFragment(/* isPin= */ true);
        ChooseLockPinPasswordFragment spyFragment = spy(mFragment);
        doNothing().when(spyFragment).onComplete();

        spyFragment.onChosenLockSaveFinished(true);

        verify(spyFragment).onComplete();
    }

    /**
     * A test to verify that the UI stage is updated when save worker fails
     */
    @Test
    public void testStageIsUpdatedWhenSaveWorkerFails() throws Throwable {
        setUpFragment(/* isPin= */ true);
        ChooseLockPinPasswordFragment spyFragment = spy(mFragment);
        doNothing().when(spyFragment).updateStage(ChooseLockPinPasswordFragment.Stage.SaveFailure);

        spyFragment.onChosenLockSaveFinished(false);

        verify(spyFragment, never()).onComplete();
        verify(spyFragment).updateStage(ChooseLockPinPasswordFragment.Stage.SaveFailure);
    }

    @Test
    @UiThreadTest
    public void pin_saveFailure_hintSet() throws Throwable {
        setUpFragment(/* isPin= */ true);
        ChooseLockPinPasswordFragment spyFragment = spy(mFragment);

        spyFragment.updateStage(ChooseLockPinPasswordFragment.Stage.SaveFailure);

        assertThat(spyFragment.getHintText()).isEqualTo(
                mContext.getString(R.string.error_saving_lockpin));
    }

    @Test
    @UiThreadTest
    public void pin_confirmWrong_hintSet() throws Throwable {
        setUpFragment(/* isPin= */ true);
        ChooseLockPinPasswordFragment spyFragment = spy(mFragment);

        spyFragment.updateStage(ChooseLockPinPasswordFragment.Stage.ConfirmWrong);

        assertThat(spyFragment.getHintText()).isEqualTo(
                mContext.getString(R.string.confirm_pins_dont_match));
    }

    @Test
    @UiThreadTest
    public void password_saveFailure_hintSet() throws Throwable {
        setUpFragment(/* isPin= */ false);
        ChooseLockPinPasswordFragment spyFragment = spy(mFragment);

        spyFragment.updateStage(ChooseLockPinPasswordFragment.Stage.SaveFailure);

        assertThat(spyFragment.getHintText()).isEqualTo(
                mContext.getString(R.string.error_saving_password));
    }

    @Test
    @UiThreadTest
    public void password_confirmWrong_hintSet() throws Throwable {
        setUpFragment(/* isPin= */ false);
        ChooseLockPinPasswordFragment spyFragment = spy(mFragment);

        spyFragment.updateStage(ChooseLockPinPasswordFragment.Stage.ConfirmWrong);

        assertThat(spyFragment.getHintText()).isEqualTo(
                mContext.getString(R.string.confirm_passwords_dont_match));
    }

    private void setUpFragment(boolean isPin) throws Throwable {
        String chooseLockPinPasswordFragmentTag = "choose_lock_pin_password_fragment";
        mActivityTestRule.runOnUiThread(() -> {
            mFragmentManager.beginTransaction()
                    .replace(
                            R.id.fragment_container, isPin
                                    ? ChooseLockPinPasswordFragment.newPinInstance()
                                    : ChooseLockPinPasswordFragment.newPasswordInstance(),
                            chooseLockPinPasswordFragmentTag)
                    .commitNow();
        });
        mFragment = (ChooseLockPinPasswordFragment) mFragmentManager
                .findFragmentByTag(chooseLockPinPasswordFragmentTag);
        mFragment.setPasswordHelper(mMockPasswordHelper);
    }
}
