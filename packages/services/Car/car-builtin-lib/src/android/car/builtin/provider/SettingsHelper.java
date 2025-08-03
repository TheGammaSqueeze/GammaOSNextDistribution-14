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

package android.car.builtin.provider;

import android.annotation.SystemApi;

/**
 * Helper for hidden {@link android.provider.Settings} APIs.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class SettingsHelper {

    // TODO(b/226458202): deprecated constant (and class) when we provide the proper API
    /**
     * Value of {@link android.provider.Settings.System#SYSTEM_LOCALES}.
     */
    public static final String SYSTEM_LOCALES = "system_locales";

    private SettingsHelper() {
        throw new UnsupportedOperationException("contains only static members");
    }
}
