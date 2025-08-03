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

package com.android.car.settings.wifi;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.fragment.app.FragmentManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.wifitrackerlib.WifiEntry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class WifiPasswordDialogTest {

    private static final String TEST_TITLE = "test_title";

    private WifiPasswordDialog mFragment;
    private FragmentManager mFragmentManager;

    @Mock
    private WifiEntry mMockWifiEntry;
    @Mock
    private WifiPasswordDialog.WifiDialogListener mMockListener;

    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule =
            new ActivityTestRule<>(BaseCarSettingsTestActivity.class);

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();
        when(mMockWifiEntry.getTitle()).thenReturn(TEST_TITLE);
    }

    @Test
    public void onDialogCreated_titleSet() throws Throwable {
        setUpDialog();
        View titleView = mFragment.getDialog().findViewById(com.android.internal.R.id.alertTitle);
        assertThat(titleView).isNotNull();
        assertThat(((TextView) titleView).getText().toString()).isEqualTo(TEST_TITLE);
    }

    @Test
    public void onDialogCreated_editTextInputTypeSet() throws Throwable {
        setUpDialog();
        EditText editText = mFragment.getDialog().findViewById(android.R.id.edit);
        assertThat(editText.getInputType()).isEqualTo(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    }

    @Test
    public void onCheckboxChanged_editTextInputTypeSet() throws Throwable {
        setUpDialog();
        EditText editText = mFragment.getDialog().findViewById(android.R.id.edit);
        CheckBox cb = mFragment.getDialog().findViewById(R.id.checkbox);
        mActivityTestRule.runOnUiThread(() -> cb.performClick());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertThat(editText.getInputType()).isEqualTo(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        mActivityTestRule.runOnUiThread(() -> cb.performClick());
        assertThat(editText.getInputType()).isEqualTo(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
    }

    @Test
    public void onConfirm_listenerTriggered() throws Throwable {
        setUpDialog();
        Button confirmButton = ((AlertDialog) mFragment.getDialog()).getButton(
                DialogInterface.BUTTON_POSITIVE);
        mActivityTestRule.runOnUiThread(() -> confirmButton.performClick());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        verify(mMockListener).onSubmit(any());
    }

    private void setUpDialog() throws Throwable {
        mActivityTestRule.runOnUiThread(
                () -> new WifiPasswordDialog(mMockWifiEntry, mMockListener).show(mFragmentManager,
                        WifiPasswordDialog.TAG));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mFragment = (WifiPasswordDialog) mFragmentManager.findFragmentByTag(
                WifiPasswordDialog.TAG);
    }
}
