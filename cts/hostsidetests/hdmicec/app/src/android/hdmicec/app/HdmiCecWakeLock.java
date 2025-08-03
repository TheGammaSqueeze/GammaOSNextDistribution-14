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

package android.hdmicec.app;

import static android.os.PowerManager.PARTIAL_WAKE_LOCK;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

/**
 * A simple activity that can be used to acquire and release the wake lock for controlling power
 * management. The actions supported are:
 *
 * <p>
 *
 * <p>1. android.hdmicec.app.ACQUIRE_LOCK: Acquires the wake lock.
 *
 * <p>Usage: <code>START_COMMAND -a android.hdmicec.app.ACQUIRE_LOCK</code>
 *
 * <p>2. android.hdmicec.app.RELEASE_LOCK: Releases the wake lock.
 *
 * <p>Usage: <code>START_COMMAND -a android.hdmicec.app.RELEASE_LOCK</code>
 *
 * <p>
 *
 * <p>where START_COMMAND is
 *
 * <p><code>
 * adb shell am start -n "android.hdmicec.app/android.hdmicec.app.HdmiCecWakeLock -a "
 * </code>
 */
public class HdmiCecWakeLock extends Activity {
    private static final String TAG = HdmiCecWakeLock.class.getSimpleName();
    private WakeLock mWakeLock;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        handleIntent(getIntent().getAction());
    }

    // Overriding this method since we expect intents to be sent to this activity.
    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent.getAction());
    }

    private void handleIntent(String action) {
        if (mWakeLock == null && !initializeWakeLock()) {
            return;
        }
        switch (action) {
            case "android.hdmicec.app.ACQUIRE_LOCK":
                acquireWakeLock();
                break;
            case "android.hdmicec.app.RELEASE_LOCK":
                releaseWakeLock();
                // Finish the activity after releasing the lock.
                finish();
                break;
            default:
                Log.i(TAG, "Unknown intent!");
        }
    }

    private boolean initializeWakeLock() {
        PowerManager powerManager = getSystemService(PowerManager.class);
        if (powerManager == null) {
            Log.i(TAG, "Failed to get PowerManager");
            return false;
        }
        // Creates a new wake lock.
        mWakeLock = powerManager.newWakeLock(PARTIAL_WAKE_LOCK, TAG);
        mWakeLock.setReferenceCounted(false);
        Log.i(TAG, "wake lock object is : " + mWakeLock.toString());
        return true;
    }

    private void acquireWakeLock() {
        if (!mWakeLock.isHeld()) {
            mWakeLock.acquire();
            Log.i(TAG, "Acquired wake lock.");
        } else {
            Log.i(TAG, "Wake lock is already acquired.");
        }
    }

    private void releaseWakeLock() {
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
            Log.i(TAG, "Released wake lock.");
        } else {
            Log.i(TAG, "No active wake locks to release.");
        }
    }
}
