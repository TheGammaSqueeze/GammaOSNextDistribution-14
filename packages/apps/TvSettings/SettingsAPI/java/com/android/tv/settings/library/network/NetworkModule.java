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

package com.android.tv.settings.library.network;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.ArraySet;
import android.util.Log;

import com.android.tv.settings.library.State;
import com.android.tv.settings.library.data.Module;

/** Provide network listener for all network states. */
public class NetworkModule implements Module, ConnectivityListener.Listener,
        ConnectivityListener.WifiNetworkListener {
    private static final String TAG = "NetworkModule";
    private static final boolean DEBUG = false;
    private ConnectivityListener mConnectivityListener;
    private boolean mIsWifiHardwarePresent;
    private static NetworkModule instance;
    private final Context mContext;
    ArraySet<State> states = new ArraySet<>();

    public static NetworkModule getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkModule(context.getApplicationContext());
        }
        return instance;
    }

    private NetworkModule(Context context) {
        mContext = context;
    }

    @Override
    public void addState(State state) {
        if (states.size() == 0) {
            if (DEBUG) {
                Log.d(TAG, "Network module create");
            }
            create();
        }
        if (DEBUG) {
            Log.d(TAG, "Network module add state " + state.getStateIdentifier());
        }

        states.add(state);
    }

    @Override
    public void removeState(State state) {
        if (DEBUG) {
            Log.d(TAG, "Network module remove state " + state.getStateIdentifier());
        }
        states.remove(state);
        if (states.size() == 0) {
            if (DEBUG) {
                Log.d(TAG, "Network module destroy");
            }
            destroy();
        }
    }

    @Override
    public void create() {
        mIsWifiHardwarePresent = mContext.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_WIFI);
        mConnectivityListener = new ConnectivityListener(mContext, this, null);
        mConnectivityListener.setWifiListener(this);
        mConnectivityListener.start();
    }

    @Override
    public void destroy() {
        mConnectivityListener.destroy();
    }

    @Override
    public void onConnectivityChange() {
        states.stream()
                .filter(state -> state instanceof ConnectivityListener.Listener)
                .forEach(state -> ((ConnectivityListener.Listener) state).onConnectivityChange());
    }

    ConnectivityListener getConnectivityListener() {
        return mConnectivityListener;
    }

    boolean isWifiHardwarePresent() {
        return mIsWifiHardwarePresent;
    }

    @Override
    public void onWifiListChanged() {
        states.stream()
                .filter(state -> state instanceof ConnectivityListener.WifiNetworkListener)
                .forEach(
                        state -> ((ConnectivityListener.WifiNetworkListener) state)
                                .onWifiListChanged());
    }
}
