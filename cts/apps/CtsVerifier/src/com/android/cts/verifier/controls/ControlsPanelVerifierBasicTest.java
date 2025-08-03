/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.cts.verifier.controls;

import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * This tests verifies that an app that declares the proper meta-data with
 * {@link android.service.controls.ControlsProviderService#META_DATA_PANEL_ACTIVITY} in their
 * {@link android.service.controls.ControlsProviderService} will have their panel visible in the
 * device controls space.
 */
public class ControlsPanelVerifierBasicTest extends PassFailButtons.Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_text);
        setPassFailButtonClickListeners();
        setInfoResources(
                R.string.controls_panel_basic_test,
                R.string.controls_panel_basic_test_info,
                View.NO_ID
        );
        TextView textView = findViewById(R.id.text);
        SpannableStringBuilder builder = new SpannableStringBuilder(
                ControlsPanelTestUtils.createOpenPanelInstructions(this,
                        textView.getLineHeight()));
        builder.append(getString(R.string.controls_panel_basic_test_instructions));
        textView.setText(builder);
    }

}
