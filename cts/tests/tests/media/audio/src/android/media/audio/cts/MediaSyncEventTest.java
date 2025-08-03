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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.media.MediaSyncEvent;
import android.media.cts.AudioHelper;
import android.os.Parcel;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.NonMainlineTest;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import org.junit.Test;
import org.junit.runner.RunWith;

@NonMainlineTest
@RunWith(AndroidJUnit4.class)
public class MediaSyncEventTest {
    private final static String TAG = "MediaSyncEventTest";
    private static final String REPORT_LOG_NAME = "CtsMediaAudioTestCases";

    @Test
    public void testSynchronizedRecord() throws Exception {
        if (!hasMicrophone()) {
            return;
        }

        final String TEST_NAME = "testSynchronizedRecord";
        AudioTrack track = null;
        AudioRecord record = null;

        try {
            // 1. create a static AudioTrack.
            final int PLAYBACK_TIME_IN_MS = 2000; /* ms duration. */
            final int PLAYBACK_SAMPLE_RATE = 8000; /* in hz */
            AudioFormat format = new AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_8BIT)
                    .setSampleRate(PLAYBACK_SAMPLE_RATE)
                    .build();
            final int frameCount = AudioHelper.frameCountFromMsec(PLAYBACK_TIME_IN_MS, format);
            final int frameSize = AudioHelper.frameSizeFromFormat(format);
            track = new AudioTrack.Builder()
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(frameCount * frameSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            // create float array and write it
            final int sampleCount = frameCount * format.getChannelCount();
            byte[] vab = AudioHelper.createSoundDataInByteArray(
                    sampleCount, PLAYBACK_SAMPLE_RATE, 600 /* frequency */, 0 /* sweep */);
            assertEquals(TEST_NAME, vab.length,
                    track.write(vab, 0 /* offsetInBytes */, vab.length,
                            AudioTrack.WRITE_NON_BLOCKING));
            final int trackSessionId = track.getAudioSessionId();

            // 2. create an AudioRecord to sync off of AudioTrack completion.
            final int RECORD_TIME_IN_MS = 2000;
            final int RECORD_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
            final int RECORD_CHANNEL_MASK = AudioFormat.CHANNEL_IN_STEREO;
            final int RECORD_SAMPLE_RATE = 44100;
            record = new AudioRecord.Builder()
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(RECORD_SAMPLE_RATE)
                            .setChannelMask(RECORD_CHANNEL_MASK)
                            .setEncoding(RECORD_ENCODING)
                            .build())
                    .build();
            // AudioRecord creation may have silently failed, check state now
            assertEquals(TEST_NAME, AudioRecord.STATE_INITIALIZED, record.getState());

            // 3. create a MediaSyncEvent
            // This MediaSyncEvent checks playback completion of an AudioTrack
            // (or MediaPlayer, or ToneGenerator) based on its audio session id.
            //
            // Note: when synchronizing record from a MediaSyncEvent
            // (1) You need to be "close" to the end of the associated AudioTrack.
            // If the track does not complete in 30 seconds, recording begins regardless.
            // (actual delay limit may vary).
            //
            // (2) Track completion may be triggered by pause() as well as stop()
            // or when a static AudioTrack completes playback.
            //
            final int eventType = MediaSyncEvent.SYNC_EVENT_PRESENTATION_COMPLETE;
            MediaSyncEvent event = MediaSyncEvent.createEvent(eventType)
                    .setAudioSessionId(trackSessionId);
            assertEquals(TEST_NAME, trackSessionId, event.getAudioSessionId());
            assertEquals(TEST_NAME, eventType, event.getType());

            // 4. now set the AudioTrack playing and start the recording synchronized
            track.play();
            // start recording.  Recording state turns to RECORDSTATE_RECORDING immediately
            // but the data read() only occurs after the AudioTrack completes.
            record.startRecording(event);
            assertEquals(TEST_NAME,
                    AudioRecord.RECORDSTATE_RECORDING, record.getRecordingState());

            // We anchor start time here after issuing the play() and startRecording().
            // It is possible that the track has already started playing a few ms.
            final long nanosToMillis = 1000 * 1000;
            final long startTimeNs = System.nanoTime();
            final long startTimeMs = startTimeNs / nanosToMillis;
            Log.d(TEST_NAME, "startTimeMs: " + startTimeMs);

            // Establish when playback actually started
            long playbackStartLatencyMs = 0;
            final AudioTimestamp timestamp = new AudioTimestamp();
            final int pollGranularityMs = 10;
            for (int i = 0; i < PLAYBACK_TIME_IN_MS / pollGranularityMs; ++i) {
                // sample in the middle of playback to get a better estimate of start latency.
                if (track.getTimestamp(timestamp) && timestamp.framePosition > frameCount / 2) {
                    playbackStartLatencyMs = (timestamp.nanoTime - startTimeNs) / nanosToMillis
                            - timestamp.framePosition * 1000 / PLAYBACK_SAMPLE_RATE;
                    break;
                }
                Thread.sleep(pollGranularityMs);
            }
            Log.d(TEST_NAME, "playbackStartLatencyMs: " + playbackStartLatencyMs);

            // 5. get record data.
            // For our tests, we could set test duration by timed sleep or by # frames received.
            // Since we don't know *exactly* when AudioRecord actually begins recording,
            // we end the test by # frames read.
            final int numChannels =
                    AudioFormat.channelCountFromInChannelMask(RECORD_CHANNEL_MASK);
            final int bytesPerSample = AudioFormat.getBytesPerSample(RECORD_ENCODING);
            final int bytesPerFrame = numChannels * bytesPerSample;
            // careful about integer overflow in the formula below:
            final int targetSamples =
                    (int)((long)RECORD_TIME_IN_MS * RECORD_SAMPLE_RATE * numChannels / 1000);
            final int BUFFER_FRAMES = 512;
            final int BUFFER_SAMPLES = BUFFER_FRAMES * numChannels;

            // After starting, there is no guarantee when the first frame of data is read.
            long firstSampleReadTimeMs   = 0;
            int samplesRead = 0;

            // For 16 bit data, use shorts
            short[] shortData = new short[BUFFER_SAMPLES];
            while (samplesRead < targetSamples) {
                // the first time through, we read a single frame.
                // this sets the recording anchor position.
                int amount = samplesRead == 0 ? numChannels :
                        Math.min(BUFFER_SAMPLES, targetSamples - samplesRead);
                int ret = record.read(shortData, 0, amount);
                assertEquals(TEST_NAME, amount, ret);
                if (samplesRead == 0 && ret > 0) {
                    firstSampleReadTimeMs = System.nanoTime() / nanosToMillis;
                    Log.d(TEST_NAME, "firstSampleReadTimeMs: " + firstSampleReadTimeMs);
                }
                samplesRead += ret;
                // validity check: elapsed time cannot be more than a second
                // than what we expect.
                assertTrue(TEST_NAME + " exceeds one second past expectation of "
                        + (PLAYBACK_TIME_IN_MS + RECORD_TIME_IN_MS) + " ms",
                        System.nanoTime() / nanosToMillis - startTimeMs
                        <= PLAYBACK_TIME_IN_MS + RECORD_TIME_IN_MS + 1000);
            }

            // 6. We've read all the frames, now check the timing.
            final long endTimeMs = System.nanoTime() / nanosToMillis;
            Log.d(TEST_NAME, "endTimeMs : " + endTimeMs);

            // Stop the test.  All the data is received.
            record.stop();

            // Verify recording starts shortly after AudioTrack completion (typical diff 180ms)
            final long recordStartAfterPlaybackMs =
                    firstSampleReadTimeMs - startTimeMs - PLAYBACK_TIME_IN_MS;
            final long recordStartToleranceMs = isLowLatencyDevice() ? 200 : 800;
            Log.d(TEST_NAME, "recordStartAfterPlaybackMs: " + recordStartAfterPlaybackMs
                    + " recordStartToleranceMs: " + recordStartToleranceMs);

            // Verify recording time matches the expected time (typical diff 20ms)
            final long recordTimeMs = endTimeMs - firstSampleReadTimeMs;
            final long recordTimeToleranceMs = isLowLatencyDevice() ? 50 : 400;
            Log.d(TEST_NAME, "recordTimeMs: " + recordTimeMs
                    + " expectedRecordTimeMs: " + RECORD_TIME_IN_MS
                    + " recordTimeToleranceMs: " + recordTimeToleranceMs);

            // Report the values here.
            final DeviceReportLog log = new DeviceReportLog(REPORT_LOG_NAME,
                    "media_sync_event_test" /* reportName */);
            log.setSummary("record_start_after_playback_ms",
                    recordStartAfterPlaybackMs, ResultType.LOWER_BETTER, ResultUnit.MS);
            log.submit(InstrumentationRegistry.getInstrumentation());

            // Assert after logging to ensure statistics are recorded.
            assertEquals(TEST_NAME + " record start time after playback, limit("
                    + recordStartToleranceMs + ") ", 0 /* expected */,
                    recordStartAfterPlaybackMs, recordStartToleranceMs);
            assertEquals(TEST_NAME + " record total time, limit("
                    + recordTimeToleranceMs + ") ", RECORD_TIME_IN_MS,
                    recordTimeMs, recordTimeToleranceMs);
            assertEquals(TEST_NAME + " record state: ",
                    AudioRecord.RECORDSTATE_STOPPED, record.getRecordingState());
        } finally {
            if (record != null) {
                record.release();
                record = null;
            }
            if (track != null) {
                track.release();
                track = null;
            }
        }
    }

    // -----------------------------------------------------------------
    // Parcelable tests
    // ----------------------------------

    @Test
    public void testParcelableDescribeContents() throws Exception {
        final MediaSyncEvent event =
                MediaSyncEvent.createEvent(MediaSyncEvent.SYNC_EVENT_PRESENTATION_COMPLETE);
        assertNotNull("Failure to create the MediaSyncEvent", event);
        assertEquals(0, event.describeContents());
    }

    @Test
    public void testParcelableWriteToParcelCreate() throws Exception {
        final MediaSyncEvent srcEvent =
                MediaSyncEvent.createEvent(MediaSyncEvent.SYNC_EVENT_PRESENTATION_COMPLETE);
        assertNotNull("Failure to create the MediaSyncEvent", srcEvent);
        AudioManager am =
                InstrumentationRegistry.getTargetContext().getSystemService(AudioManager.class);
        srcEvent.setAudioSessionId(am.generateAudioSessionId());

        final Parcel srcParcel = Parcel.obtain();
        final Parcel dstParcel = Parcel.obtain();
        final byte[] mbytes;

        srcEvent.writeToParcel(srcParcel, 0 /*no public flags for marshalling*/);
        mbytes = srcParcel.marshall();
        dstParcel.unmarshall(mbytes, 0, mbytes.length);
        dstParcel.setDataPosition(0);
        final MediaSyncEvent targetEvent = MediaSyncEvent.CREATOR.createFromParcel(dstParcel);

        assertEquals("Marshalled/restored type doesn't match",
                srcEvent.getType(), targetEvent.getType());
        assertEquals("Marshalled/restored session doesn't match",
                srcEvent.getAudioSessionId(), targetEvent.getAudioSessionId());
    }

    private boolean hasMicrophone() {
        return InstrumentationRegistry.getTargetContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_MICROPHONE);
    }

    private boolean isLowLatencyDevice() {
        return InstrumentationRegistry.getTargetContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_AUDIO_LOW_LATENCY);
    }
}
