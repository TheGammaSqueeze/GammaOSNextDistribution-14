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

package android.accessibilityservice.cts.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

public class NotTouchableWindowTestActivity extends AccessibilityTestActivity  {
    private class CommandReceiver extends BroadcastReceiver {
        private View rootView;

        @Override
        public void onReceive(Context context, Intent intent) {
            WindowManager.LayoutParams params;
            switch (intent.getAction()) {
                case ADD_WINDOW:
                    if (rootView != null) {
                        throw new IllegalStateException("Window already exists");
                    }
                    rootView = new View(context);
                    params = createDefaultWindowParams();
                    context.getSystemService(WindowManager.class).addView(rootView, params);
                    break;

                case ADD_TRUSTED_WINDOW:
                    if (rootView != null) {
                        throw new IllegalStateException("Window already exists");
                    }
                    rootView = new Button(context);
                    params = createDefaultWindowParams();
                    params.privateFlags |= PRIVATE_FLAG_TRUSTED_OVERLAY;
                    context.getSystemService(WindowManager.class).addView(rootView, params);
                    break;

                case FINISH_ACTIVITY:
                    finish();
            }
        }

    }

    private BroadcastReceiver mBroadcastReceiver = new CommandReceiver();

    public static final String TITLE =
            "NotTouchableWindowTestActivity";
    public static final String NON_TOUCHABLE_WINDOW_TITLE =
            "android.accessibilityservice.cts.activities.NON_TOUCHABLE_WINDOW_TITLE";

    public static final String ADD_WINDOW =
            "android.accessibilityservice.cts.ADD_WINDOW";
    public static final String ADD_TRUSTED_WINDOW =
            "android.accessibilityservice.cts.ADD_TRUSTED_WINDOW";
    public static final String FINISH_ACTIVITY =
            "android.accessibilityservice.cts.FINISH_ACTIVITY";

    // From WindowManager.java.
    public static final int PRIVATE_FLAG_TRUSTED_OVERLAY = 0x20000000;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(ADD_WINDOW);
        filter.addAction(ADD_TRUSTED_WINDOW);
        filter.addAction(FINISH_ACTIVITY);
        this.registerReceiver(mBroadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        setTitle(TITLE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        this.unregisterReceiver(mBroadcastReceiver);
    }

    private static WindowManager.LayoutParams createDefaultWindowParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
        params.setFitInsetsTypes(0);
        params.gravity = Gravity.TOP;
        params.setTitle(NON_TOUCHABLE_WINDOW_TITLE);

        return params;
    }
}