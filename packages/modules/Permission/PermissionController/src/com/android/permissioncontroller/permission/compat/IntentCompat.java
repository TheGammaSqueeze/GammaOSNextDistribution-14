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

package com.android.permissioncontroller.permission.compat;

import android.content.Intent;

/** Holds Intent constants across SDKs for compatibility. */
public class IntentCompat {

    /**
     * A boolean mentioning UI shows attribution for the app.
     * <p> used with {@link Intent#ACTION_VIEW_PERMISSION_USAGE_FOR_PERIOD}. </p>
     */
    public static final String EXTRA_SHOWING_ATTRIBUTION =
            "android.intent.extra.SHOWING_ATTRIBUTION";
}
