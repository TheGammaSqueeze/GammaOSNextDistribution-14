/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.car.kitchensink.display;

import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH;

import android.annotation.Nullable;
import android.content.Context;
import android.content.res.TypedArray;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.google.android.car.kitchensink.R;

import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Custom view that hosts a virtual display.
 */
public final class VirtualDisplayView extends SurfaceView {

    private static final String TAG = VirtualDisplayView.class.getSimpleName();

    private static final boolean REALLY_VERBOSE_IS_FINE = false;

    private static final int WAIT_TIMEOUT_MS = 4_000;
    private static final int NO_DISPLAY_ID = -42;

    private final Context mContext;
    private final InputManager mInputManager;
    private String mName = "LAYOUT XML, Y U NO HAVE A NAME?";

    private final SurfaceHolder.Callback mSurfaceViewCallback = new SurfaceHolder.Callback() {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.d(TAG, "surfaceCreated(): holder=" + holder);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.d(TAG, "surfaceChanged(): holder=" + holder + ", forma=:" + format
                    + ", width=" + width + ", height=" + height);

            if (mSurface != null) {
                Log.d(TAG, "Releasing old surface (" + mSurface + ")");
                mSurface.release();
            }

            mSurface = holder.getSurface();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.d(TAG, "surfaceDestroyed, holder: " + holder + ", detaching surface from"
                    + " display, surface: " + holder.getSurface());
            // Detaching surface is similar to turning off the display
            mSurface = null;
            if (mVirtualDisplay != null) {
                mVirtualDisplay.setSurface(null);
            }
        }
    };

    @Nullable
    private VirtualDisplay mVirtualDisplay;

    private int mDisplayId = NO_DISPLAY_ID;

    @Nullable
    private Surface mSurface;

    @Nullable
    private Handler mHandler;

    public VirtualDisplayView(Context context) {
        this(context, null);
    }

    public VirtualDisplayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        mContext = context;
        String name = getName(context, attrs);
        if (name != null) {
            mName = name;
        }
        mInputManager = context.getSystemService(InputManager.class);
        getHolder().addCallback(mSurfaceViewCallback);
    }

    // NOTE: it might be needed to handle focus and a11y events as well, but for now we'll assume
    // it's not needed and keep it simpler
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (REALLY_VERBOSE_IS_FINE) {
            Log.v(TAG, "onTouchEvent(" + event + ")");
        }

        if (mVirtualDisplay == null) {
            return super.onTouchEvent(event);
        }

        if (mDisplayId == NO_DISPLAY_ID) {
            // Shouldn't happen, but it doesn't hurt to check...
            Log.w(TAG, "onTouchEvent(): display id not set, calling super instead");
            return super.onTouchEvent(event);
        }

        // NOTE: it might be need to re-calculate the coordinates to offset the diplay, but
        // pparently it's working without it
        event.setDisplayId(mDisplayId);

        if (REALLY_VERBOSE_IS_FINE) {
            Log.v(TAG, "re-dispatching event after changing display id to " + mDisplayId);
        }
        if (mInputManager.injectInputEvent(event, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC)) {
            return true;
        }

        Log.w(TAG, "onTouchEvent(): not handled by display, calling super instead");
        return super.onTouchEvent(event);
    }

    /**
     * Sets the name of the display
     */
    public void setName(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        Log.v(TAG, "Changing name from " + mName + " to " + name);
        mName = name;
    }

    /**
     * Gets the name of the display
     */
    public String getName() {
        return mName;
    }

    /**
     * Creates the virtual display and return its id.
     */
    public int createVirtualDisplay() {
        if (mVirtualDisplay != null) {
            throw new IllegalStateException("Display already exist: " + mVirtualDisplay);
        }

        if (mSurface == null) {
            throw new IllegalStateException("Surface not created yet (or released)");
        }

        if (mHandler == null) {
            HandlerThread handlerThread = new HandlerThread("VirtualDisplayHelperThread");
            Log.i(TAG, "Starting " + handlerThread);
            handlerThread.start();
            mHandler = new Handler(handlerThread.getLooper());
        }

        CountDownLatch latch = new CountDownLatch(1);

        DisplayManager.DisplayListener listener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                Log.d(TAG, "onDisplayAdded(" + displayId + ")");
                latch.countDown();
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                Log.v(TAG, "onDisplayRemoved(" + displayId + ")");
            }

            @Override
            public void onDisplayChanged(int displayId) {
                Log.v(TAG, "onDisplayChanged(" + displayId + ")");
            }
        };

        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        DisplayMetrics metrics = new DisplayMetrics();
        displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY).getRealMetrics(metrics);
        Log.v(TAG, "Physical display size: " + metrics.widthPixels + " x " + metrics.heightPixels);
        Log.v(TAG, "View size: " + getWidth() + " x " + getHeight());

        Log.v(TAG, "Registering listener " + listener);
        displayManager.registerDisplayListener(listener, mHandler);

        int flags = VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                | VIRTUAL_DISPLAY_FLAG_PUBLIC
                | VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH;

        Log.d(TAG, "Creating display named '" + mName + "'");
        mVirtualDisplay = displayManager.createVirtualDisplay(mName,
                getWidth(), getHeight(), (int) metrics.xdpi, mSurface, flags);
        int displayId = mVirtualDisplay.getDisplay().getDisplayId();
        Log.i(TAG, "Created display with id " + displayId);
        boolean created = false;
        try {
            created = latch.await(WAIT_TIMEOUT_MS, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Log.e(TAG, "Interruped waiting for display callback", e);
            Thread.currentThread().interrupt();
        } finally {
            Log.v(TAG, "Unregistering listener " + listener);
            displayManager.unregisterDisplayListener(listener);
        }
        if (!created) {
            throw new IllegalStateException("Timed out (up to " + WAIT_TIMEOUT_MS
                    + "ms waiting for callback");
        }
        mDisplayId = displayId;
        return displayId;
    }

    /**
     * Deletes the virtual display.
     */
    public void deleteVirtualDisplay() {
        if (mVirtualDisplay == null) {
            throw new IllegalStateException("Display doesn't exist");
        }
        releaseDisplay();
    }

    /**
     * Gets the virtual display.
     */
    @Nullable
    public VirtualDisplay getVirtualDisplay() {
        return mVirtualDisplay;
    }

    /**
     * Releases the internal resources.
     */
    public void release() {
        releaseDisplay();

        if (mSurface != null) {
            Log.d(TAG, "Releasing surface");
            mSurface.release();
            mSurface = null;
        }
    }

    /**
     * Dumps its state.
     */
    public void dump(String prefix, PrintWriter writer) {
        writer.printf("%sName: %s\n", prefix, mName);
        writer.printf("%sSurface: %s\n", prefix, mSurface);
        writer.printf("%sVirtualDisplay: %s\n", prefix, mVirtualDisplay);
        writer.printf("%sDisplayId: %d%s\n", prefix, mDisplayId,
                (mDisplayId == NO_DISPLAY_ID ? " (not set)" : ""));
        writer.printf("%sHandler: %s\n", prefix, mHandler);
        writer.printf("%sWait timeout: %dms\n", prefix, WAIT_TIMEOUT_MS);
        writer.printf("%sREALLY_VERBOSE_IS_FINE: %b\n", prefix, REALLY_VERBOSE_IS_FINE);
    }

    private void releaseDisplay() {
        if (mVirtualDisplay != null) {
            Log.i(TAG, "Releasing display id " + mDisplayId);
            mVirtualDisplay.release();
            mVirtualDisplay = null;
            mDisplayId = NO_DISPLAY_ID;
        }
    }

    @Nullable
    static String getName(Context context, AttributeSet attrs) {
        if (attrs == null) {
            return null;
        }
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs,
                R.styleable.VirtualDisplayView, /* defStyleAttr= */ 0, /* defStyleRes= */ 0);
        try {
            return a.getString(R.styleable.VirtualDisplayView_name);
        } finally {
            a.recycle();
        }
    }
}
