/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.settings.common;

import static com.google.common.truth.Truth.assertWithMessage;

import androidx.preference.Preference;

public class PreferenceControllerTestUtil {

    /**
     * Associates a PreferenceController with its Preference.
     */
    public static void assignPreference(PreferenceController controller, Preference preference) {
        controller.setPreference(preference);
    }

    /**
     * Asserts that the test availability status matches the expected result
     *
     * @param actualValue availability returned by the test
     * @param expectedValue expected availability
     */
    public static void assertAvailability(int actualValue, int expectedValue) {
        assertWithMessage("controller availability (%s=%s, %s=%s)",
                actualValue, availabilityToString(actualValue),
                expectedValue, availabilityToString(expectedValue))
                .that(actualValue).isEqualTo(expectedValue);
    }

    private static String availabilityToString(int value) {
        switch (value) {
            case PreferenceController.AVAILABLE:
                return "AVAILABLE";
            case PreferenceController.AVAILABLE_FOR_VIEWING:
                return "AVAILABLE_FOR_VIEWING";
            case PreferenceController.CONDITIONALLY_UNAVAILABLE:
                return "CONDITIONALLY_UNAVAILABLE";
            case PreferenceController.DISABLED_FOR_PROFILE:
                return "DISABLED_FOR_PROFILE";
            case PreferenceController.UNSUPPORTED_ON_DEVICE:
                return "UNSUPPORTED_ON_DEVICE";
            default:
                return "INVALID-" + value;
        }
    }
}
