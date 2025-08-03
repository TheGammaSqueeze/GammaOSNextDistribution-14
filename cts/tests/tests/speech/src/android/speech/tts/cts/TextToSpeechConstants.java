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

/**
 * {@link android.speech.tts.TextToSpeech} testing related constants.
 */
class TextToSpeechConstants {

    static final String TTS_TEST_ON_UNBIND_ACTION = "android.speech.tts.cts.ON_UNBIND_ACTION";

    static final String MOCK_TTS_ENGINE = "android.speech.tts.cts";

    static final String TTS_TEST_SERVICE_CRASH_FLAG_FILE = "tts_test_service_crash_flag_file";

    private TextToSpeechConstants() {
    }
}
