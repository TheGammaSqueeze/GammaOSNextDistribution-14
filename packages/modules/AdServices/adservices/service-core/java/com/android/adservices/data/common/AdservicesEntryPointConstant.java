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

package com.android.adservices.data.common;

/**
 * Public key value pair for Adservice entry point status
 *
 * <p>Hiding for future implementation and review for public exposure.
 *
 * @hide
 */
//TODO (b/266152092): remove unnecessary hide annotations
public class AdservicesEntryPointConstant {
    // timestamp of when AdServices entry point was first called.
    public static final String FIRST_ENTRY_REQUEST_TIMESTAMP = "firstEntryRequestTimestamp";

    public static final String KEY_ADSERVICES_ENTRY_POINT_STATUS = "adservicesEntryPointStatus";

    public static final int ADSERVICES_ENTRY_POINT_STATUS_ENABLE = 0;

    public static final int ADSERVICES_ENTRY_POINT_STATUS_DISABLE = 1;
}
