/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.nativemedia.aaudio;

import static android.Manifest.permission.CAPTURE_AUDIO_HOTWORD;
import static android.Manifest.permission.CAPTURE_AUDIO_OUTPUT;

import android.app.UiAutomation;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.os.IBinder;
import android.os.ServiceManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.gtestrunner.GtestRunner;
import com.android.gtestrunner.TargetLibrary;

import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(GtestRunner.class)
@TargetLibrary("nativeaaudiotest")
public class AAudioTests {
    static IBinder getAudioFlinger() {
        return ServiceManager.getService("media.audio_flinger");
    }

    static boolean isIEC61937Supported() {
        AudioDeviceInfo[] devices = AudioManager.getDevicesStatic(AudioManager.GET_DEVICES_OUTPUTS);
        for (AudioDeviceInfo device : devices) {
            if (Arrays.stream(device.getEncodings()).anyMatch(
                    encoding -> encoding == AudioFormat.ENCODING_IEC61937)) {
                return true;
            }
        }
        return false;
    }

    static boolean isEchoReferenceSupported() {
        AudioDeviceInfo[] devices = AudioManager.getDevicesStatic(AudioManager.GET_DEVICES_INPUTS);
        for (AudioDeviceInfo device : devices) {
            if (device.getType() == AudioDeviceInfo.TYPE_ECHO_REFERENCE) {
                return true;
            }
        }
        return false;
    }

    static void enableAudioOutputPermission() {
        // Drop any identity adopted earlier.
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.dropShellPermissionIdentity();
        // need to retain the identity until the callback is triggered
        uiAutomation.adoptShellPermissionIdentity(CAPTURE_AUDIO_OUTPUT);
    }

    static void enableAudioHotwordPermission() {
        // Drop any identity adopted earlier.
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.dropShellPermissionIdentity();
        // need to retain the identity until the callback is triggered
        uiAutomation.adoptShellPermissionIdentity(CAPTURE_AUDIO_HOTWORD);
    }

    static void disablePermissions() {
        // Drop any identity adopted earlier.
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.dropShellPermissionIdentity();
    }
}
