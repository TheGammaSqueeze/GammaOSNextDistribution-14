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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

/** Unit tests for {@link AdFilters}. */
// TODO(b/273329939): Delete duplicates once CTS tests are unignored
@SmallTest
public class InternalAdFiltersTest {

    private static final String DIFFERENT_PACKAGE_NAME =
            CommonFixture.TEST_PACKAGE_NAME_1 + "arbitrary";

    private static final AdFilters APP_INSTALL_ONLY_FILTER =
            new AdFilters.Builder()
                    .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                    .build();

    @Test
    public void testBuildValidAdFilters_success() {
        final AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                        .build();

        assertThat(originalFilters.getFrequencyCapFilters())
                .isEqualTo(FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS);
        assertThat(originalFilters.getAppInstallFilters())
                .isEqualTo(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS);
    }

    @Test
    public void testParcelAdFilters_success() {
        final AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                        .build();

        Parcel targetParcel = Parcel.obtain();
        originalFilters.writeToParcel(targetParcel, 0);
        targetParcel.setDataPosition(0);
        final AdFilters filtersFromParcel = AdFilters.CREATOR.createFromParcel(targetParcel);

        assertThat(filtersFromParcel.getFrequencyCapFilters())
                .isEqualTo(originalFilters.getFrequencyCapFilters());
        assertThat(filtersFromParcel.getAppInstallFilters())
                .isEqualTo(originalFilters.getAppInstallFilters());
    }

    @Test
    public void testEqualsIdentical_success() {
        final AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                        .build();
        final AdFilters identicalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                        .build();

        assertThat(originalFilters.equals(identicalFilters)).isTrue();
    }

    @Test
    public void testEqualsDifferent_success() {
        final AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .build();
        final AdFilters differentFilters = new AdFilters.Builder().build();

        assertThat(originalFilters.equals(differentFilters)).isFalse();
    }

    @Test
    public void testEqualsNull_success() {
        final AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .build();
        final AdFilters nullFilters = null;

        assertThat(originalFilters.equals(nullFilters)).isFalse();
    }

    @Test
    public void testHashCodeIdentical_success() {
        final AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                        .build();
        final AdFilters identicalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                        .build();

        assertThat(originalFilters.hashCode()).isEqualTo(identicalFilters.hashCode());
    }

    @Test
    public void testHashCodeDifferent_success() {
        final AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .build();
        final AdFilters differentFilters = new AdFilters.Builder().build();

        assertThat(originalFilters.hashCode()).isNotEqualTo(differentFilters.hashCode());
    }

    @Test
    public void testBuildNullAdFilters_success() {
        final AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(null)
                        .setAppInstallFilters(null)
                        .build();

        assertThat(originalFilters.getFrequencyCapFilters()).isNull();
        assertThat(originalFilters.getAppInstallFilters()).isNull();
    }

    @Test
    public void testBuildNoSetters_success() {
        final AdFilters originalFilters = new AdFilters.Builder().build();

        assertThat(originalFilters.getFrequencyCapFilters()).isNull();
        assertThat(originalFilters.getAppInstallFilters()).isNull();
    }

    @Test
    public void testGetSizeInBytes() {
        final AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                        .build();
        int size =
                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS.getSizeInBytes()
                        + AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS.getSizeInBytes();
        assertEquals(size, originalFilters.getSizeInBytes());
    }

    @Test
    public void testBuildValidAdFiltersAppInstallOnly_success() {
        assertNull(APP_INSTALL_ONLY_FILTER.getFrequencyCapFilters());
        assertThat(APP_INSTALL_ONLY_FILTER.getAppInstallFilters())
                .isEqualTo(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS);
    }

    @Test
    public void testParcelAdFiltersAppInstallOnly_success() {
        Parcel targetParcel = Parcel.obtain();
        APP_INSTALL_ONLY_FILTER.writeToParcel(targetParcel, 0);
        targetParcel.setDataPosition(0);
        final AdFilters filtersFromParcel = AdFilters.CREATOR.createFromParcel(targetParcel);

        assertThat(filtersFromParcel.getFrequencyCapFilters())
                .isEqualTo(APP_INSTALL_ONLY_FILTER.getFrequencyCapFilters());
        assertThat(filtersFromParcel.getAppInstallFilters())
                .isEqualTo(APP_INSTALL_ONLY_FILTER.getAppInstallFilters());
    }

    @Test
    public void testEqualsIdenticalAppInstallOnly_success() {
        final AdFilters identicalFilters =
                new AdFilters.Builder()
                        .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                        .build();

        assertThat(APP_INSTALL_ONLY_FILTER.equals(identicalFilters)).isTrue();
    }

    @Test
    public void testEqualsDifferentAppInstallOnly_success() {
        final AdFilters differentFilters =
                new AdFilters.Builder()
                        .setAppInstallFilters(
                                new AppInstallFilters.Builder()
                                        .setPackageNames(
                                                new HashSet<>(
                                                        Arrays.asList(DIFFERENT_PACKAGE_NAME)))
                                        .build())
                        .build();

        assertThat(APP_INSTALL_ONLY_FILTER.equals(differentFilters)).isFalse();
    }

    @Test
    public void testHashCodeIdenticalAppInstallOnly_success() {
        final AdFilters identicalFilters =
                new AdFilters.Builder()
                        .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                        .build();

        assertThat(APP_INSTALL_ONLY_FILTER.hashCode()).isEqualTo(identicalFilters.hashCode());
    }

    @Test
    public void testHashCodeDifferentAppInstallOnly_success() {
        final AdFilters differentFilters =
                new AdFilters.Builder()
                        .setAppInstallFilters(
                                new AppInstallFilters.Builder()
                                        .setPackageNames(
                                                new HashSet<>(
                                                        Arrays.asList(DIFFERENT_PACKAGE_NAME)))
                                        .build())
                        .build();

        assertThat(APP_INSTALL_ONLY_FILTER.hashCode()).isNotEqualTo(differentFilters.hashCode());
    }

    @Test
    public void testGetSizeInBytesAppInstallOnly() {
        final AdFilters originalFilters =
                new AdFilters.Builder()
                        .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                        .build();
        int size = AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS.getSizeInBytes();
        assertEquals(size, originalFilters.getSizeInBytes());
    }

    @Test
    public void testGetSizeInBytesFrequencyCapOnly() {
        final AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .build();
        int size = FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS.getSizeInBytes();
        assertEquals(size, originalFilters.getSizeInBytes());
    }

    @Test
    public void testGetSizeInBytesNulls() {
        final AdFilters originalFilters = new AdFilters.Builder().build();
        assertEquals(0, originalFilters.getSizeInBytes());
    }

    @Test
    public void testJsonSerialization() throws JSONException {
        final AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                        .build();
        assertEquals(originalFilters, AdFilters.fromJson(originalFilters.toJson()));
    }

    @Test
    public void testJsonSerializationAppInstallOnly() throws JSONException {
        final AdFilters originalFilters =
                new AdFilters.Builder()
                        .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                        .build();
        assertEquals(originalFilters, AdFilters.fromJson(originalFilters.toJson()));
    }

    @Test
    public void testJsonSerializationNullFcapOnly() throws JSONException {
        final AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .build();
        assertEquals(originalFilters, AdFilters.fromJson(originalFilters.toJson()));
    }

    @Test
    public void testJsonSerializationEmpty() throws JSONException {
        final AdFilters originalFilters = new AdFilters.Builder().build();
        assertEquals(originalFilters, AdFilters.fromJson(originalFilters.toJson()));
    }

    @Test
    public void testJsonSerializationUnrelatedKey() throws JSONException {
        final AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                        .build();
        JSONObject json = originalFilters.toJson();
        json.put("key", "value");
        assertEquals(originalFilters, AdFilters.fromJson(json));
    }

    @Test
    public void testJsonSerializationFcapWrongType() throws JSONException {
        final AdFilters originalFilters =
                new AdFilters.Builder()
                        .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                        .build();
        JSONObject json = originalFilters.toJson();
        json.put(AdFilters.FREQUENCY_CAP_FIELD_NAME, "value");
        assertThrows(JSONException.class, () -> AdFilters.fromJson(json));
    }

    @Test
    public void testJsonSerializationAppInstallWrongType() throws JSONException {
        final AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .build();
        JSONObject json = originalFilters.toJson();
        json.put("app_install", "value");
        assertThrows(JSONException.class, () -> AdFilters.fromJson(json));
    }

    @Test
    public void testJsonSerializationUnrelatedKeyAppInstallOnly() throws JSONException {
        final AdFilters originalFilters =
                new AdFilters.Builder()
                        .setFrequencyCapFilters(
                                FrequencyCapFiltersFixture.VALID_FREQUENCY_CAP_FILTERS)
                        .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                        .build();
        JSONObject json = originalFilters.toJson();
        json.put("key", "value");
        assertEquals(originalFilters, AdFilters.fromJson(json));
    }
}
