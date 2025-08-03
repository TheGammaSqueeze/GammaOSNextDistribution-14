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

package com.android.tv.samples.sampletunertvinput;

import android.content.Context;
import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.dvr.DvrPlayback;
import android.media.tv.tuner.dvr.DvrSettings;
import android.media.tv.tuner.filter.AvSettings;
import android.media.tv.tuner.filter.Filter;
import android.media.tv.tuner.filter.FilterCallback;
import android.media.tv.tuner.filter.FilterEvent;
import android.media.tv.tuner.filter.SectionSettingsWithSectionBits;
import android.media.tv.tuner.filter.TsFilterConfiguration;
import android.media.tv.tuner.frontend.DvbtFrontendSettings;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;

public class SampleTunerTvInputUtils {
    private static final String TAG = "SampleTunerTvInput";
    private static final boolean DEBUG = true;

    private static final int AUDIO_TPID = 257;
    private static final int VIDEO_TPID = 256;
    private static final int SECTION_TPID = 255;
    private static final int FILTER_BUFFER_SIZE = 16000000;

    private static final int STATUS_MASK = 0xf;
    private static final int LOW_THRESHOLD = 0x1000;
    private static final int HIGH_THRESHOLD = 0x07fff;
    private static final int DVR_BUFFER_SIZE = 4000000;
    private static final int PACKET_SIZE = 188;
    private static final long FREQUENCY = 578000;
    private static final int INPUT_FILE_MAX_SIZE = 1000000;

    public static DvrPlayback configureDvrPlayback(Tuner tuner, Handler handler, int dataFormat) {
        DvrPlayback dvr = tuner.openDvrPlayback(DVR_BUFFER_SIZE, new HandlerExecutor(handler),
                status -> {
                    if (DEBUG) {
                        Log.d(TAG, "onPlaybackStatusChanged status=" + status);
                    }
                });
        int res = dvr.configure(
                DvrSettings.builder()
                        .setStatusMask(STATUS_MASK)
                        .setLowThreshold(LOW_THRESHOLD)
                        .setHighThreshold(HIGH_THRESHOLD)
                        .setDataFormat(dataFormat)
                        .setPacketSize(PACKET_SIZE)
                        .build());
        if (DEBUG) {
            Log.d(TAG, "config res=" + res);
        }
        return dvr;
    }

    public static void readFilePlaybackInput(Context context, DvrPlayback dvr, String fileName) {
        String testFile = context.getFilesDir().getAbsolutePath() + "/" + fileName;
        File file = new File(testFile);
        if (file.exists()) {
            try {
                dvr.setFileDescriptor(
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE));
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Failed to create FD");
            }
        } else {
            Log.w(TAG, "File not existing");
        }

        long read = dvr.read(INPUT_FILE_MAX_SIZE);
        if (DEBUG) {
            Log.d(TAG, "read=" + read);
        }
    }

    public static void tune(Tuner tuner, Handler handler) {
        DvbtFrontendSettings feSettings = DvbtFrontendSettings.builder()
                .setFrequencyLong(FREQUENCY)
                .setTransmissionMode(DvbtFrontendSettings.TRANSMISSION_MODE_AUTO)
                .setBandwidth(DvbtFrontendSettings.BANDWIDTH_8MHZ)
                .setConstellation(DvbtFrontendSettings.CONSTELLATION_AUTO)
                .setHierarchy(DvbtFrontendSettings.HIERARCHY_AUTO)
                .setHighPriorityCodeRate(DvbtFrontendSettings.CODERATE_AUTO)
                .setLowPriorityCodeRate(DvbtFrontendSettings.CODERATE_AUTO)
                .setGuardInterval(DvbtFrontendSettings.GUARD_INTERVAL_AUTO)
                .setHighPriority(true)
                .setStandard(DvbtFrontendSettings.STANDARD_T)
                .build();

        tuner.setOnTuneEventListener(new HandlerExecutor(handler), tuneEvent -> {
            if (DEBUG) {
                Log.d(TAG, "onTuneEvent " + tuneEvent);
            }
        });

        tuner.tune(feSettings);
    }

    public static Filter createSectionFilter(Tuner tuner, Handler handler,
            FilterCallback callback) {
        Filter sectionFilter = tuner.openFilter(Filter.TYPE_TS, Filter.SUBTYPE_SECTION,
                FILTER_BUFFER_SIZE, new HandlerExecutor(handler), callback);

        SectionSettingsWithSectionBits settings = SectionSettingsWithSectionBits
                .builder(Filter.TYPE_TS).build();

        sectionFilter.configure(
                TsFilterConfiguration.builder().setTpid(SECTION_TPID)
                        .setSettings(settings).build());

        return sectionFilter;
    }

    public static Filter createAvFilter(Tuner tuner, Handler handler,
            FilterCallback callback, boolean isAudio) {
        Filter avFilter = tuner.openFilter(Filter.TYPE_TS,
                isAudio ? Filter.SUBTYPE_AUDIO : Filter.SUBTYPE_VIDEO,
                FILTER_BUFFER_SIZE,
                new HandlerExecutor(handler),
                callback);

        AvSettings settings =
                AvSettings.builder(Filter.TYPE_TS, isAudio).setPassthrough(false).build();
        avFilter.configure(
                TsFilterConfiguration.builder().
                        setTpid(isAudio ? AUDIO_TPID : VIDEO_TPID)
                        .setSettings(settings).build());
        return avFilter;
    }

    public static FilterCallback createDefaultLoggingFilterCallback(String filterType) {
        return new FilterCallback() {
            @Override
            public void onFilterEvent(Filter filter, FilterEvent[] events) {
                if (DEBUG) {
                    Log.d(TAG, "onFilterEvent " + filterType + ", size=" + events.length);
                }
                for (int i = 0; i < events.length; i++) {
                    if (DEBUG) {
                        Log.d(TAG, "events[" + i + "] is "
                                + events[i].getClass().getSimpleName());
                    }
                }
            }

            @Override
            public void onFilterStatusChanged(Filter filter, int status) {
                if (DEBUG) {
                    Log.d(TAG, "onFilterStatusChanged " + filterType + ", status=" + status);
                }
            }
        };
    }
}
