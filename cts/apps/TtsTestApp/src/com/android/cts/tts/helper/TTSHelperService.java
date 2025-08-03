package com.android.cts.tts.helper;

import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;

/**
 * Stub implementation of TTS service
 */
public class TTSHelperService extends TextToSpeechService {

  public TTSHelperService() {}

  @Override
  protected int onIsLanguageAvailable(String lang, String country, String variant) {
    return TextToSpeech.LANG_AVAILABLE;
  }

  @Override
  public int onLoadLanguage(String lang, String country, String variant) {
    return TextToSpeech.LANG_AVAILABLE;
  }

  @Override
  protected void onStop() {
    return;
  }

  @Override
  protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
    return;
  }

  @Override
  protected String[] onGetLanguage() {
    return new String[] {"", "", ""};
  }
}
