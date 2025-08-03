/**
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
package com.android.cts.tts.helper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Activity called by the framework to return the list the installable voices. */
public class CheckVoiceData extends Activity {
    @Override
      protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Set<String> availableVoices = new HashSet<>();
        availableVoices.add("eng-USA");
        availableVoices.add("");

        // Populate a test list of languages that are available at the server.
        final Intent returnVal = new Intent();
        ArrayList<String> availableVoicesList = new ArrayList<>(availableVoices);
        returnVal.putStringArrayListExtra(
                TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, availableVoicesList);

        // Populate a test list of languages that are unavailable at the server.
        ArrayList<String> unavailableVoicesList = new ArrayList<>();
        unavailableVoicesList.add("");
        returnVal.putStringArrayListExtra(
                TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, unavailableVoicesList);

        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, returnVal);
        finish();
    }
}
