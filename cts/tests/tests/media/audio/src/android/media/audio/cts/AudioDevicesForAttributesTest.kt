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

package android.media.audio.cts

import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.platform.test.annotations.AppModeSdkSandbox
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.NonMainlineTest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@NonMainlineTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RunWith(AndroidJUnit4::class)
class AudioDevicesForAttributesTest {
    /**
     * Test that getAudioDevicesForAttributes reports the output audio device(s)
     */
    @Test
    fun testGetAudioDevicesForAttributes() {
        val context = InstrumentationRegistry.getInstrumentation().context
        assumeTrue(
            context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
        )

        val audioManager = context.getSystemService(AudioManager::class.java)!!
        val allOutDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var hasAtLeastOneDeviceForAttributes = false

        for (usage in AudioAttributes.getSdkUsages()) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(usage)
                .build()

            val audioDevicesForAttributes: List<AudioDeviceInfo> =
                audioManager.getAudioDevicesForAttributes(audioAttributes)

            assertTrue(
                "Unknown device for attributes!",
                allOutDevices.toList().containsAll(audioDevicesForAttributes)
            )

            if (audioDevicesForAttributes.isNotEmpty()) {
                hasAtLeastOneDeviceForAttributes = true
            }
        }

        if (allOutDevices.isNotEmpty()) {
            assertTrue(
                "No device for any AudioAttributes, though output device exists.",
                hasAtLeastOneDeviceForAttributes
            )
        }
    }
}
