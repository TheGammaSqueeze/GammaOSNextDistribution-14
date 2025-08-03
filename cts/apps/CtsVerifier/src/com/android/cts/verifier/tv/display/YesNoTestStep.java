/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.verifier.tv.display;

import android.app.Activity;
import android.view.View;

import com.android.cts.verifier.tv.TestStepBase;
import com.android.cts.verifier.tv.TvAppVerifierActivity;

import java.util.List;

/**
 * Encapsulates the logic of a test step, which displays human instructions for a manual test and
 * two buttons - Yes and No, which respectively set the test in passing and failing state.
 */
public class YesNoTestStep extends TestStepBase {
    private View mPositiveButton;
    private View mNegativeButton;
    private final int mPositiveButtonText;
    private final int mNegativeButtonText;

    /**
     * Constructs a test step containing human instructions for a manual test and two buttons - Yes
     * and No.
     *
     * @param context The test activity which this test step is part of.
     * @param instructionText The text of the test instruction visible to the user.
     */
    public YesNoTestStep(Activity context, String instructionText,
            int positiveButtonText, int negativeButtonText) {
        super(context, instructionText);
        this.mPositiveButtonText = positiveButtonText;
        this.mNegativeButtonText = negativeButtonText;
    }

    @Override
    public List<View> createUiElements() {
        List<View> list = super.createUiElements();
        mPositiveButton =
                TvAppVerifierActivity.createButtonItem(
                        mContext.getLayoutInflater(),
                        null,
                        mPositiveButtonText,

                        (View view) -> {
                            disableInteractivity();
                            // do nothing so the test will pass
                            done();
                        });
        list.add(mPositiveButton);

        mNegativeButton =
                TvAppVerifierActivity.createButtonItem(
                        mContext.getLayoutInflater(),
                        null,
                        mNegativeButtonText,
                        (View view) -> {
                            disableInteractivity();
                            getAsserter().fail();
                            done();
                        });
        list.add(mNegativeButton);

        return list;
    }

    @Override
    public void enableInteractivity() {
        TvAppVerifierActivity.setButtonEnabled(mPositiveButton, true);
        TvAppVerifierActivity.setButtonEnabled(mNegativeButton, true);
        mPositiveButton.requestFocus();
    }

    @Override
    public void disableInteractivity() {
        TvAppVerifierActivity.setButtonEnabled(mPositiveButton, false);
        TvAppVerifierActivity.setButtonEnabled(mNegativeButton, false);
    }
}
