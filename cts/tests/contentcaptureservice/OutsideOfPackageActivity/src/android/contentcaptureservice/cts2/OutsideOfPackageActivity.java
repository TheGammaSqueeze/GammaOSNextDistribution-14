/*
 * Copyright (C) 2019 The Android Open Source Project
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
package android.contentcaptureservice.cts2;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.contentcapture.ContentCaptureManager;

/**
 * This activity is used to test temporary Content Capture Service interactions with activities
 * outside of its own package.
 */
public class OutsideOfPackageActivity extends Activity {

    private static final String TAG = "OutsideOfPackageActivity";
    boolean mFinishActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        mFinishActivity = intent.getBooleanExtra("finishActivity", false);

    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(TAG, "onNewIntent()");
        super.onNewIntent(intent);
        mFinishActivity = intent.getBooleanExtra("finishActivity", false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onCreate(), mFinishActivity=" + mFinishActivity);
        sendContentCaptureEnableStatussult();
        if (mFinishActivity) {
            finish();
        }
        Log.d(TAG, "finish onResume()");
    }

    private boolean isContentCaptureEnabled() {
        ContentCaptureManager captureManager = getSystemService(ContentCaptureManager.class);
        if (captureManager == null) {
            return false;
        }
        return captureManager.isContentCaptureEnabled();
    }

    private void sendContentCaptureEnableStatussult() {
        boolean isEnable = isContentCaptureEnabled();
        Log.d(TAG, "send enable: " + isEnable + " for " + getPackageName());
        Intent intent = new Intent("ACTION_ACTIVITY_CC_STATUS_TEST")
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_REGISTERED_ONLY)
                .putExtra("cc_enable", isEnable);
        sendBroadcast(intent);
    }
}
