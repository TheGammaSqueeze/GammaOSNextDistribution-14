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

package android.voiceinteraction.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.ComponentName;
import android.content.Intent;
import android.hardware.soundtrigger.SoundTrigger;
import android.hardware.soundtrigger.SoundTrigger.KeyphraseRecognitionExtra;
import android.media.AudioFormat;
import android.os.ConditionVariable;
import android.platform.test.annotations.AppModeFull;
import android.provider.Settings;
import android.util.Log;
import android.voiceinteraction.common.Utils;
import android.voiceinteraction.service.IProxyAlwaysOnHotwordDetector;
import android.voiceinteraction.service.IProxyDetectorCallback;
import android.voiceinteraction.service.ITestVoiceInteractionService;
import android.voiceinteraction.service.ITestVoiceInteractionServiceListener;
import android.voiceinteraction.service.IVoiceInteractionServiceBindingHelper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;

import com.android.compatibility.common.util.SettingsStateChangerRule;
import com.android.compatibility.common.util.UserSettings;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.Locale;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode hotword detector")
public class VoiceInteractionMultiDetectorTest {
    private static final String TAG = VoiceInteractionMultiDetectorTest.class.getSimpleName();
    private static final Duration TEST_SERVICE_TIMEOUT = Duration.ofSeconds(3);

    @Rule
    public final SettingsStateChangerRule mServiceSetterRule = new SettingsStateChangerRule(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            Settings.Secure.VOICE_INTERACTION_SERVICE,
            Utils.PROXY_VOICEINTERACTION_SERVICE_COMPONENT);
    private final ConditionVariable mIsTestServiceReady = new ConditionVariable();
    private final ConditionVariable mIsTestServiceShutdown = new ConditionVariable();
    @Rule
    public ServiceTestRule mServiceTestRule = new ServiceTestRule();
    private ITestVoiceInteractionService mTestServiceInterface = null;

    private final UserSettings mUserSettings = new UserSettings();

    @Before
    public void setUp() throws Exception {
        Intent serviceIntent = new Intent();
        serviceIntent.setAction(Utils.ACTION_BIND_TEST_VOICE_INTERACTION);
        serviceIntent.setComponent(
                new ComponentName(Utils.TEST_VOICE_INTERACTION_SERVICE_PACKAGE_NAME,
                        Utils.VOICE_INTERACTION_SERVICE_BINDING_HELPER_CLASS_NAME));
        IVoiceInteractionServiceBindingHelper voiceInteractionServiceBindingHelper =
                IVoiceInteractionServiceBindingHelper.Stub.asInterface(
                        mServiceTestRule.bindService(serviceIntent)
                );
        mTestServiceInterface = voiceInteractionServiceBindingHelper.getVoiceInteractionService();

        mTestServiceInterface.registerListener(new ITestVoiceInteractionServiceListener.Stub() {
            @Override
            public void onReady() {
                Log.i(TAG, "ITestVoiceInteractionServiceListener: onReady");
                mIsTestServiceReady.open();
                mIsTestServiceShutdown.close();
            }

            @Override
            public void onShutdown() {
                Log.i(TAG, "ITestVoiceInteractionServiceListener: onShutdown");
                mIsTestServiceReady.close();
                mIsTestServiceShutdown.open();
            }
        });
        assertThat(mIsTestServiceReady.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();
    }

    @After
    public void tearDown() throws Exception {
        Log.i(TAG, "tearDown: clearing settings value");
        mUserSettings.syncSet(Settings.Secure.VOICE_INTERACTION_SERVICE, "dummy_service");
        Log.i(TAG, "tearDown: waiting for shutdown");
        assertThat(mIsTestServiceShutdown.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();
        mServiceTestRule.unbindService();
        mIsTestServiceReady.close();
        mIsTestServiceShutdown.close();
    }

    @Test
    @Ignore("b/274789133 - ProxyVIService instrumentation issue")
    public void testAlwaysOnHotwordDetectorDestroy_throwsExceptionAfterDestroy() throws Exception {
        final ConditionVariable availabilityChanged = new ConditionVariable();
        IProxyAlwaysOnHotwordDetector alwaysOnHotwordDetector =
                mTestServiceInterface.createAlwaysOnHotwordDetector("test keyphrase",
                        Locale.ENGLISH.toLanguageTag(),
                        new IProxyDetectorCallback.Stub() {
                            @Override
                            public void onAvailabilityChanged(int status) {
                                availabilityChanged.open();
                            }
                        });
        assertThat(availabilityChanged.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();

        alwaysOnHotwordDetector.startRecognitionOnFakeAudioStream();
        alwaysOnHotwordDetector.destroy();
        assertThrows(IllegalStateException.class,
                () -> alwaysOnHotwordDetector.startRecognitionOnFakeAudioStream());
        assertThrows(IllegalStateException.class, () -> alwaysOnHotwordDetector.stopRecognition());
        assertThrows(IllegalStateException.class,
                () -> alwaysOnHotwordDetector.updateState(null, null));
        assertThrows(IllegalStateException.class, () ->
                alwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(/* status */ 0,
                        /* soundModelHandle */ 100, /* halEventReceivedMillis */ 12345,
                        /* captureAvailable */ true, /* captureSession */ 101,
                        /* captureDelayMs */ 1000, /* capturePreambleMs */ 1001,
                        /* triggerInData */ true,
                        new AudioFormat.Builder()
                                .setSampleRate(32000)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build(),
                        new byte[1024],
                        ImmutableList.of(
                                new KeyphraseRecognitionExtra(10,
                                        SoundTrigger.RECOGNITION_MODE_VOICE_TRIGGER,
                                        100))));
    }

    @Test
    @Ignore("b/274789133 - ProxyVIService instrumentation issue")
    public void testAlwaysOnHotwordDetectorCreate_rejectMultipleDetectorsOfTheSameType()
            throws Exception {
        final ConditionVariable availabilityChanged = new ConditionVariable();

        IProxyAlwaysOnHotwordDetector alwaysOnHotwordDetectorEnglish =
                mTestServiceInterface.createAlwaysOnHotwordDetector("test keyphrase",
                        Locale.ENGLISH.toLanguageTag(),
                        new IProxyDetectorCallback.Stub() {
                            @Override
                            public void onAvailabilityChanged(int status) {
                                availabilityChanged.open();
                            }
                        });
        assertThat(alwaysOnHotwordDetectorEnglish).isNotNull();
        assertThat(availabilityChanged.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();

        availabilityChanged.close();
        assertThrows(IllegalStateException.class,
                () -> mTestServiceInterface.createAlwaysOnHotwordDetector("test keyphrase",
                        Locale.ENGLISH.toLanguageTag(),
                        new IProxyDetectorCallback.Stub() {
                            @Override
                            public void onAvailabilityChanged(int status) {
                                availabilityChanged.open();
                            }
                        }));
        alwaysOnHotwordDetectorEnglish.destroy();
    }
}
