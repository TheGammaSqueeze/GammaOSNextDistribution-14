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

package com.android.tv.settings.connectivity;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

/**
 * Listens for changes in internet connectivity without using WifiTracker to avoid wifi scans.
 */
public class ConnectivityListenerLite implements LifecycleObserver {
    private static final int CONNECTIVITY_CHANGE = 0;
    private final ConnectivityManager mConnectivityManager;
    private final ActiveNetworkProvider mActiveNetworkProvider;
    private Listener mListener;

    private final Handler mHandler;
    private final ConnectivityManager.NetworkCallback mNetworkCallback;

    private final Lifecycle mLifecycle;

    public ConnectivityListenerLite(Context context, Listener listener, Lifecycle lifecycle) {
        mConnectivityManager = context.getSystemService(ConnectivityManager.class);
        mActiveNetworkProvider = new ActiveNetworkProvider(context);
        mListener = listener;
        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == CONNECTIVITY_CHANGE) {
                    handleConnectivityChange();
                }
            }
        };
        mNetworkCallback = new DefaultNetworkCallback(mHandler);
        mLifecycle = lifecycle;
        mLifecycle.addObserver(this);
    }

    public void handleConnectivityChange() {
        mActiveNetworkProvider.updateActiveNetwork();
        mListener.onConnectivityChange(mActiveNetworkProvider);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    private void registerNetworkCallback() {
        mConnectivityManager.registerDefaultNetworkCallback(mNetworkCallback);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private void unregisterNetworkCallback() {
        mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    public void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        mLifecycle.removeObserver(this);
        mListener = null;
    }

    public void setListener(Listener listener) {
        this.mListener = listener;
    }

    public interface Listener {
        void onConnectivityChange(ActiveNetworkProvider activeNetworkProvider);
    }

    private static class DefaultNetworkCallback extends ConnectivityManager.NetworkCallback {
        private static final int DELAY = 100;
        private final Handler mHandler;

        private DefaultNetworkCallback(Handler handler) {
            this.mHandler = handler;
        }

        @Override
        public void onAvailable(@NonNull Network network) {
            super.onAvailable(network);
            mHandler.removeMessages(CONNECTIVITY_CHANGE);
            mHandler.sendEmptyMessageDelayed(CONNECTIVITY_CHANGE, DELAY);
        }

        @Override
        public void onLost(@NonNull Network network) {
            super.onLost(network);
            mHandler.removeMessages(CONNECTIVITY_CHANGE);
            mHandler.sendEmptyMessageDelayed(CONNECTIVITY_CHANGE, DELAY);
        }
    }
}
