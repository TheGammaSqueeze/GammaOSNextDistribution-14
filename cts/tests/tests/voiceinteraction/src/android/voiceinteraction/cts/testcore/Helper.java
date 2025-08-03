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

package android.voiceinteraction.cts.testcore;

import static android.media.AudioFormat.CHANNEL_IN_FRONT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.app.compat.CompatChanges;
import android.content.Context;
import android.hardware.soundtrigger.SoundTrigger;
import android.hardware.soundtrigger.SoundTrigger.KeyphraseRecognitionExtra;
import android.media.AudioFormat;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.SharedMemory;
import android.provider.DeviceConfig;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordAudioStream;
import android.service.voice.HotwordDetectedResult;
import android.service.voice.HotwordRejectedResult;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;
import android.system.ErrnoException;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import com.google.common.collect.ImmutableList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Helper for common functionalities.
 */
public final class Helper {

    public static final String TAG = "VoiceInteractionCtsHelper";

    // The timeout to wait for async result
    public static final long WAIT_TIMEOUT_IN_MS = 10_000;
    public static final long WAIT_LONG_TIMEOUT_IN_MS = 15_000;
    public static final long WAIT_EXPECTED_NO_CALL_TIMEOUT_IN_MS = 3_000;

    // The test package
    public static final String CTS_SERVICE_PACKAGE = "android.voiceinteraction.cts";

    // The id that is used to gate compat change
    public static final long MULTIPLE_ACTIVE_HOTWORD_DETECTORS = 193232191L;
    public static final Long PERMISSION_INDICATORS_NOT_PRESENT = 162547999L;

    // The mic indicator information
    public static final Long CLEAR_CHIP_MS = 10000L;
    private static final String PRIVACY_CHIP_PKG = "com.android.systemui";
    private static final String PRIVACY_CHIP_ID = "privacy_chip";
    private static final String INDICATORS_FLAG = "camera_mic_icons_enabled";
    private static final String NAMESPACE_VOICE_INTERACTION = "voice_interaction";
    private static final String KEY_RESTART_PERIOD_IN_SECONDS = "restart_period_in_seconds";

    private static final String KEY_FAKE_DATA = "fakeData";
    private static final String VALUE_FAKE_DATA = "fakeData";
    private static final byte[] FAKE_BYTE_ARRAY_DATA = new byte[]{1, 2, 3};

    public static final int DEFAULT_PHRASE_ID = 5;
    public static byte[] FAKE_HOTWORD_AUDIO_DATA =
            new byte[]{'h', 'o', 't', 'w', 'o', 'r', 'd', '!'};

    // The permission is used to test keyphrase triggered.
    // This is not exposed as an API so we define it here.
    // TODO(b/273567812)
    public static final String MANAGE_VOICE_KEYPHRASES =
            "android.permission.MANAGE_VOICE_KEYPHRASES";

    // The locale is used to test keyphrase triggered
    public static final Locale KEYPHRASE_LOCALE = Locale.forLanguageTag("en-US");
    // The text is used to test keyphrase triggered
    public static final String KEYPHRASE_TEXT = "Hello Android";

    // The key or extra used for HotwordDetectionService
    public static final String KEY_TEST_SCENARIO = "testScenario";
    public static final int EXTRA_HOTWORD_DETECTION_SERVICE_ON_UPDATE_STATE_CRASH = 1;

    // The expected HotwordDetectedResult for testing
    public static final HotwordDetectedResult DETECTED_RESULT =
            new HotwordDetectedResult.Builder()
                    .setAudioChannel(CHANNEL_IN_FRONT)
                    .setConfidenceLevel(HotwordDetectedResult.CONFIDENCE_LEVEL_HIGH)
                    .setHotwordDetectionPersonalized(true)
                    .setHotwordDurationMillis(1000)
                    .setHotwordOffsetMillis(500)
                    .setHotwordPhraseId(DEFAULT_PHRASE_ID)
                    .setPersonalizedScore(10)
                    .setScore(15)
                    .setBackgroundAudioPower(50)
                    .build();
    public static final HotwordDetectedResult DETECTED_RESULT_AFTER_STOP_DETECTION =
            new HotwordDetectedResult.Builder()
                    .setHotwordPhraseId(DEFAULT_PHRASE_ID)
                    .setScore(57)
                    .build();
    public static final HotwordDetectedResult DETECTED_RESULT_FOR_MIC_FAILURE =
            new HotwordDetectedResult.Builder()
                    .setHotwordPhraseId(DEFAULT_PHRASE_ID)
                    .setScore(58)
                    .build();
    public static final HotwordRejectedResult REJECTED_RESULT =
            new HotwordRejectedResult.Builder()
                    .setConfidenceLevel(HotwordRejectedResult.CONFIDENCE_LEVEL_MEDIUM)
                    .build();

    /**
     * Returns the SharedMemory data that is used for testing.
     */
    public static SharedMemory createFakeSharedMemoryData() {
        try {
            SharedMemory sharedMemory = SharedMemory.create("SharedMemory", 3);
            ByteBuffer byteBuffer = sharedMemory.mapReadWrite();
            byteBuffer.put(FAKE_BYTE_ARRAY_DATA);
            return sharedMemory;
        } catch (ErrnoException e) {
            Log.w(TAG, "createFakeSharedMemoryData ErrnoException : " + e);
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Returns the PersistableBundle data that is used for testing.
     */
    public static PersistableBundle createFakePersistableBundleData() {
        // TODO : Add more data for testing
        PersistableBundle persistableBundle = new PersistableBundle();
        persistableBundle.putString(KEY_FAKE_DATA, VALUE_FAKE_DATA);
        return persistableBundle;
    }

    /**
     * Returns the AudioFormat data that is used for testing.
     */
    public static AudioFormat createFakeAudioFormat() {
        return new AudioFormat.Builder()
                .setSampleRate(32000)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
    }

    /**
     * Returns a list of KeyphraseRecognitionExtra that is used for testing.
     */
    public static List<KeyphraseRecognitionExtra> createFakeKeyphraseRecognitionExtraList() {
        return ImmutableList.of(new KeyphraseRecognitionExtra(DEFAULT_PHRASE_ID,
                SoundTrigger.RECOGNITION_MODE_VOICE_TRIGGER, 100));
    }

    /**
     * Returns the ParcelFileDescriptor data that is used for testing.
     */
    public static ParcelFileDescriptor createFakeAudioStream() {
        ParcelFileDescriptor[] tempParcelFileDescriptors = null;
        try {
            tempParcelFileDescriptors = ParcelFileDescriptor.createPipe();
            try (OutputStream fos =
                         new ParcelFileDescriptor.AutoCloseOutputStream(
                                 tempParcelFileDescriptors[1])) {
                fos.write(FAKE_HOTWORD_AUDIO_DATA, 0, 8);
            } catch (IOException e) {
                Log.w(TAG, "Failed to pipe audio data : ", e);
                throw new IllegalStateException();
            }
            return tempParcelFileDescriptors[0];
        } catch (IOException e) {
            Log.w(TAG, "Failed to create a pipe : " + e);
        }
        throw new IllegalStateException();
    }

    /**
     * Returns the list of KeyphraseRecognitionExtra that is used for testing.
     */
    public static List<KeyphraseRecognitionExtra> createKeyphraseRecognitionExtraList() {
        return Arrays.asList(new SoundTrigger.KeyphraseRecognitionExtra(DEFAULT_PHRASE_ID,
                SoundTrigger.RECOGNITION_MODE_VOICE_TRIGGER, /* coarseConfidenceLevel= */ 10));
    }

    /**
     * Returns the array of {@link SoundTrigger.Keyphrase} that is used for testing.
     */
    public static SoundTrigger.Keyphrase[] createKeyphraseArray(Context context) {
        return new SoundTrigger.Keyphrase[]{new SoundTrigger.Keyphrase(DEFAULT_PHRASE_ID,
                SoundTrigger.RECOGNITION_MODE_VOICE_TRIGGER,
                KEYPHRASE_LOCALE,
                KEYPHRASE_TEXT,
                new int[]{context.getUserId()}
        )};
    }

    /**
     * Checks if the privacy indicators are enabled on this device. Sets the state to the parameter,
     * And returns the original enable state (to allow this state to be reset after the test)
     */
    public static String getIndicatorEnabledState() {
        return SystemUtil.runWithShellPermissionIdentity(() -> {
            String currentlyEnabled = DeviceConfig.getProperty(DeviceConfig.NAMESPACE_PRIVACY,
                    INDICATORS_FLAG);
            Log.v(TAG, "getIndicatorEnabledStateIfNeeded()=" + currentlyEnabled);
            return currentlyEnabled;
        });
    }

    /**
     * Checks if the privacy indicators are enabled on this device. Sets the state to the parameter,
     * and returns the original enable state (to allow this state to be reset after the test)
     */
    public static void setIndicatorEnabledState(String shouldEnable) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            Log.v(TAG, "setIndicatorEnabledState()=" + shouldEnable);
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_PRIVACY, INDICATORS_FLAG, shouldEnable,
                    false);
        });
    }

    /**
     * Returns the period of restarting the hotword detection service.
     */
    public static String getHotwordDetectionServiceRestartPeriod() {
        return SystemUtil.runWithShellPermissionIdentity(() -> {
            String currentPeriod = DeviceConfig.getProperty(NAMESPACE_VOICE_INTERACTION,
                    KEY_RESTART_PERIOD_IN_SECONDS);
            Log.v(TAG, "getHotwordDetectionServiceRestartPeriod()=" + currentPeriod);
            return currentPeriod;
        });
    }

    /**
     * Sets the period of restarting the hotword detection service.
     */
    public static void setHotwordDetectionServiceRestartPeriod(String period) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            Log.v(TAG, "setHotwordDetectionServiceRestartPeriod()=" + period);
            DeviceConfig.setProperty(NAMESPACE_VOICE_INTERACTION, KEY_RESTART_PERIOD_IN_SECONDS,
                    period, false);
        });
    }

    /**
     * Verify the microphone indicator present status.
     */
    public static void verifyMicrophoneChipHandheld(boolean shouldBePresent) throws Exception {
        // If the change Id is not present, then isChangeEnabled will return true. To bypass this,
        // the change is set to "false" if present.
        if (SystemUtil.callWithShellPermissionIdentity(() -> CompatChanges.isChangeEnabled(
                PERMISSION_INDICATORS_NOT_PRESENT, Process.SYSTEM_UID))) {
            return;
        }
        // Ensure the privacy chip is present (or not)
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        final boolean chipFound = device.wait(Until.hasObject(
                By.res(PRIVACY_CHIP_PKG, PRIVACY_CHIP_ID)), CLEAR_CHIP_MS);
        assertEquals("chip display state", shouldBePresent, chipFound);
    }

    /**
     * Verify HotwordDetectedResult.
     */
    public static void verifyDetectedResult(AlwaysOnHotwordDetector.EventPayload detectedResult,
            HotwordDetectedResult expectedDetectedResult) {
        // TODO: Implement HotwordDetectedResult#equals to override the Bundle equality check; then
        // simply check that the HotwordDetectedResults are equal.
        HotwordDetectedResult hotwordDetectedResult = detectedResult.getHotwordDetectedResult();
        verifyHotwordDetectedResult(expectedDetectedResult, hotwordDetectedResult);

        ParcelFileDescriptor audioStream = detectedResult.getAudioStream();
        assertThat(audioStream).isNull();
    }

    private static void verifyHotwordDetectedResult(HotwordDetectedResult expectedDetectedResult,
            HotwordDetectedResult hotwordDetectedResult) {
        assertThat(hotwordDetectedResult).isNotNull();
        assertThat(hotwordDetectedResult.getAudioChannel())
                .isEqualTo(expectedDetectedResult.getAudioChannel());
        assertThat(hotwordDetectedResult.getConfidenceLevel())
                .isEqualTo(expectedDetectedResult.getConfidenceLevel());
        assertThat(hotwordDetectedResult.isHotwordDetectionPersonalized())
                .isEqualTo(expectedDetectedResult.isHotwordDetectionPersonalized());
        assertThat(hotwordDetectedResult.getHotwordDurationMillis())
                .isEqualTo(expectedDetectedResult.getHotwordDurationMillis());
        assertThat(hotwordDetectedResult.getHotwordOffsetMillis())
                .isEqualTo(expectedDetectedResult.getHotwordOffsetMillis());
        assertThat(hotwordDetectedResult.getHotwordPhraseId())
                .isEqualTo(expectedDetectedResult.getHotwordPhraseId());
        assertThat(hotwordDetectedResult.getPersonalizedScore())
                .isEqualTo(expectedDetectedResult.getPersonalizedScore());
        assertThat(hotwordDetectedResult.getScore()).isEqualTo(expectedDetectedResult.getScore());
        assertThat(hotwordDetectedResult.getBackgroundAudioPower())
                .isEqualTo(expectedDetectedResult.getBackgroundAudioPower());
    }

    /**
     * Verify Audio Egress HotwordDetectedResult.
     */
    public static void verifyAudioEgressDetectedResult(
            AlwaysOnHotwordDetector.EventPayload detectedResult,
            HotwordDetectedResult expectedDetectedResult) throws Exception {
        // TODO: Implement HotwordDetectedResult#equals to override the Bundle equality check; then
        // simply check that the HotwordDetectedResults are equal.
        HotwordDetectedResult hotwordDetectedResult = detectedResult.getHotwordDetectedResult();
        verifyHotwordDetectedResult(expectedDetectedResult, hotwordDetectedResult);

        // Verify the HotwordAudioStream result
        verifyHotwordAudioStream(hotwordDetectedResult.getAudioStreams().get(0),
                expectedDetectedResult.getAudioStreams().get(0));

        ParcelFileDescriptor audioStream = detectedResult.getAudioStream();
        assertThat(audioStream).isNull();
    }

    private static void verifyHotwordAudioStream(HotwordAudioStream detectedAudioStream,
            HotwordAudioStream expectedAudioStream) throws Exception {
        assertThat(detectedAudioStream.getAudioFormat()).isNotNull();
        assertThat(detectedAudioStream.getAudioStreamParcelFileDescriptor()).isNotNull();
        assertThat(detectedAudioStream.getAudioFormat()).isEqualTo(
                expectedAudioStream.getAudioFormat());
        assertThat(detectedAudioStream.getInitialAudio()).isNotNull();
        assertThat(detectedAudioStream.getInitialAudio()).isEqualTo(
                expectedAudioStream.getInitialAudio());
        assertAudioStream(detectedAudioStream.getAudioStreamParcelFileDescriptor(),
                FAKE_HOTWORD_AUDIO_DATA);
        assertThat(detectedAudioStream.getTimestamp().framePosition).isEqualTo(
                expectedAudioStream.getTimestamp().framePosition);
        assertThat(detectedAudioStream.getTimestamp().nanoTime).isEqualTo(
                expectedAudioStream.getTimestamp().nanoTime);
        assertThat(detectedAudioStream.getMetadata().size()).isEqualTo(
                expectedAudioStream.getMetadata().size());
        assertThat(detectedAudioStream.getMetadata().getString(KEY_FAKE_DATA)).isEqualTo(
                VALUE_FAKE_DATA);
    }

    private static void assertAudioStream(ParcelFileDescriptor audioStream, byte[] expected)
            throws IOException {
        try (InputStream audioSource = new ParcelFileDescriptor.AutoCloseInputStream(audioStream)) {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = audioSource.read(buffer)) != -1) {
                result.write(buffer, 0, count);
            }
            assertThat(result.toByteArray()).isEqualTo(expected);
        }

        try (OutputStream audioSource = new ParcelFileDescriptor.AutoCloseOutputStream(
                audioStream)) {
            audioSource.write(1);
            fail("The parcelFileDescriptor should be ready only!");
        } catch (IOException exception) {
            // expected
        }
    }

    /**
     * Returns {@code true} if the device supports multiple detectors, otherwise
     * returns {@code false}.
     */
    public static boolean isEnableMultipleDetectors() {
        final boolean enableMultipleHotwordDetectors = CompatChanges.isChangeEnabled(
                MULTIPLE_ACTIVE_HOTWORD_DETECTORS);
        Log.d(TAG, "enableMultipleHotwordDetectors = " + enableMultipleHotwordDetectors);
        return enableMultipleHotwordDetectors;
    }

    /**
     * TODO: remove this helper when FutureSubject is available from
     * {@link com.google.common.truth.Truth}
     */
    public static <V> V waitForFutureDoneAndAssertSuccessful(Future<V> future) {
        try {
            return future.get(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new AssertionError("future failed to complete", e);
        }
    }

    /**
     * TODO: remove this helper when FutureSubject is available from
     * {@link com.google.common.truth.Truth}
     */
    public static void waitForVoidFutureAndAssertSuccessful(Future<Void> future) {
        assertThat(waitForFutureDoneAndAssertSuccessful(future)).isNull();
    }
}
