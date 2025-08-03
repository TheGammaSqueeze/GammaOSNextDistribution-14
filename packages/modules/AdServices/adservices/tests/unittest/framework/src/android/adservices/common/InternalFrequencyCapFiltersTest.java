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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class InternalFrequencyCapFiltersTest {

    @Test
    public void testGetSizeInBytes() {
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
        final int[] setSize = new int[1];
        KeyedFrequencyCapFixture.VALID_KEYED_FREQUENCY_CAP_LIST.forEach(
                x -> setSize[0] += x.getSizeInBytes());
        assertEquals(setSize[0] * 4L, originalFilters.getSizeInBytes());
    }

    @Test
    public void testJsonSerialization() throws JSONException {
        final FrequencyCapFilters originalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();
        assertEquals(originalFilters, FrequencyCapFilters.fromJson(originalFilters.toJson()));
    }

    @Test
    public void testJsonSerializationEmptyWins() throws JSONException {
        final FrequencyCapFilters originalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();
        JSONObject json = originalFilters.toJson();
        json.remove(FrequencyCapFilters.WIN_EVENTS_FIELD_NAME);
        assertThat(FrequencyCapFilters.fromJson(json).getKeyedFrequencyCapsForWinEvents())
                .isEmpty();
    }

    @Test
    public void testJsonSerializationEmptyImpressions() throws JSONException {
        final FrequencyCapFilters originalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();
        JSONObject json = originalFilters.toJson();
        json.remove(FrequencyCapFilters.IMPRESSION_EVENTS_FIELD_NAME);
        assertThat(FrequencyCapFilters.fromJson(json).getKeyedFrequencyCapsForImpressionEvents())
                .isEmpty();
    }

    @Test
    public void testJsonSerializationEmptyViews() throws JSONException {
        final FrequencyCapFilters originalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();
        JSONObject json = originalFilters.toJson();
        json.remove(FrequencyCapFilters.VIEW_EVENTS_FIELD_NAME);
        assertThat(FrequencyCapFilters.fromJson(json).getKeyedFrequencyCapsForViewEvents())
                .isEmpty();
    }

    @Test
    public void testJsonSerializationEmptyClicks() throws JSONException {
        final FrequencyCapFilters originalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();
        JSONObject json = originalFilters.toJson();
        json.remove(FrequencyCapFilters.CLICK_EVENTS_FIELD_NAME);
        assertThat(FrequencyCapFilters.fromJson(json).getKeyedFrequencyCapsForClickEvents())
                .isEmpty();
    }

    @Test
    public void testJsonSerializationNonStringKeyedFrequencyCap() throws JSONException {
        final FrequencyCapFilters originalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();
        JSONObject json = originalFilters.toJson();
        json.put(
                FrequencyCapFilters.WIN_EVENTS_FIELD_NAME,
                json.getJSONArray(FrequencyCapFilters.WIN_EVENTS_FIELD_NAME).put(0));
        assertThrows(JSONException.class, () -> FrequencyCapFilters.fromJson(json));
    }

    @Test
    public void testJsonSerializationWrongType() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(FrequencyCapFilters.WIN_EVENTS_FIELD_NAME, "value");
        assertThrows(JSONException.class, () -> FrequencyCapFilters.fromJson(json));
    }

    @Test
    public void testJsonSerializationUnrelatedKey() throws JSONException {
        final FrequencyCapFilters originalFilters =
                FrequencyCapFiltersFixture.getValidFrequencyCapFiltersBuilder().build();
        JSONObject json = originalFilters.toJson();
        json.put("key", "value");
        assertEquals(originalFilters, FrequencyCapFilters.fromJson(originalFilters.toJson()));
    }
}
