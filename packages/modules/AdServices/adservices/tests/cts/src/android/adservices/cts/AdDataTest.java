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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdFiltersFixture;
import android.adservices.common.CommonFixture;
import android.net.Uri;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.Collections;

/** Unit tests for {@link AdData} */
@SmallTest
public final class AdDataTest {
    private static final Uri VALID_RENDER_URI =
            new Uri.Builder().path("valid.example.com/testing/hello").build();


    @Test
    public void testBuildValidAdDataSuccess() {
        AdData validAdData =
                new AdData.Builder()
                        .setRenderUri(VALID_RENDER_URI)
                        .setMetadata(AdDataFixture.VALID_METADATA)
                        .setAdCounterKeys(AdDataFixture.getAdCounterKeys())
                        .setAdFilters(AdFiltersFixture.getValidUnhiddenFilters())
                        .setAdRenderId(AdDataFixture.VALID_RENDER_ID)
                        .build();

        assertThat(validAdData.getRenderUri()).isEqualTo(VALID_RENDER_URI);
        assertThat(validAdData.getMetadata()).isEqualTo(AdDataFixture.VALID_METADATA);
        assertThat(validAdData.getAdCounterKeys())
                .containsExactlyElementsIn(AdDataFixture.getAdCounterKeys());
        assertThat(validAdData.getAdFilters())
                .isEqualTo(AdFiltersFixture.getValidUnhiddenFilters());
        assertThat(validAdData.getAdRenderId()).isEqualTo(AdDataFixture.VALID_RENDER_ID);
    }

    @Test
    public void testParcelValidAdDataWithUnsetKeysAndFiltersSuccess() {
        AdData validAdData =
                new AdData.Builder()
                        .setRenderUri(VALID_RENDER_URI)
                        .setMetadata(AdDataFixture.VALID_METADATA)
                        .build();

        Parcel p = Parcel.obtain();
        validAdData.writeToParcel(p, 0);
        p.setDataPosition(0);
        AdData fromParcel = AdData.CREATOR.createFromParcel(p);

        assertThat(fromParcel.getRenderUri()).isEqualTo(VALID_RENDER_URI);
        assertThat(fromParcel.getMetadata()).isEqualTo(AdDataFixture.VALID_METADATA);
        assertThat(fromParcel.getAdCounterKeys()).isNotNull();
        assertThat(fromParcel.getAdCounterKeys()).isEmpty();
        assertThat(fromParcel.getAdFilters()).isNull();
    }

    @Test
    public void testParcelValidAdDataWithUnsetRenderIdSuccess() {
        AdData validAdData =
                new AdData.Builder()
                        .setRenderUri(VALID_RENDER_URI)
                        .setMetadata(AdDataFixture.VALID_METADATA)
                        .build();

        Parcel p = Parcel.obtain();
        validAdData.writeToParcel(p, 0);
        p.setDataPosition(0);
        AdData fromParcel = AdData.CREATOR.createFromParcel(p);

        assertThat(fromParcel.getAdRenderId()).isNull();
    }

    @Test
    public void testSetNullUriAdDataThrows() {
        assertThrows(NullPointerException.class, () -> new AdData.Builder().setRenderUri(null));
    }

    @Test
    public void testSetNullMetadataAdDataThrows() {
        assertThrows(NullPointerException.class, () -> new AdData.Builder().setMetadata(null));
    }

    @Test
    public void testSetNullAdCounterKeysThrows() {
        assertThrows(NullPointerException.class, () -> new AdData.Builder().setAdCounterKeys(null));
    }

    @Test
    public void testSetAdCounterKeysWithNullValueThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AdData.Builder().setAdCounterKeys(Collections.singleton(null)));
    }

    @Test
    public void testSetExcessiveNumberOfAdCounterKeysThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new AdData.Builder()
                                .setAdCounterKeys(
                                        AdDataFixture.getExcessiveNumberOfAdCounterKeys()));
    }

    @Test
    public void testBuildUnsetAdCounterKeysSuccess() {
        AdData validAdData =
                new AdData.Builder()
                        .setRenderUri(VALID_RENDER_URI)
                        .setMetadata(AdDataFixture.VALID_METADATA)
                        .setAdFilters(AdFiltersFixture.getValidUnhiddenFilters())
                        .build();

        assertThat(validAdData.getRenderUri()).isEqualTo(VALID_RENDER_URI);
        assertThat(validAdData.getMetadata()).isEqualTo(AdDataFixture.VALID_METADATA);
        assertThat(validAdData.getAdCounterKeys()).isNotNull();
        assertThat(validAdData.getAdCounterKeys()).isEmpty();
        assertThat(validAdData.getAdFilters())
                .isEqualTo(AdFiltersFixture.getValidUnhiddenFilters());
    }

    @Test
    public void testBuildValidAdDataWithUnsetFiltersSuccess() {
        final AdData validAdData =
                new AdData.Builder()
                        .setRenderUri(VALID_RENDER_URI)
                        .setMetadata(AdDataFixture.VALID_METADATA)
                        .setAdCounterKeys(AdDataFixture.getAdCounterKeys())
                        .build();

        assertThat(validAdData.getRenderUri()).isEqualTo(VALID_RENDER_URI);
        assertThat(validAdData.getMetadata()).isEqualTo(AdDataFixture.VALID_METADATA);
        assertThat(validAdData.getAdCounterKeys())
                .containsExactlyElementsIn(AdDataFixture.getAdCounterKeys());
        assertThat(validAdData.getAdFilters()).isNull();
    }

    @Test
    public void testAdDataToString() {
        AdData validAdData =
                new AdData.Builder()
                        .setRenderUri(VALID_RENDER_URI)
                        .setMetadata(AdDataFixture.VALID_METADATA)
                        .setAdCounterKeys(AdDataFixture.getAdCounterKeys())
                        .setAdFilters(AdFiltersFixture.getValidUnhiddenFilters())
                        .setAdRenderId(AdDataFixture.VALID_RENDER_ID)
                        .build();
        final String actualString = validAdData.toString();

        assertThat(actualString).startsWith("AdData{");
        assertThat(actualString).contains("mRenderUri=" + VALID_RENDER_URI);
        assertThat(actualString).contains(", mMetadata='" + AdDataFixture.VALID_METADATA + "'");
        assertThat(actualString).contains(", mAdCounterKeys=" + AdDataFixture.getAdCounterKeys());
        assertThat(actualString).contains(", mAdRenderId='" + AdDataFixture.VALID_RENDER_ID + "'");
        assertThat(actualString).endsWith("}");
    }

    @Test
    public void testAdDataDescribeContent() {
        AdData obj =
                new AdData.Builder()
                        .setRenderUri(VALID_RENDER_URI)
                        .setMetadata(AdDataFixture.VALID_METADATA)
                        .build();

        assertEquals(0, obj.describeContents());
    }

    @Test
    public void testParcelWithFilters_success() {
        final AdData originalAdData =
                new AdData.Builder()
                        .setRenderUri(VALID_RENDER_URI)
                        .setMetadata(AdDataFixture.VALID_METADATA)
                        .setAdCounterKeys(AdDataFixture.getAdCounterKeys())
                        .setAdFilters(AdFiltersFixture.getValidUnhiddenFilters())
                        .build();

        Parcel targetParcel = Parcel.obtain();
        originalAdData.writeToParcel(targetParcel, 0);
        targetParcel.setDataPosition(0);
        final AdData adDataFromParcel = AdData.CREATOR.createFromParcel(targetParcel);

        assertThat(adDataFromParcel.getRenderUri()).isEqualTo(VALID_RENDER_URI);
        assertThat(adDataFromParcel.getMetadata()).isEqualTo(AdDataFixture.VALID_METADATA);
        assertThat(adDataFromParcel.getAdCounterKeys())
                .containsExactlyElementsIn(AdDataFixture.getAdCounterKeys());
        assertThat(adDataFromParcel.getAdFilters())
                .isEqualTo(AdFiltersFixture.getValidUnhiddenFilters());
    }

    @Test
    public void testEqualsIdenticalFilters_success() {
        final AdData originalAdData =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdFilters(AdFiltersFixture.getValidUnhiddenFilters())
                        .build();
        final AdData identicalAdData =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdFilters(AdFiltersFixture.getValidUnhiddenFilters())
                        .build();

        assertThat(originalAdData.equals(identicalAdData)).isTrue();
    }

    @Test
    public void testEqualsDifferentFilters_success() {
        final AdData originalAdData =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdFilters(AdFiltersFixture.getValidUnhiddenFilters())
                        .build();
        final AdData differentAdData =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdFilters(null)
                        .build();

        assertThat(originalAdData.equals(differentAdData)).isFalse();
    }

    @Test
    public void testEqualsNullFilters_success() {
        final AdData originalAdData =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdFilters(AdFiltersFixture.getValidUnhiddenFilters())
                        .build();
        final AdData nullAdData = null;

        assertThat(originalAdData.equals(nullAdData)).isFalse();
    }

    @Test
    public void testHashCodeIdenticalFilters_success() {
        final AdData originalAdData =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdFilters(AdFiltersFixture.getValidUnhiddenFilters())
                        .build();
        final AdData identicalAdData =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdFilters(AdFiltersFixture.getValidUnhiddenFilters())
                        .build();

        assertThat(originalAdData.hashCode()).isEqualTo(identicalAdData.hashCode());
    }

    @Test
    public void testHashCodeDifferentFilters_success() {
        final AdData originalAdData =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdFilters(AdFiltersFixture.getValidUnhiddenFilters())
                        .build();
        final AdData differentAdData =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdFilters(null)
                        .build();

        assertThat(originalAdData.hashCode()).isNotEqualTo(differentAdData.hashCode());
    }
}
