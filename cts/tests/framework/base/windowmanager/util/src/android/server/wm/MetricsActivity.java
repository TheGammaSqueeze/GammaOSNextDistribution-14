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

package android.server.wm;

import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import android.os.Bundle;
import android.view.WindowManager;
import android.view.WindowMetrics;

public class MetricsActivity extends WindowManagerTestBase.FocusableActivity {
    private WindowMetrics mOnCreateMaximumMetrics;
    private WindowMetrics mOnCreateCurrentMetrics;
    private final WindowMetricsTestHelper.OnLayoutChangeListener mListener =
            new WindowMetricsTestHelper.OnLayoutChangeListener();

    public WindowMetricsTestHelper.OnLayoutChangeListener getListener() {
        return mListener;
    }

    public WindowMetrics getOnCreateMaximumMetrics() {
        return mOnCreateMaximumMetrics;
    }

    public WindowMetrics getOnCreateCurrentMetrics() {
        return mOnCreateCurrentMetrics;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mOnCreateCurrentMetrics = getWindowManager().getCurrentWindowMetrics();
        mOnCreateMaximumMetrics = getWindowManager().getMaximumWindowMetrics();
        getWindow().getDecorView().addOnLayoutChangeListener(mListener);

        // Always extend the cutout areas because layout doesn't get the waterfall cutout.
        final WindowManager.LayoutParams attrs = getWindow().getAttributes();
        attrs.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        getWindow().setAttributes(attrs);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getWindow().getDecorView().removeOnLayoutChangeListener(mListener);
    }
}
