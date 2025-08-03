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

package com.android.adservices.service.ui.constants;

/** UX debug messages. */
public class DebugMessages {

    public static String UNAUTHORIZED_CALLER_MESSAGE =
            "Caller is not authorized to control AdServices state";

    public static String IS_AD_SERVICES_ENABLED_API_CALLED_MESSAGE =
            "isAdServicesEnabled() API is called.";

    public static String SET_AD_SERVICES_ENABLED_API_CALLED_MESSAGE =
            "setAdServicesEnabled() API is called.";

    public static String ENABLE_AD_SERVICES_API_CALLED_MESSAGE =
            "enableAdServices() API is called.";

    public static String BACK_COMPAT_FEATURE_ENABLED_MESSAGE =
            "Back compatibility feature is enabled.";

    public static String ENABLE_AD_SERVICES_API_ENABLED_MESSAGE =
            "enableAdServices() API is enabled.";

    public static String ENABLE_AD_SERVICES_API_DISABLED_MESSAGE =
            "enableAdServices() API is disabled.";

    public static String PRIVACY_SANDBOX_UI_REQUEST_MESSAGE =
            "PS UI request was received and enrollment will not be triggered.";

    public static String NO_ENROLLMENT_CHANNEL_AVAILABLE_MESSAGE =
            "No enrollment channel available";

    public static String CALLER_NOT_ALLOWED_MESSAGE =
            "Caller is not authorized in the allowed list";
}
