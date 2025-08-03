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

package android.virtualdevice.cts.audio;

import static android.Manifest.permission.MODIFY_AUDIO_ROUTING;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_AUDIO;
import static android.media.AudioAttributes.CONTENT_TYPE_SPEECH;
import static android.media.AudioPlaybackConfiguration.PLAYER_STATE_STARTED;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Uninterruptibles.getUninterruptibly;

import static org.junit.Assume.assumeNotNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.content.Context;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class TextToSpeechTest {
    private static final String TAG = TextToSpeechTest.class.getSimpleName();
    private static final String TTS_TEXT = "My hovercraft is full of eels";
    private static final String UTTERANCE_ID = "vdmTtsTestUtteranceId";

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Rule
    public VirtualDeviceRule mVirtualDeviceRule = VirtualDeviceRule.withAdditionalPermissions(
            // Modify audio routing permission is needed because without it, the audio session id
            // entry in AudioPlaybackConfiguration is redacted.
            MODIFY_AUDIO_ROUTING);

    private AudioManager mAudioManager;

    private SpeechPlaybackObserver mSpeechPlaybackObserver;

    private int mVirtualDeviceAudioSessionId;
    private TextToSpeech mTextToSpeech;

    @Before
    public void setUp() {
        Context context = getApplicationContext();
        mAudioManager = context.getSystemService(AudioManager.class);
        assumeNotNull(mAudioManager);
        mSpeechPlaybackObserver = new SpeechPlaybackObserver();
        mAudioManager.registerAudioPlaybackCallback(mSpeechPlaybackObserver, /*handler=*/null);

        mVirtualDeviceAudioSessionId = mAudioManager.generateAudioSessionId();
        VirtualDevice virtualDevice = mVirtualDeviceRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM)
                        .setAudioPlaybackSessionId(mVirtualDeviceAudioSessionId)
                        .build());
        Context virtualDeviceContext = virtualDevice.createContext();
        mTextToSpeech = initializeTextToSpeech(virtualDeviceContext);
        assumeNotNull(mTextToSpeech);
    }

    @After
    public void tearDown() {
        if (mTextToSpeech != null) {
            mTextToSpeech.shutdown();
        }
        if (mAudioManager != null) {
            mAudioManager.unregisterAudioPlaybackCallback(mSpeechPlaybackObserver);
        }
    }

    @Test
    public void textToSpeechWithVirtualDeviceContext_hasVdmSpecificSessionId() throws Exception {
        mTextToSpeech.speak(TTS_TEXT, TextToSpeech.QUEUE_ADD, /*params=*/null, UTTERANCE_ID);

        // Wait for audio playback with SPEECH content.
        AudioPlaybackConfiguration ttsAudioPlaybackConfig = getUninterruptibly(
                mSpeechPlaybackObserver.getSpeechAudioPlaybackConfigFuture(),
                TIMEOUT.getSeconds(), TimeUnit.SECONDS);

        // Verify the SPEECH playback has audio session id corresponding to virtual device.
        assertThat(ttsAudioPlaybackConfig.getSessionId()).isEqualTo(mVirtualDeviceAudioSessionId);
    }

    @Test
    public void textToSpeechWithVirtualDeviceContext_explicitSessionIdOverridesVdmSessionId()
            throws Exception {
        // Issue TTS.speak request with explicitly configured audio session id.
        int explicitlyRequestedAudioSessionId = mAudioManager.generateAudioSessionId();
        mTextToSpeech.speak(TTS_TEXT, TextToSpeech.QUEUE_ADD,
                createAudioSessionIdParamForTts(explicitlyRequestedAudioSessionId),
                UTTERANCE_ID);

        // Wait for audio playback with SPEECH content.
        AudioPlaybackConfiguration ttsAudioPlaybackConfig = getUninterruptibly(
                mSpeechPlaybackObserver.getSpeechAudioPlaybackConfigFuture(),
                TIMEOUT.getSeconds(), TimeUnit.SECONDS);

        // Verify that explicitly requested audio session id has overridden the virtual
        // device audio session id.
        assertThat(ttsAudioPlaybackConfig.getSessionId())
                .isEqualTo(explicitlyRequestedAudioSessionId);
    }

    private static Bundle createAudioSessionIdParamForTts(int sessionId) {
        Bundle bundle = new Bundle();
        bundle.putInt(TextToSpeech.Engine.KEY_PARAM_SESSION_ID, sessionId);
        return bundle;
    }

    private static @Nullable TextToSpeech initializeTextToSpeech(@NonNull Context context) {
        SettableFuture<Integer> ttsInitFuture = SettableFuture.create();
        TextToSpeech tts = new TextToSpeech(context, ttsInitFuture::set);
        int status;
        try {
            status = getUninterruptibly(ttsInitFuture, TIMEOUT.getSeconds(),
                    TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException exception) {
            Log.w(TAG, "Failed to initialize TTS", exception);
            return null;
        }
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, String.format("TextToSpeech initialization failed with %d", status));
            return null;
        }
        tts.setLanguage(Locale.US);
        return tts;
    }

    /**
     * Helper class implementing AudioPlaybackCallback to detect playback with SPEECH content type.
     */
    private static class SpeechPlaybackObserver extends AudioManager.AudioPlaybackCallback {

        private final SettableFuture<AudioPlaybackConfiguration> mAudioPlaybackConfigurationFuture =
                SettableFuture.create();

        @Override
        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
            super.onPlaybackConfigChanged(configs);
            configs.stream().filter(SpeechPlaybackObserver::isSpeechPlaybackStarted)
                    .findAny().ifPresent(mAudioPlaybackConfigurationFuture::set);
        }

        /**
         * Get {@link ListenableFuture} with observed SPEECH playback
         *
         * @return {@code ListenableFuture} instance which will be completed with
         * @code AudioPlaybackConfiguration} corresponding to SPEECH playback once detected.
         */
        ListenableFuture<AudioPlaybackConfiguration> getSpeechAudioPlaybackConfigFuture() {
            return mAudioPlaybackConfigurationFuture;
        }

        private static boolean isSpeechPlaybackStarted(AudioPlaybackConfiguration config) {
            return config.getAudioAttributes().getContentType() == CONTENT_TYPE_SPEECH
                    && config.getPlayerState() == PLAYER_STATE_STARTED;
        }
    }
}
