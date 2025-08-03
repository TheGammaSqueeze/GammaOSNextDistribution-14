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

/** Utility class for creating and testing {@link AppInstallFilters} objects. */
public class AppInstallFiltersFixture {
    public static final AppInstallFilters VALID_APP_INSTALL_FILTERS =
            getValidAppInstallFiltersBuilder().build();

    public static AppInstallFilters.Builder getValidAppInstallFiltersBuilder() {
        return new AppInstallFilters.Builder().setPackageNames(CommonFixture.PACKAGE_SET);
    }
}
