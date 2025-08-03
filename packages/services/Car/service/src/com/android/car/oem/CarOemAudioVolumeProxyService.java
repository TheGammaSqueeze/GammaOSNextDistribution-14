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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.car.oem.IOemCarAudioVolumeService;
import android.car.oem.OemCarAudioVolumeRequest;
import android.car.oem.OemCarVolumeChangeInfo;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import com.android.car.CarLog;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.internal.util.LocalLog;
import com.android.internal.util.Preconditions;

/**
 * Provides functionality of the OEM Audio Volume Service.
 */
public final class CarOemAudioVolumeProxyService {

    private static final String TAG = CarLog.tagFor(CarOemAudioVolumeProxyService.class);
    private static final int QUEUE_SIZE = 10;

    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);
    private static final String CALLER_TAG = CarLog.tagFor(CarOemAudioVolumeProxyService.class);

    private final CarOemProxyServiceHelper mHelper;
    private final IOemCarAudioVolumeService mOemCarAudioVolumeService;
    @Nullable
    private final LocalLog mLocalLog; // Is null if DBG is false. No logging will be produced.

    public CarOemAudioVolumeProxyService(CarOemProxyServiceHelper helper,
            IOemCarAudioVolumeService oemAudioVolumeService) {
        mHelper = helper;
        mOemCarAudioVolumeService = oemAudioVolumeService;
        mLocalLog = DBG ? new LocalLog(QUEUE_SIZE) : null;
    }

    /**
     * Call to evaluate a volume change.
     */
    @NonNull
    public OemCarVolumeChangeInfo getSuggestedGroupForVolumeChange(
            @NonNull OemCarAudioVolumeRequest requestInfo, int volumeAdjustment) {
        Preconditions.checkArgument(requestInfo != null,
                "Audio volume evaluation request can not be null");

        long startTime = 0;
        if (mLocalLog != null) {
            startTime = SystemClock.uptimeMillis();
        }

        OemCarVolumeChangeInfo result = mHelper.doBinderTimedCallWithDefaultValue(CALLER_TAG,
                () -> {
                    try {
                        return mOemCarAudioVolumeService.getSuggestedGroupForVolumeChange(
                                requestInfo, volumeAdjustment);
                    } catch (RemoteException e) {
                        Slogf.e(TAG, e,
                                "Suggested group for volume Change with request " + requestInfo);
                    }
                    return OemCarVolumeChangeInfo.EMPTY_OEM_VOLUME_CHANGE;
                }, OemCarVolumeChangeInfo.EMPTY_OEM_VOLUME_CHANGE);

        if (mLocalLog != null) {
            mLocalLog.log(startTime + ", " + (SystemClock.uptimeMillis() - startTime));
        }

        return result;
    }

    public void dump(IndentingPrintWriter writer) {
        if (mLocalLog == null) {
            return;
        }
        writer.println("** Dump for CarOemAudioVolumeProxyService **");
        mLocalLog.dump(writer);
        // This print statement is used to indicate the end of a test. Do not change or remove
        // this statement.
        writer.println("Dump CarOemAudioVolumeProxyService time log complete");
    }
}
