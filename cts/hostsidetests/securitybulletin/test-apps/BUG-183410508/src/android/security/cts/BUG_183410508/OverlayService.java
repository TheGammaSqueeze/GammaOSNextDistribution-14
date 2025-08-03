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

package android.security.cts.BUG_183410508;

import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;

/** Service that starts the overlay for the test. */
public final class OverlayService extends Service {
    public Button mButton;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mLayoutParams;

    @Override
    public void onCreate() {
        Log.d(Constants.TAG, "onCreate() called");
        super.onCreate();

        DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
        int scaledWidth = (int) (displayMetrics.widthPixels * 0.9);
        int scaledHeight = (int) (displayMetrics.heightPixels * 0.9);

        mWindowManager = getSystemService(WindowManager.class);
        mLayoutParams = new WindowManager.LayoutParams();
        mLayoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        mLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        mLayoutParams.format = PixelFormat.OPAQUE;
        mLayoutParams.gravity = Gravity.CENTER;
        mLayoutParams.width = scaledWidth;
        mLayoutParams.height = scaledHeight;
        mLayoutParams.x = scaledWidth / 2;
        mLayoutParams.y = scaledHeight / 2;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(Constants.TAG, "onStartCommand() called");
        showFloatingWindow();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d(Constants.TAG, "onDestroy() called");
        if (mWindowManager != null && mButton != null) {
            mWindowManager.removeView(mButton);
        }
        super.onDestroy();
    }

    private void showFloatingWindow() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(Constants.TAG, "Cannot show overlay window. Permission denied");
        }

        mButton = new Button(getApplicationContext());
        mButton.setText(getResources().getString(R.string.tapjacking_text));
        mButton.setTag(mButton.getVisibility());
        mWindowManager.addView(mButton, mLayoutParams);

        new Handler(Looper.myLooper()).postDelayed(this::stopSelf, 60_000);
        Log.d(Constants.TAG, "Floating window button created");
    }
}
