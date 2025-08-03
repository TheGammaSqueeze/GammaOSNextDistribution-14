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

package com.android.cts.verifier.managedprovisioning;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Base activity for tests that require a preparation step.
 */
public abstract class ByodTestActivityWithPrepare extends PassFailButtons.Activity {
    private TextView mLogView;
    private TextView mInstructionsView;
    private Button mPrepareButton;
    private Button mGoButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.byod_test_with_prepare);
        setPassFailButtonClickListeners();

        mLogView = findViewById(R.id.test_log);
        mLogView.setMovementMethod(new ScrollingMovementMethod());
        mInstructionsView = findViewById(R.id.instructions_text);
        mPrepareButton = findViewById(R.id.prepare_test_button);
        mGoButton = findViewById(R.id.run_test_button);
        mGoButton.setEnabled(false);
        getPassButton().setEnabled(false);
    }

    protected TextView getLogView() {
        return mLogView;
    }

    protected TextView getInstructionsView() {
        return mInstructionsView;
    }

    protected Button getPrepareButton() {
        return mPrepareButton;
    }

    protected Button getGoButton() {
        return mGoButton;
    }
}
