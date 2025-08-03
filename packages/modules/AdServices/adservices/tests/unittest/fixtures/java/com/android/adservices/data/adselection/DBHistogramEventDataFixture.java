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

package com.android.adservices.data.adselection;

import com.android.adservices.service.adselection.HistogramEventFixture;

public class DBHistogramEventDataFixture {
    public static final DBHistogramEventData VALID_DB_HISTOGRAM_EVENT_DATA =
            getValidDBHistogramEventDataBuilder(DBHistogramIdentifierFixture.VALID_FOREIGN_KEY)
                    .build();

    public static DBHistogramEventData.Builder getValidDBHistogramEventDataBuilder(
            long foreignKeyId) {
        return DBHistogramEventData.builder()
                .setHistogramIdentifierForeignKey(foreignKeyId)
                .setAdCounterIntKey(HistogramEventFixture.VALID_HISTOGRAM_EVENT.getAdCounterKey())
                .setAdEventType(HistogramEventFixture.VALID_HISTOGRAM_EVENT.getAdEventType())
                .setTimestamp(HistogramEventFixture.VALID_HISTOGRAM_EVENT.getTimestamp());
    }
}
