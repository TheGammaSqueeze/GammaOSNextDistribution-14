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

package android.view.accessibility.cts;

import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public abstract class AccessibilityTestActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        turnOnScreen();
        super.onCreate(savedInstanceState);
    }

    private void turnOnScreen() {
        // Call setTurnScreenOn in conjunction with showWhenLocked, to make sure the activity is
        // visible when the lockscreen is up.
        setTurnScreenOn(true);
        setShowWhenLocked(true);
        final Window window = getWindow();
        // FLAG_KEEP_SCREEN_ON will ensure the screen stays on and is bright as long as the window
        // is visible.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        KeyguardManager keyguardManager = getSystemService(KeyguardManager.class);
        if (keyguardManager != null) {
            keyguardManager.requestDismissKeyguard(this, null);
        } else {
            // Instant apps don't have access to KeyguardManager
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
    }
}
