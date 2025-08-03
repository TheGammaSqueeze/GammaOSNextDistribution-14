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

package com.android.car.oemcarservice.testapp;

import android.car.oem.OemCarAudioVolumeRequest;
import android.car.oem.OemCarAudioVolumeService;
import android.car.oem.OemCarVolumeChangeInfo;
import android.content.Context;
import android.media.AudioAttributes;
import android.util.Log;
import android.util.Slog;

import androidx.annotation.NonNull;

import com.android.car.oem.utils.OemCarServiceHelper;
import com.android.car.oem.volume.VolumeInteractions;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class OemCarAudioVolumeServiceImp implements OemCarAudioVolumeService {

    private static final String TAG = "OemCarAudioVolumeSrv";

    private final VolumeInteractions mVolumeInteractions;

    /**
     * Constructs a {@link VolumeInteractions} with the given context and volume priority lit, if
     * volume the given priority is null, it will default to
     * {@link VolumeInteractions.VOLUME_PRIORITIES}
     *
     * @param context The given context
     * @param volumePriorities A list of volume priorities from highest priority to lowest priority
     */
    public OemCarAudioVolumeServiceImp(Context context) {
        List<AudioAttributes> priorityList = parseAndGetVolumeConfig(context);
        if (priorityList == null) {
            priorityList = VolumeInteractions.VOLUME_PRIORITIES;
        }
        mVolumeInteractions = new VolumeInteractions(context, priorityList);
    }

    @NonNull
    @Override
    public OemCarVolumeChangeInfo getSuggestedGroupForVolumeChange(
            @NonNull OemCarAudioVolumeRequest requestInfo, int volumeAdjustment) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "getSuggestedGroupForVolumeChange " + requestInfo);
        }
        return mVolumeInteractions.getVolumeGroupToChange(requestInfo, volumeAdjustment);
    }

    @Override
    public void init() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "init");
        }
    }

    @Override
    public void release() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "release");
        }
    }

    @Override
    public void onCarServiceReady() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "onCarServiceReady");
        }
        mVolumeInteractions.init();
    }

    @Override
    public void dump(PrintWriter writer, String[] args) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "dump");
        }
        writer.println("  OemCarAudioVolumeServiceImpl");
        mVolumeInteractions.dump(writer, "  ");
    }

    private List<AudioAttributes> parseAndGetVolumeConfig(Context context) {
        OemCarServiceHelper oemCarServiceHelper = new OemCarServiceHelper();
        try {
            oemCarServiceHelper.parseAudioManagementConfiguration(context.getAssets().open(
                    "oem_volume_config.xml"));
            return oemCarServiceHelper.getVolumePriorityList();
        } catch (XmlPullParserException | IOException e) {
            Slog.w(TAG, "Volume config file was not able to be parsed", e);
            return null;
        }
    }
}
