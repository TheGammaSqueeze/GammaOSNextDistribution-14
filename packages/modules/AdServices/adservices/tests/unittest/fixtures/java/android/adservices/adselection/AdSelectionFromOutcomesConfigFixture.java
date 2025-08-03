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

package android.adservices.adselection;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.net.Uri;

import java.util.Collections;
import java.util.List;

public class AdSelectionFromOutcomesConfigFixture {
    public static final AdTechIdentifier SAMPLE_SELLER = AdSelectionConfigFixture.SELLER;
    public static final long SAMPLE_AD_SELECTION_ID_1 = 12345L;
    public static final long SAMPLE_AD_SELECTION_ID_2 = 123456L;
    public static final AdSelectionSignals SAMPLE_SELECTION_SIGNALS =
            AdSelectionSignals.fromString("{bidFloor: 10}");
    public static final Uri SAMPLE_SELECTION_LOGIC_URI_1 =
            CommonFixture.getUri(SAMPLE_SELLER, "/finalWinnerSelectionLogic");
    public static final Uri SAMPLE_SELECTION_LOGIC_URI_2 =
            CommonFixture.getUri(SAMPLE_SELLER, "/openBiddingLogic");

    public static AdSelectionFromOutcomesConfig anAdSelectionFromOutcomesConfig() {
        return new AdSelectionFromOutcomesConfig.Builder()
                .setSeller(SAMPLE_SELLER)
                .setAdSelectionIds(Collections.singletonList(SAMPLE_AD_SELECTION_ID_1))
                .setSelectionSignals(SAMPLE_SELECTION_SIGNALS)
                .setSelectionLogicUri(SAMPLE_SELECTION_LOGIC_URI_1)
                .build();
    }

    public static AdSelectionFromOutcomesConfig anAdSelectionFromOutcomesConfig(
            AdTechIdentifier seller) {
        return new AdSelectionFromOutcomesConfig.Builder()
                .setSeller(seller)
                .setAdSelectionIds(Collections.singletonList(SAMPLE_AD_SELECTION_ID_1))
                .setSelectionSignals(SAMPLE_SELECTION_SIGNALS)
                .setSelectionLogicUri(SAMPLE_SELECTION_LOGIC_URI_1)
                .build();
    }

    public static AdSelectionFromOutcomesConfig anAdSelectionFromOutcomesConfig(
            List<Long> adOutcomes) {
        return new AdSelectionFromOutcomesConfig.Builder()
                .setSeller(SAMPLE_SELLER)
                .setAdSelectionIds(adOutcomes)
                .setSelectionSignals(SAMPLE_SELECTION_SIGNALS)
                .setSelectionLogicUri(SAMPLE_SELECTION_LOGIC_URI_1)
                .build();
    }

    public static AdSelectionFromOutcomesConfig anAdSelectionFromOutcomesConfig(Uri selectionUri) {
        return new AdSelectionFromOutcomesConfig.Builder()
                .setSeller(SAMPLE_SELLER)
                .setAdSelectionIds(Collections.singletonList(SAMPLE_AD_SELECTION_ID_1))
                .setSelectionSignals(SAMPLE_SELECTION_SIGNALS)
                .setSelectionLogicUri(selectionUri)
                .build();
    }

    public static AdSelectionFromOutcomesConfig anAdSelectionFromOutcomesConfig(
            List<Long> adOutcomes, AdSelectionSignals selectionSignals, Uri selectionUri) {
        return new AdSelectionFromOutcomesConfig.Builder()
                .setSeller(AdTechIdentifier.fromString(selectionUri.getHost()))
                .setAdSelectionIds(adOutcomes)
                .setSelectionSignals(selectionSignals)
                .setSelectionLogicUri(selectionUri)
                .build();
    }

    public static AdSelectionFromOutcomesConfig anAdSelectionFromOutcomesConfig(
            AdTechIdentifier seller, Uri selectionUri) {
        return new AdSelectionFromOutcomesConfig.Builder()
                .setSeller(seller)
                .setAdSelectionIds(Collections.singletonList(SAMPLE_AD_SELECTION_ID_1))
                .setSelectionSignals(SAMPLE_SELECTION_SIGNALS)
                .setSelectionLogicUri(selectionUri)
                .build();
    }

    public static AdSelectionFromOutcomesConfig anAdSelectionFromOutcomesConfig(
            AdTechIdentifier seller,
            List<Long> adOutcomes,
            AdSelectionSignals selectionSignals,
            Uri selectionUri) {
        return new AdSelectionFromOutcomesConfig.Builder()
                .setSeller(seller)
                .setAdSelectionIds(adOutcomes)
                .setSelectionSignals(selectionSignals)
                .setSelectionLogicUri(selectionUri)
                .build();
    }
}
