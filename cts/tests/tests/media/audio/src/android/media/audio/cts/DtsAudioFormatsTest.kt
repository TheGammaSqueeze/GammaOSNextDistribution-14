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
import android.media.AudioTrack
import android.platform.test.annotations.AppModeSdkSandbox
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.NonMainlineTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@NonMainlineTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
@RunWith(AndroidJUnit4::class)
class DtsAudioFormatsTest {

    /**
     * When DTS audio formats are supported, it is done over a direct path.
     * Use getDirectProfilesForAttributes to query them
     */
    @Test
    fun testCreateDirectAudioTracksForDtsEncodings() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        for (dtsEncoding in dtsEncodings) {
            val dtsFormats = getDtsAudioFormats(dtsEncoding)
            for (dtsFormat in dtsFormats) {
                val supported = (AudioManager.getDirectPlaybackSupport(dtsFormat, audioAttributes)
                        != AudioManager.DIRECT_PLAYBACK_NOT_SUPPORTED)

                checkCreateAudioTrack(audioAttributes, dtsFormat, supported)
            }
        }
    }

    // Creates an AudioTrack with the passed AudioAttributes and AudioFormat and expects
    // the result to match expectedCreationSuccess.
    // Doesn't start the track.
    private fun checkCreateAudioTrack(
        audioAttributes: AudioAttributes,
        audioFormat: AudioFormat,
        expectedCreationSuccess: Boolean
    ) {
        try {
            AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .build()
                .release()
            // allow a short time to free the AudioTrack resources
            Thread.sleep(SMALL_SLEEP_TIMEOUT)
            assertTrue(
                "Unexpected successful creation of DTS AudioTrack for " +
                        "attributes ($audioAttributes) and audio format ($audioFormat)!",
                expectedCreationSuccess
            )
        } catch (e: Exception) {
            assertFalse(
                "Failed to create DTS AudioTrack for attributes ($audioAttributes) and " +
                        "audio format ($audioFormat) with exception ($e)!",
                expectedCreationSuccess
            )
        }
    }

    // Utils
    private fun getDtsAudioFormats(dtsEncoding: Int) =
        sampleRates.map { sampleRate ->
            channelMasks.map { channelMask ->
                AudioFormat.Builder()
                    .setEncoding(dtsEncoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build()
            }
        }.flatten()

    companion object {
        // Time to sleep between consecutive creations of AudioTracks to allow the audio framework
        // to properly release resources
        private const val SMALL_SLEEP_TIMEOUT = 100L /*millis*/

        private val dtsEncodings = listOf(
            AudioFormat.ENCODING_DTS,
            AudioFormat.ENCODING_DTS_HD,
            AudioFormat.ENCODING_DTS_HD_MA,
            AudioFormat.ENCODING_DTS_UHD,
            AudioFormat.ENCODING_DTS_UHD_P1,
            AudioFormat.ENCODING_DTS_UHD_P2,
        )

        // list of commonly used sample rates
        private val sampleRates =
            listOf(22050, 24000, 44100, 48000, 88200, 96000, 176400, 192000)

        // list of commonly used channel masks
        private val channelMasks = listOf(
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.CHANNEL_OUT_5POINT1,
            AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
        )

        private lateinit var audioManager: AudioManager

        @JvmStatic
        @BeforeClass
        fun setup() {
            val context = InstrumentationRegistry.getInstrumentation().context
            assumeTrue(
                context.getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)
            )
            audioManager = context.getSystemService(AudioManager::class.java)!!
        }
    }
}
