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

package android.voicerecognition.cts;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;

class TestObjects {
    public static final int ERROR_CODE = 42;
    public static final float RMS_CHANGED_VALUE = 13.13f;

    public static final Bundle RESULTS_BUNDLE = new Bundle();
    static {
        RESULTS_BUNDLE.putChar("a", 'a');
    }
    public static final Bundle PARTIAL_RESULTS_BUNDLE = new Bundle();
    static {
        PARTIAL_RESULTS_BUNDLE.putChar("b", 'b');
    }
    public static final Bundle READY_FOR_SPEECH_BUNDLE = new Bundle();
    static {
        READY_FOR_SPEECH_BUNDLE.putChar("c", 'c');
    }
    public static final Intent START_LISTENING_INTENT =
            new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    static {
        START_LISTENING_INTENT.putExtra("d", 'd');
    }
    public static final Bundle SEGMENT_RESULTS_BUNDLE = new Bundle();
    static {
        SEGMENT_RESULTS_BUNDLE.putChar("e", 'e');
    }
    public static final Bundle LANGUAGE_DETECTION_BUNDLE = new Bundle();
    static {
        PARTIAL_RESULTS_BUNDLE.putChar("f", 'f');
    }
}
