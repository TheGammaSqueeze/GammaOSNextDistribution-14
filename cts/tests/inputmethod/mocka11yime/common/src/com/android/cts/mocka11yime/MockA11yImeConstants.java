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

package com.android.cts.mocka11yime;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.content.ComponentName;

import androidx.annotation.StringDef;

import java.lang.annotation.Retention;

final class MockA11yImeConstants {
    static final String SETTINGS_PROVIDER_AUTHORITY = "com.android.cts.mocka11yime.provider";
    static final ComponentName COMPONENT_NAME = new ComponentName(
            "com.android.cts.mocka11yime", "com.android.cts.mocka11yime.MockA11yIme");

    @Retention(SOURCE)
    @StringDef(value = {
            BundleKey.EVENT_CALLBACK_INTENT_ACTION_NAME,
            BundleKey.SETTINGS,
    })
    public @interface BundleKey {
        String EVENT_CALLBACK_INTENT_ACTION_NAME = "eventCallbackActionName";
        String SETTINGS = "settings";
    }

    @Retention(SOURCE)
    @StringDef(value = {
            ContentProviderCommand.DELETE,
            ContentProviderCommand.WRITE,
    })
    public @interface ContentProviderCommand {
        String DELETE = "delete";
        String WRITE = "write";
    }
}
