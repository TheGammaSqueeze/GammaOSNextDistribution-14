/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.deviceandprofileowner;

import android.app.admin.DevicePolicyKeyguardService;
import android.content.Context;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Service that provides the secondary lockscreen content when the secondary lockscreen is enabled.
 *
 * <p>Handles the {@link DevicePolicyManager#ACTION_BIND_SECONDARY_LOCKSCREEN_SERVICE} action and
 * in used in conjunction with {@link DevicePolicyManager.setSecondaryLockscreenEnabled}.
 */
public class SimpleKeyguardService extends DevicePolicyKeyguardService {

    public static final String TITLE_LABEL = "SimpleKeyguardService Title";
    public static final String DISMISS_BUTTON_LABEL = "Dismiss";
    private static final String TAG = "SimpleKeyguardService";

    @Override
    public SurfaceControlViewHost.SurfacePackage onCreateKeyguardSurface(IBinder hostInputToken) {
        Log.d(TAG, "onCreateKeyguardSurface()");
        Context context = getApplicationContext();

        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();

        SurfaceControlViewHost wvr = new SurfaceControlViewHost(context, display, hostInputToken);
        wvr.setView(createView(context), displayMetrics.widthPixels, displayMetrics.heightPixels);

        return wvr.getSurfacePackage();
    }

    private View createView(Context context) {
        TextView title = new TextView(context);
        title.setText(TITLE_LABEL);

        Button button = new Button(context);
        // Avoid potential all caps text transformation on button. (eg. b/172993563)
        button.setTransformationMethod(null);
        button.setText(DISMISS_BUTTON_LABEL);
        button.setOnClickListener(ignored -> {
            button.setText("Dismissing...");
            button.setEnabled(false);
            dismiss();
        });

        LinearLayout rootView = new LinearLayout(context);
        rootView.setOrientation(LinearLayout.VERTICAL);
        rootView.setGravity(Gravity.CENTER);
        rootView.setBackgroundColor(Color.WHITE);
        rootView.addView(title);
        rootView.addView(button);
        return rootView;
    }
}
