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
package com.android.telephony.qns;

import android.os.Handler;
import android.util.Log;

/**
 * monitor cellular network status like attach or detach. The CellularNetworkStatusTracker is used
 * as data to evaluate cellular availability and coverage. Availability : Whether the data
 * registration state is in-service. Coverage : Whether the data registration state is Home or Roam.
 */
class CellularNetworkStatusTracker {

    private final String mLogTag;
    private final int mSlotIndex;
    private final QnsTelephonyListener mQnsTelephonyListener;

    /**
     * Constructor to instantiate CellularNetworkStatusTracker
     *
     * @param listener QnsTelephonyListener instance
     * @param slotIndex slot index
     */
    CellularNetworkStatusTracker(QnsTelephonyListener listener, int slotIndex) {
        mSlotIndex = slotIndex;
        mLogTag =
                QnsConstants.QNS_TAG
                        + "_"
                        + CellularNetworkStatusTracker.class.getSimpleName()
                        + "_"
                        + mSlotIndex;
        mQnsTelephonyListener = listener;
    }

    protected void log(String s) {
        Log.d(mLogTag, s);
    }

    /**
     * Register for QnsTelephonyInfoChanged
     *
     * @param netCapability Network Capability
     * @param h Handler
     * @param what Event
     */
    void registerQnsTelephonyInfoChanged(int netCapability, Handler h, int what) {
        mQnsTelephonyListener.registerQnsTelephonyInfoChanged(netCapability, h, what, null, true);
    }

    /**
     * Unregister for QnsTelephonyInfoChanged
     *
     * @param netCapability Network Capability
     * @param h Handler
     */
    void unregisterQnsTelephonyInfoChanged(int netCapability, Handler h) {
        mQnsTelephonyListener.unregisterQnsTelephonyInfoChanged(netCapability, h);
    }

    boolean isAirplaneModeEnabled() {
        return mQnsTelephonyListener.isAirplaneModeEnabled();
    }

    boolean isSupportVoPS() {
        return mQnsTelephonyListener.isSupportVoPS();
    }

    boolean isVoiceBarring() {
        return mQnsTelephonyListener.isVoiceBarring();
    }

    public void close() {}
}
