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

package com.android.car.settings.datausage;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.DialogInterface;

import androidx.fragment.app.FragmentManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class UsageBytesThresholdPickerDialogTest {

    private FragmentManager mFragmentManager;

    @Mock
    private UsageBytesThresholdPickerDialog.BytesThresholdPickedListener
            mBytesThresholdPickedListener;
    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BaseCarSettingsTestActivity.class);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();
    }

    @Test
    public void dialogInit_validValue_showsCurrentValue() throws Throwable {
        long twoGB = 2 * UsageBytesThresholdPickerDialog.GIB_IN_BYTES;
        UsageBytesThresholdPickerDialog dialog = UsageBytesThresholdPickerDialog.newInstance(
                R.string.data_usage_limit_editor_title, twoGB);
        mActivityTestRule.runOnUiThread(() -> {
            dialog.show(mFragmentManager, /* tag= */ null);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(dialog.getCurrentThreshold()).isEqualTo(twoGB);
    }

    @Test
    public void dialogInit_lowInvalidValue_showsLowestPossibleValue() throws Throwable {
        UsageBytesThresholdPickerDialog dialog = UsageBytesThresholdPickerDialog.newInstance(
                R.string.data_usage_limit_editor_title, -1);
        mActivityTestRule.runOnUiThread(() -> {
            dialog.show(mFragmentManager, /* tag= */ null);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertThat(dialog.getCurrentThreshold()).isEqualTo(0);
    }

    @Test
    public void positiveButtonClick_noChangeInValue_dialogListenerNotCalled() throws Throwable {
        long twoGB = 2 * UsageBytesThresholdPickerDialog.GIB_IN_BYTES;
        UsageBytesThresholdPickerDialog dialog = UsageBytesThresholdPickerDialog.newInstance(
                R.string.data_usage_limit_editor_title, twoGB);
        dialog.setBytesThresholdPickedListener(mBytesThresholdPickedListener);
        mActivityTestRule.runOnUiThread(() -> {
            dialog.show(mFragmentManager, /* tag= */ null);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        DialogInterface dialogInterface = mock(DialogInterface.class);
        dialog.onClick(dialogInterface, DialogInterface.BUTTON_POSITIVE);

        verify(mBytesThresholdPickedListener, never()).onThresholdPicked(anyLong());
    }

    @Test
    public void positiveButtonClick_changeInValue_dialogListenerCalled() throws Throwable {
        long twoGB = 2 * UsageBytesThresholdPickerDialog.GIB_IN_BYTES;
        UsageBytesThresholdPickerDialog dialog = UsageBytesThresholdPickerDialog.newInstance(
                R.string.data_usage_limit_editor_title, twoGB);
        dialog.setBytesThresholdPickedListener(mBytesThresholdPickedListener);
        mActivityTestRule.runOnUiThread(() -> {
            dialog.show(mFragmentManager, /* tag= */ null);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        long threeGB = 3 * UsageBytesThresholdPickerDialog.GIB_IN_BYTES;
        mActivityTestRule.runOnUiThread(() -> {
            dialog.setThresholdEditor(threeGB);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        DialogInterface dialogInterface = mock(DialogInterface.class);
        dialog.onClick(dialogInterface, DialogInterface.BUTTON_POSITIVE);

        verify(mBytesThresholdPickedListener).onThresholdPicked(threeGB);
    }

    @Test
    public void getCurrentThreshold_aboveLimit_returnLimit() throws Throwable {
        long limitGBTimesTwo = 2 * UsageBytesThresholdPickerDialog.MAX_DATA_LIMIT_BYTES;
        UsageBytesThresholdPickerDialog dialog = UsageBytesThresholdPickerDialog.newInstance(
                R.string.data_usage_limit_editor_title, limitGBTimesTwo);
        mActivityTestRule.runOnUiThread(() -> {
            dialog.show(mFragmentManager, /* tag= */ null);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        assertThat(dialog.getCurrentThreshold()).isEqualTo(
                UsageBytesThresholdPickerDialog.MAX_DATA_LIMIT_BYTES);
    }
}
