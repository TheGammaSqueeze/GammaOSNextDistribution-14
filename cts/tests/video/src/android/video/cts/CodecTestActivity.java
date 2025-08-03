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

package android.video.cts;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CodecTestActivity extends Activity implements SurfaceHolder.Callback {
    private static final String LOG_TAG = CodecTestActivity.class.getSimpleName();
    private SurfaceView mSurfaceView;
    private SurfaceHolder mHolder;
    private Surface mSurface;
    private final Lock mLock = new ReentrantLock();
    private final Condition mCondition = mLock.newCondition();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.media_decoder_surface_layout);
        mSurfaceView = findViewById(R.id.surface);
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(LOG_TAG, "surface created");
        mLock.lock();
        mSurface = mHolder.getSurface();
        mLock.unlock();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.v(LOG_TAG, "surface changed " + format + " " + width + " " + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(LOG_TAG, "surface deleted");
        mLock.lock();
        mSurface = null;
        mLock.unlock();
    }

    public void waitTillSurfaceIsCreated() throws InterruptedException {
        final long mWaitTimeMs = 1000;
        final int retries = 3;
        mLock.lock();
        final long start = SystemClock.elapsedRealtime();
        while ((SystemClock.elapsedRealtime() - start) < (retries * mWaitTimeMs) &&
                mSurface == null) {
            mCondition.await(mWaitTimeMs, TimeUnit.MILLISECONDS);
        }
        mLock.unlock();
        if (mSurface == null) {
            throw new InterruptedException("Taking too long to attach a SurfaceView to a window.");
        }
    }

    public Surface getSurface() { return mSurface; }
}
