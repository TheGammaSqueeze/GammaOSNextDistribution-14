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

package com.android.devicelockcontroller.storage;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.devicelockcontroller.util.LogUtil;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * This class implements a client what automatically binds to a service and allows
 * asynchronous calls.
 * Subclasses can invoke the "call" method, that is responsible for binding and returning a
 * listenable future.
 */
abstract class DlcClient {
    private static final String TAG = "DlcClient";
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private IBinder mDlcService;

    @GuardedBy("mLock")
    private ServiceConnection mServiceConnection;

    private Context mContext;

    private final ComponentName mComponentName;
    private final ListeningExecutorService mListeningExecutorService;

    private class DlcServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (mLock) {
                mDlcService = service;
                mLock.notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // Binding still valid, unbind anyway so we can bind again as needed.
            unbind();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            unbind();
        }
    }

    DlcClient(Context context, ComponentName componentName,
            @Nullable ListeningExecutorService executorService) {
        mContext = context;
        mComponentName = componentName;
        mListeningExecutorService = executorService;
    }

    IBinder getService() {
        synchronized (mLock) {
            return Objects.requireNonNull(mDlcService);
        }
    }

    @VisibleForTesting
    public void setService(IBinder service) {
        synchronized (mLock) {
            mDlcService = service;
        }
    }

    @GuardedBy("mLock")
    private boolean bindLocked() {
        if (mDlcService != null || mServiceConnection != null) {
            return true;
        }

        mServiceConnection = new DlcServiceConnection();

        final Intent service = new Intent().setComponent(mComponentName);
        final boolean bound = mContext.bindService(service, mServiceConnection,
                Context.BIND_AUTO_CREATE);

        if (bound) {
            LogUtil.i(TAG, "Binding " + mComponentName.flattenToShortString());
        } else {
            // As per bindService() documentation, we still need to call unbindService()
            // if binding fails.
            mContext.unbindService(mServiceConnection);
            mServiceConnection = null;
            LogUtil.e(TAG, "Binding " + mComponentName.flattenToShortString() + " failed.");
        }

        return bound;
    }

    @GuardedBy("mLock")
    private void unbindLocked() {
        if (mServiceConnection == null) {
            return;
        }

        LogUtil.i(TAG, "Unbinding " + mComponentName.flattenToShortString());

        mContext.unbindService(mServiceConnection);

        mDlcService = null;
        mServiceConnection = null;
    }

    private void unbind() {
        synchronized (mLock) {
            unbindLocked();
        }
    }

    protected <T> ListenableFuture<T> call(Callable<T> callable) {
        return mListeningExecutorService.submit(() -> {
            synchronized (mLock) {
                if (bindLocked()) {
                    while (mDlcService == null) {
                        try {
                            mLock.wait();
                        } catch (InterruptedException e) {
                            // Nothing to do.
                        }
                    }
                    return callable.call();
                }
            }
            throw new Exception("Failed to call remote DLC API");
        });
    }

    void tearDown() {
        unbind();
        mContext = null;
    }
}
