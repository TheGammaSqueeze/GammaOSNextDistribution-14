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

package android.accessibilityservice.cts.utils;

/**
 * Utility class for providing multiprocess support. Used in AccessibilityDisplayProxyTest.
 */
public class MultiProcessUtils {
    /** Intent action for tracking ouchExplorationStateChangeListener calls. */
    public static final String TOUCH_EXPLORATION_STATE = "TOUCH_EXPLORATION_STATE";

    /** Intent action for tracking AccessibilityServiceStateChangeListener calls. */
    public static final String ACCESSIBILITY_SERVICE_STATE = "ACCESSIBILITY_SERVICE_STATE";

    /** Intent action for tracking AccessibilityStateChangeListener calls. */
    public static final String ACCESSIBILITY_STATE = "ACCESSIBILITY_STATE";

    /** Intent extra to track enabled states. */
    public static final String EXTRA_ENABLED = "extra_enabled";

    /** Intent extra to track enabled services. */
    public static final String EXTRA_ENABLED_SERVICES = "extra_enabled_services";

    public static final String SEPARATE_PROCESS_ACTIVITY_TITLE = "Separate process activity title";
}
