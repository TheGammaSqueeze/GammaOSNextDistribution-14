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

package com.android.car.developeroptions;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Constant values for CarDeveloperOptions.
 */
public class CarDevelopmentConstants {
    // Preferences
    static final String BUGREPORT = "bugreport";
    static final String BUGREPORT_IN_POWER = "bugreport_in_power";
    static final String GRAPHICS_DRIVER_DASHBOARD = "graphics_driver_dashboard";
    static final String QUICK_SETTINGS_TILES = "quick_settings_tiles";
    static final String SHARED_DATA = "shared_data";
    static final String SYSTEM_SERVER_HEAP_DUMP = "system_server_heap_dump";
    static final String TOGGLE_ADB_WIRELESS = "toggle_adb_wireless";

    static final Set<String> PREFERENCES_TO_REMOVE =
            new LinkedHashSet<>(
                    Arrays.asList(
                            BUGREPORT,
                            BUGREPORT_IN_POWER,
                            GRAPHICS_DRIVER_DASHBOARD,
                            QUICK_SETTINGS_TILES,
                            SHARED_DATA,
                            SYSTEM_SERVER_HEAP_DUMP,
                            TOGGLE_ADB_WIRELESS
                    ));
}
