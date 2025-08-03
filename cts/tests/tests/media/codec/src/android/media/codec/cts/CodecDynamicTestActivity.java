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

package android.media.codec.cts;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CodecDynamicTestActivity extends Activity implements SurfaceHolder.Callback {
    private static final String LOG_TAG = CodecDynamicTestActivity.class.getSimpleName();

    private final int mMaxSurfaces = 32;
    private LinearLayout mLayOutList;
    private final ArrayList<SurfaceView> mSurfaceViews = new ArrayList<>();
    private final ArrayList<SurfaceHolder> mHolders = new ArrayList<>();
    private final ArrayList<Surface> mSurfaces = new ArrayList<>();
    private final Lock[] mLocks = new Lock[mMaxSurfaces];
    private final Condition[] mConditions = new Condition[mMaxSurfaces];
    private final ReentrantLock mMutex = new ReentrantLock();
    private final boolean[] mIsUsable = new boolean[mMaxSurfaces];

    public int addSurfaceView() {
        if (mMaxSurfaces == mSurfaceViews.size()) {
            throw new RuntimeException("number of surfaceViews exceed preconfigured limit");
        }
        View view = getLayoutInflater().inflate(R.layout.display_surface_layout, mLayOutList,
                false);
        SurfaceView surfaceView = view.findViewById(R.id.add_surface);
        SurfaceHolder holder = surfaceView.getHolder();
        holder.addCallback(this);
        int index = mSurfaceViews.size();
        mLocks[index].lock();
        mSurfaceViews.add(surfaceView);
        mSurfaces.add(null);
        mHolders.add(holder);
        mLocks[index].unlock();
        runOnUiThread(() -> mLayOutList.addView(view));
        return index;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(LOG_TAG, "onCreate");
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setTurnScreenOn(true);
        setShowWhenLocked(true);

        setContentView(R.layout.main_layout);
        mLayOutList = findViewById(R.id.layout_list);
        for (int i = 0; i < mMaxSurfaces; i++) {
            mIsUsable[i] = false;
            mLocks[i] = new ReentrantLock();
            mConditions[i] = mLocks[i].newCondition();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(LOG_TAG, "surface created");
        int index = mHolders.indexOf(holder);
        mLocks[index].lock();
        mSurfaces.set(index, mHolders.get(index).getSurface());
        mLocks[index].unlock();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.v(LOG_TAG, "surface changed " + format + " " + width + " " + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(LOG_TAG, "surface deleted");
        int index = mHolders.indexOf(holder);
        markSurface(index, false);
        mLocks[index].lock();
        mSurfaces.set(index, null);
        mLocks[index].unlock();
    }

    public void waitTillSurfaceIsCreated(int index) throws InterruptedException {
        final long mWaitTimeMs = 1000;
        final int retries = 3;
        mLocks[index].lock();
        final long start = SystemClock.elapsedRealtime();
        while ((SystemClock.elapsedRealtime() - start) < (retries * mWaitTimeMs)
                && mSurfaces.get(index) == null) {
            mConditions[index].await(mWaitTimeMs, TimeUnit.MILLISECONDS);
        }
        mLocks[index].unlock();
        if (mSurfaces.get(index) == null) {
            throw new InterruptedException("Taking too long to attach a SurfaceView to a window.");
        }
        markSurface(index, true);
    }

    public Pair<Integer, Surface> getSurface() {
        Pair<Integer, Surface> obj = null;
        mMutex.lock();
        for (int index = 0; index < mSurfaces.size(); index++) {
            if (mIsUsable[index]) {
                mIsUsable[index] = false;
                obj = Pair.create(index, mSurfaces.get(index));
                break;
            }
        }
        mMutex.unlock();
        return obj;
    }

    public void markSurface(int index, boolean usable) {
        mMutex.lock();
        mIsUsable[index] = usable;
        mMutex.unlock();
    }
}
