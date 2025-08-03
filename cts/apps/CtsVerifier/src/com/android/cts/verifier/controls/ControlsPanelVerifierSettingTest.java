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

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;


/**
 * This tests verify that the correct value of the Setting is passed through the extra
 * {@link android.service.controls.ControlsProviderService#EXTRA_LOCKSCREEN_ALLOW_TRIVIAL_CONTROLS}.
 */
public abstract class ControlsPanelVerifierSettingTest extends PassFailButtons.Activity{

    abstract boolean getSettingState();

    abstract int getTestNameResId();

    abstract int getInstructions();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.controls_panel_settings_pass_fail);
        setPassFailButtonClickListeners();
        setInfoResources(
                getTestNameResId(),
                R.string.controls_panel_setting_test_info,
                View.NO_ID
        );
        TextView textView = findViewById(R.id.text_below);
        textView.setText(
                ControlsPanelTestUtils.createOpenPanelInstructions(this,
                        textView.getLineHeight()));

        String text = getString(getInstructions(),
                getSettingState());
        ((TextView) findViewById(R.id.text_above)).setText(text);

        Button button = findViewById(R.id.button);
        button.setText(R.string.controls_panel_settings_test_open_lockscreen_settings);
        button.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            startActivity(intent);
        });
    }

    /**
     * Test for false value of the setting.
     */
    public static class TestFalse extends ControlsPanelVerifierSettingTest {
        @Override
        boolean getSettingState() {
            return false;
        }

        @Override
        int getTestNameResId() {
            return R.string.controls_panel_setting_test_false;
        }

        @Override
        int getInstructions() {
            return R.string.controls_panel_setting_test_instructions;
        }
    }

    /**
     * Test for true value of the setting
     */
    public static class TestTrue extends ControlsPanelVerifierSettingTest {
        @Override
        boolean getSettingState() {
            return true;
        }

        @Override
        int getTestNameResId() {
            return R.string.controls_panel_setting_test_true;
        }

        @Override
        int getInstructions() {
            return R.string.controls_panel_setting_test_instructions;
        }
    }

    /**
     * Test for false value when started on keyguard.
     */
    public static class DreamTestFalse extends ControlsPanelVerifierSettingTest {
        @Override
        boolean getSettingState() {
            return false;
        }

        @Override
        int getTestNameResId() {
            return R.string.controls_panel_dream_test_false;
        }

        @Override
        int getInstructions() {
            return R.string.controls_panel_dream_false_test_instructions;
        }
    }

    /**
     * Test for true value when hosted in as a dream service.
     */
    public static class DreamTestTrue extends ControlsPanelVerifierSettingTest {
        @Override
        boolean getSettingState() {
            return true;
        }

        @Override
        int getTestNameResId() {
            return R.string.controls_panel_dream_test_true;
        }

        @Override
        int getInstructions() {
            return R.string.controls_panel_dream_true_test_instructions;
        }
    }
}
