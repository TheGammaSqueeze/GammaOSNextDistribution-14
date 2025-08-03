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

package com.android.car.settings.common;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.FragmentManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseCarSettingsTestActivity;
import com.android.car.settings.testutils.DialogTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class ConfirmationDialogFragmentTest {

    private static final String TEST_ARG_KEY = "arg_key";
    private static final String TEST_ARG_VALUE = "arg_value";
    private static final String TEST_TITLE = "Test Title";
    private static final String TEST_MESSAGE = "Test Message";

    private Context mContext = ApplicationProvider.getApplicationContext();
    private ConfirmationDialogFragment mFragment;
    private ConfirmationDialogFragment.Builder mDialogFragmentBuilder;
    private FragmentManager mFragmentManager;

    @Rule
    public ActivityTestRule<BaseCarSettingsTestActivity> mActivityTestRule = new ActivityTestRule<>(
            BaseCarSettingsTestActivity.class);

    @Mock
    private ConfirmationDialogFragment.ConfirmListener mConfirmListener;
    @Mock
    private ConfirmationDialogFragment.RejectListener mRejectListener;
    @Mock
    private ConfirmationDialogFragment.DismissListener mDismissListener;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mFragmentManager = mActivityTestRule.getActivity().getSupportFragmentManager();
        mDialogFragmentBuilder = new ConfirmationDialogFragment.Builder(mContext);
        mDialogFragmentBuilder.setTitle(TEST_TITLE);
        mDialogFragmentBuilder.setMessage(TEST_MESSAGE);
        mDialogFragmentBuilder.addArgumentString(TEST_ARG_KEY, TEST_ARG_VALUE);
    }

    @Test
    public void buildDialogFragment_hasTitleAndMessage() throws Throwable {
        ConfirmationDialogFragment dialogFragment = mDialogFragmentBuilder.build();
        launchDialogFragment(dialogFragment);

        // TODO(b/148687802): Figure out why title returns empty string.
        // assertThat(DialogTestUtils.getTitle(dialogFragment)).isEqualTo(TEST_TITLE);
        assertThat(DialogTestUtils.getMessage(dialogFragment)).isEqualTo(TEST_MESSAGE);
    }

    @Test
    public void buildDialogFragment_positiveButtonSet_negativeAndNeutralButtonNotVisible()
            throws Throwable {
        mDialogFragmentBuilder.setPositiveButton(R.string.test_positive_button_label, null);
        ConfirmationDialogFragment dialogFragment = mDialogFragmentBuilder.build();
        launchDialogFragment(dialogFragment);

        AlertDialog dialog = (AlertDialog) mFragment.getDialog();
        assertThat(dialog.getButton(DialogInterface.BUTTON_POSITIVE).getVisibility()).isEqualTo(
                View.VISIBLE);
        assertThat(dialog.getButton(DialogInterface.BUTTON_NEGATIVE).getVisibility()).isEqualTo(
                View.GONE);
        assertThat(dialog.getButton(DialogInterface.BUTTON_NEUTRAL).getVisibility()).isEqualTo(
                View.GONE);
    }

    @Test
    public void buildDialogFragment_negativeButtonSet_positiveAndNeutralButtonNotVisible()
            throws Throwable {
        mDialogFragmentBuilder.setNegativeButton(R.string.test_negative_button_label, null);
        ConfirmationDialogFragment dialogFragment = mDialogFragmentBuilder.build();
        launchDialogFragment(dialogFragment);

        AlertDialog dialog = (AlertDialog) mFragment.getDialog();
        assertThat(dialog.getButton(DialogInterface.BUTTON_POSITIVE).getVisibility()).isEqualTo(
                View.GONE);
        assertThat(dialog.getButton(DialogInterface.BUTTON_NEGATIVE).getVisibility()).isEqualTo(
                View.VISIBLE);
        assertThat(dialog.getButton(DialogInterface.BUTTON_NEUTRAL).getVisibility()).isEqualTo(
                View.GONE);
    }

    @Test
    public void buildDialogFragment_neutralButtonSet_positiveAndNegativeButtonNotVisible()
            throws Throwable {
        mDialogFragmentBuilder.setNeutralButton(R.string.test_neutral_button_label, null);
        ConfirmationDialogFragment dialogFragment = mDialogFragmentBuilder.build();
        launchDialogFragment(dialogFragment);

        AlertDialog dialog = (AlertDialog) mFragment.getDialog();
        assertThat(dialog.getButton(DialogInterface.BUTTON_POSITIVE).getVisibility()).isEqualTo(
                View.GONE);
        assertThat(dialog.getButton(DialogInterface.BUTTON_NEGATIVE).getVisibility()).isEqualTo(
                View.GONE);
        assertThat(dialog.getButton(DialogInterface.BUTTON_NEUTRAL).getVisibility()).isEqualTo(
                View.VISIBLE);
    }

    @Test
    public void clickPositiveButton_callsCallbackWithArgs() throws Throwable {
        mDialogFragmentBuilder.setPositiveButton(R.string.test_positive_button_label,
                mConfirmListener);
        ConfirmationDialogFragment dialogFragment = mDialogFragmentBuilder.build();
        launchDialogFragment(dialogFragment);

        AlertDialog dialog = (AlertDialog) mFragment.getDialog();
        mActivityTestRule.runOnUiThread(() ->
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        ArgumentCaptor<Bundle> bundle = ArgumentCaptor.forClass(Bundle.class);
        verify(mConfirmListener).onConfirm(bundle.capture());
        assertThat(bundle.getValue().getString(TEST_ARG_KEY)).isEqualTo(TEST_ARG_VALUE);
    }

    @Test
    public void clickNegativeButton_callsCallbackWithArgs() throws Throwable {
        mDialogFragmentBuilder.setNegativeButton(R.string.test_negative_button_label,
                mRejectListener);
        ConfirmationDialogFragment dialogFragment = mDialogFragmentBuilder.build();
        launchDialogFragment(dialogFragment);

        AlertDialog dialog = (AlertDialog) mFragment.getDialog();
        mActivityTestRule.runOnUiThread(() ->
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        ArgumentCaptor<Bundle> bundle = ArgumentCaptor.forClass(Bundle.class);
        verify(mRejectListener).onReject(bundle.capture());
        assertThat(bundle.getValue().getString(TEST_ARG_KEY)).isEqualTo(TEST_ARG_VALUE);
    }

    @Test
    public void dismissDialog_callsCallbackWithArgs() throws Throwable {
        mDialogFragmentBuilder.setDismissListener(mDismissListener);
        ConfirmationDialogFragment dialogFragment = mDialogFragmentBuilder.build();
        launchDialogFragment(dialogFragment);

        AlertDialog dialog = (AlertDialog) mFragment.getDialog();
        mActivityTestRule.runOnUiThread(() -> dialog.dismiss());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        dialog.dismiss();
        ArgumentCaptor<Bundle> bundle = ArgumentCaptor.forClass(Bundle.class);
        verify(mDismissListener).onDismiss(bundle.capture(), eq(false));
        assertThat(bundle.getValue().getString(TEST_ARG_KEY)).isEqualTo(TEST_ARG_VALUE);
    }

    private void launchDialogFragment(ConfirmationDialogFragment dialog) throws Throwable {
        mActivityTestRule.runOnUiThread(
                () -> dialog.show(mFragmentManager,
                        ConfirmationDialogFragment.TAG));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        mFragment = (ConfirmationDialogFragment) mFragmentManager.findFragmentByTag(
                ConfirmationDialogFragment.TAG);
    }
}
