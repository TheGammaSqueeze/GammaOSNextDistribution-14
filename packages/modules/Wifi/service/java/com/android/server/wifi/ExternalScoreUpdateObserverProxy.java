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

package com.android.server.wifi;

import android.annotation.NonNull;
import android.net.wifi.IScoreUpdateObserver;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.android.modules.utils.build.SdkLevel;


/**
 * This is the callback proxy used to listen to external scorer actions triggered via
 * {@link android.net.wifi.WifiManager.ScoreUpdateObserver}.
 *
 * <p>
 * Note:
 * <li>This was extracted out of {@link WifiScoreReport} class since that is not a singleton and
 * is associated with a {@link ClientModeImpl} instance. However, this proxy sent to external scorer
 * needs to be a singleton (i.e we cannot send the external scorer a new proxy every time
 * a new primary {@link ClientModeImpl} is created).</li>
 * <li> Whenever a new primary CMM is created, it needs to register to this proxy to listen for
 * actions from the external scorer.</li>
 * </p>
 */
public class ExternalScoreUpdateObserverProxy extends IScoreUpdateObserver.Stub {
    private static final String TAG = "WifiExternalScoreUpdateObserverProxy";

    private final WifiThreadRunner mWifiThreadRunner;
    private WifiManager.ScoreUpdateObserver mCallback;
    private int mCountNullCallback = 0;
    private static final int MAX_NULL_CALLBACK_TRIGGER_WTF = 3;

    ExternalScoreUpdateObserverProxy(WifiThreadRunner wifiThreadRunner) {
        mWifiThreadRunner = wifiThreadRunner;
    }

    /**
     * Register a new callback to listen for events from external scorer.
     */
    public void registerCallback(@NonNull WifiManager.ScoreUpdateObserver callback) {
        if (mCallback != null) {
            Log.i(TAG, "Replacing an existing callback (new primary CMM created)");
        }
        mCallback = callback;
    }

    /**
     * Unregister callback to listen for events from external scorer.
     */
    public void unregisterCallback(@NonNull WifiManager.ScoreUpdateObserver callback) {
        // mCallback can be overwritten by another CMM, when we remove it we should only
        // remove if it is the most recently registered mCallback
        if (mCallback == callback) {
            mCallback = null;
        }
    }

    private void incrementAndMaybeLogWtf(String message) {
        mCountNullCallback++;
        if (mCountNullCallback >= MAX_NULL_CALLBACK_TRIGGER_WTF) {
            Log.wtf(TAG, message, null);
        }
    }

    @Override
    public void notifyScoreUpdate(int sessionId, int score) {
        mWifiThreadRunner.post(() -> {
            if (mCallback == null) {
                incrementAndMaybeLogWtf("No callback registered, dropping notifyScoreUpdate");
                return;
            }
            mCountNullCallback = 0;
            mCallback.notifyScoreUpdate(sessionId, score);
        });
    }

    @Override
    public void triggerUpdateOfWifiUsabilityStats(int sessionId) {
        mWifiThreadRunner.post(() -> {
            if (mCallback == null) {
                incrementAndMaybeLogWtf("No callback registered, "
                        + "dropping triggerUpdateOfWifiUsability");
                return;
            }
            mCountNullCallback = 0;
            mCallback.triggerUpdateOfWifiUsabilityStats(sessionId);
        });
    }

    @Override
    public void notifyStatusUpdate(int sessionId, boolean isUsable) {
        if (!SdkLevel.isAtLeastS()) {
            throw new UnsupportedOperationException();
        }
        mWifiThreadRunner.post(() -> {
            if (mCallback == null) {
                incrementAndMaybeLogWtf("No callback registered, dropping notifyStatusUpdate");
                return;
            }
            mCountNullCallback = 0;
            mCallback.notifyStatusUpdate(sessionId, isUsable);
        });
    }

    @Override
    public void requestNudOperation(int sessionId) {
        if (!SdkLevel.isAtLeastS()) {
            throw new UnsupportedOperationException();
        }
        mWifiThreadRunner.post(() -> {
            if (mCallback == null) {
                incrementAndMaybeLogWtf("No callback registered, dropping requestNudOperation");
                return;
            }
            mCountNullCallback = 0;
            mCallback.requestNudOperation(sessionId);
        });
    }

    @Override
    public void blocklistCurrentBssid(int sessionId) {
        if (!SdkLevel.isAtLeastS()) {
            throw new UnsupportedOperationException();
        }
        mWifiThreadRunner.post(() -> {
            if (mCallback == null) {
                incrementAndMaybeLogWtf("No callback registered, dropping blocklistCurrentBssid");
                return;
            }
            mCountNullCallback = 0;
            mCallback.blocklistCurrentBssid(sessionId);
        });
    }
}
