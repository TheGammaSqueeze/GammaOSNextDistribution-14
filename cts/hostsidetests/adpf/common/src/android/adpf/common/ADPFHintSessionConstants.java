/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.adpf.common;

public class ADPFHintSessionConstants {
    public static final String TEST_NAME_KEY = "Test_Name";

    public static final int MINIMUM_VALID_SDK = 33; // T
    public static final String IS_HINT_SESSION_SUPPORTED_KEY = "isHintSessionSupported";
    public static final String BASELINE_KEY = "baseline";
    public static final String LIGHT_LOAD_KEY = "light_load";
    public static final String HEAVY_LOAD_KEY = "heavy_load";
    public static final String TRANSITION_LOAD_KEY = "transition_load";

    // public static final String SECOND_LIGHT_LOAD_KEY = "second_light_load";


    public static final Double ERROR_MARGIN = 0.2;

    // Used to determine which tests to run on the native side.
    public static final String[] TESTS_ENABLED = new String[] {
        BASELINE_KEY,
        // LIGHT_LOAD_KEY,
        // HEAVY_LOAD_KEY,
        TRANSITION_LOAD_KEY,
    };
}
