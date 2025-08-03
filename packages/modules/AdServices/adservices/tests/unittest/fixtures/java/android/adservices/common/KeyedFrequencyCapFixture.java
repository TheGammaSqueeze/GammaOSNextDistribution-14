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

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/** Utility class for creating and testing {@link KeyedFrequencyCap} objects. */
public class KeyedFrequencyCapFixture {
    public static final int KEY1 = 1;
    public static final int KEY2 = 2;
    public static final int KEY3 = 3;
    public static final int KEY4 = 4;
    public static final int VALID_COUNT = 10;
    public static final int FILTER_COUNT = 1;
    public static final int FILTER_UNDER_MAX_COUNT = FILTER_COUNT - 1;
    public static final int FILTER_EXCEED_COUNT = FILTER_COUNT;
    public static final Duration ONE_DAY_DURATION = Duration.ofDays(1);

    public static final ImmutableList<KeyedFrequencyCap> VALID_KEYED_FREQUENCY_CAP_LIST =
            ImmutableList.of(
                    getValidKeyedFrequencyCapBuilderOncePerDay(KEY1).build(),
                    getValidKeyedFrequencyCapBuilderOncePerDay(KEY2).build(),
                    getValidKeyedFrequencyCapBuilderOncePerDay(KEY3).build(),
                    getValidKeyedFrequencyCapBuilderOncePerDay(KEY4).build());

    public static final List<KeyedFrequencyCap> KEYED_FREQUENCY_CAP_LIST_CONTAINING_NULL =
            Arrays.asList(
                    getValidKeyedFrequencyCapBuilderOncePerDay(KEY1).build(),
                    getValidKeyedFrequencyCapBuilderOncePerDay(KEY2).build(),
                    getValidKeyedFrequencyCapBuilderOncePerDay(KEY3).build(),
                    null,
                    getValidKeyedFrequencyCapBuilderOncePerDay(KEY4).build());

    public static ImmutableList<KeyedFrequencyCap> getExcessiveNumberOfFrequencyCapsList() {
        ImmutableList.Builder<KeyedFrequencyCap> listBuilder = ImmutableList.builder();

        // Add just one more than the limit
        for (int key = 0; key <= FrequencyCapFilters.MAX_NUM_FREQUENCY_CAP_FILTERS; key++) {
            listBuilder.add(getValidKeyedFrequencyCapBuilderOncePerDay(key).build());
        }

        return listBuilder.build();
    }

    public static KeyedFrequencyCap.Builder getValidKeyedFrequencyCapBuilderOncePerDay(int key) {
        return new KeyedFrequencyCap.Builder(key, FILTER_COUNT, ONE_DAY_DURATION);
    }

    public static KeyedFrequencyCap getKeyedFrequencyCapWithFields(
            int adCounterKey, int maxCount, Duration interval) {
        Parcel sourceParcel = Parcel.obtain();
        sourceParcel.writeInt(adCounterKey);
        sourceParcel.writeInt(maxCount);
        sourceParcel.writeLong(interval.getSeconds());
        sourceParcel.setDataPosition(0);

        return KeyedFrequencyCap.CREATOR.createFromParcel(sourceParcel);
    }
}
