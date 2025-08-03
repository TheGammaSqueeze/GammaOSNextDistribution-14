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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlertDialog;
import android.content.Context;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TtsEngines;
import android.speech.tts.Voice;

import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class TtsPlaybackSettingsManagerTest {
    private static final String ENGINE_NAME = "com.android.car.settings.tts.test";
    private static final String SAMPLE_TEXT = "Sample text";

    private Context mContext = ApplicationProvider.getApplicationContext();
    private TtsPlaybackSettingsManager mPlaybackSettingsManager;

    @Mock
    private TextToSpeech mTts;
    @Mock
    private TtsEngines mEnginesHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mPlaybackSettingsManager = new TtsPlaybackSettingsManager(mContext, mTts, mEnginesHelper);

        when(mTts.getCurrentEngine()).thenReturn(ENGINE_NAME);
        Settings.Secure.putInt(mContext.getContentResolver(), Settings.Secure.TTS_DEFAULT_RATE,
                TextToSpeech.Engine.DEFAULT_RATE);
        Settings.Secure.putInt(mContext.getContentResolver(), Settings.Secure.TTS_DEFAULT_PITCH,
                TextToSpeech.Engine.DEFAULT_PITCH);
    }

    @Test
    public void updateSpeechRate_updatesSetting() {
        int newSpeechRate = TextToSpeech.Engine.DEFAULT_RATE + 40;
        mPlaybackSettingsManager.updateSpeechRate(newSpeechRate);
        assertThat(Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.TTS_DEFAULT_RATE, TextToSpeech.Engine.DEFAULT_RATE)).isEqualTo(
                newSpeechRate);
    }

    @Test
    public void updateSpeechRate_updatesTts() {
        int newSpeechRate = TextToSpeech.Engine.DEFAULT_RATE + 40;
        mPlaybackSettingsManager.updateSpeechRate(newSpeechRate);
        verify(mTts).setSpeechRate(newSpeechRate / TtsPlaybackSettingsManager.SCALING_FACTOR);
    }

    @Test
    public void getCurrentSpeechRate_returnsCorrectRate() {
        int newSpeechRate = TextToSpeech.Engine.DEFAULT_RATE + 40;
        mPlaybackSettingsManager.updateSpeechRate(newSpeechRate);

        assertThat(mPlaybackSettingsManager.getCurrentSpeechRate()).isEqualTo(newSpeechRate);
    }

    @Test
    public void resetSpeechRate_setsToDefault() {
        int newSpeechRate = TextToSpeech.Engine.DEFAULT_RATE + 40;
        mPlaybackSettingsManager.updateSpeechRate(newSpeechRate);

        mPlaybackSettingsManager.resetSpeechRate();

        assertThat(mPlaybackSettingsManager.getCurrentSpeechRate()).isEqualTo(
                TextToSpeech.Engine.DEFAULT_RATE);
    }

    @Test
    public void updateVoicePitch_updatesSetting() {
        int newVoicePitch = TextToSpeech.Engine.DEFAULT_PITCH + 40;
        mPlaybackSettingsManager.updateVoicePitch(newVoicePitch);
        assertThat(Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.TTS_DEFAULT_PITCH, TextToSpeech.Engine.DEFAULT_PITCH)).isEqualTo(
                newVoicePitch);
    }

    @Test
    public void updateVoicePitch_updatesTts() {
        int newVoicePitch = TextToSpeech.Engine.DEFAULT_PITCH + 40;
        mPlaybackSettingsManager.updateVoicePitch(newVoicePitch);
        verify(mTts).setPitch(newVoicePitch / TtsPlaybackSettingsManager.SCALING_FACTOR);
    }

    @Test
    public void getCurrentVoicePitch_returnsCorrectRate() {
        int newVoicePitch = TextToSpeech.Engine.DEFAULT_PITCH + 40;
        mPlaybackSettingsManager.updateVoicePitch(newVoicePitch);

        assertThat(mPlaybackSettingsManager.getCurrentVoicePitch()).isEqualTo(newVoicePitch);
    }

    @Test
    public void resetVoicePitch_setsToDefault() {
        int newVoicePitch = TextToSpeech.Engine.DEFAULT_PITCH + 40;
        mPlaybackSettingsManager.updateVoicePitch(newVoicePitch);

        mPlaybackSettingsManager.resetVoicePitch();

        assertThat(mPlaybackSettingsManager.getCurrentVoicePitch()).isEqualTo(
                TextToSpeech.Engine.DEFAULT_PITCH);
    }

    @Test
    public void getStoredTtsLocale_localeIsNotDefaultForEngine_returnsLocale() {
        when(mEnginesHelper.isLocaleSetToDefaultForEngine(ENGINE_NAME)).thenReturn(false);
        when(mEnginesHelper.getLocalePrefForEngine(ENGINE_NAME)).thenReturn(Locale.CHINESE);

        assertThat(mPlaybackSettingsManager.getStoredTtsLocale()).isEqualTo(Locale.CHINESE);
    }

    @Test
    public void getStoredTtsLocale_localeIsDefaultForEngine_returnsNull() {
        when(mEnginesHelper.isLocaleSetToDefaultForEngine(ENGINE_NAME)).thenReturn(true);

        assertThat(mPlaybackSettingsManager.getStoredTtsLocale()).isNull();
    }

    @Test
    public void updateTtsLocale_nonNullLocale_setsLanguage() {
        mPlaybackSettingsManager.updateTtsLocale(Locale.KOREAN);

        verify(mTts).setLanguage(Locale.KOREAN);
    }

    @Test
    public void updateTtsLocale_nullLocale_setsDefaultLanguage() {
        mPlaybackSettingsManager.updateTtsLocale(null);

        verify(mTts).setLanguage(Locale.getDefault());
    }

    @Test
    public void updateTtsLocale_nonNullLocale_correctResultCode_updatesLocalePref() {
        when(mTts.setLanguage(Locale.KOREAN)).thenReturn(TextToSpeech.LANG_AVAILABLE);
        mPlaybackSettingsManager.updateTtsLocale(Locale.KOREAN);

        verify(mEnginesHelper).updateLocalePrefForEngine(ENGINE_NAME, Locale.KOREAN);
    }

    @Test
    public void updateTtsLocale_nonNullLocale_wrongResultCode_doesNotUpdateLocalePref() {
        when(mTts.setLanguage(Locale.KOREAN)).thenReturn(TextToSpeech.LANG_MISSING_DATA);
        mPlaybackSettingsManager.updateTtsLocale(Locale.KOREAN);

        verify(mEnginesHelper, never()).updateLocalePrefForEngine(any(), any());
    }

    @Test
    @UiThreadTest
    public void speakSampleText_requiresNetworkConnection_languageNotAvailable_showsAlert() {
        TtsPlaybackSettingsManager spiedManager = spy(mPlaybackSettingsManager);
        AlertDialog alertDialog = mock(AlertDialog.class);
        when(spiedManager.createNetworkAlertDialog()).thenReturn(alertDialog);

        Voice voice = new Voice("Test Name", Locale.FRANCE, /* quality= */ 0,
                /* latency= */ 0, /* requiresNetworkConnection= */ true, /* features= */ null);
        when(mTts.getVoice()).thenReturn(voice);
        when(mEnginesHelper.parseLocaleString(Locale.FRANCE.toString())).thenReturn(Locale.FRANCE);
        when(mTts.isLanguageAvailable(any(Locale.class))).thenReturn(
                TextToSpeech.LANG_MISSING_DATA);

        spiedManager.speakSampleText(SAMPLE_TEXT);

        verify(alertDialog).show();
    }

    @Test
    public void speakSampleText_requiresNetworkConnection_languageAvailable_speaksText() {
        Voice voice = new Voice("Test Name", Locale.FRENCH, /* quality= */ 0,
                /* latency= */ 0, /* requiresNetworkConnection= */ true, /* features= */ null);
        when(mTts.getVoice()).thenReturn(voice);
        when(mTts.isLanguageAvailable(Locale.FRENCH)).thenReturn(TextToSpeech.LANG_AVAILABLE);

        mPlaybackSettingsManager.speakSampleText(SAMPLE_TEXT);

        verify(mTts).speak(SAMPLE_TEXT, TextToSpeech.QUEUE_FLUSH, null, "Sample");
    }

    @Test
    public void speakSampleText_doesNotRequireNetworkConnection_speaksText() {
        Voice voice = new Voice("Test Name", Locale.FRENCH, /* quality= */ 0,
                /* latency= */ 0, /* requiresNetworkConnection= */ false, /* features= */ null);
        when(mTts.getVoice()).thenReturn(voice);

        mPlaybackSettingsManager.speakSampleText(SAMPLE_TEXT);

        verify(mTts).speak(SAMPLE_TEXT, TextToSpeech.QUEUE_FLUSH, null, "Sample");
    }
}
