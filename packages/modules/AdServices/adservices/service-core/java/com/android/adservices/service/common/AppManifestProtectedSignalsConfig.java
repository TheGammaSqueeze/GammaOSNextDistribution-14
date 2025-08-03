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

package com.android.adservices.service.common;

import android.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/** Protected Signals part of the app manifest config (<ad-services-config>). */
final class AppManifestProtectedSignalsConfig extends AppManifestApiConfig {

    @Nullable private static AppManifestProtectedSignalsConfig sEnabledByDefaultInstance;

    private static final boolean ALLOW_ACCESS_TO_ALL_FOR_TESTING_VALUE = true;

    /**
     * Constructor.
     *
     * @param allowAllToAccess corresponds to the boolean in the config.
     * @param allowAdPartnersToAccess corresponds to the list in the config.
     */
    AppManifestProtectedSignalsConfig(
            boolean allowAllToAccess, List<String> allowAdPartnersToAccess) {
        super(allowAllToAccess, allowAdPartnersToAccess);
    }

    static AppManifestProtectedSignalsConfig getEnabledByDefaultInstance() {
        if (sEnabledByDefaultInstance == null) {
            sEnabledByDefaultInstance =
                    new AppManifestProtectedSignalsConfig(
                            ALLOW_ACCESS_TO_ALL_FOR_TESTING_VALUE, Collections.emptyList());
        }
        return sEnabledByDefaultInstance;
    }
}
