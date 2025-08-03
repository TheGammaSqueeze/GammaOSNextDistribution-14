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

package com.android.server.adservices.consent;

import static com.android.server.adservices.consent.AppConsentManager.DATASTORE_KEY_SEPARATOR;

/** Test Fixtures for App Consent Manager. Provides datastore related constants. */
public class AppConsentManagerFixture {
    public static final String TEST_DATASTORE_NAME = "appconsentdaotest.xml";

    public static final String APP10_PACKAGE_NAME = "app10.fake.name";
    public static final String APP20_PACKAGE_NAME = "app20.fake.name";
    public static final String APP30_PACKAGE_NAME = "app30.fake.name";

    public static final int APP10_UID = 10;
    public static final int APP20_UID = 20;
    public static final int APP30_UID = 30;

    public static final String APP10_DATASTORE_KEY =
            APP10_PACKAGE_NAME + DATASTORE_KEY_SEPARATOR + APP10_UID;
    public static final String APP20_DATASTORE_KEY =
            APP20_PACKAGE_NAME + DATASTORE_KEY_SEPARATOR + APP20_UID;
    public static final String APP30_DATASTORE_KEY =
            APP30_PACKAGE_NAME + DATASTORE_KEY_SEPARATOR + APP30_UID;
}
