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

import android.annotation.NonNull;
import android.app.timedetector.TimeDetector;
import android.content.Context;

public class TimeDetectorCompat {
    private final Object mTimeDetector;

    public TimeDetectorCompat(Context context) {
        mTimeDetector = context.getSystemService("time_detector");
    }

    public static ManualTimeSuggestionCompat createManualTimeSuggestion(
            long tzId, String debugInfo) {
        return new ManualTimeSuggestionCompat(
                TimeDetector.createManualTimeSuggestion(tzId, debugInfo));
    }

    public boolean suggestManualTime(@NonNull ManualTimeSuggestionCompat timeSuggestion) {
        return ((TimeDetector) mTimeDetector)
                .suggestManualTime(timeSuggestion.mManualTimeSuggestion);
    }
}
