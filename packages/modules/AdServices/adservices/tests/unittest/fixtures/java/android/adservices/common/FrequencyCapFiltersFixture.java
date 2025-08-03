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

import android.os.Parcel;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;

/** Utility class for creating and testing {@link FrequencyCapFilters} objects. */
public class FrequencyCapFiltersFixture {
    public static final FrequencyCapFilters VALID_FREQUENCY_CAP_FILTERS =
            getValidFrequencyCapFiltersBuilder().build();

    public static final FrequencyCapFilters VALID_FREQUENCY_CAP_FILTERS_ONLY_WIN =
            new FrequencyCapFilters.Builder()
                    .setKeyedFrequencyCapsForWinEvents(
                            KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                    .build();

    public static final FrequencyCapFilters VALID_FREQUENCY_CAP_FILTERS_ONLY_IMPRESSION =
            new FrequencyCapFilters.Builder()
                    .setKeyedFrequencyCapsForImpressionEvents(
                            KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                    .build();

    public static final FrequencyCapFilters VALID_FREQUENCY_CAP_FILTERS_ONLY_VIEW =
            new FrequencyCapFilters.Builder()
                    .setKeyedFrequencyCapsForViewEvents(
                            KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                    .build();

    public static final FrequencyCapFilters VALID_FREQUENCY_CAP_FILTERS_ONLY_CLICK =
            new FrequencyCapFilters.Builder()
                    .setKeyedFrequencyCapsForClickEvents(
                            KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                    .build();

    public static FrequencyCapFilters.Builder getValidFrequencyCapFiltersBuilder() {
        return new FrequencyCapFilters.Builder()
                .setKeyedFrequencyCapsForWinEvents(
                        KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                .setKeyedFrequencyCapsForImpressionEvents(
                        KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                .setKeyedFrequencyCapsForViewEvents(
                        KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                .setKeyedFrequencyCapsForClickEvents(
                        KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST);
    }

    public static FrequencyCapFilters getFrequencyCapFiltersWithExcessiveNumFilters() {
        // Distribute the maximum number of filters across all event types
        final int distributedNumFilters = FrequencyCapFilters.MAX_NUM_FREQUENCY_CAP_FILTERS / 4;
        ImmutableList.Builder<KeyedFrequencyCap> listBuilder = ImmutableList.builder();
        for (int key = 0; key < distributedNumFilters; key++) {
            listBuilder.add(
                    KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(key)
                            .build());
        }

        Parcel sourceParcel = Parcel.obtain();
        sourceParcel.writeTypedList(listBuilder.build());
        sourceParcel.writeTypedList(listBuilder.build());
        sourceParcel.writeTypedList(listBuilder.build());

        // Add extra filters to the final list so that the total is exceeded
        final int numExtraFiltersToExceed =
                FrequencyCapFilters.MAX_NUM_FREQUENCY_CAP_FILTERS - (4 * distributedNumFilters) + 1;
        for (int key = 0; key < numExtraFiltersToExceed; key++) {
            listBuilder.add(
                    KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(key)
                            .build());
        }

        sourceParcel.writeTypedList(listBuilder.build());
        sourceParcel.setDataPosition(0);

        return FrequencyCapFilters.CREATOR.createFromParcel(sourceParcel);
    }

    public static FrequencyCapFilters getFrequencyCapFiltersWithNullCaps() {
        ArrayList<KeyedFrequencyCap> capList = new ArrayList<>();
        for (int key = 0; key < 3; key++) {
            capList.add(
                    KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(key)
                            .build());
        }

        // Each list ends with null
        capList.add(null);

        Parcel sourceParcel = Parcel.obtain();
        sourceParcel.writeTypedList(capList);
        sourceParcel.writeTypedList(capList);
        sourceParcel.writeTypedList(capList);
        sourceParcel.writeTypedList(capList);
        sourceParcel.setDataPosition(0);

        return FrequencyCapFilters.CREATOR.createFromParcel(sourceParcel);
    }
}
