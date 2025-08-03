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

package android.media.audio.cts

import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioProfile
import android.media.AudioTrack
import android.platform.test.annotations.AppModeSdkSandbox
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.NonMainlineTest
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@NonMainlineTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RunWith(AndroidJUnit4::class)
class DirectAudioProfilesForAttributesTest {

    private lateinit var audioManager: AudioManager

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().context
        assumeTrue(
            context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
        )

        audioManager = context.getSystemService(AudioManager::class.java)!!
    }

    /**
     * Test that only AudioProfiles returned in getDirectProfilesForAttributes can create direct
     * AudioTracks
     */
    @Test
    fun testCreateDirectAudioTracksOnlyForGetDirectProfilesForAttributes() {
        for (usage in AudioAttributes.getSdkUsages()) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(usage)
                .build()
            val directProfiles = audioManager.getDirectProfilesForAttributes(audioAttributes)

            // All compressed format (non pcm) profiles can create direct AudioTracks.
            // getDirectProfilesForAttributes does not include profiles supporting
            // compressed playback in offload mode, so it is expected that all creation
            // succeeds even if the audio track is not explicitly created in offload mode.
            val compressedProfiles = directProfiles.filterOutPcmFormats()
            for (directProfile in compressedProfiles) {
                checkCreateAudioTracks(audioAttributes, directProfile, true)
            }
        }
    }

    // Returns true if all the AudioTracks with all combinations of parameters that can be derived
    // from the passed audio profile can be created with the expected result.
    // Doesn't start the tracks.
    private fun checkCreateAudioTracks(
        audioAttributes: AudioAttributes,
        audioProfile: AudioProfile,
        expectedCreationSuccess: Boolean
    ) {
        if (audioProfile.format == AudioFormat.ENCODING_INVALID) {
            fail("Found INVALID audio format in audio profile ($audioProfile) " +
                    "when trying to create audio tracks with it!")
        }
        for (audioFormat in audioProfile.getAllAudioFormats()) {
            try {
                AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .build()
                    .release()
                // allow a short time to free the AudioTrack resources
                Thread.sleep(100)
                if (!expectedCreationSuccess) {
                    fail(
                        "Created AudioTrack for attributes ($audioAttributes) and " +
                                "audio format ($audioFormat)!"
                    )
                }
            } catch (e: Exception) {
                if (expectedCreationSuccess) {
                    fail(
                        "Failed to create AudioTrack for attributes ($audioAttributes) and " +
                                "audio format ($audioFormat) with exception ($e)!"
                    )
                }
            }
        }
    }

    // Utils
    private fun AudioProfile.getAllAudioFormats() =
        sampleRates.map { sampleRate ->
            channelMasks.map { channelMask ->
                AudioFormat.Builder()
                    .setEncoding(format)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build()
            }.plus(
                channelIndexMasks.map { channelIndexMask ->
                    AudioFormat.Builder()
                        .setEncoding(format)
                        .setSampleRate(sampleRate)
                        .setChannelIndexMask(channelIndexMask)
                        .build()
                }
            )
        }.flatten()

    private fun List<AudioProfile>.filterOutPcmFormats() = filter { it.format !in pcmFormats }

    companion object {
        private val pcmFormats = listOf(
            AudioFormat.ENCODING_PCM_8BIT,
            AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_PCM_FLOAT,
            AudioFormat.ENCODING_PCM_24BIT_PACKED,
            AudioFormat.ENCODING_PCM_32BIT
        )
    }
}
