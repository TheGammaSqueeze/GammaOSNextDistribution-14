/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.hdmicec.app;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;

import com.android.server.hdmi.SadConfigurationReaderTest;

import org.junit.runner.JUnitCore;

/**
 * A simple app that can be used to mute, unmute, set volume or get the volume status of a device.
 * The actions supported are:
 *
 * <p>1. android.hdmicec.app.MUTE: Mutes the STREAM_MUSIC of the device, irrespective of the
 * previous state. Usage: START_COMMAND -a android.hdmicec.app.MUTE
 *
 * <p>2. android.hdmicec.app.UNMUTE: Unmutes the STREAM_MUSIC of the device, irrespective of the
 * previous state. Usage: START_COMMAND -a android.hdmicec.app.UNMUTE
 *
 * <p>3. android.hdmicec.app.REPORT_VOLUME: Reports current volume level in percent if not muted.
 * Adds 128 to volume percent level if the device is muted. Usage: START_COMMAND -a
 * android.hdmicec.app.REPORT_VOLUME
 *
 * <p>4. android.hdmicec.app.SET_VOLUME: Sets the volume of STREAM_MUSIC to a particular level. Has
 * to be used with --ei "volumePercent" x. Usage: START_COMMAND -a android.hdmicec.app.SET_VOLUME
 * --ei "volumePercent" x
 *
 * <p>where START_COMMAND is adb shell am start -n
 * "android.hdmicec.app/android.hdmicec.app.HdmiCecAudioManager"
 */
public class HdmiCecAudioManager extends Activity {

    private static final String TAG = HdmiCecAudioManager.class.getSimpleName();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int minVolume = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        switch(getIntent().getAction()) {
            case "android.hdmicec.app.MUTE":
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_MUTE, 0);
                break;
            case "android.hdmicec.app.UNMUTE":
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_UNMUTE, 0);
                break;
            case "android.hdmicec.app.REPORT_VOLUME":
                int volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int percentageVolume = 100 * volumeLevel / (maxVolume - minVolume);
                if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                    percentageVolume += 128;
                }
                Log.i(TAG, "Volume at " + percentageVolume + "%");
                break;
            case "android.hdmicec.app.RAISE_VOLUME":
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE, 0);
                break;
            case "android.hdmicec.app.LOWER_VOLUME":
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER, 0);
                break;
            case "android.hdmicec.app.SET_VOLUME":
                int percentVolume = getIntent().getIntExtra("volumePercent", 50);
                int volume = minVolume + ((maxVolume - minVolume) * percentVolume / 100);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
                Log.i(TAG, "Set volume to " + volume + " (" + percentVolume + "%)");
                break;
            case "android.hdmicec.app.GET_SUPPORTED_SAD_FORMATS":
                JUnitCore junit = new JUnitCore();
                junit.run(SadConfigurationReaderTest.class);
                break;
            default:
                Log.w(TAG, "Unknown intent!");
        }
        finishAndRemoveTask();
    }
}

