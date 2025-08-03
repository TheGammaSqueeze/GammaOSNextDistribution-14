/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.view.cts.surfacevalidator;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;

import java.util.concurrent.CountDownLatch;

public class SurfaceControlViewHostHelper {
    private final String mTag;

    private boolean mIsAttached;

    private IAttachEmbeddedWindow mIAttachEmbeddedWindow;

    private SurfaceView mSurfaceView;

    private final int mDisplayId;

    private final long mDelayMs;
    private SurfaceControlViewHost.SurfacePackage mSurfacePackage;

    private final Size mInitialSize;

    private boolean mSurfaceCreated;

    private boolean mSurfaceViewAttached;

    private final CountDownLatch mReadyLatch;

    private final Context mContext;

    HandlerThread mHandlerThread;

    public SurfaceControlViewHostHelper(String tag, CountDownLatch countDownLatch, Context context,
            long delayMs, Size initialSize) {
        mTag = tag;
        mContext = context;
        mDisplayId = context.getDisplayId();
        mDelayMs = delayMs;
        mInitialSize = initialSize;
        mReadyLatch = countDownLatch;
        mHandlerThread = new HandlerThread("SurfaceControlViewHostHelper");
        mHandlerThread.start();
    }

    public SurfaceView attachSurfaceView(ViewGroup parent, ViewGroup.LayoutParams layoutParams) {
        mSurfaceView = new SurfaceView(mContext);
        mSurfaceView.getHolder().addCallback(mCallback);
        mSurfaceView.getViewTreeObserver().addOnWindowAttachListener(
                new ViewTreeObserver.OnWindowAttachListener() {
                    @Override
                    public void onWindowAttached() {
                        mSurfaceViewAttached = true;
                        attachEmbedded();
                    }

                    @Override
                    public void onWindowDetached() {
                        mSurfaceViewAttached = false;
                    }
                });
        parent.addView(mSurfaceView, layoutParams);
        return mSurfaceView;
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        // Called when the connection with the service is established
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(mTag, "Service Connected");
            mIAttachEmbeddedWindow = IAttachEmbeddedWindow.Stub.asInterface(service);
            attachEmbedded();
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d(mTag, "Service Disconnected");
            mIAttachEmbeddedWindow = null;
            mIsAttached = false;
        }
    };

    final SurfaceHolder.Callback mCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            Log.d(mTag, "surface created");
            mSurfaceCreated = true;
            attachEmbedded();
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                int height) {
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        }
    };

    private boolean isReadyToAttach() {
        if (!mSurfaceCreated) {
            Log.d(mTag, "surface is not created");
        }
        if (!mSurfaceViewAttached) {
            Log.d(mTag, "SurfaceView is not attached to window");
        }
        if (mIAttachEmbeddedWindow == null) {
            Log.d(mTag, "Service is not attached");
        }
        if (mIsAttached) {
            Log.d(mTag, "Already attached");
        }

        return mSurfaceCreated && mIAttachEmbeddedWindow != null && !mIsAttached;
    }

    private void attachEmbedded() {
        if (!isReadyToAttach()) {
            return;
        }
        mIsAttached = true;

        Handler handler = new Handler(mHandlerThread.getLooper());
        handler.post(() -> {
            try {
                mSurfacePackage = mIAttachEmbeddedWindow.attachEmbedded(mSurfaceView.getHostToken(),
                        mInitialSize.getWidth(), mInitialSize.getHeight(), mDisplayId, mDelayMs);
                mSurfaceView.setChildSurfacePackage(mSurfacePackage);
            } catch (RemoteException e) {
                Log.e(mTag, "Failed to attach embedded window");
            }

            mReadyLatch.countDown();
        });
    }

    public SurfaceControlViewHost.SurfacePackage getSurfacePackage() {
        return mSurfacePackage;
    }

    public IAttachEmbeddedWindow getAttachedEmbeddedWindow() {
        return mIAttachEmbeddedWindow;
    }

    public void bindEmbeddedService(boolean inProcess) {
        Class<? extends EmbeddedSCVHService> classToBind;
        if (inProcess) {
            classToBind = InProcessEmbeddedSCVHService.class;
        } else {
            classToBind = EmbeddedSCVHService.class;
        }
        Intent intent = new Intent(mContext, classToBind);
        intent.setAction(classToBind.getName());
        mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    // Use this one for in process requests.
    public static class InProcessEmbeddedSCVHService extends EmbeddedSCVHService {
    }
}
