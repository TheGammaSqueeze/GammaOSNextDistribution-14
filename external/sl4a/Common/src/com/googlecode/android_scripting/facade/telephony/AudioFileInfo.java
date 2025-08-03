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

package com.googlecode.android_scripting.facade.telephony;

import androidx.annotation.NonNull;
import com.google.auto.value.AutoValue;

/** An AutoValue class to maintain the information of an audio file. */
@AutoValue
abstract class AudioFileInfo {
  static AudioFileInfo create(String mime, int sampleRate, int channelCount) {
    return new AutoValue_AudioFileInfo(mime, sampleRate, channelCount);
  }

  @NonNull
  @Override
  public final String toString() {
    return String.format("{mime:%s, sample rate:%d, channel count:%d}",
        mime(),
        sampleRate(),
        channelCount());
  }

  abstract String mime();
  abstract int sampleRate();
  abstract int channelCount();
}
