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

package com.android.car.settings.tts;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TtsEngines;
import android.speech.tts.Voice;

import androidx.lifecycle.LifecycleOwner;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.settings.R;
import com.android.car.settings.common.ActivityResultCallback;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestUtil;
import com.android.car.settings.common.SeekBarPreference;
import com.android.car.settings.testutils.TestLifecycleOwner;

import com.google.android.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class TtsPlaybackPreferenceControllerTest {

    private static final String DEFAULT_ENGINE_NAME = "com.android.car.settings.tts.test.default";
    private static final TextToSpeech.EngineInfo ENGINE_INFO = new TextToSpeech.EngineInfo();
    private static final Voice VOICE = new Voice("Test Name", Locale.ENGLISH, /* quality= */ 0,
            /* latency= */ 0, /* requiresNetworkConnection= */ true, /* features= */ null);

    static {
        ENGINE_INFO.label = "Test Engine";
        ENGINE_INFO.name = "com.android.car.settings.tts.test.other";
    }

    private Context mContext = ApplicationProvider.getApplicationContext();
    private LifecycleOwner mLifecycleOwner;
    private TtsPlaybackPreferenceController mPreferenceController;
    private CarUxRestrictions mCarUxRestrictions;
    private PreferenceGroup mPreferenceGroup;
    private ListPreference mDefaultLanguagePreference;
    private SeekBarPreference mSpeechRatePreference;
    private SeekBarPreference mVoicePitchPreference;
    private Preference mResetPreference;

    @Mock
    private TextToSpeech mTextToSpeech;
    @Mock
    private TtsEngines mEnginesHelper;
    @Mock
    private FragmentController mFragmentController;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLifecycleOwner = new TestLifecycleOwner();

        mCarUxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, /* timestamp= */ 0).build();

        PreferenceManager preferenceManager = new PreferenceManager(mContext);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
        mPreferenceController = new TestTtsPlaybackPreferenceController(mContext,
                "key", mFragmentController, mCarUxRestrictions, mEnginesHelper);

        mDefaultLanguagePreference = new ListPreference(mContext);
        mDefaultLanguagePreference.setKey(mContext.getString(R.string.pk_tts_default_language));
        mPreferenceGroup.addPreference(mDefaultLanguagePreference);

        mSpeechRatePreference = new SeekBarPreference(mContext);
        mSpeechRatePreference.setKey(mContext.getString(R.string.pk_tts_speech_rate));
        mPreferenceGroup.addPreference(mSpeechRatePreference);

        mVoicePitchPreference = new SeekBarPreference(mContext);
        mVoicePitchPreference.setKey(mContext.getString(R.string.pk_tts_pitch));
        mPreferenceGroup.addPreference(mVoicePitchPreference);

        mResetPreference = new Preference(mContext);
        mResetPreference.setKey(mContext.getString(R.string.pk_tts_reset));
        mPreferenceGroup.addPreference(mResetPreference);
        PreferenceControllerTestUtil.assignPreference(mPreferenceController, mPreferenceGroup);

        when(mTextToSpeech.getCurrentEngine()).thenReturn(ENGINE_INFO.name);
        when(mTextToSpeech.getVoice()).thenReturn(VOICE);
        when(mEnginesHelper.parseLocaleString(VOICE.getLocale().toString())).thenReturn(
                VOICE.getLocale());
        when(mEnginesHelper.parseLocaleString(Locale.CANADA.toString())).thenReturn(Locale.CANADA);
        when(mEnginesHelper.parseLocaleString(Locale.KOREA.toString())).thenReturn(Locale.KOREA);

        Settings.Secure.putInt(mContext.getContentResolver(), Settings.Secure.TTS_DEFAULT_RATE,
                TextToSpeech.Engine.DEFAULT_RATE);
        Settings.Secure.putInt(mContext.getContentResolver(), Settings.Secure.TTS_DEFAULT_PITCH,
                TextToSpeech.Engine.DEFAULT_PITCH);
    }

    @Test
    public void onCreate_startsCheckVoiceData() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.mOnInitListener.onInit(TextToSpeech.SUCCESS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(mFragmentController).startActivityForResult(
                intent.capture(), eq(TtsPlaybackPreferenceController.VOICE_DATA_CHECK),
                any(ActivityResultCallback.class));

        assertThat(intent.getValue().getAction()).isEqualTo(
                TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        assertThat(intent.getValue().getPackage()).isEqualTo(ENGINE_INFO.name);
    }

    @Test
    public void voiceDataCheck_processActivityResult_dataIsNull_defaultSynthRemainsUnchanged() {
        mPreferenceController.onCreate(mLifecycleOwner);
        Settings.Secure.putString(mContext.getContentResolver(), Settings.Secure.TTS_DEFAULT_SYNTH,
                DEFAULT_ENGINE_NAME);

        mPreferenceController.processActivityResult(
                TtsPlaybackPreferenceController.VOICE_DATA_CHECK,
                TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, /* data= */ null);

        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.TTS_DEFAULT_SYNTH)).isEqualTo(DEFAULT_ENGINE_NAME);
    }

    @Test
    public void voiceDataCheck_processActivityResult_dataIsNotNull_updatesDefaultSynth() {
        mPreferenceController.onCreate(mLifecycleOwner);
        Settings.Secure.putString(mContext.getContentResolver(), Settings.Secure.TTS_DEFAULT_SYNTH,
                DEFAULT_ENGINE_NAME);

        Intent data = new Intent();
        mPreferenceController.processActivityResult(
                TtsPlaybackPreferenceController.VOICE_DATA_CHECK,
                TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, data);

        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.TTS_DEFAULT_SYNTH)).isEqualTo(ENGINE_INFO.name);
    }

    @Test
    public void voiceDataCheck_processActivityResult_checkSuccess_hasVoices_populatesPreference() {
        mPreferenceController.onCreate(mLifecycleOwner);

        // Check that the length is 0 initially.
        assertThat(mDefaultLanguagePreference.getEntries()).isNull();

        Intent data = new Intent();
        data.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,
                Lists.newArrayList(
                        Locale.ENGLISH.toString(),
                        Locale.CANADA.toString(),
                        Locale.KOREA.toString()
                ));

        mPreferenceController.mOnInitListener.onInit(TextToSpeech.SUCCESS);
        mPreferenceController.processActivityResult(
                TtsPlaybackPreferenceController.VOICE_DATA_CHECK,
                TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, data);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // Length is 3 languages + default language.
        assertThat(mDefaultLanguagePreference.getEntries().length).isEqualTo(4);
    }

    @Test
    public void getSampleText_processActivityResult_dataIsNull_setsDefaultText() {
        mPreferenceController.processActivityResult(TtsPlaybackPreferenceController.GET_SAMPLE_TEXT,
                TextToSpeech.LANG_AVAILABLE, /* data= */ null);

        assertThat(mPreferenceController.getSampleText()).isEqualTo(
                mContext.getString(R.string.tts_default_sample_string));
    }

    @Test
    public void getSampleText_processActivityResult_emptyText_setsDefaultText() {
        String testData = "";

        Intent data = new Intent();
        data.putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, testData);

        mPreferenceController.processActivityResult(TtsPlaybackPreferenceController.GET_SAMPLE_TEXT,
                TextToSpeech.LANG_AVAILABLE, data);

        assertThat(mPreferenceController.getSampleText()).isEqualTo(
                mContext.getString(R.string.tts_default_sample_string));
    }

    @Test
    public void getSampleText_processActivityResult_dataIsNotNull_setsCorrectText() {
        String testData = "Test sample text";

        Intent data = new Intent();
        data.putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, testData);

        mPreferenceController.processActivityResult(TtsPlaybackPreferenceController.GET_SAMPLE_TEXT,
                TextToSpeech.LANG_AVAILABLE, data);

        assertThat(mPreferenceController.getSampleText()).isEqualTo(testData);
    }

    @Test
    public void defaultLanguage_handlePreferenceChanged_passEmptyValue_setsDefault() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.mOnInitListener.onInit(TextToSpeech.SUCCESS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        Intent data = new Intent();
        data.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,
                Lists.newArrayList(
                        Locale.ENGLISH.toString(),
                        Locale.CANADA.toString(),
                        Locale.KOREA.toString()
                ));
        mPreferenceController.processActivityResult(
                TtsPlaybackPreferenceController.VOICE_DATA_CHECK,
                TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, data);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // Test change listener.
        mDefaultLanguagePreference.callChangeListener("");

        verify(mTextToSpeech).setLanguage(Locale.getDefault());
    }

    @Test
    public void defaultLanguage_handlePreferenceChanged_passLocale_setsLocale() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.mOnInitListener.onInit(TextToSpeech.SUCCESS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        Intent data = new Intent();
        data.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,
                Lists.newArrayList(
                        Locale.ENGLISH.toString(),
                        Locale.CANADA.toString(),
                        Locale.KOREA.toString()
                ));
        mPreferenceController.processActivityResult(
                TtsPlaybackPreferenceController.VOICE_DATA_CHECK,
                TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, data);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // Test change listener.
        mDefaultLanguagePreference.callChangeListener(Locale.ENGLISH.toString());

        verify(mTextToSpeech).setLanguage(Locale.ENGLISH);
    }

    @Test
    public void defaultLanguage_handlePreferenceChanged_passLocale_setsSummary() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.mOnInitListener.onInit(TextToSpeech.SUCCESS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        Intent data = new Intent();
        data.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,
                Lists.newArrayList(
                        Locale.ENGLISH.toString(),
                        Locale.CANADA.toString(),
                        Locale.KOREA.toString()
                ));
        mPreferenceController.processActivityResult(
                TtsPlaybackPreferenceController.VOICE_DATA_CHECK,
                TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, data);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        mDefaultLanguagePreference.callChangeListener(Locale.ENGLISH.toString());

        assertThat(mDefaultLanguagePreference.getSummary()).isEqualTo(
                Locale.ENGLISH.getDisplayName());
    }

    @Test
    public void speechRate_handlePreferenceChanged_updatesSecureSettings() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.mOnInitListener.onInit(TextToSpeech.SUCCESS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        int newSpeechRate = TextToSpeech.Engine.DEFAULT_RATE + 40;
        mSpeechRatePreference.callChangeListener(newSpeechRate);

        assertThat(Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.TTS_DEFAULT_RATE, TextToSpeech.Engine.DEFAULT_RATE)).isEqualTo(
                newSpeechRate);
    }

    @Test
    public void speechRate_handlePreferenceChanged_updatesTts() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.mOnInitListener.onInit(TextToSpeech.SUCCESS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        int newSpeechRate = TextToSpeech.Engine.DEFAULT_RATE + 40;
        mSpeechRatePreference.callChangeListener(newSpeechRate);

        verify(mTextToSpeech).setSpeechRate(
                newSpeechRate / TtsPlaybackSettingsManager.SCALING_FACTOR);
    }

    @Test
    public void speechRate_handlePreferenceChanged_speaksSampleText() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.mOnInitListener.onInit(TextToSpeech.SUCCESS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        int newSpeechRate = TextToSpeech.Engine.DEFAULT_RATE + 40;
        mSpeechRatePreference.callChangeListener(newSpeechRate);

        verify(mTextToSpeech).speak(any(), eq(TextToSpeech.QUEUE_FLUSH), isNull(), eq("Sample"));
    }

    @Test
    public void voicePitch_handlePreferenceChanged_updatesSecureSettings() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.mOnInitListener.onInit(TextToSpeech.SUCCESS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        int newVoicePitch = TextToSpeech.Engine.DEFAULT_PITCH + 40;
        mVoicePitchPreference.callChangeListener(newVoicePitch);

        assertThat(Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.TTS_DEFAULT_PITCH, TextToSpeech.Engine.DEFAULT_PITCH)).isEqualTo(
                newVoicePitch);
    }

    @Test
    public void voicePitch_handlePreferenceChanged_updatesTts() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.mOnInitListener.onInit(TextToSpeech.SUCCESS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        int newVoicePitch = TextToSpeech.Engine.DEFAULT_PITCH + 40;
        mVoicePitchPreference.callChangeListener(newVoicePitch);

        verify(mTextToSpeech).setPitch(
                newVoicePitch / TtsPlaybackSettingsManager.SCALING_FACTOR);
    }

    @Test
    public void voicePitch_handlePreferenceChanged_speaksSampleText() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.mOnInitListener.onInit(TextToSpeech.SUCCESS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        int newVoicePitch = TextToSpeech.Engine.DEFAULT_PITCH + 40;
        mVoicePitchPreference.callChangeListener(newVoicePitch);

        verify(mTextToSpeech).speak(any(), eq(TextToSpeech.QUEUE_FLUSH), isNull(), eq("Sample"));
    }

    @Test
    public void refreshUi_notInitialized_disablesPreference() {
        mPreferenceController.onCreate(mLifecycleOwner);

        mPreferenceController.refreshUi();

        assertThat(mDefaultLanguagePreference.isEnabled()).isFalse();
        // Default language preference is always hidden
        assertThat(mDefaultLanguagePreference.isVisible()).isFalse();
        assertThat(mSpeechRatePreference.isEnabled()).isFalse();
        assertThat(mVoicePitchPreference.isEnabled()).isFalse();
        assertThat(mResetPreference.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_initialized_defaultLocaleIsNull_disablesPreference() {
        when(mEnginesHelper.parseLocaleString(any())).thenReturn(null);

        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.mOnInitListener.onInit(TextToSpeech.SUCCESS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        mPreferenceController.refreshUi();

        assertThat(mDefaultLanguagePreference.isEnabled()).isFalse();
        // Default language preference is always hidden
        assertThat(mDefaultLanguagePreference.isVisible()).isFalse();
        assertThat(mSpeechRatePreference.isEnabled()).isFalse();
        assertThat(mVoicePitchPreference.isEnabled()).isFalse();
        assertThat(mResetPreference.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_defaultLocaleNotSupported_disablesPreferencesExceptLanguage() {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.mOnInitListener.onInit(TextToSpeech.SUCCESS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        Intent data = new Intent();
        data.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,
                Lists.newArrayList(
                        // English is default locale, but isn't available.
                        Locale.CANADA.toString(),
                        Locale.KOREA.toString()
                ));
        mPreferenceController.processActivityResult(
                TtsPlaybackPreferenceController.VOICE_DATA_CHECK,
                TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, data);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        mPreferenceController.refreshUi();

        assertThat(mDefaultLanguagePreference.isEnabled()).isTrue();
        // Default language preference is always hidden
        assertThat(mDefaultLanguagePreference.isVisible()).isFalse();
        assertThat(mSpeechRatePreference.isEnabled()).isFalse();
        assertThat(mVoicePitchPreference.isEnabled()).isFalse();
        assertThat(mResetPreference.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_initialized_defaultLocaleSupported_enablesPreference()
            throws InterruptedException {
        mPreferenceController.onCreate(mLifecycleOwner);
        mPreferenceController.mOnInitListener.onInit(TextToSpeech.SUCCESS);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        Intent data = new Intent();
        data.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,
                Lists.newArrayList(
                        Locale.ENGLISH.toString(),
                        Locale.CANADA.toString(),
                        Locale.KOREA.toString()
                ));
        mPreferenceController.processActivityResult(
                TtsPlaybackPreferenceController.VOICE_DATA_CHECK,
                TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, data);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        mPreferenceController.refreshUi();
        // Some fields are posted to the background handler.
        // Wait to make sure all states have been finalized.
        CountDownLatch latch = new CountDownLatch(1);
        latch.await(5, TimeUnit.SECONDS);
        mPreferenceController.mBackgroundHandler.post(latch::countDown);

        assertThat(mPreferenceGroup.isEnabled()).isTrue();
        assertThat(mDefaultLanguagePreference.isEnabled()).isTrue();
        // Default language preference is always hidden
        assertThat(mDefaultLanguagePreference.isVisible()).isFalse();
        assertThat(mSpeechRatePreference.isEnabled()).isTrue();
        assertThat(mVoicePitchPreference.isEnabled()).isTrue();
        assertThat(mResetPreference.isEnabled()).isTrue();
    }

    private class TestTtsPlaybackPreferenceController extends TtsPlaybackPreferenceController {

        TestTtsPlaybackPreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController,
                CarUxRestrictions uxRestrictions, TtsEngines enginesHelper) {
            super(context, preferenceKey, fragmentController, uxRestrictions, enginesHelper);
        }

        @Override
        TextToSpeech createTts() {
            return mTextToSpeech;
        }
    }
}
