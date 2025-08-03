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

package android.media.audio.cts;

import android.media.AudioDeviceInfo;
import android.platform.test.annotations.AppModeSdkSandbox;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@NonMainlineTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RunWith(AndroidJUnit4.class)
public class AudioDeviceInfoTest {
    private static final Set<Integer> INPUT_TYPES = Stream.of(
        AudioDeviceInfo.TYPE_BUILTIN_MIC,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_TELEPHONY,
        AudioDeviceInfo.TYPE_DOCK,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_FM_TUNER,
        AudioDeviceInfo.TYPE_TV_TUNER,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        AudioDeviceInfo.TYPE_IP,
        AudioDeviceInfo.TYPE_BUS,
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC,
        AudioDeviceInfo.TYPE_ECHO_REFERENCE,
        AudioDeviceInfo.TYPE_DOCK_ANALOG)
            .collect(Collectors.toCollection(HashSet::new));

    private static final Set<Integer> OUTPUT_TYPES = Stream.of(
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_DOCK,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_TELEPHONY,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        AudioDeviceInfo.TYPE_FM,
        AudioDeviceInfo.TYPE_AUX_LINE,
        AudioDeviceInfo.TYPE_IP,
        AudioDeviceInfo.TYPE_BUS,
        AudioDeviceInfo.TYPE_HEARING_AID,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        AudioDeviceInfo.TYPE_DOCK_ANALOG)
            .collect(Collectors.toCollection(HashSet::new));

    private static int MAX_TYPE;
    private static int MIN_TYPE;
    {
        int maxType = Integer.MIN_VALUE;
        int minType = Integer.MAX_VALUE;
        for (int type : INPUT_TYPES) {
            minType = Integer.min(minType, type);
            maxType = Integer.max(maxType, type);
        }
        for (int type : OUTPUT_TYPES) {
            minType = Integer.min(minType, type);
            maxType = Integer.max(maxType, type);
        }
        MIN_TYPE = minType;
        MAX_TYPE = maxType;
    }

    /**
     * Ensure no regression on accepted input device types.
     *
     * @throws Exception
     */
    @Test
    public void testDeviceTypeIn() throws Exception {
        for (int type : INPUT_TYPES) {
            // throws IllegalArgumentException on failure
            AudioDeviceInfo.enforceValidAudioDeviceTypeIn(type);
        }
    }

    /**
     * Ensure no regression on accepted output device types.
     *
     * @throws Exception
     */
    @Test
    public void testDeviceTypeOut() throws Exception {
        for (int type : OUTPUT_TYPES) {
            // throws IllegalArgumentException on failure
            AudioDeviceInfo.enforceValidAudioDeviceTypeOut(type);
        }
    }
}
