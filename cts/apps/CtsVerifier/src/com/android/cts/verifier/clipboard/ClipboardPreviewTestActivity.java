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

package com.android.cts.verifier.clipboard;


import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.view.View;
import android.widget.Button;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;


/**
 * A CTS Verifier test case for validating the user-visible clipboard confirmation.
 */
public class ClipboardPreviewTestActivity extends PassFailButtons.Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup the UI.
        setContentView(R.layout.clipboard_preview);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.clipboard_preview_test, R.string.clipboard_preview_test_info, -1);
        // Get the share button and attach the listener.
        Button copyButton = findViewById(R.id.clipboard_preview_test_copy);
        copyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setClipboardData();
            }
        });
        disablePassFail();
    }

    private void setClipboardData() {
        ClipboardManager cm = this.getSystemService(ClipboardManager.class);

        ClipData cd = ClipData.newPlainText("",
                getString(R.string.clipboard_preview_test_secret));
        PersistableBundle pb = new PersistableBundle(1);
        pb.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
        cd.getDescription().setExtras(pb);
        cm.setPrimaryClip(cd);
        enablePassFail();
    }

    private void disablePassFail() {
        findViewById(R.id.clipboard_preview_test_pass_fail).setVisibility(View.INVISIBLE);
    }

    private void enablePassFail() {
        findViewById(R.id.clipboard_preview_test_pass_fail).setVisibility(View.VISIBLE);
    }
}
