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

package com.android.cts.voiceinteraction;

import static android.Manifest.permission.BIND_VOICE_INTERACTION;
import static android.Manifest.permission.CAPTURE_AUDIO_HOTWORD;
import static android.Manifest.permission.MANAGE_HOTWORD_DETECTION;
import static android.Manifest.permission.MANAGE_SOUND_TRIGGER;
import static android.Manifest.permission.RECORD_AUDIO;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Intent;
import android.hardware.soundtrigger.SoundTrigger;
import android.os.ConditionVariable;
import android.os.RemoteException;
import android.provider.Settings;
import android.service.voice.AlwaysOnHotwordDetector;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;

import com.android.compatibility.common.util.SettingsStateChangerRule;
import com.android.compatibility.common.util.UserSettings;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

/**
 * The role of this abstract class is to handle the setup and tear down of the {@link
 * ProxyVoiceInteractionService}.
 *
 * Prior to the @Test method of classes extending from this class, the test
 * VoiceinteractionService is started and currently the active service.
 */
public abstract class AbstractVoiceInteractionServiceTest {
    private static final String TAG = AbstractVoiceInteractionServiceTest.class.getSimpleName();
    static final Duration TEST_SERVICE_TIMEOUT = Duration.ofSeconds(3);
    static final String TEST_DETECTOR_KEYPHRASE = "test keyphrase";
    static final int DEFAULT_HOTWORD_DETECTED_RESULT_PHRASE_ID = 0;

    /**
     * Exception used when {@link AlwaysOnHotwordDetector.Callback#onError()} is seen by a test
     */
    static class DetectorErrorException extends RuntimeException {
        DetectorErrorException(String message) {
            super(message);
        }
    }

    @Rule
    public final SettingsStateChangerRule mServiceSetterRule = new SettingsStateChangerRule(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            Settings.Secure.VOICE_INTERACTION_SERVICE,
            ProxyVoiceInteractionService.class.getPackageName() + "/"
                    + ProxyVoiceInteractionService.class.getName());
    private final ConditionVariable mIsTestServiceReady = new ConditionVariable();
    private final ConditionVariable mIsTestServiceShutdown = new ConditionVariable();
    @Rule
    public ServiceTestRule mServiceTestRule = new ServiceTestRule();
    ITestVoiceInteractionService mTestServiceInterface = null;

    private final UserSettings mUserSettings = new UserSettings();

    @Before
    public void setUp() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.LOG_COMPAT_CHANGE",
                        "android.permission.READ_COMPAT_CHANGE_CONFIG",
                        RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD, BIND_VOICE_INTERACTION,
                        "android.permission.MANAGE_VOICE_KEYPHRASES",
                        MANAGE_HOTWORD_DETECTION, MANAGE_SOUND_TRIGGER);

        // Start the ProxyVoiceInteractionService
        Intent serviceIntent = new Intent();
        serviceIntent.setAction(ProxyVoiceInteractionService.ACTION_BIND_TEST_VOICE_INTERACTION);
        serviceIntent.setComponent(
                new ComponentName(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        ProxyVoiceInteractionService.class));
        mTestServiceInterface = ITestVoiceInteractionService.Stub.asInterface(
                mServiceTestRule.bindService(serviceIntent)
        );

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
    public void tearDown() {
        Log.i(TAG, "tearDown: clearing settings value");
        mUserSettings.syncSet(Settings.Secure.VOICE_INTERACTION_SERVICE, "dummy_service");
        Log.i(TAG, "tearDown: waiting for shutdown");
        assertThat(mIsTestServiceShutdown.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();
        mServiceTestRule.unbindService();
        mIsTestServiceReady.close();
        mIsTestServiceShutdown.close();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().dropShellPermissionIdentity();
    }

    protected void enrollFakeSoundModelWithDetector(IProxyAlwaysOnHotwordDetector detector,
            Locale locale)
            throws RemoteException {
        // enroll a fake sound model and wait for enrolled state in detector
        if (mTestServiceInterface.getDspModuleProperties() != null) {
            IProxyKeyphraseModelManager keyphraseModelManager =
                    mTestServiceInterface.createKeyphraseModelManager();
            SoundTrigger.Keyphrase testKeyphrase = new SoundTrigger.Keyphrase(1 /* id */,
                    AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                    locale, TEST_DETECTOR_KEYPHRASE, new int[]{0});
            SoundTrigger.KeyphraseSoundModel soundModel = new SoundTrigger.KeyphraseSoundModel(
                    UUID.randomUUID(),
                    UUID.randomUUID(), null /* data */,
                    new SoundTrigger.Keyphrase[]{testKeyphrase});
            keyphraseModelManager.updateKeyphraseSoundModel(soundModel);
        } else {
            detector.overrideAvailability(AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);
        }
    }
}
