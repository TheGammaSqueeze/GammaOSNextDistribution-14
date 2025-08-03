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

package com.android.adservices.common;

import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

import com.android.adservices.data.common.DBAdData;

import java.util.List;
import java.util.stream.Collectors;

public class DBAdDataFixture {
    public static final DBAdData VALID_DB_AD_DATA_NO_FILTERS =
            getValidDbAdDataNoFiltersBuilder().build();

    public static DBAdData.Builder getValidDbAdDataBuilder() {
        AdData mirrorAdData =
                AdDataFixture.getValidFilterAdDataByBuyer(CommonFixture.VALID_BUYER_1, 0);
        return getValidDbAdDataNoFiltersBuilder()
                .setAdCounterKeys(mirrorAdData.getAdCounterKeys())
                .setAdFilters(mirrorAdData.getAdFilters());
    }

    public static DBAdData.Builder getValidDbAdDataNoFiltersBuilder() {
        AdData mirrorAdData =
                AdDataFixture.getValidFilterAdDataByBuyer(CommonFixture.VALID_BUYER_1, 0);
        return new DBAdData.Builder()
                .setRenderUri(mirrorAdData.getRenderUri())
                .setMetadata(mirrorAdData.getMetadata());
    }

    public static DBAdData.Builder getValidDbAdDataNoFiltersBuilder(
            AdTechIdentifier buyer, int sequenceNumber) {
        AdData mirrorAdData = AdDataFixture.getValidFilterAdDataByBuyer(buyer, sequenceNumber);
        return new DBAdData.Builder()
                .setRenderUri(mirrorAdData.getRenderUri())
                .setMetadata(mirrorAdData.getMetadata());
    }

    public static List<DBAdData> getValidDbAdDataListByBuyer(AdTechIdentifier buyer) {
        return AdDataFixture.getValidFilterAdsByBuyer(buyer).stream()
                .map(DBAdDataFixture::convertAdDataToDBAdData)
                .collect(Collectors.toList());
    }

    public static List<DBAdData> getValidDbAdDataListByBuyerWithAdRenderId(AdTechIdentifier buyer) {
        return AdDataFixture.getValidFilterAdsWithAdRenderIdByBuyer(buyer).stream()
                .map(DBAdDataFixture::convertAdDataToDBAdData)
                .collect(Collectors.toList());
    }

    public static List<DBAdData> getValidDbAdDataListByBuyerNoFilters(AdTechIdentifier buyer) {
        return AdDataFixture.getValidAdsByBuyer(buyer).stream()
                .map(DBAdDataFixture::convertAdDataToDBAdData)
                .collect(Collectors.toList());
    }

    public static List<DBAdData> getInvalidDbAdDataListByBuyer(AdTechIdentifier buyer) {
        return AdDataFixture.getInvalidAdsByBuyer(buyer).stream()
                .map(DBAdDataFixture::convertAdDataToDBAdData)
                .collect(Collectors.toList());
    }

    public static DBAdData convertAdDataToDBAdData(AdData adData) {
        return new DBAdData(
                adData.getRenderUri(),
                adData.getMetadata(),
                adData.getAdCounterKeys(),
                adData.getAdFilters(),
                adData.getAdRenderId());
    }
}
