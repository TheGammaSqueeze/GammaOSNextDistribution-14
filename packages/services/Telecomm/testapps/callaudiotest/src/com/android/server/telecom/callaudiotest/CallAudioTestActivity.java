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

package com.android.server.telecom.callaudiotest;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;

/**
 * Activity to configure the auto answer behavior.
 */
public class CallAudioTestActivity extends Activity {
    private static final int RESULT_PICK_FILE = 1;
    public static final String AUDIO_TEST_PREFS = "audio_test_prefs";
    public static final String AUTO_ANSWER_ENABLED = "auto_answer_enabled";
    private CheckBox mEnable;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.call_audio_test_activity);
        mEnable = findViewById(R.id.enableAutoAnswer);
        mEnable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences prefs = getSharedPreferences(AUDIO_TEST_PREFS, MODE_PRIVATE);
                SharedPreferences.Editor edit = prefs.edit();
                edit.putBoolean(AUTO_ANSWER_ENABLED, isChecked);
                edit.apply();
            }
        });
        loadPreferences();
    }

    private void loadPreferences() {
        SharedPreferences prefs = getSharedPreferences(AUDIO_TEST_PREFS, MODE_PRIVATE);
        mEnable.setChecked(prefs.getBoolean(AUTO_ANSWER_ENABLED, false));
    }
}
