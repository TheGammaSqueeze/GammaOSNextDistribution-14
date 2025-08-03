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

import android.annotation.NonNull;
import android.car.oem.OemCarAudioFocusEvaluationRequest;
import android.car.oem.OemCarAudioFocusResult;
import android.car.oem.OemCarAudioFocusService;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusInfo;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Slog;

import com.android.car.oem.focus.FocusInteraction;
import com.android.car.oem.utils.OemCarServiceHelper;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public final class OemCarAudioFocusServiceImpl implements OemCarAudioFocusService {

    private static final String TAG = "OemCarAudioFocusSrv";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final FocusInteraction mFocusInteraction;

    /**
     * Constructs a {@link FocusInteraction} with the given interaction mapping if
     * the interaction mapping is null, it will default to
     * {@link FocusInteraction.ATTRIBUTES_INTERACTIONS}
     *
     * @param attributeInteractions An array map of mappings from an incoming AudioAttributes to
     *        an array map of other AudioAttributes to focus interactions.
     */
    public OemCarAudioFocusServiceImpl(Context context) {
        ArrayMap<AudioAttributes, ArrayMap<AudioAttributes, Integer>>
                attributePriority = parseAndGetFocusInteractions(context);
        if (attributePriority == null) {
            attributePriority = FocusInteraction.ATTRIBUTES_INTERACTIONS;
        }
        mFocusInteraction = new FocusInteraction(attributePriority);
        if (DEBUG) {
            Slog.d(TAG, "constructor");
        }
    }

    @Override
    public void init() {
        if (DEBUG) {
            Slog.d(TAG, "init");
        }
    }

    @Override
    public void release() {
        if (DEBUG) {
            Slog.d(TAG, "release");
        }
    }

    @Override
    public void onCarServiceReady() {
        if (DEBUG) {
            Slog.d(TAG, "onCarServiceReady");
        }
        // Do any CarService calls
    }

    @Override
    public void dump(PrintWriter writer, String[] args) {
        if (DEBUG) {
            Slog.d(TAG, "dump");
        }
        writer.println("  OemCarAudioFocusServiceImpl");
        mFocusInteraction.dump(writer, /* indent= */ "  ");
    }

    @Override
    public void notifyAudioFocusChange(List<AudioFocusInfo> currentFocusHolders,
            List<AudioFocusInfo> currentFocusLosers, int zoneId) {
        if (DEBUG) {
            Slog.d(TAG, "OemCarAudioFocusServiceImpl audioFocusChanged called zone id " + zoneId);
            Slog.d(TAG, "OemCarAudioFocusServiceImpl focus holders " + currentFocusHolders);
            Slog.d(TAG, "OemCarAudioFocusServiceImpl focus losers " + currentFocusLosers);
        }
    }

    @Override
    @NonNull
    public OemCarAudioFocusResult evaluateAudioFocusRequest(
            @NonNull OemCarAudioFocusEvaluationRequest request) {
        return mFocusInteraction.evaluateFocusRequest(request);
    }

    @Override
    public String toString() {
        return new StringBuilder().append("{Class: ").append(TAG).append(", package: ")
                .append(OemCarAudioFocusServiceImpl.class.getPackage()).append("}").toString();
    }

    private ArrayMap<AudioAttributes, ArrayMap<AudioAttributes, Integer>>
            parseAndGetFocusInteractions(Context context) {
        OemCarServiceHelper oemCarServiceHelper = new OemCarServiceHelper();
        try {
            oemCarServiceHelper.parseAudioManagementConfiguration(context.getAssets().open(
                    "oem_focus_config.xml"));
            return oemCarServiceHelper.getCurrentFocusToIncomingFocusInteractions();
        } catch (XmlPullParserException | IOException e) {
            Slog.w(TAG, "Focus config file was not able to be parsed ", e);
            return null;
        }
    }
}
