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

package android.virtualdevice.streamedtestapp;

import static android.content.Intent.EXTRA_RESULT_RECEIVER;
import static android.virtualdevice.cts.common.StreamedAppConstants.ACTION_READ;
import static android.virtualdevice.cts.common.StreamedAppConstants.ACTION_WRITE;
import static android.virtualdevice.cts.common.StreamedAppConstants.EXTRA_CLIP_DATA;
import static android.virtualdevice.cts.common.StreamedAppConstants.EXTRA_DEVICE_ID;
import static android.virtualdevice.cts.common.StreamedAppConstants.EXTRA_HAS_CLIP_DATA;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteCallback;

/**
 * Activity used for testing clipboard access on different devices. It needs to be in a separate
 * apk because the virtual device owner always has access to its clipboard.
 */
public class ClipboardTestActivity extends Activity {

    @Override
    protected void onResume() {
        super.onResume();
        if (hasWindowFocus()) {
            processAction();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            processAction();
        }
    }

    private void processAction() {
        int deviceId = getIntent().getIntExtra(EXTRA_DEVICE_ID, getDeviceId());
        ClipboardManager clipboardManager =
                createDeviceContext(deviceId).getSystemService(ClipboardManager.class);

        String action = getIntent().getAction();
        Bundle result = null;
        if (ACTION_READ.equals(action)) {
            result = new Bundle();
            result.putBoolean(EXTRA_HAS_CLIP_DATA, clipboardManager.hasPrimaryClip());
            result.putParcelable(EXTRA_CLIP_DATA, clipboardManager.getPrimaryClip());
        } else if (ACTION_WRITE.equals(action)) {
            Intent intent = getIntent();
            ClipData clip = intent.getParcelableExtra(EXTRA_CLIP_DATA, ClipData.class);
            clipboardManager.setPrimaryClip(clip);
        }

        RemoteCallback resultReceiver =
                getIntent().getParcelableExtra(EXTRA_RESULT_RECEIVER, RemoteCallback.class);
        if (resultReceiver != null) {
            resultReceiver.sendResult(result);
        }
        finish();
    }
}