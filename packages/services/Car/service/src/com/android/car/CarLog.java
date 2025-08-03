/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.car;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;

/**
 * Helper class for class tags for CarService.
 */
public final class CarLog {
    private static final String PREFIX = "CAR.";
    // Matcher to check if class name has keyword "Car"
    private static final String MATCHER = ".*Car([A-Z].*|$)";

    public static final String TAG_AM = "CAR.AM";
    public static final String TAG_APP_FOCUS = "CAR.APP_FOCUS";
    public static final String TAG_AUDIO = "CAR.AUDIO";
    public static final String TAG_CLUSTER = "CAR.CLUSTER";
    public static final String TAG_DIAGNOSTIC = "CAR.DIAGNOSTIC";
    public static final String TAG_EVS = "CAR.EVS";
    public static final String TAG_HAL = "CAR.HAL";
    public static final String TAG_INPUT = "CAR.INPUT";
    public static final String TAG_MEDIA = "CAR.MEDIA";
    public static final String TAG_POWER = "CAR.POWER";
    public static final String TAG_PROJECTION = "CAR.PROJECTION";
    public static final String TAG_SENSOR = "CAR.SENSOR";
    public static final String TAG_SERVICE = "CAR.SERVICE";
    public static final String TAG_STORAGE = "CAR.STORAGE";
    public static final String TAG_TELEMETRY = "CAR.TELEMETRY";
    public static final String TAG_TIME = "CAR.TIME";
    public static final String TAG_WATCHDOG = "CAR.WATCHDOG";

    /**
     * Returns TAG that should be used for logging.
     */
    public static String tagFor(Class<?> clazz) {
        String tag = clazz.getSimpleName();
        // If class has a matcher, don't add prefix.
        if (tag.matches(MATCHER)) return tag;
        return PREFIX + tag;
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE,
            details = "private constructor")
    private CarLog() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}
