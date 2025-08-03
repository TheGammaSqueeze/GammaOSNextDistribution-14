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

package android.app.stubs.shared;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.os.ConditionVariable;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityEvent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/** Accessibility service that posts a window as soon as it's enabled. */
public class AppAccessibilityService extends AccessibilityService {
    private static final int BACKGROUND_COLOR = 0xFFFF0000;
    private static volatile CompletableFuture<AppAccessibilityService> sServiceFuture =
            new CompletableFuture<>();

    public static Future<AppAccessibilityService> getConnected() {
        return sServiceFuture;
    }

    private WindowManager mWindowManager;
    private View mView;

    /**
     * This doesn't need to be volatile because of the inner sync barriers of sServiceFuture. It's
     * set before sServiceFuture.obtrudeValue() and read after sServiceFuture.get().
     */
    private ConditionVariable mWindowAdded;

    @Override
    public void onCreate() {
        super.onCreate();
        mWindowManager = getSystemService(WindowManager.class);
    }

    /** Always call after {@link #getConnected()}}. */
    public boolean waitWindowAdded(long timeoutMs) {
        return mWindowAdded.block(timeoutMs);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    protected void onServiceConnected() {
        mWindowAdded = new ConditionVariable();
        sServiceFuture.obtrudeValue(this);
        mView = new CustomView(this);
        mView.setBackgroundColor(BACKGROUND_COLOR);
        LayoutParams params =
                new LayoutParams(
                        200,
                        200,
                        LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT);
        mWindowManager.addView(mView, params);
    }

    @Override
    public void onDestroy() {
        sServiceFuture = new CompletableFuture<>();
        if (mView != null) {
            mWindowManager.removeViewImmediate(mView);
        }
    }

    private class CustomView extends View {
        CustomView(Context context) {
            super(context);
        }
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            mWindowAdded.open();
        }
    }
}

