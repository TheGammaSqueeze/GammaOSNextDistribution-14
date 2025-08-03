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

package android.media.audio.cts;

import static android.media.AudioAttributes.ALLOW_CAPTURE_BY_NONE;
import static android.media.MediaFormat.KEY_AAC_DRC_EFFECT_TYPE;
import static android.media.MediaFormat.KEY_AAC_DRC_TARGET_REFERENCE_LEVEL;
import static android.media.audio.Flags.FLAG_LOUDNESS_CONFIGURATOR_API;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.LoudnessCodecController;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@NonMainlineTest
@RunWith(AndroidJUnit4.class)
public class LoudnessCodecControllerTest {
    private static final String TAG = "LoudnessCodecControllerTest";

    private static final String TEST_MEDIA_AUDIO_CODEC_PREFIX = "audio/";
    private static final int TEST_AUDIO_TRACK_SAMPLERATE = 48000;
    private static final int TEST_AUDIO_TRACK_CHANNELS = 2;

    private static final Duration TEST_LOUDNESS_CALLBACK_TIMEOUT = Duration.ofMillis(200);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private LoudnessCodecController mLcc;

    private int mSessionId;

    private AudioTrack mAt;

    private final AtomicInteger mCodecUpdateCallNumber = new AtomicInteger(0);

    private final AtomicReference<Bundle> mLastCodecUpdate = new AtomicReference<>();

    private final class MyLoudnessCodecUpdateListener
            implements LoudnessCodecController.OnLoudnessCodecUpdateListener {
        @Override
        @NonNull
        public Bundle onLoudnessCodecUpdate(@NonNull MediaCodec mediaCodec,
                @NonNull Bundle codecValues) {
            mCodecUpdateCallNumber.incrementAndGet();
            mLastCodecUpdate.set(codecValues);
            return codecValues;
        }
    }

    @Before
    public void setUp() {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final AudioManager audioManager = (AudioManager) context.getSystemService(
                AudioManager.class);
        mSessionId = 0;
        if (audioManager != null) {
            mSessionId = audioManager.generateAudioSessionId();
        }
        mLcc = LoudnessCodecController.create(mSessionId,
                Executors.newSingleThreadExecutor(), new MyLoudnessCodecUpdateListener());
    }

    @After
    public void tearDown() throws Exception {
        if (mAt != null) {
            mAt.release();
            mAt = null;
        }
        mLcc.close();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void createNewLoudnessCodecController_notNull() {
        assertNotNull("LoudnessCodecController must not be null", mLcc);

        mLcc = LoudnessCodecController.create(mSessionId);
        assertNotNull("LoudnessCodecController must not be null", mLcc);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void addUnconfiguredMediaCodec_returnsFalse() throws Exception {
        final MediaCodec mediaCodec = createMediaCodec(/*configure*/false);
        try {
            assertFalse(mLcc.addMediaCodec(mediaCodec));
        } finally {
            mediaCodec.release();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void addMediaCodec_withAudioTrack_sendsUpdate() throws Exception {
        mAt = createAndStartAudioTrack();

        final MediaCodec mediaCodec = createMediaCodec(/*configure*/true);
        try {
            mLcc.addMediaCodec(mediaCodec);
            Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

            assertEquals(1, mCodecUpdateCallNumber.get());
        } finally {
            mediaCodec.release();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void addMediaCodecs_triggersUpdate() throws Exception {
        final MediaCodec mediaCodec1 = createMediaCodec(/*configure*/true);
        final MediaCodec mediaCodec2 = createMediaCodec(/*configure*/true);
        final MediaCodec mediaCodec3 = createMediaCodec(/*configure*/true);

        try {
            mLcc.addMediaCodec(mediaCodec1);
            mLcc.addMediaCodec(mediaCodec2);
            mLcc.addMediaCodec(mediaCodec3);
            Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

            assertEquals(3, mCodecUpdateCallNumber.get());
        } finally {
            mediaCodec1.release();
            mediaCodec2.release();
            mediaCodec3.release();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void getLoudnessCodecParams_returnsCurrentParameters() throws Exception {
        final MediaCodec mediaCodec = createMediaCodec(/*configure*/true);
        try {
            mLcc.addMediaCodec(mediaCodec);
            assertFalse(mLcc.getLoudnessCodecParams(mediaCodec).isDefinitelyEmpty());
        } finally {
            mediaCodec.release();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void getLoudnessCodecParamsForWrongMediaCodec_throwsIAE() throws Exception {
        final MediaCodec mediaCodec1 = createMediaCodec(/*configure*/true);
        final MediaCodec mediaCodec2 = createMediaCodec(/*configure*/true);
        try {
            mLcc.addMediaCodec(mediaCodec1);
            assertThrows(IllegalArgumentException.class,
                    () -> mLcc.getLoudnessCodecParams(mediaCodec2));
        } finally {
            mediaCodec1.release();
            mediaCodec2.release();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void multipleLcc_withRegisteredCodecs_triggerUpdate() throws Exception {
        try (LoudnessCodecController lcc2 = LoudnessCodecController.create(mSessionId,
                Executors.newSingleThreadExecutor(), new MyLoudnessCodecUpdateListener())) {

            final MediaCodec mediaCodec1 = createMediaCodec(/*configure*/true);
            final MediaCodec mediaCodec2 = createMediaCodec(/*configure*/true);
            final MediaCodec mediaCodec3 = createMediaCodec(/*configure*/true);
            try {
                mLcc.addMediaCodec(mediaCodec1);
                mLcc.addMediaCodec(mediaCodec2);
                lcc2.addMediaCodec(mediaCodec3);
                Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

                assertEquals(3, mCodecUpdateCallNumber.get());
            } finally {
                mediaCodec1.release();
                mediaCodec2.release();
                mediaCodec3.release();
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void removeUnregisteredCodec_throwsIAE() throws Exception {
        final MediaCodec mediaCodec1 = createMediaCodec(/*configure*/true);
        final MediaCodec mediaCodec2 = createMediaCodec(/*configure*/true);

        try {
            mLcc.addMediaCodec(mediaCodec1);
            assertThrows(IllegalArgumentException.class, () -> mLcc.removeMediaCodec(mediaCodec2));
        } finally {
            mediaCodec1.release();
            mediaCodec2.release();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void addMediaCodec_afterRelease_noUpdate() throws Exception {
        final MediaCodec mediaCodec = createMediaCodec(/*configure*/true);
        try {
            mLcc.close();
            mLcc.addMediaCodec(mediaCodec);
            Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

            assertEquals(0, mCodecUpdateCallNumber.get());
        } finally {
            mediaCodec.release();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void audioTrackStart_afterAddMediaCodec_checkUpdateNumber() throws Exception {
        final MediaCodec mediaCodec1 = createMediaCodec(/*configure*/true);
        final MediaCodec mediaCodec2 = createMediaCodec(/*configure*/true);

        try {
            mLcc.addMediaCodec(mediaCodec1);
            mLcc.addMediaCodec(mediaCodec2);
            Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

            // Device id for AudioAttributes should not change to send more updates
            // after creating the AudioTrack
            mAt = createAndStartAudioTrack();
            Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

            assertEquals(2, mCodecUpdateCallNumber.get());
        } finally {
            mediaCodec1.release();
            mediaCodec2.release();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void audioTrackStart_beforeAddMediaCodec_checkUpdateNumber() throws Exception {
        final MediaCodec mediaCodec1 = createMediaCodec(/*configure*/true);
        final MediaCodec mediaCodec2 = createMediaCodec(/*configure*/true);

        try {
            mAt = createAndStartAudioTrack();
            Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

            mLcc.addMediaCodec(mediaCodec1);
            mLcc.addMediaCodec(mediaCodec2);
            Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

            assertEquals(2, mCodecUpdateCallNumber.get());
        } finally {
            mediaCodec1.release();
            mediaCodec2.release();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void addMediaCodec_afterRelease_noSecondUpdate() throws Exception {
        final MediaCodec mediaCodec1 = createMediaCodec(/*configure*/true);
        final MediaCodec mediaCodec2 = createMediaCodec(/*configure*/true);

        try {
            mLcc.addMediaCodec(mediaCodec1);
            Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

            mLcc.close();
            mLcc.addMediaCodec(mediaCodec2);
            Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

            assertEquals(1, mCodecUpdateCallNumber.get());
        } finally {
            mediaCodec1.release();
            mediaCodec2.release();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void mediaCodecUpdate_checkParameters() throws Exception {
        final MediaCodec mediaCodec = createMediaCodec(/*configure*/true);

        try {
            mLcc.addMediaCodec(mediaCodec);
            Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

            assertEquals(1, mCodecUpdateCallNumber.get());
            Bundle lastUpdate = mLastCodecUpdate.get();
            assertNotNull(lastUpdate);
            assertTrue(lastUpdate.getInt(KEY_AAC_DRC_TARGET_REFERENCE_LEVEL) != 0);
            assertTrue(lastUpdate.getInt(KEY_AAC_DRC_EFFECT_TYPE) != 0);
        } finally {
            mediaCodec.release();
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_LOUDNESS_CONFIGURATOR_API)
    public void mediaCodecUpdate_checkParametersOnCodec() throws Exception {
        mLcc = LoudnessCodecController.create(mSessionId);
        final MediaCodec mediaCodec = createMediaCodec(/*configure*/true);

        try {
            mLcc.addMediaCodec(mediaCodec);
            Thread.sleep(TEST_LOUDNESS_CALLBACK_TIMEOUT.toMillis());

            MediaFormat format = mediaCodec.getOutputFormat();
            assertNotNull(format);
            assertTrue(format.getInteger(KEY_AAC_DRC_TARGET_REFERENCE_LEVEL) != 0);
            assertTrue(format.getInteger(KEY_AAC_DRC_EFFECT_TYPE) != 0);
        } finally {
            mediaCodec.release();
        }
    }


    private AudioTrack createAndStartAudioTrack() {
        final int bufferSizeInBytes =
                TEST_AUDIO_TRACK_SAMPLERATE * TEST_AUDIO_TRACK_CHANNELS * Short.BYTES;

        final AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setAllowedCapturePolicy(ALLOW_CAPTURE_BY_NONE)
                        .build())
                .setSessionId(mSessionId)
                .setBufferSizeInBytes(bufferSizeInBytes)
                .setAudioFormat(new AudioFormat.Builder()
                        .setChannelMask(TEST_AUDIO_TRACK_CHANNELS)
                        .setSampleRate(TEST_AUDIO_TRACK_SAMPLERATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build())
                .build();

        // enqueue silence to have a device assigned
        short[] data = new short[bufferSizeInBytes / Short.BYTES];
        track.write(data, 0, data.length, AudioTrack.WRITE_NON_BLOCKING);
        track.play();

        return track;
    }

    /** Creates a decoder for xHE-AAC content. */
    private MediaCodec createMediaCodec(boolean configure) throws Exception {
        AssetFileDescriptor testFd = InstrumentationRegistry.getInstrumentation().getContext()
                .getResources()
                .openRawResourceFd(R.raw.noise_2ch_48khz_tlou_19lufs_anchor_17lufs_mp4);

        MediaExtractor extractor;
        extractor = new MediaExtractor();
        try {
            extractor.setDataSource(testFd.getFileDescriptor(), testFd.getStartOffset(),
                    testFd.getLength());

            assertEquals("wrong number of tracks", 1, extractor.getTrackCount());
            MediaFormat format = extractor.getTrackFormat(0);
            String mime = format.getString(MediaFormat.KEY_MIME);
            assertTrue("not an audio file", mime.startsWith(TEST_MEDIA_AUDIO_CODEC_PREFIX));
            final MediaCodec mediaCodec = MediaCodec.createDecoderByType(mime);

            if (configure) {
                Log.v(TAG, "configuring with " + format);
                mediaCodec.configure(format, null /* surface */, null /* crypto */, 0 /* flags */);
            }
            return mediaCodec;
        } finally {
            extractor.release();
            testFd.close();
        }
    }

}
