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

package com.android.car.settings.qc;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.android.car.settings.wifi.WifiUtil;
import com.android.wifitrackerlib.WifiPickerTracker;

import java.io.IOException;

/**
 * Background worker for Wifi QCItems.
 * @param <E> The {@link SettingsQCItem} the background worker is associated with.
 */
public class WifiBaseWorker<E extends SettingsQCItem> extends SettingsQCBackgroundWorker<E>
        implements LifecycleOwner, WifiPickerTracker.WifiPickerTrackerCallback {

    private final LifecycleRegistry mLifecycleRegistry;
    private final WifiPickerTracker mWifiPickerTracker;
    private final HandlerThread mWorkerThread;

    protected WifiBaseWorker(Context context, Uri uri) {
        super(context, uri);
        mLifecycleRegistry = new LifecycleRegistry(/* provider= */ this);

        mWorkerThread = new HandlerThread(getClass().getSimpleName()
                + "{" + Integer.toHexString(System.identityHashCode(this)) + "}",
                Process.THREAD_PRIORITY_BACKGROUND);
        mWorkerThread.start();
        mWifiPickerTracker = WifiUtil.createWifiPickerTracker(mLifecycleRegistry, context,
                new Handler(Looper.getMainLooper()),
                mWorkerThread.getThreadHandler(), /* listener= */ this);

        mLifecycleRegistry.setCurrentState(Lifecycle.State.CREATED);
    }

    @Override
    protected void onQCItemSubscribe() {
        mLifecycleRegistry.setCurrentState(Lifecycle.State.RESUMED);
    }

    @Override
    protected void onQCItemUnsubscribe() {
        mLifecycleRegistry.setCurrentState(Lifecycle.State.CREATED);
    }

    @Override
    public void close() throws IOException {
        mWorkerThread.quit();
        mLifecycleRegistry.setCurrentState(Lifecycle.State.DESTROYED);
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return mLifecycleRegistry;
    }

    @Override
    public void onWifiStateChanged() {
        notifyQCItemChange();
    }

    @Override
    public void onWifiEntriesChanged() {
        notifyQCItemChange();
    }

    @Override
    public void onNumSavedNetworksChanged() {
    }

    @Override
    public void onNumSavedSubscriptionsChanged() {
    }
}
