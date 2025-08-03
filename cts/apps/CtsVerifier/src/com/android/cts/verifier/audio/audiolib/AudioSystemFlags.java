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

package com.android.cts.verifier.audio.audiolib;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.DisplayMetrics;

public class AudioSystemFlags {
    static final String TAG = AudioSystemFlags.class.getName();

    private static final float MIN_TV_DIMENSION = 20;

    public static boolean claimsOutput(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT);
    }

    public static boolean claimsInput(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
    }

    public static boolean claimsProAudio(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO);
    }

    public static boolean claimsLowLatencyAudio(Context context) {
        // CDD Section C-1-1: android.hardware.audio.low_latency
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY);
    }

    public static boolean claimsMIDI(Context context) {
        // CDD Section C-1-4: android.software.midi
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MIDI);
    }

    public static boolean claimsUSBHostMode(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST);
    }

    public static boolean claimsUSBPeripheralMode(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY);
    }

    /**
     * @param context The Context of the application.
     * @return true if the device is a watch
     */
    public static boolean isWatch(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    /**
     * @param context The Context of the application.
     * @return true if the device is Android Auto
     */
    public static boolean isAutomobile(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    /**
     * @param context The Context of the application.
     * @return true if the device is a TV
     */
    public static boolean isTV(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    /**
     * @param context The Context of the application.
     * @return true if the device is a handheld (Phone or tablet)
     */
    public static boolean isHandheld(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float widthInInches = metrics.widthPixels / metrics.xdpi;
        float heightInInches = metrics.heightPixels / metrics.ydpi;

        // it is handheld if
        // 1. it is not identified as any of those special devices
        // 2. and it is small enough to actually hold in your hand.
        return !(isWatch(context) || isAutomobile(context) || isTV(context))
                && (widthInInches < MIN_TV_DIMENSION && heightInInches < MIN_TV_DIMENSION);
    }
}
