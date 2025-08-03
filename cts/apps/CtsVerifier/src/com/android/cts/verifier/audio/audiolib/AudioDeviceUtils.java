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

import android.media.AudioDeviceInfo;

import java.util.HashMap;

/**
 * Utility methods for AudioDevices
 */
public class AudioDeviceUtils {
    public static final String TAG = "AudioDeviceUtils";
    /*
     * Channel Mask Utilities
     */
    private static final HashMap<Integer, String> sDeviceTypeStrings =
            new HashMap<Integer, String>();

    private static void initDeviceTypeStrings() {
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_UNKNOWN, "TYPE_UNKNOWN");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, "TYPE_BUILTIN_EARPIECE");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "TYPE_BUILTIN_SPEAKER");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_WIRED_HEADSET, "TYPE_WIRED_HEADSET");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "TYPE_WIRED_HEADPHONES");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_LINE_ANALOG, "TYPE_LINE_ANALOG");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_LINE_DIGITAL, "TYPE_LINE_DIGITAL");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "TYPE_BLUETOOTH_SCO");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "TYPE_BLUETOOTH_A2DP");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_HDMI, "TYPE_HDMI");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_HDMI_ARC, "TYPE_HDMI_ARC");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_USB_DEVICE, "TYPE_USB_DEVICE");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_USB_ACCESSORY, "TYPE_USB_ACCESSORY");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_DOCK, "TYPE_DOCK");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_FM, "TYPE_FM");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_BUILTIN_MIC, "TYPE_BUILTIN_MIC");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_FM_TUNER, "TYPE_FM_TUNER");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_TV_TUNER, "TYPE_TV_TUNER");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_TELEPHONY, "TYPE_TELEPHONY");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_AUX_LINE, "TYPE_AUX_LINE");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_IP, "TYPE_IP");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_BUS, "TYPE_BUS");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_USB_HEADSET, "TYPE_USB_HEADSET");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_HEARING_AID, "TYPE_HEARING_AID");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
                "TYPE_BUILTIN_SPEAKER_SAFE");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_REMOTE_SUBMIX, "TYPE_REMOTE_SUBMIX");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_BLE_HEADSET, "TYPE_BLE_HEADSET");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_BLE_SPEAKER, "TYPE_BLE_SPEAKER");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_ECHO_REFERENCE, "TYPE_ECHO_REFERENCE");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_HDMI_EARC, "TYPE_HDMI_EARC");
        sDeviceTypeStrings.put(AudioDeviceInfo.TYPE_BLE_BROADCAST, "TYPE_BLE_BROADCAST");
    }

    static {
        initDeviceTypeStrings();
    }

    /**
     * @param deviceType
     * @return a human-readable device type name.
     */
    public static String getDeviceTypeName(int deviceType) {
        String typeName = sDeviceTypeStrings.get(deviceType);
        return typeName != null ? typeName : "invalid type";
    }

    /**
     * @param deviceInfo
     * @return A human-readable description of the specified DeviceInfo
     */
    public static String formatDeviceName(AudioDeviceInfo deviceInfo) {
        StringBuilder sb = new StringBuilder();
        if (deviceInfo != null) {
            sb.append(deviceInfo.getProductName());
            sb.append(" - " + getDeviceTypeName(deviceInfo.getType()));
        } else {
            sb.append("null");
        }

        return sb.toString();
    }

    /**
     * @param deviceInfo Specifies the audio device to characterize.
     * @return true if the device is (probably) a Mic
     */
    public static boolean isMicDevice(AudioDeviceInfo deviceInfo) {
        if (deviceInfo == null || !deviceInfo.isSource()) {
            return false;
        }

        switch (deviceInfo.getType()) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC:
            case AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case AudioDeviceInfo.TYPE_USB_HEADSET:
                return true;

            default:
                return false;
        }
    }
}
