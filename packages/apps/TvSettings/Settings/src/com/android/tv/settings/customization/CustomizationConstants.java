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

package com.android.tv.settings.customization;

/**
 * List of string keys mapping Partner specified settings screens to
 * their corresponding fragments.
 */
public final class CustomizationConstants {
    private CustomizationConstants() {}

    public static final String MAIN_SCREEN = "main";

    public static final String CHANNELS_AND_INPUTS_SCREEN = "channels_and_inputs";

    public static final String DISPLAY_PREVIEW_SCREEN = "preview_display";

    public static final String DEVICE_SCREEN = "device";

    public static final String ABOUT_SCREEN = "device_info";

    public static final String RESET_OPTIONS_SCREEN = "reset_options";

    public static final String POWER_AND_ENERGY_SCREEN = "power_and_energy";
}
