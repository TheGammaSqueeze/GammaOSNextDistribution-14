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

package com.android.ondevicepersonalization.libraries.plugin.internal;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import com.google.common.util.concurrent.SettableFuture;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Implementation of the PluginExecutorServiceProvider interface. */
public final class PluginExecutorServiceProviderImpl implements PluginExecutorServiceProvider {
    private static final String TAG = PluginExecutorServiceProviderImpl.class.getSimpleName();
    private static final Executor SINGLE_THREAD_EXECUTOR = Executors.newSingleThreadExecutor();
    private final Context mContext;
    private IPluginExecutorService mPluginExecutorService = null;
    private SettableFuture<Boolean> mPluginExecutorServiceReadiness = SettableFuture.create();
    private boolean mBound;

    private void reset() {
        mPluginExecutorService = null;
        mBound = false;
        mPluginExecutorServiceReadiness = SettableFuture.create();
    }

    private final ServiceConnection mConnection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    if (service == null) {
                        Log.e(TAG, "onServiceConnected() received null binder");
                        return;
                    }
                    mPluginExecutorService = IPluginExecutorService.Stub.asInterface(service);
                    mBound = true;
                    mPluginExecutorServiceReadiness.set(true);
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    reset();
                }
            };

    public PluginExecutorServiceProviderImpl(Context context) {
        this.mContext = context;
    }

    @Override
    public @Nullable IPluginExecutorService getExecutorService() {
        return mPluginExecutorService;
    }

    @Override
    public SettableFuture<Boolean> getExecutorServiceReadiness() {
        return mPluginExecutorServiceReadiness;
    }

    @Override
    public boolean bindService() {
        Intent intent = new Intent(mContext, PluginExecutorService.class);
        return mContext.bindService(
                intent, Context.BIND_AUTO_CREATE, SINGLE_THREAD_EXECUTOR, mConnection);
    }

    @Override
    public void unbindService() {
        if (mBound) {
            mContext.unbindService(mConnection);
            reset();
        }
    }
}
