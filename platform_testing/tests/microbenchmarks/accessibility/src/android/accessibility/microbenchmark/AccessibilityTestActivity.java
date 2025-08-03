/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.accessibility.microbenchmark;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.KeyguardManager;
import android.os.Bundle;
import android.widget.Button;

/** An activity for accessibility test. */
public class AccessibilityTestActivity extends Activity {
    DatePickerDialog mDatePickerDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        turnOnScreen();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.accessibility_test);

        // TODO(b/270755989) Replace this popup with a DatePickerDialog once we can figure out why
        // it crashes when trying to show it.
        Button button = findViewById(R.id.openDialogBtn);
        button.setOnClickListener(
                v ->
                        new AlertDialog.Builder(this)
                                .setTitle("Title")
                                .setMessage("Message")
                                .create()
                                .show());
    }

    private void turnOnScreen() {
        setTurnScreenOn(true);
        setShowWhenLocked(true);
        KeyguardManager keyguardManager = getSystemService(KeyguardManager.class);
        keyguardManager.requestDismissKeyguard(this, null);
    }
}
