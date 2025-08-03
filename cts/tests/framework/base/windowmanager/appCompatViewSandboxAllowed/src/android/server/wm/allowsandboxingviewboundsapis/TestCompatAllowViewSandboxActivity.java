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

package android.server.wm.allowsandboxingviewboundsapis;

import static android.content.pm.ActivityInfo.OVERRIDE_SANDBOX_VIEW_BOUNDS_APIS;
import static android.server.wm.allowsandboxingviewboundsapis.Components.ACTION_TEST_VIEW_SANDBOX_ALLOWED_PASSED;
import static android.server.wm.allowsandboxingviewboundsapis.Components.ACTION_TEST_VIEW_SANDBOX_NOT_ALLOWED_PASSED;

import android.app.Activity;
import android.app.compat.CompatChanges;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Arrays;

/**
 * Activity which verifies with {@link CompatChanges#isChangeEnabled} that
 * {@link android.content.pm.ActivityInfo#OVERRIDE_SANDBOX_VIEW_BOUNDS_APIS} is enabled
 * creates a {@link View}, calls
 * {@link View#getWindowVisibleDisplayFrame},
 * {@link View#getWindowDisplayFrame},
 * {@link View#getBoundsOnScreen},
 * {@link View#getLocationOnScreen}
 * and verifies that the results returned from these functions contain {@link Rect#left} coordinate
 * equal to 0 (if sandboxing is enabled) or greater than 0 (if sandboxing is disabled).
 * This also verifies that
 * {@link android.view.WindowManager#PROPERTY_COMPAT_ALLOW_SANDBOXING_VIEW_BOUNDS_APIS} value=true
 * in AndroidManifest.xml has no effect.
 */
public class TestCompatAllowViewSandboxActivity extends Activity implements
        ViewTreeObserver.OnDrawListener {

    private static final String TAG = TestCompatAllowViewSandboxActivity.class.getSimpleName();
    private boolean mFinishing = false;
    public View mView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.addView(new TextView(this));
        mView = layout;
        setContentView(mView);
        mView.getViewTreeObserver().addOnDrawListener(this);
    }

    @Override
    public void onDraw() {
        if (mFinishing) {
            return;
        }
        mFinishing = true;

        final boolean isCompatChangeEnabled = CompatChanges.isChangeEnabled(
                OVERRIDE_SANDBOX_VIEW_BOUNDS_APIS);

        final Rect visibleDisplayFrame = new Rect();
        mView.getWindowVisibleDisplayFrame(visibleDisplayFrame);

        final Rect displayFrame = new Rect();
        mView.getWindowDisplayFrame(displayFrame);

        final Rect boundsOnScreen = new Rect();
        mView.getBoundsOnScreen(boundsOnScreen, true);

        final int[] location = new int[]{ 0, 0 };
        mView.getLocationOnScreen(location);

        if (isCompatChangeEnabled
                && visibleDisplayFrame.left == 0
                && displayFrame.left == 0
                && boundsOnScreen.left == 0
                && location[0] == 0) {
            sendBroadcast(new Intent(ACTION_TEST_VIEW_SANDBOX_ALLOWED_PASSED));
        } else if (!isCompatChangeEnabled
                && visibleDisplayFrame.left > 0
                && displayFrame.left > 0
                && boundsOnScreen.left > 0
                && location[0] > 0) {
            sendBroadcast(new Intent(ACTION_TEST_VIEW_SANDBOX_NOT_ALLOWED_PASSED));
        } else {
            Log.e(TAG, "CompatChange value " + isCompatChangeEnabled);
            Log.e(TAG, "visibleDisplayFrame " + visibleDisplayFrame);
            Log.e(TAG, "displayFrame " + displayFrame);
            Log.e(TAG, "boundsOnScreen " + boundsOnScreen);
            Log.e(TAG, "location " + Arrays.toString(location));
        }
    }
}
