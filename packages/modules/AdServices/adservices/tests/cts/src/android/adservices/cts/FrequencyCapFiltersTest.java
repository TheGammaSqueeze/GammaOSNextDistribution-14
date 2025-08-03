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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.FrequencyCapFilters;
import android.adservices.common.FrequencyCapFiltersFixture;
import android.adservices.common.KeyedFrequencyCap;
import android.adservices.common.KeyedFrequencyCapFixture;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link FrequencyCapFilters}. */
@SmallTest
public class FrequencyCapFiltersTest {
    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildValidFrequencyCapFilters_success() {
        final FrequencyCapFilters originalFilters =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForWinEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForImpressionEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForViewEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForClickEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .build();

        assertThat(originalFilters.getKeyedFrequencyCapsForWinEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST);
        assertThat(originalFilters.getKeyedFrequencyCapsForImpressionEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST);
        assertThat(originalFilters.getKeyedFrequencyCapsForViewEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST);
        assertThat(originalFilters.getKeyedFrequencyCapsForClickEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST);
    }

    @Test
    public void testParcelFrequencyCapFilters_success() {
        final FrequencyCapFilters originalFilters =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForWinEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForImpressionEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForViewEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForClickEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .build();

        Parcel targetParcel = Parcel.obtain();
        originalFilters.writeToParcel(targetParcel, 0);
        targetParcel.setDataPosition(0);
        final FrequencyCapFilters filtersFromParcel =
                FrequencyCapFilters.CREATOR.createFromParcel(targetParcel);

        assertThat(filtersFromParcel.getKeyedFrequencyCapsForWinEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST);
        assertThat(filtersFromParcel.getKeyedFrequencyCapsForImpressionEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST);
        assertThat(filtersFromParcel.getKeyedFrequencyCapsForViewEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST);
        assertThat(filtersFromParcel.getKeyedFrequencyCapsForClickEvents())
                .containsExactlyElementsIn(KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST);
    }

    @Test
    public void testEqualsIdentical_success() {
        final FrequencyCapFilters originalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();
        final FrequencyCapFilters identicalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();

        assertThat(originalFilters.equals(identicalFilters)).isTrue();
    }

    @Test
    public void testEqualsDifferent_success() {
        final FrequencyCapFilters originalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();
        final FrequencyCapFilters differentFilters = new FrequencyCapFilters.Builder().build();

        assertThat(originalFilters.equals(differentFilters)).isFalse();
    }

    @Test
    public void testEqualsNull_success() {
        final FrequencyCapFilters originalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();
        final FrequencyCapFilters nullFilters = null;

        assertThat(originalFilters.equals(nullFilters)).isFalse();
    }

    @Test
    public void testHashCodeIdentical_success() {
        final FrequencyCapFilters originalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();
        final FrequencyCapFilters identicalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();

        assertThat(originalFilters.hashCode()).isEqualTo(identicalFilters.hashCode());
    }

    @Test
    public void testHashCodeDifferent_success() {
        final FrequencyCapFilters originalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();
        final FrequencyCapFilters differentFilters = new FrequencyCapFilters.Builder().build();

        assertThat(originalFilters.hashCode()).isNotEqualTo(differentFilters.hashCode());
    }

    @Test
    public void testToString() {
        final FrequencyCapFilters originalFilters =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForWinEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForImpressionEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForViewEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .setKeyedFrequencyCapsForClickEvents(
                                KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST)
                        .build();

        final String expectedString =
                String.format(
                        "FrequencyCapFilters{mKeyedFrequencyCapsForWinEvents=%s,"
                                + " mKeyedFrequencyCapsForImpressionEvents=%s,"
                                + " mKeyedFrequencyCapsForViewEvents=%s,"
                                + " mKeyedFrequencyCapsForClickEvents=%s}",
                        KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST,
                        KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST,
                        KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST,
                        KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST);
        assertThat(originalFilters.toString()).isEqualTo(expectedString);
    }

    @Test
    public void testBuildNullWinCaps_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new FrequencyCapFilters.Builder().setKeyedFrequencyCapsForWinEvents(null));
    }

    @Test
    public void testBuildWinCapsContainingNull_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FrequencyCapFilters.Builder()
                                .setKeyedFrequencyCapsForWinEvents(
                                        KeyedFrequencyCapFixture
                                                .KEYED_FREQUENCY_CAP_LIST_CONTAINING_NULL));
    }

    @Test
    public void testBuildNullImpressionCaps_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new FrequencyCapFilters.Builder()
                                .setKeyedFrequencyCapsForImpressionEvents(null));
    }

    @Test
    public void testBuildImpressionCapsContainingNull_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FrequencyCapFilters.Builder()
                                .setKeyedFrequencyCapsForImpressionEvents(
                                        KeyedFrequencyCapFixture
                                                .KEYED_FREQUENCY_CAP_LIST_CONTAINING_NULL));
    }

    @Test
    public void testBuildNullViewCaps_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new FrequencyCapFilters.Builder().setKeyedFrequencyCapsForViewEvents(null));
    }

    @Test
    public void testBuildViewCapsContainingNull_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FrequencyCapFilters.Builder()
                                .setKeyedFrequencyCapsForViewEvents(
                                        KeyedFrequencyCapFixture
                                                .KEYED_FREQUENCY_CAP_LIST_CONTAINING_NULL));
    }

    @Test
    public void testBuildNullClickCaps_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new FrequencyCapFilters.Builder().setKeyedFrequencyCapsForClickEvents(null));
    }

    @Test
    public void testBuildClickCapsContainingNull_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FrequencyCapFilters.Builder()
                                .setKeyedFrequencyCapsForClickEvents(
                                        KeyedFrequencyCapFixture
                                                .KEYED_FREQUENCY_CAP_LIST_CONTAINING_NULL));
    }

    @Test
    public void testBuildNoSetters_success() {
        final FrequencyCapFilters originalFilters = new FrequencyCapFilters.Builder().build();

        assertThat(originalFilters.getKeyedFrequencyCapsForWinEvents()).isEmpty();
        assertThat(originalFilters.getKeyedFrequencyCapsForImpressionEvents()).isEmpty();
        assertThat(originalFilters.getKeyedFrequencyCapsForViewEvents()).isEmpty();
        assertThat(originalFilters.getKeyedFrequencyCapsForClickEvents()).isEmpty();
    }

    @Test
    public void testBuildExcessiveNumberOfWinFilters_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FrequencyCapFilters.Builder()
                                .setKeyedFrequencyCapsForWinEvents(
                                        KeyedFrequencyCapFixture
                                                .getExcessiveNumberOfFrequencyCapsList())
                                .build());
    }

    @Test
    public void testBuildExcessiveNumberOfImpressionFilters_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FrequencyCapFilters.Builder()
                                .setKeyedFrequencyCapsForImpressionEvents(
                                        KeyedFrequencyCapFixture
                                                .getExcessiveNumberOfFrequencyCapsList())
                                .build());
    }

    @Test
    public void testBuildExcessiveNumberOfViewFilters_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FrequencyCapFilters.Builder()
                                .setKeyedFrequencyCapsForViewEvents(
                                        KeyedFrequencyCapFixture
                                                .getExcessiveNumberOfFrequencyCapsList())
                                .build());
    }

    @Test
    public void testBuildExcessiveNumberOfClickFilters_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FrequencyCapFilters.Builder()
                                .setKeyedFrequencyCapsForClickEvents(
                                        KeyedFrequencyCapFixture
                                                .getExcessiveNumberOfFrequencyCapsList())
                                .build());
    }

    @Test
    public void testBuildExcessiveNumberOfTotalFilters_throws() {
        final int distributedNumFilters = FrequencyCapFilters.MAX_NUM_FREQUENCY_CAP_FILTERS / 4;
        ImmutableList.Builder<KeyedFrequencyCap> listBuilder = ImmutableList.builder();
        for (int key = 0; key < distributedNumFilters; key++) {
            listBuilder.add(
                    KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(key)
                            .build());
        }

        // Add a spread number of filters across the first three types
        FrequencyCapFilters.Builder filtersBuilder =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForWinEvents(listBuilder.build())
                        .setKeyedFrequencyCapsForImpressionEvents(listBuilder.build())
                        .setKeyedFrequencyCapsForViewEvents(listBuilder.build());

        // Add extra filters to the final list so that the total is exceeded
        final int numExtraFiltersToExceed =
                FrequencyCapFilters.MAX_NUM_FREQUENCY_CAP_FILTERS - (4 * distributedNumFilters) + 1;
        for (int key = 0; key < numExtraFiltersToExceed; key++) {
            listBuilder.add(
                    KeyedFrequencyCapFixture.getValidKeyedFrequencyCapBuilderOncePerDay(key)
                            .build());
        }
        filtersBuilder.setKeyedFrequencyCapsForClickEvents(listBuilder.build());

        assertThrows(IllegalArgumentException.class, filtersBuilder::build);
    }
}
