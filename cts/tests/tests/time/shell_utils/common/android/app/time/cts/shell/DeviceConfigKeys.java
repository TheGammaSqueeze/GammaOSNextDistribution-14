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

package android.app.time.cts.shell;

/** Constants for interacting with the device_config service. */
public final class DeviceConfigKeys {

    /**
     * The DeviceConfig namespace used for the time_detector, time_zone_detector and
     * location_time_zone_manager.
     */
    public static final String NAMESPACE_SYSTEM_TIME = "system_time";

    private DeviceConfigKeys() {
        // No need to instantiate.
    }

    /**
     * Keys and values associated with the location_time_zone_manager.
     * See also {@link TimeZoneDetectorShellHelper}.
     */
    public final class TimeZoneDetector {
        private TimeZoneDetector() {
            // No need to instantiate.
        }

        /**
         * The key to alter a device's "automatic time zone detection enabled" setting default
         * value. This flag is only intended for internal testing. It only has an effect on a
         * device's default value and does not interfere with a user's explicit preference.
         */
        public static final String KEY_TIME_ZONE_DETECTOR_AUTO_DETECTION_ENABLED_DEFAULT =
                "time_zone_detector_auto_detection_enabled_default";
    }

    /**
     * Keys and values associated with the location_time_zone_manager.
     * See also {@link LocationTimeZoneManagerShellHelper}.
     */
    public final class LocationTimeZoneManager {

        private LocationTimeZoneManager() {
            // No need to instantiate.
        }

        /** The key for the setting that controls rate limiting of provider events. */
        public static final String KEY_LTZP_EVENT_FILTERING_AGE_THRESHOLD_MILLIS =
                "ltzp_event_filtering_age_threshold_millis";
    }

    /**
     * Keys and values associated with the time_detector. See also {@link
     * TimeZoneDetectorShellHelper}.
     */
    public final class TimeDetector {

        private TimeDetector() {
            // No need to instantiate.
        }

        /**
         * See {@link
         * com.android.server.timedetector.ServerFlags#KEY_TIME_DETECTOR_LOWER_BOUND_MILLIS_OVERRIDE
         * }
         */
        public static final String KEY_TIME_DETECTOR_LOWER_BOUND_MILLIS_OVERRIDE =
                "time_detector_lower_bound_millis_override";

        /**
         * See {@link
         * com.android.server.timedetector.ServerFlags#KEY_TIME_DETECTOR_ORIGIN_PRIORITIES_OVERRIDE}
         */
        public static final String KEY_TIME_DETECTOR_ORIGIN_PRIORITIES_OVERRIDE =
                "time_detector_origin_priorities_override";

        /**
         * See {@link com.android.server.timedetector.TimeDetectorStrategy#ORIGIN_NETWORK}.
         */
        public static final String ORIGIN_NETWORK = "network";

        /**
         * See {@link com.android.server.timedetector.TimeDetectorStrategy#ORIGIN_EXTERNAL}.
         */
        public static final String ORIGIN_EXTERNAL = "external";
    }
}
