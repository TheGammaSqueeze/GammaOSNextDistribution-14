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

package android.speech.tts.cts;

import static android.speech.tts.cts.TextToSpeechConstants.TTS_TEST_ON_UNBIND_ACTION;
import static android.speech.tts.cts.TextToSpeechConstants.TTS_TEST_SERVICE_CRASH_FLAG_FILE;

import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.util.Log;

import java.io.File;

/**
 * Stub service for testing {@link android.speech.tts.TextToSpeech} connection related
 * functionality.
 */
public class ConnectionTestTextToSpeechService extends TextToSpeechService {
    private static final String LOG_TAG = "ConnectionTestTextToSpeechService";


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "onCreate");

        if (new File(getApplicationContext().getCacheDir(),
                TTS_TEST_SERVICE_CRASH_FLAG_FILE).exists()) {
            Log.d(LOG_TAG, "Going to crash itself. Pid: " + Process.myPid());
            Process.killProcess(Process.myPid());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(LOG_TAG, "onBind");

        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(LOG_TAG, "onUnbind");

        sendBroadcast(new Intent(TTS_TEST_ON_UNBIND_ACTION));

        return super.onUnbind(intent);
    }

    @Override
    public void onStop() {
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    protected String[] onGetLanguage() {
        return new String[]{"eng", "US", ""};
    }

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        return TextToSpeech.LANG_NOT_SUPPORTED;
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        return onIsLanguageAvailable(lang, country, variant);
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
    }

    @Override
    public String onGetDefaultVoiceNameFor(String lang, String country, String variant) {
        return "";
    }
}
