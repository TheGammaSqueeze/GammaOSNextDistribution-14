/*
 * Copyright (C) 2009 The Android Open Source Project
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
package android.media.cts;

import android.app.Activity;
import android.content.Intent;
import android.media.cts.R;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.ApiLevelUtil;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MediaStubActivity extends Activity {
    private static final String TAG = "MediaStubActivity";
    public static final String INTENT_EXTRA_NO_TITLE = "NoTitle";

    private SurfaceHolder mHolder;
    private SurfaceHolder mHolder2;

    private final Lock mLock = new ReentrantLock();
    private final Condition mCondition = mLock.newCondition();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.R)) {
            Intent intent = getIntent();
            if (intent.getBooleanExtra(INTENT_EXTRA_NO_TITLE, false)) {
                hideTitle();
            }
        }
        setTurnScreenOn(true);
        setShowWhenLocked(true);

        setContentView(R.layout.mediaplayer);

        SurfaceView surfaceV = (SurfaceView)findViewById(R.id.surface);
        mHolder = surfaceV.getHolder();

        SurfaceView surfaceV2 = (SurfaceView)findViewById(R.id.surface2);
        mHolder2 = surfaceV2.getHolder();
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");
        super.onPause();
    }

    /*
     * Wait until the surface associated with this SurfaceHolder
     * is created.
     */
    private void waitTillSurfaceHolderIsCreated(Surface surface,
            long waitTimeMs) throws InterruptedException {
        mLock.lock();
        try {
            final long start = SystemClock.elapsedRealtime();

            while (!surface.isValid() && (SystemClock.elapsedRealtime() - start) < waitTimeMs) {
                mCondition.await(waitTimeMs, TimeUnit.MILLISECONDS);
            }
        } finally {
            mLock.unlock();
        }
        if (!surface.isValid()) {
            throw new InterruptedException("Taking too long to attach a SurfaceHolder the window.");
        }
    }

    /*
     * Get a valid Surface associated with the given SurfaceHolder.
     * It will wait until the Surface is ready/created.
     *
     * If the surface is not ready after waiting for a predefined timeout,
     * it will return null.
     */
    public Surface getSurface(SurfaceHolder holder) {
        Surface surface = holder.getSurface();

        // Check if the surface has been create already.
        if (surface.isValid()) {
            return surface;
        }

        // register for information about changes to the surface
        // so that we know when the surface associated with the SurfaceHolder
        // is created.
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                Log.d(TAG, "surfaceCreated");
                mLock.lock();
                try {
                    mCondition.signalAll();
                } finally {
                    mLock.unlock();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.v(TAG, "surfaceChanged: " + format + " " + width + " " + height);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.d(TAG, "surfaceDestroyed");
            }
        });

        // Wait until the surface is available.
        try {
            // Wait for at most 3 seconds for the surface to be ready.
            waitTillSurfaceHolderIsCreated(surface, 3000);
        } catch (InterruptedException e) {
            Log.e(TAG, "Caught exception: " + e);
            return null;
        }
        return surface;
    }

    public SurfaceHolder getSurfaceHolder() {
        return mHolder;
    }

    public SurfaceHolder getSurfaceHolder2() {
        return mHolder2;
    }

    /** Note: Must be called from the thread used to create this activity. */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
    public void hideSystemBars() {
        var surfaceV = (SurfaceView)findViewById(R.id.surface);
        WindowInsetsController windowInsetsController = surfaceV.getWindowInsetsController();
        if (windowInsetsController == null) {
            return;
        }
        // Configure the behavior of the hidden system bars
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        // Hide both the status bar and the navigation bar
        windowInsetsController.hide(WindowInsets.Type.systemBars());
    }

    /** Note: Must be called before {@code setContentView}. */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
    private void hideTitle() {
        getWindow().requestFeature(Window.FEATURE_NO_TITLE);
    }

}
