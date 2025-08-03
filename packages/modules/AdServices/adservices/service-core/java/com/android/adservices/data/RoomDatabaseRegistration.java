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

package com.android.adservices.data;

import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionDebugReportingDatabase;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.kanon.KAnonDatabase;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.common.cache.CacheDatabase;

class RoomDatabaseRegistration {
    private RoomDatabaseRegistration() {}

    AdSelectionServerDatabase mAdSelectionServerDatabase;
    CustomAudienceDatabase mCustomAudienceDatabase;
    AdSelectionDatabase mAdSelectionDatabase;
    AdSelectionDebugReportingDatabase mAdSelectionDebugReportingDatabase;
    SharedStorageDatabase mSharedStorageDatabase;
    ProtectedSignalsDatabase mProtectedSignalsDatabase;
    CacheDatabase mCacheDatabase;
    KAnonDatabase mKAnonDatabase;
}
