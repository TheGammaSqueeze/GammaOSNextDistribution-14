/*
 * Copyright 2023 The Android Open Source Project
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
package android.view.cts;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Choreographer;
import android.view.PointerIcon;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link Activity} that repeatedly creates tons of {@link Surface Surfaces} to put memory
 * pressure on the GC.
 */
public class SurfaceOOMTestActivity extends Activity implements Choreographer.FrameCallback,
        Choreographer.VsyncCallback {
    private static final String TAG = SurfaceOOMTestActivity.class.getSimpleName();

    private Choreographer mChoreographer;

    private FrameLayout mParent;
    private SurfaceView mSurfaceView;
    private Surface mSurface;
    private Paint mPaint;

    private CountDownLatch mReadyToStart = new CountDownLatch(1);
    private Handler mHandler;

    // Test State:
    private boolean mHasRendered;
    private CountDownLatch mFence;
    private int mCurrentRunWidth;
    private int mCurrentRunHeight;
    private long mNumSurfacesToCreate;
    private long mRemainingSurfacesToCreate;

    @Override
    public void onEnterAnimationComplete() {
        mReadyToStart.countDown();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
        decorView.setPointerIcon(
                PointerIcon.getSystemIcon(this, PointerIcon.TYPE_NULL));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mPaint = new Paint();
        mPaint.setColor(0xFF00FF);

        mParent = findViewById(android.R.id.content);
        mChoreographer = Choreographer.getInstance();
        mHandler = new Handler(Objects.requireNonNull(Looper.myLooper()));
    }

    /**
     * Schedules a loop of surface creation / destruction on the activity's main thread.
     *
     * A fence is created and waited on in the calling thread, and control will return once that
     * fence is triggered.
     */
    public void verifyCreatingManySurfaces(int width, int height, long numSurfacesToCreate) {
        awaitReadyState();

        assertTrue("numSurfacesToCreate must be greater than 0", numSurfacesToCreate > 0);

        Log.d(TAG, "Creating and rendering to " + numSurfacesToCreate + " surfaces.");

        mFence = new CountDownLatch(1);
        mCurrentRunWidth = width;
        mCurrentRunHeight = height;
        mNumSurfacesToCreate = numSurfacesToCreate;
        mRemainingSurfacesToCreate = numSurfacesToCreate;

        mHandler.post(() -> {
            setupNextFrame();
            mChoreographer.postFrameCallback(this);
        });

        try {
            assertTrue(
                    "Unable to finish creating many surfaces with " + mRemainingSurfacesToCreate
                            + "/" + numSurfacesToCreate + " remaining",
                    mFence.await(5, TimeUnit.MINUTES));
            mFence = null;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        mChoreographer.postVsyncCallback(this);

        if (mSurface == null) {
            // We don't have a surface yet, wait for the next frame.
            return;
        }

        Canvas c = mSurface.lockCanvas(null);
        c.drawRect(0, 0, c.getWidth(), c.getHeight(), mPaint);
        mSurface.unlockCanvasAndPost(c);

        mHasRendered = true;
    }

    @Override
    public void onVsync(@NonNull Choreographer.FrameData data) {
        if (!mHasRendered) {
            mChoreographer.postFrameCallback(this);
            return;
        }

        mRemainingSurfacesToCreate--;
        if (mRemainingSurfacesToCreate == 0) {
            mFence.countDown();
            Log.d(TAG, "Finished processing " + mNumSurfacesToCreate + " frames.");
            return;
        }

        setupNextFrame();
        mChoreographer.postFrameCallback(this);
    }

    private void awaitReadyState() {
        try {
            assertTrue(mReadyToStart.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void setupNextFrame() {
        Log.d(TAG, "Setting frame " + mRemainingSurfacesToCreate + "/"
                + mNumSurfacesToCreate + " left");

        mHasRendered = false;
        mSurface = null;
        if (mSurfaceView != null) {
            mParent.removeView(mSurfaceView);
        }
        mSurfaceView = new SurfaceView(this);
        mSurfaceView.setLayoutParams(
                new LinearLayout.LayoutParams(mCurrentRunWidth, mCurrentRunHeight));
        mParent.addView(mSurfaceView);

        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                mSurface = holder.getSurface();
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                    int height) {
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            }
        });
    }
}
