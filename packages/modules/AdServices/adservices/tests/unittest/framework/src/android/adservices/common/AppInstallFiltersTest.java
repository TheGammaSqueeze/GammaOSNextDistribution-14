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

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

/** Unit tests for {@link AppInstallFilters}. */
// TODO(b/266837113): Move to CTS tests once public APIs are unhidden
@SmallTest
public class AppInstallFiltersTest {

    @Test
    public void testBuildValidAppInstallFilters_success() {
        final AppInstallFilters originalFilters =
                new AppInstallFilters.Builder().setPackageNames(CommonFixture.PACKAGE_SET).build();

        assertThat(originalFilters.getPackageNames())
                .containsExactlyElementsIn(CommonFixture.PACKAGE_SET);
    }

    @Test
    public void testParcelAppInstallFilters_success() {
        final AppInstallFilters originalFilters =
                new AppInstallFilters.Builder().setPackageNames(CommonFixture.PACKAGE_SET).build();

        Parcel targetParcel = Parcel.obtain();
        originalFilters.writeToParcel(targetParcel, 0);
        targetParcel.setDataPosition(0);
        final AppInstallFilters filtersFromParcel =
                AppInstallFilters.CREATOR.createFromParcel(targetParcel);

        assertThat(filtersFromParcel.getPackageNames())
                .containsExactlyElementsIn(CommonFixture.PACKAGE_SET);
    }

    @Test
    public void testEqualsIdentical_success() {
        final AppInstallFilters originalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();
        final AppInstallFilters identicalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();

        assertThat(originalFilters.equals(identicalFilters)).isTrue();
    }

    @Test
    public void testEqualsDifferent_success() {
        final AppInstallFilters originalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();
        final AppInstallFilters differentFilters = new AppInstallFilters.Builder().build();

        assertThat(originalFilters.equals(differentFilters)).isFalse();
    }

    @Test
    public void testEqualsNull_success() {
        final AppInstallFilters originalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();
        final AppInstallFilters nullFilters = null;

        assertThat(originalFilters.equals(nullFilters)).isFalse();
    }

    @Test
    public void testHashCodeIdentical_success() {
        final AppInstallFilters originalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();
        final AppInstallFilters identicalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();

        assertThat(originalFilters.hashCode()).isEqualTo(identicalFilters.hashCode());
    }

    @Test
    public void testHashCodeDifferent_success() {
        final AppInstallFilters originalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();
        final AppInstallFilters differentFilters = new AppInstallFilters.Builder().build();

        assertThat(originalFilters.hashCode()).isNotEqualTo(differentFilters.hashCode());
    }

    @Test
    public void testToString() {
        final AppInstallFilters originalFilters =
                new AppInstallFilters.Builder().setPackageNames(CommonFixture.PACKAGE_SET).build();

        final String expectedString =
                String.format("AppInstallFilters{mPackageNames=%s}", CommonFixture.PACKAGE_SET);
        assertThat(originalFilters.toString()).isEqualTo(expectedString);
    }

    @Test
    public void testBuildNullPackageNames_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new AppInstallFilters.Builder().setPackageNames(null));
    }

    @Test
    public void testBuildNoSetters_success() {
        final AppInstallFilters originalFilters = new AppInstallFilters.Builder().build();

        assertThat(originalFilters.getPackageNames()).isEmpty();
    }

    @Test
    public void testCreatorNewArray_success() {
        AppInstallFilters[] filtersArray = AppInstallFilters.CREATOR.newArray(2);

        assertThat(filtersArray.length).isEqualTo(2);
        assertThat(filtersArray[0]).isNull();
        assertThat(filtersArray[1]).isNull();
    }

    @Test
    public void testGetSizeInBytes() {
        final AppInstallFilters originalFilters =
                new AppInstallFilters.Builder().setPackageNames(CommonFixture.PACKAGE_SET).build();
        int[] size = new int[1];
        CommonFixture.PACKAGE_SET.forEach(x -> size[0] += x.getBytes().length);
        assertEquals(size[0], originalFilters.getSizeInBytes());
    }

    @Test
    public void testJsonSerialization() throws JSONException {
        final AppInstallFilters originalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();
        assertEquals(originalFilters, AppInstallFilters.fromJson(originalFilters.toJson()));
    }

    @Test
    public void testJsonSerializationNonString() throws JSONException {
        final AppInstallFilters originalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();
        JSONObject json = originalFilters.toJson();
        JSONArray mixedArray = json.getJSONArray(AppInstallFilters.PACKAGE_NAMES_FIELD_NAME);
        mixedArray.put(0);
        json.put(AppInstallFilters.PACKAGE_NAMES_FIELD_NAME, mixedArray);
        assertThrows(JSONException.class, () -> AppInstallFilters.fromJson(json));
    }

    @Test
    public void testJsonSerializationNulls() throws JSONException {
        final AppInstallFilters originalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();
        JSONObject json = originalFilters.toJson();
        JSONArray mixedArray = json.getJSONArray(AppInstallFilters.PACKAGE_NAMES_FIELD_NAME);
        mixedArray.put(null);
        json.put(AppInstallFilters.PACKAGE_NAMES_FIELD_NAME, mixedArray);
        assertThrows(JSONException.class, () -> AppInstallFilters.fromJson(json));
    }

    @Test
    public void testJsonSerializationWrongType() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(AppInstallFilters.PACKAGE_NAMES_FIELD_NAME, "value");
        assertThrows(JSONException.class, () -> AppInstallFilters.fromJson(json));
    }

    @Test
    public void testJsonSerializationUnrelatedKey() throws JSONException {
        final AppInstallFilters originalFilters =
                AppInstallFiltersFixture.getValidAppInstallFiltersBuilder().build();
        JSONObject json = originalFilters.toJson();
        json.put("key", "value");
        assertEquals(originalFilters, AppInstallFilters.fromJson(originalFilters.toJson()));
    }
}
