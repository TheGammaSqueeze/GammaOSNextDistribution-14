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
import android.app.timezonedetector.TimeZoneDetector;
import android.content.Context;

public class TimeZoneDetectorCompat {
    private final Object mTimeZoneDetector;

    public TimeZoneDetectorCompat(Context context) {
        this.mTimeZoneDetector = context.getSystemService("time_zone_detector");
    }

    public boolean suggestManualTimeZone(
            @NonNull ManualTimeZoneSuggestionCompat timeZoneSuggestion) {
        return ((TimeZoneDetector) this.mTimeZoneDetector)
                .suggestManualTimeZone(timeZoneSuggestion.mManualTimeZoneSuggestion);
    }

    public static ManualTimeZoneSuggestionCompat createManualTimeZoneSuggestion(
            String tzId, String debugInfo) {
        return new ManualTimeZoneSuggestionCompat(
                TimeZoneDetector.createManualTimeZoneSuggestion(tzId, debugInfo));
    }
}
