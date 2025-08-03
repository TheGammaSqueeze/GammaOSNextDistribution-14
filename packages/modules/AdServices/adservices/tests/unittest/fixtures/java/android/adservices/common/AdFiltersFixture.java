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

package android.adservices.common;

import static android.adservices.common.AdDataFixture.APP_INSTALL_ENABLED;

public class AdFiltersFixture {

    public static AdFilters getValidUnhiddenFilters() {
        AdFilters.Builder builder = new AdFilters.Builder();
        if (APP_INSTALL_ENABLED) {
            builder.setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS);
        }
        builder.setFrequencyCapFilters(FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS);
        return builder.build();
    }

    public static AdFilters.Builder getValidAdFiltersBuilder() {
        return new AdFilters.Builder()
                .setFrequencyCapFilters(FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS);
    }

    public static AdFilters getValidAdFilters() {
        return getValidAdFiltersBuilder().build();
    }
}
