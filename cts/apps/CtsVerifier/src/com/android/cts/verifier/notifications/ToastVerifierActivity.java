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

package com.android.cts.verifier.notifications;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Tests visual requirements for toasts.
 *   - max 2 lines
 *   - show app icon
 */
public class ToastVerifierActivity extends PassFailButtons.Activity {

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(getLayoutInflater().inflate(R.layout.toast_main, null));
        setPassFailButtonClickListeners();

        // Sets the text in the dialog
        setInfoResources(R.string.toast_title,
                R.string.toast_info, -1);

        if (getApplicationContext().getApplicationInfo().targetSdkVersion < Build.VERSION_CODES.S) {
            // don't run the test, auto-pass
            findViewById(R.id.toast_description_s).setVisibility(View.GONE);
            findViewById(R.id.toast_post_button).setVisibility(View.GONE);
            getPassButton().callOnClick();
        } else {
            // only enable pass after tester has pressed button to post a toast
            getPassButton().setEnabled(false);
            findViewById(R.id.toast_description_pre_s).setVisibility(View.GONE);
        }

        // Post toast
        findViewById(R.id.toast_post_button).setOnClickListener(v -> {
            postToast();
            getPassButton().setEnabled(true);
        });
    }

    private void postToast() {
        Toast.makeText(getApplicationContext(), R.string.toast_long_text, Toast.LENGTH_LONG)
                .show();
    }
}
