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

package com.google.android.iwlan;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.telephony.CarrierConfigManager;
import android.util.Log;
import com.android.internal.annotations.VisibleForTesting;

public class IwlanCarrierConfigChangeListener
        implements CarrierConfigManager.CarrierConfigChangeListener {

    private static final String TAG = "IwlanCarrierConfig";

    private static boolean mIsListenerRegistered = false;
    private static IwlanCarrierConfigChangeListener mInstance;
    private static HandlerThread mHandlerThread;

    private Handler mHandler;

    public static void startListening(Context context) {
        if (mIsListenerRegistered) {
            Log.d(TAG, "startListening: Listener already registered");
            return;
        }
        var carrierConfigManager = context.getSystemService(CarrierConfigManager.class);
        if (carrierConfigManager != null) {
            carrierConfigManager.registerCarrierConfigChangeListener(
                    getInstance().getHandler()::post, getInstance());
            mIsListenerRegistered = true;
        }
    }

    public static void stopListening(Context context) {
        if (!mIsListenerRegistered) {
            Log.d(TAG, "stopListening: Listener not registered!");
            return;
        }
        var carrierConfigManager = context.getSystemService(CarrierConfigManager.class);
        if (carrierConfigManager != null) {
            carrierConfigManager.unregisterCarrierConfigChangeListener(getInstance());
            mIsListenerRegistered = false;
        }
    }

    private static IwlanCarrierConfigChangeListener getInstance() {
        if (mInstance == null) {
            mHandlerThread = new HandlerThread("IwlanCarrierConfigChangeListenerThread");
            mHandlerThread.start();
            mInstance = new IwlanCarrierConfigChangeListener(mHandlerThread.getLooper());
        }
        return mInstance;
    }

    @VisibleForTesting
    IwlanCarrierConfigChangeListener(Looper looper) {
        mHandler = new Handler(looper);
    }

    private Handler getHandler() {
        return mHandler;
    }

    @Override
    public void onCarrierConfigChanged(
            int logicalSlotIndex, int subscriptionId, int carrierId, int specificCarrierId) {
        Context context = IwlanDataService.getContext();
        if (context != null) {
            IwlanEventListener.onCarrierConfigChanged(
                    context, logicalSlotIndex, subscriptionId, carrierId);
        }
    }
}
