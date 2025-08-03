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

package com.android.ondevicepersonalization.libraries.plugin.impl;

import android.content.Context;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import com.android.ondevicepersonalization.libraries.plugin.FailureType;
import com.android.ondevicepersonalization.libraries.plugin.PluginApplication;
import com.android.ondevicepersonalization.libraries.plugin.PluginCallback;
import com.android.ondevicepersonalization.libraries.plugin.PluginController;
import com.android.ondevicepersonalization.libraries.plugin.PluginHost;
import com.android.ondevicepersonalization.libraries.plugin.PluginInfo;
import com.android.ondevicepersonalization.libraries.plugin.PluginState;
import com.android.ondevicepersonalization.libraries.plugin.PluginStateCallback;
import com.android.ondevicepersonalization.libraries.plugin.internal.CallbackConverter;
import com.android.ondevicepersonalization.libraries.plugin.internal.IPluginCallback;
import com.android.ondevicepersonalization.libraries.plugin.internal.IPluginExecutorService;
import com.android.ondevicepersonalization.libraries.plugin.internal.IPluginStateCallback;
import com.android.ondevicepersonalization.libraries.plugin.internal.PluginArchiveManager;
import com.android.ondevicepersonalization.libraries.plugin.internal.PluginExecutorServiceProvider;
import com.android.ondevicepersonalization.libraries.plugin.internal.PluginInfoInternal;

import com.google.common.util.concurrent.SettableFuture;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Implementation of {@link PluginController} that executes {@link Plugin} implementations in the
 * {@link IPluginExecutorService} provided by the passed in {@link PluginExecutorServiceProvider}.
 */
public class PluginControllerImpl implements PluginController {
    private static final String TAG = PluginControllerImpl.class.getSimpleName();
    private final PluginInfo mInfo;
    private final Context mContext;
    private final PluginExecutorServiceProvider mPluginExecutorServiceProvider;
    private final PluginArchiveManager mPluginArchiveManager;

    public PluginControllerImpl(
            Context context,
            PluginExecutorServiceProvider pluginExecutorServiceProvider,
            PluginInfo info) {
        this.mContext = context;
        this.mInfo = info;
        this.mPluginExecutorServiceProvider = pluginExecutorServiceProvider;
        this.mPluginArchiveManager = new PluginArchiveManager(context);
    }

    private void loadInternal(PluginInfoInternal infoInternal, PluginCallback callback)
            throws RemoteException {
        Context applicationContext = mContext.getApplicationContext();
        @Nullable PluginApplication pluginApplication = null;
        @Nullable PluginHost pluginHost = null;
        if (applicationContext instanceof PluginApplication) {
            pluginApplication = (PluginApplication) applicationContext;
            pluginHost = pluginApplication.getPluginHost();
        }

        @Nullable Bundle pluginContextInitData = null;
        if (pluginHost != null) {
            pluginContextInitData = pluginHost.createPluginContextInitData(mInfo.taskName());
        }

        IPluginCallback parcelablePluginCallback = CallbackConverter.toIPluginCallback(callback);

        mPluginExecutorServiceProvider
                .getExecutorService()
                .load(infoInternal, parcelablePluginCallback, pluginContextInitData);
    }

    @Override
    public void load(PluginCallback callback) throws RemoteException {
        if (!mPluginExecutorServiceProvider.bindService()) {
            Log.e(TAG, "Failed to bind to service");
            callback.onFailure(FailureType.ERROR_LOADING_PLUGIN);
            return;
        }

        PluginInfoInternal.Builder infoBuilder = PluginInfoInternal.builder();
        infoBuilder.setTaskName(mInfo.taskName());
        infoBuilder.setEntryPointClassName(mInfo.entryPointClassName());

        PluginArchiveManager.PluginTask task = infoInternal -> loadInternal(infoInternal, callback);

        SettableFuture<Boolean> serviceReadiness =
                mPluginExecutorServiceProvider.getExecutorServiceReadiness();

        if (!mPluginArchiveManager.copyPluginArchivesToCacheAndAwaitService(
                serviceReadiness, "PluginExecutorService", infoBuilder, mInfo.archives(), task)) {
            callback.onFailure(FailureType.ERROR_LOADING_PLUGIN);
        }
    }

    @Override
    public void unload(PluginCallback callback) throws RemoteException {
        IPluginExecutorService pluginExecutorService =
                mPluginExecutorServiceProvider.getExecutorService();
        if (pluginExecutorService == null) {
            callback.onFailure(FailureType.ERROR_UNLOADING_PLUGIN);
            return;
        }

        IPluginCallback parcelablePluginCallback = CallbackConverter.toIPluginCallback(callback);
        try {
            pluginExecutorService.unload(mInfo.taskName(), parcelablePluginCallback);
            mPluginExecutorServiceProvider.unbindService();
        } catch (RemoteException e) {
            // This callback call may throw RemoteException, which we pass on.
            callback.onFailure(FailureType.ERROR_UNLOADING_PLUGIN);
        }
    }

    @Override
    public void execute(Bundle input, PluginCallback callback) throws RemoteException {
        IPluginExecutorService pluginExecutorService =
                mPluginExecutorServiceProvider.getExecutorService();
        if (pluginExecutorService == null) {
            callback.onFailure(FailureType.ERROR_EXECUTING_PLUGIN);
            return;
        }

        IPluginCallback parcelablePluginCallback = CallbackConverter.toIPluginCallback(callback);
        try {
            pluginExecutorService.execute(mInfo.taskName(), input, parcelablePluginCallback);
        } catch (RemoteException e) {
            // This callback call may throw RemoteException, which we pass on.
            callback.onFailure(FailureType.ERROR_EXECUTING_PLUGIN);
        }
    }

    @Override
    public void checkPluginState(PluginStateCallback callback) {
        IPluginExecutorService pluginExecutorService =
                mPluginExecutorServiceProvider.getExecutorService();
        if (pluginExecutorService == null) {
            callback.onState(PluginState.STATE_NO_SERVICE);
            return;
        }
        IPluginStateCallback parcelableStateCallback =
                CallbackConverter.toIPluginStateCallback(callback);
        try {
            pluginExecutorService.checkPluginState(mInfo.taskName(), parcelableStateCallback);
        } catch (RemoteException e) {
            callback.onState(PluginState.STATE_EXCEPTION_THROWN);
        }
    }

    @Override
    public String getName() {
        return mInfo.taskName();
    }
}
