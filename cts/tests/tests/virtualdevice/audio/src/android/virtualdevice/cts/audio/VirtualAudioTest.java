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
package android.virtualdevice.cts.audio;

import static android.Manifest.permission.CAPTURE_AUDIO_OUTPUT;
import static android.Manifest.permission.MODIFY_AUDIO_ROUTING;
import static android.media.AudioFormat.CHANNEL_IN_MONO;
import static android.media.AudioFormat.CHANNEL_OUT_MONO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static android.media.AudioRecord.RECORDSTATE_RECORDING;
import static android.media.AudioRecord.RECORDSTATE_STOPPED;
import static android.media.AudioTrack.PLAYSTATE_PLAYING;
import static android.media.AudioTrack.PLAYSTATE_STOPPED;
import static android.media.AudioTrack.WRITE_BLOCKING;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.audio.AudioCapture;
import android.companion.virtual.audio.AudioInjection;
import android.companion.virtual.audio.VirtualAudioDevice;
import android.companion.virtual.audio.VirtualAudioDevice.AudioConfigurationChangeCallback;
import android.content.pm.PackageManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.FeatureUtil;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests for injection and capturing of audio from streamed apps
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualAudioTest {

    public static final int FREQUENCY = 264;
    public static final int SAMPLE_RATE = 44100;
    public static final int CHANNEL_COUNT = 1;
    public static final int AMPLITUDE = 32767;
    public static final int BUFFER_SIZE_IN_BYTES = 65536;
    public static final int NUMBER_OF_SAMPLES = computeNumSamples(SAMPLE_RATE, CHANNEL_COUNT);
    private static final Duration TIMEOUT = Duration.ofMillis(5000);

    private static final AudioFormat CAPTURE_FORMAT = new AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(CHANNEL_IN_MONO)
            .build();
    private static final AudioFormat INJECTION_FORMAT = new AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(CHANNEL_IN_MONO)
            .build();

    @Rule
    public VirtualDeviceRule mVirtualDeviceRule = VirtualDeviceRule.withAdditionalPermissions(
            MODIFY_AUDIO_ROUTING, CAPTURE_AUDIO_OUTPUT);

    private VirtualDevice mVirtualDevice;
    private VirtualDisplay mVirtualDisplay;
    private VirtualAudioDevice mVirtualAudioDevice;

    @Mock
    private AudioConfigurationChangeCallback mAudioConfigurationChangeCallback;
    @Mock
    private SignalObserver.SignalChangeListener mSignalChangeListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mVirtualDevice = mVirtualDeviceRule.createManagedVirtualDevice();
        mVirtualDisplay = mVirtualDeviceRule.createManagedVirtualDisplay(
                mVirtualDevice, VirtualDeviceRule.TRUSTED_VIRTUAL_DISPLAY_CONFIG);
        mVirtualAudioDevice = mVirtualDevice.createVirtualAudioDevice(
                mVirtualDisplay, Runnable::run, mAudioConfigurationChangeCallback);
    }

    @Test
    public void audioCapture_createCorrectly() {
        AudioCapture audioCapture = mVirtualAudioDevice.startAudioCapture(CAPTURE_FORMAT);
        assertThat(audioCapture).isNotNull();
        assertThat(audioCapture.getFormat()).isEqualTo(CAPTURE_FORMAT);
        assertThat(mVirtualAudioDevice.getAudioCapture()).isEqualTo(audioCapture);

        audioCapture.startRecording();
        assertThat(audioCapture.getRecordingState()).isEqualTo(RECORDSTATE_RECORDING);
        audioCapture.stop();
        assertThat(audioCapture.getRecordingState()).isEqualTo(RECORDSTATE_STOPPED);
    }

    @Test
    public void audioInjection_createCorrectly() {
        AudioInjection audioInjection = mVirtualAudioDevice.startAudioInjection(INJECTION_FORMAT);
        assertThat(audioInjection).isNotNull();
        assertThat(audioInjection.getFormat()).isEqualTo(INJECTION_FORMAT);
        assertThat(mVirtualAudioDevice.getAudioInjection()).isEqualTo(audioInjection);

        audioInjection.play();
        assertThat(audioInjection.getPlayState()).isEqualTo(PLAYSTATE_PLAYING);
        audioInjection.stop();
        assertThat(audioInjection.getPlayState()).isEqualTo(PLAYSTATE_STOPPED);
    }

    @Test
    public void audioInjection_createWithNull() {
        assertThrows(NullPointerException.class, () -> mVirtualDevice.createVirtualAudioDevice(
                null, /* executor= */ null, /* callback= */ null));
    }

    @Test
    public void audioCapture_receivesAudioConfigurationChangeCallback() {
        AudioCapture capture = mVirtualAudioDevice.startAudioCapture(CAPTURE_FORMAT);

        AudioActivity activity = startAudioActivity();
        AudioTrack audioTrack = activity.playAudio();
        verify(mAudioConfigurationChangeCallback, timeout(5000).atLeastOnce())
                .onPlaybackConfigChanged(any());

        audioTrack.stop();
        activity.finish();
        capture.stop();
    }

    @Test
    public void audioInjection_receivesAudioConfigurationChangeCallback() throws Exception {
        ByteBuffer byteBuffer = createAudioData(
                SAMPLE_RATE, NUMBER_OF_SAMPLES, CHANNEL_COUNT, FREQUENCY, AMPLITUDE);
        try (AudioInjector ignored = new AudioInjector(byteBuffer, mVirtualAudioDevice)) {

            AudioActivity audioActivity = startAudioActivity();
            audioActivity.recordAudio(mSignalChangeListener);

            verify(mAudioConfigurationChangeCallback, timeout(5000).atLeastOnce())
                    .onRecordingConfigChanged(any());
            audioActivity.finish();
        }
    }

    @Test
    public void audioCapture_capturesAppPlaybackFrequency() {
        // Automotive has its own audio policies that don't play well with the VDM-created ones.
        assumeFalse(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));

        AudioCapture audioCapture = mVirtualAudioDevice.startAudioCapture(CAPTURE_FORMAT);

        try (SignalObserver signalObserver = new SignalObserver(audioCapture, Set.of(FREQUENCY))) {
            signalObserver.registerSignalChangeListener(mSignalChangeListener);

            AudioActivity activity = startAudioActivity();
            AudioTrack audioTrack = activity.playAudio();

            verify(mSignalChangeListener, timeout(TIMEOUT.toMillis()).atLeastOnce()).onSignalChange(
                    Set.of(FREQUENCY));

            audioTrack.stop();
            activity.finish();
            audioCapture.stop();
        }
    }


    @Test
    public void audioInjection_appShouldRecordInjectedFrequency() throws Exception {
        assumeFalse(FeatureUtil.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));

        ByteBuffer byteBuffer = createAudioData(
                SAMPLE_RATE, NUMBER_OF_SAMPLES, CHANNEL_COUNT, FREQUENCY, AMPLITUDE);
        try (AudioInjector ignored = new AudioInjector(byteBuffer, mVirtualAudioDevice)) {

            AudioActivity audioActivity = startAudioActivity();
            audioActivity.recordAudio(mSignalChangeListener);

            verify(mSignalChangeListener, timeout(TIMEOUT.toMillis()).atLeastOnce()).onSignalChange(
                    Set.of(FREQUENCY));
        }
    }

    private AudioActivity startAudioActivity() {
        return mVirtualDeviceRule.startActivityOnDisplaySync(
                mVirtualDisplay, AudioActivity.class);
    }

    public static class AudioActivity extends Activity {

        private SignalObserver mSignalObserver;

        AudioTrack playAudio() {

            ByteBuffer audioData = createAudioData(
                    SAMPLE_RATE, NUMBER_OF_SAMPLES, CHANNEL_COUNT, FREQUENCY, AMPLITUDE);
            AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
                    CHANNEL_OUT_MONO, ENCODING_PCM_16BIT, audioData.capacity(),
                    AudioTrack.MODE_STATIC);
            audioTrack.write(audioData, audioData.capacity(), WRITE_BLOCKING);
            audioTrack.play();

            return audioTrack;
        }

        void recordAudio(SignalObserver.SignalChangeListener signalChangeListener) {
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, ENCODING_PCM_16BIT, BUFFER_SIZE_IN_BYTES);
            if (mSignalObserver != null) {
                mSignalObserver.close();
            }

            mSignalObserver = new SignalObserver(audioRecord, Set.of(FREQUENCY));
            mSignalObserver.registerSignalChangeListener(signalChangeListener);
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();

            if (mSignalObserver != null) {
                mSignalObserver.close();
            }
        }
    }

    private static class AudioInjector implements AutoCloseable {

        private final ByteBuffer mAudioDataByteBuffer;
        private final VirtualAudioDevice mVirtualAudioDevice;

        private final AtomicBoolean mRunning = new AtomicBoolean(true);

        private final Thread mAudioInjectorThread = new Thread() {
            @Override
            public void run() {
                super.run();

                AudioInjection audioInjection = mVirtualAudioDevice.startAudioInjection(
                        INJECTION_FORMAT);
                audioInjection.play();
                while (mRunning.get() && !isInterrupted()) {
                    int remaining = mAudioDataByteBuffer.remaining();
                    while (remaining > 0 && mRunning.get() && !isInterrupted()) {
                        remaining -= audioInjection.write(mAudioDataByteBuffer,
                                mAudioDataByteBuffer.remaining(),
                                WRITE_BLOCKING);
                    }
                    mAudioDataByteBuffer.rewind();
                }
                audioInjection.stop();
            }
        };

        AudioInjector(ByteBuffer audioDataByteBuffer, VirtualAudioDevice virtualAudioDevice) {
            mAudioDataByteBuffer = audioDataByteBuffer;
            mVirtualAudioDevice = virtualAudioDevice;
            mAudioInjectorThread.start();
        }


        @Override
        public void close() throws Exception {
            mRunning.set(false);
            mAudioInjectorThread.interrupt();
            mAudioInjectorThread.join();
        }
    }

    static int computeNumSamples(int samplingRate, int channelCount) {
        return (int) ((long) 1000 * samplingRate * channelCount / 1000);
    }

    static ByteBuffer createAudioData(int samplingRate, int numSamples, int channelCount,
            double signalFrequencyHz, float amplitude) {
        ByteBuffer playBuffer =
                ByteBuffer.allocateDirect(numSamples * 2).order(ByteOrder.nativeOrder());
        final double multiplier = 2f * Math.PI * signalFrequencyHz / samplingRate;
        for (int i = 0; i < numSamples; ) {
            double vDouble = amplitude * Math.sin(multiplier * (i / channelCount));
            short v = (short) vDouble;
            for (int c = 0; c < channelCount; c++) {
                playBuffer.putShort(i * 2, v);
                i++;
            }
        }
        return playBuffer;
    }
}
