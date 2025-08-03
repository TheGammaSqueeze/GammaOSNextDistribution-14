/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.display.cts;

import android.app.Activity;
import android.os.Bundle;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.Nullable;

/**
 * Test activity to exercise getting metrics for displays.
 */
public class DisplayTestActivity extends Activity {

    private static final String PREFERRED_DISPLAY_MODE_ID = "preferred_display_mode_id";
    private int mPreferredDisplayModeId = 0;

    @Override
    public void onSaveInstanceState(Bundle outBundle) {
        super.onSaveInstanceState(outBundle);

        outBundle.putInt(PREFERRED_DISPLAY_MODE_ID, mPreferredDisplayModeId);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mPreferredDisplayModeId = savedInstanceState.getInt(PREFERRED_DISPLAY_MODE_ID, 0);
            resetLayoutParams();
        }
    }

    /** Set an override for the display mode. This is called directly from test instrumentation. */
    public void setPreferredDisplayMode(Display.Mode mode) {
        mPreferredDisplayModeId = mode.getModeId();
        resetLayoutParams();
    }

    private void resetLayoutParams() {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.preferredDisplayModeId = mPreferredDisplayModeId;
        getWindow().setAttributes(params);
    }
}
