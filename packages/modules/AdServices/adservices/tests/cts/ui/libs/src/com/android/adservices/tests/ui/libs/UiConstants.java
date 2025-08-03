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
package com.android.adservices.tests.ui.libs;

public class UiConstants {

    public static final String SIM_REGION = "simRegion";

    public static final boolean ENTRY_POINT_ENABLED = true;

    public static final boolean ENTRY_POINT_DISABLED = false;

    public static final boolean AD_ID_ENABLED = true; /* non-zeroed out AdId */

    public static final boolean AD_ID_DISABLED = false; /* zeroed out AdId */

    public static final int LAUNCH_TIMEOUT_MS = 8000; /* wait time for UI elements to launch */

    public static final int SCROLL_TIMEOUT = 500;

    public static final int PRIMITIVE_UI_OBJECTS_LAUNCH_TIMEOUT_MS = 1000;
    public static final String SYSTEM_UI_NAME = "com.android.systemui";

    public static final String SYSTEM_UI_RESOURCE_ID =
            "com.android.systemui:id/notification_stack_scroller";

    public enum UX {
        GA_UX,
        BETA_UX,
        U18_UX,
        RVC_UX,
    }
}
