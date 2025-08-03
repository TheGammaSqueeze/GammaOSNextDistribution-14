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
import android.net.TetheringManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.Looper;

import java.io.IOException;

/**
 * Background worker for the {@link HotspotTile} QCItem.
 */
public class HotspotTileWorker extends SettingsQCBackgroundWorker<HotspotTile> {

    private final WifiManager mWifiManager;
    private final TetheringManager mTetheringManager;
    private final Handler mHandler;
    private boolean mCallbacksRegistered;

    private final TetheringManager.TetheringEventCallback mTetheringEventCallback =
            new TetheringManager.TetheringEventCallback() {
                @Override
                public void onTetheringSupported(boolean supported) {
                    if (getQCItem().getHotspotSupported() != supported) {
                        getQCItem().setHotspotSupported(supported);
                        notifyQCItemChange();
                    }
                }
            };

    private final WifiManager.SoftApCallback mSoftApCallback = new WifiManager.SoftApCallback() {
        @Override
        public void onStateChanged(int state, int failureReason) {
            notifyQCItemChange();
        }
    };

    public HotspotTileWorker(Context context, Uri uri) {
        super(context, uri);
        mWifiManager = context.getSystemService(WifiManager.class);
        mTetheringManager = context.getSystemService(TetheringManager.class);
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void onQCItemSubscribe() {
        if (!mCallbacksRegistered) {
            mTetheringManager.registerTetheringEventCallback(
                    new HandlerExecutor(mHandler), mTetheringEventCallback);
            mWifiManager.registerSoftApCallback(getContext().getMainExecutor(), mSoftApCallback);
            mCallbacksRegistered = true;
        }
    }

    @Override
    protected void onQCItemUnsubscribe() {
        unregisterCallbacks();
    }

    @Override
    public void close() throws IOException {
        unregisterCallbacks();
    }

    private void unregisterCallbacks() {
        if (mCallbacksRegistered) {
            mWifiManager.unregisterSoftApCallback(mSoftApCallback);
            mTetheringManager.unregisterTetheringEventCallback(mTetheringEventCallback);
            mCallbacksRegistered = false;
        }
    }
}
