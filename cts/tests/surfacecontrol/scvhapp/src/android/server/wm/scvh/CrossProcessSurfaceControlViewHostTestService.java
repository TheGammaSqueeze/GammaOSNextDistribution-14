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

package android.server.wm.scvh;

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;

import static org.junit.Assert.assertTrue;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.server.wm.CtsWindowInfoUtils;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CrossProcessSurfaceControlViewHostTestService extends Service {
    private static final long WAIT_TIMEOUT_S = HW_TIMEOUT_MULTIPLIER * 5L;
    private static final String TAG = "CrossProcessSurfaceControlViewHostTestService";

    private final ICrossProcessSurfaceControlViewHostTestService mBinder = new ServiceImpl();
    private Handler mHandler;

    class MotionRecordingView extends View {
        boolean mGotEvent = false;
        boolean mGotObscuredEvent = false;

        private CountDownLatch mReceivedTouchLatch = new CountDownLatch(1);

        MotionRecordingView(Context c) {
            super(c);
        }

        public boolean onTouchEvent(MotionEvent e) {
            super.onTouchEvent(e);
            synchronized (this) {
                mGotEvent = true;
                if ((e.getFlags() & MotionEvent.FLAG_WINDOW_IS_OBSCURED) != 0) {
                    mGotObscuredEvent = true;
                }
            }
            mReceivedTouchLatch.countDown();
            return true;
        }

        boolean gotEvent() {
            synchronized (this) {
                return mGotEvent;
            }
        }

        boolean gotObscuredTouch() {
            synchronized (this) {
                return mGotObscuredEvent;
            }
        }

        void waitOnEvent() {
            try {
                mReceivedTouchLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
            }
        }
    }

    @Override
    public void onCreate() {
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder.asBinder();
    }

    Display getDefaultDisplay() {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        return wm.getDefaultDisplay();
    }

    SurfaceControlViewHost mSurfaceControlViewHost;
    MotionRecordingView mView;

    SurfaceControlViewHost.SurfacePackage createSurfacePackage(IBinder hostInputToken) {
        mView = new MotionRecordingView(this);
        mSurfaceControlViewHost = new SurfaceControlViewHost(this, getDefaultDisplay(),
                hostInputToken);
        mSurfaceControlViewHost.setView(mView, 100, 100);
        return mSurfaceControlViewHost.getSurfacePackage();
    }

    private class ServiceImpl extends ICrossProcessSurfaceControlViewHostTestService.Stub {
        private void drainHandler() {
            final CountDownLatch latch = new CountDownLatch(1);
            mHandler.post(latch::countDown);
            try {
                assertTrue("Failed to wait for handler to drain",
                        latch.await(HW_TIMEOUT_MULTIPLIER * 1L, TimeUnit.SECONDS));
            } catch (Exception e) {
            }
        }

        @Override
        public SurfaceControlViewHost.SurfacePackage getSurfacePackage(IBinder hostInputToken) {
            final CountDownLatch latch = new CountDownLatch(1);
            mHandler.post(() -> {
                createSurfacePackage(hostInputToken);
                mView.getViewTreeObserver().registerFrameCommitCallback(latch::countDown);
                mView.invalidate();
            });
            try {
                assertTrue("Failed to wait for frame to draw",
                        latch.await(HW_TIMEOUT_MULTIPLIER * 1L, TimeUnit.SECONDS));
            } catch (Exception e) {
                return null;
            }
            return mSurfaceControlViewHost.getSurfacePackage();
        }

        @Override
        public boolean getViewIsTouched() {
            drainHandler();
            mView.waitOnEvent();
            return mView.gotEvent();
        }

        @Override
        public boolean getViewIsTouchedAndObscured() {
            return getViewIsTouched() && mView.gotObscuredTouch();
        }

        @Override
        public IBinder getWindowToken() {
            return mView.getWindowToken();
        }

        @Override
        public boolean waitForFocus(boolean waitForFocus) {
            return CtsWindowInfoUtils.waitForWindowFocus(mView, waitForFocus);
        }

        @Override
        public void setKeepScreenOnFlag(boolean keepScreenOn) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            mHandler.post(() -> {
                mView.setKeepScreenOn(keepScreenOn);
                mView.getViewTreeObserver().addOnDrawListener(countDownLatch::countDown);
            });

            try {
                countDownLatch.await(WAIT_TIMEOUT_S, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to set keep screen on flag");
            }
        }
    }
}
