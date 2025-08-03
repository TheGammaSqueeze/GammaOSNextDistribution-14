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

/** AppSetId part of the app manifest config (<ad-services-config>). */
final class AppManifestAppSetIdConfig extends AppManifestApiConfig {

    @Nullable private static AppManifestAppSetIdConfig sEnabledByDefaultInstance;

    /**
     * Constructor.
     *
     * @param allowAllToAccess corresponds to the boolean in the config.
     * @param allowAdPartnersToAccess corresponds to the list in the config.
     */
    public AppManifestAppSetIdConfig(
            boolean allowAllToAccess, List<String> allowAdPartnersToAccess) {
        super(allowAllToAccess, allowAdPartnersToAccess);
    }

    static AppManifestAppSetIdConfig getEnabledByDefaultInstance() {
        if (sEnabledByDefaultInstance == null) {
            sEnabledByDefaultInstance =
                    new AppManifestAppSetIdConfig(true, Collections.emptyList());
        }
        return sEnabledByDefaultInstance;
    }
}
