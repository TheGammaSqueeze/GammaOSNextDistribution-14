/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.tools.monitors

import android.app.Instrumentation
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaParser
import android.os.SystemClock
import android.tools.io.TraceType
import android.tools.traces.TRACE_CONFIG_REQUIRE_CHANGES
import android.tools.traces.io.ResultReader
import android.tools.traces.monitors.ScreenRecorder
import android.tools.utils.CleanFlickerEnvironmentRule
import android.tools.utils.newTestResultWriter
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth
import org.junit.After
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** Contains [ScreenRecorder] tests. To run this test: `atest FlickerLibTest:ScreenRecorderTest` */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ScreenRecorderTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val mScreenRecorder = ScreenRecorder(instrumentation.targetContext)

    @After
    fun teardown() {
        if (mScreenRecorder.isEnabled) {
            mScreenRecorder.stop(newTestResultWriter())
        }
    }

    @Test
    fun videoIsRecorded() {
        mScreenRecorder.start()
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        SystemClock.sleep(500)
        device.pressHome()
        var remainingTime = TIMEOUT
        do {
            remainingTime -= 100
            SystemClock.sleep(STEP)
        } while (!mScreenRecorder.isFrameRecorded && remainingTime > 0)
        val writer = newTestResultWriter()
        mScreenRecorder.stop(writer)
        val result = writer.write()

        val reader = ResultReader(result, TRACE_CONFIG_REQUIRE_CHANGES)
        Truth.assertWithMessage("Screen recording file exists")
            .that(reader.hasTraceFile(TraceType.SCREEN_RECORDING))
            .isTrue()

        val outputData =
            reader.readBytes(TraceType.SCREEN_RECORDING) ?: error("Screen recording not found")
        val (metadataTrack, videoTrack) = parseScreenRecording(outputData)

        Truth.assertThat(metadataTrack.isEmpty()).isFalse()
        Truth.assertThat(videoTrack.isEmpty()).isFalse()

        val actualMagicString = metadataTrack.copyOfRange(0, WINSCOPE_MAGIC_STRING.size)
        Truth.assertThat(actualMagicString).isEqualTo(WINSCOPE_MAGIC_STRING)
    }

    private fun parseScreenRecording(data: ByteArray): Pair<ByteArray, ByteArray> {
        val inputReader = ScreenRecorderSeekableInputReader(data)
        val outputConsumer = ScreenRecorderOutputConsumer()
        val mediaParser = MediaParser.create(outputConsumer)

        while (mediaParser.advance(inputReader)) {
            // no op
        }
        mediaParser.release()

        return Pair(outputConsumer.getMetadataTrack(), outputConsumer.getVideoTrack())
    }

    companion object {
        private const val TIMEOUT = 10000L
        private const val STEP = 100L
        private val WINSCOPE_MAGIC_STRING =
            byteArrayOf(
                0x23,
                0x56,
                0x56,
                0x31,
                0x4e,
                0x53,
                0x43,
                0x30,
                0x50,
                0x45,
                0x54,
                0x31,
                0x4d,
                0x45,
                0x32,
                0x23
            ) // "#VV1NSC0PET1ME2#"

        @ClassRule @JvmField val ENV_CLEANUP = CleanFlickerEnvironmentRule()
    }

    internal class ScreenRecorderSeekableInputReader(private val bytes: ByteArray) :
        MediaParser.SeekableInputReader {
        private var position = 0L

        override fun getPosition(): Long = position

        override fun getLength(): Long = bytes.size.toLong() - position

        override fun seekToPosition(position: Long) {
            this.position = position
        }

        override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
            if (position >= bytes.size) {
                return -1
            }

            val actualLength = kotlin.math.min(readLength.toLong(), bytes.size - position)
            for (i in 0 until actualLength) {
                buffer[(offset + i).toInt()] = bytes[(position + i).toInt()]
            }

            position += actualLength

            return actualLength.toInt()
        }
    }

    internal class ScreenRecorderOutputConsumer : MediaParser.OutputConsumer {
        private var videoTrack = ArrayList<Byte>()
        private var metadataTrack = ArrayList<Byte>()
        private var videoTrackIndex = -1
        private var metadataTrackIndex = -1
        private val auxBuffer = ByteArray(4 * 1024)

        fun getVideoTrack(): ByteArray {
            return videoTrack.toByteArray()
        }

        fun getMetadataTrack(): ByteArray {
            return metadataTrack.toByteArray()
        }

        override fun onSeekMapFound(seekMap: MediaParser.SeekMap) {
            // do nothing
        }

        override fun onTrackCountFound(numberOfTracks: Int) {
            Truth.assertThat(numberOfTracks).isEqualTo(2)
        }

        override fun onTrackDataFound(i: Int, trackData: MediaParser.TrackData) {
            if (
                videoTrackIndex == -1 &&
                    trackData.mediaFormat.getString(MediaFormat.KEY_MIME, "").startsWith("video/")
            ) {
                videoTrackIndex = i
            }

            if (
                metadataTrackIndex == -1 &&
                    trackData.mediaFormat.getString(MediaFormat.KEY_MIME, "") ==
                        "application/octet-stream"
            ) {
                metadataTrackIndex = i
            }
        }

        override fun onSampleDataFound(trackIndex: Int, inputReader: MediaParser.InputReader) {
            when (trackIndex) {
                videoTrackIndex -> processSampleData(inputReader, videoTrack)
                metadataTrackIndex -> processSampleData(inputReader, metadataTrack)
                else -> throw RuntimeException("unexpected track index: $trackIndex")
            }
        }

        override fun onSampleCompleted(
            trackIndex: Int,
            timeMicros: Long,
            flags: Int,
            size: Int,
            offset: Int,
            cryptoData: MediaCodec.CryptoInfo?
        ) {
            // do nothing
        }

        private fun processSampleData(
            inputReader: MediaParser.InputReader,
            buffer: ArrayList<Byte>
        ) {
            while (inputReader.length > 0) {
                val requestLength = kotlin.math.min(inputReader.length, auxBuffer.size.toLong())
                val actualLength = inputReader.read(auxBuffer, 0, requestLength.toInt())
                if (actualLength == -1) {
                    break
                }

                for (i in 0 until actualLength) {
                    buffer.add(auxBuffer[i])
                }
            }
        }
    }
}
