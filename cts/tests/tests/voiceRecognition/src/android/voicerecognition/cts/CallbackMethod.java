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

enum CallbackMethod {
    CALLBACK_METHOD_UNSPECIFIED,
    CALLBACK_METHOD_BEGINNING_OF_SPEECH,
    CALLBACK_METHOD_BUFFER_RECEIVED,
    CALLBACK_METHOD_END_OF_SPEECH,
    CALLBACK_METHOD_ERROR,
    CALLBACK_METHOD_PARTIAL_RESULTS,
    CALLBACK_METHOD_READY_FOR_SPEECH,
    CALLBACK_METHOD_RESULTS,
    CALLBACK_METHOD_RMS_CHANGED,
    CALLBACK_METHOD_SEGMENTS_RESULTS,
    CALLBACK_METHOD_END_SEGMENTED_SESSION,
    CALLBACK_METHOD_LANGUAGE_DETECTION
}
