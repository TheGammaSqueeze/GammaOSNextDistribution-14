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
package com.android.car.oem;

import static android.car.oem.OemCarAudioFocusResult.EMPTY_OEM_CAR_AUDIO_FOCUS_RESULTS;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.car.oem.IOemCarAudioFocusService;
import android.car.oem.OemCarAudioFocusEvaluationRequest;
import android.car.oem.OemCarAudioFocusResult;
import android.media.AudioFocusInfo;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import com.android.car.CarLog;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.internal.util.LocalLog;
import com.android.internal.util.Preconditions;

import java.util.List;
import java.util.Optional;

/**
 * Provides functionality of the OEM Audio Focus Service.
 */
public final class CarOemAudioFocusProxyService {

    private static final String TAG = CarLog.tagFor(CarOemAudioFocusProxyService.class);
    private static final int QUEUE_SIZE = 10;

    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);
    private static final String CALLER_TAG = CarLog.tagFor(CarOemAudioFocusProxyService.class);

    private final CarOemProxyServiceHelper mHelper;
    private final IOemCarAudioFocusService mOemCarAudioFocusService;
    @Nullable
    private final LocalLog mLocalLog; // Is null if DBG is false. No logging will be produced.


    public CarOemAudioFocusProxyService(CarOemProxyServiceHelper helper,
            IOemCarAudioFocusService oemAudioFocusService) {
        mHelper = helper;
        mOemCarAudioFocusService = oemAudioFocusService;
        mLocalLog = DBG ? new LocalLog(QUEUE_SIZE) : null;
    }

    /**
     * Updates audio focus changes.
     */
    public void notifyAudioFocusChange(List<AudioFocusInfo> currentFocusHolders,
            List<AudioFocusInfo> currentFocusLosers, int zoneId) {
        mHelper.doBinderOneWayCall(CALLER_TAG, () -> {
            try {
                mOemCarAudioFocusService
                        .notifyAudioFocusChange(currentFocusHolders, currentFocusLosers, zoneId);
            } catch (RemoteException e) {
                Slogf.e(TAG, e,
                        "audioFocusChanged call received RemoteException- currentFocusHolders:%s, "
                        + "currentFocusLosers:%s, ZoneId: %s, , calling to crash CarService",
                        currentFocusHolders, currentFocusLosers, zoneId);
            }
        });
    }

    /**
     * Requests to evaluate a new focus request
     * @param request which includes the current audio focus info, current focus holders,
     *                and current focus losers.
     *
     * @return the focus evaluation results including any changes to the current focus stack.
     */
    @NonNull
    public OemCarAudioFocusResult evaluateAudioFocusRequest(
            @NonNull OemCarAudioFocusEvaluationRequest request) {
        Preconditions.checkArgument(request != null,
                "Audio focus evaluation request can not be null");

        long startTime = 0;
        if (mLocalLog != null) {
            startTime = SystemClock.uptimeMillis();
        }

        Optional<OemCarAudioFocusResult> result = mHelper.doBinderCallWithTimeoutCrash(CALLER_TAG,
                () -> {
                    try {
                        return mOemCarAudioFocusService.evaluateAudioFocusRequest(request);
                    } catch (RemoteException e) {
                        Slogf.e(TAG, e,
                                "evaluateAudioFocusRequest with request " + request);
                    }
                    return EMPTY_OEM_CAR_AUDIO_FOCUS_RESULTS;
                });
        if (result.isEmpty()) {
            return EMPTY_OEM_CAR_AUDIO_FOCUS_RESULTS;
        }

        OemCarAudioFocusResult focusResult = result.get();

        if (mLocalLog != null) {
            mLocalLog.log(startTime + ", " + (SystemClock.uptimeMillis() - startTime));
        }

        return focusResult;
    }

    public void dump(IndentingPrintWriter writer) {
        if (mLocalLog == null) {
            return;
        }
        writer.println("** Dump for CarOemAudioFocusProxyService **");
        mLocalLog.dump(writer);
        // This print statement is used to indicate the end of a test. Do not change or remove
        // this statement.
        writer.println("Dump CarOemAudioFocusProxyService time log complete");
    }
}
