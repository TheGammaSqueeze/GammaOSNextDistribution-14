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

package android.server.wm.jetpack.utils;

import android.app.Activity;
import android.os.Bundle;
import android.server.wm.jetpack.extensions.util.ExtensionsUtil;

import androidx.window.extensions.area.WindowAreaComponent;

/**
 * Test {@link Activity} used to initiate and enable the rear display presentation feature, and
 * explicitly crashes when the feature is enabled.
 */
public class TestRearDisplayCrashingActivity extends Activity {

    private WindowAreaComponent mWindowAreaComponent;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mWindowAreaComponent = ExtensionsUtil.getExtensionWindowAreaComponent();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        mWindowAreaComponent.startRearDisplayPresentationSession(this, integer -> {
            if (integer == WindowAreaComponent.SESSION_STATE_ACTIVE) {
                throw new RuntimeException("TEST EXCEPTION");
            }
        });
    }
}
