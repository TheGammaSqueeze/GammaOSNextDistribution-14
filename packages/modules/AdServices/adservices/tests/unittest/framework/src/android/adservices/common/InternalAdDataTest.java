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

import android.net.Uri;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/** Unit tests for temporarily hidden public APIs in {@link AdData}. */
// TODO(b/273329939): Merge into CTS AdData test class once APIs are unhidden
@SmallTest
public class InternalAdDataTest {
    private static final Uri VALID_RENDER_URI =
            AdDataFixture.getValidRenderUriByBuyer(CommonFixture.VALID_BUYER_1, 0);

    @Test
    public void testParcelWithFilters_success() {
        final AdData originalAdData =
                new AdData.Builder()
                        .setRenderUri(VALID_RENDER_URI)
                        .setMetadata(AdDataFixture.VALID_METADATA)
                        .setAdFilters(getValidAppInstallOnlyFilters())
                        .build();

        Parcel targetParcel = Parcel.obtain();
        originalAdData.writeToParcel(targetParcel, 0);
        targetParcel.setDataPosition(0);
        final AdData adDataFromParcel = AdData.CREATOR.createFromParcel(targetParcel);

        assertThat(adDataFromParcel.getRenderUri()).isEqualTo(VALID_RENDER_URI);
        assertThat(adDataFromParcel.getMetadata()).isEqualTo(AdDataFixture.VALID_METADATA);
        assertThat(adDataFromParcel.getAdFilters()).isEqualTo(getValidAppInstallOnlyFilters());
    }

    @Test
    public void testEqualsIdenticalFilters_success() {
        final AdData originalAdData =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdFilters(getValidAppInstallOnlyFilters())
                        .build();
        final AdData identicalAdData =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdFilters(getValidAppInstallOnlyFilters())
                        .build();

        assertThat(originalAdData.equals(identicalAdData)).isTrue();
    }

    @Test
    public void testEqualsDifferentFilters_success() {
        final AdData originalAdData =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdFilters(getValidAppInstallOnlyFilters())
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
                        .setAdFilters(getValidAppInstallOnlyFilters())
                        .build();
        final AdData nullAdData = null;

        assertThat(originalAdData.equals(nullAdData)).isFalse();
    }

    @Test
    public void testHashCodeIdenticalFilters_success() {
        final AdData originalAdData =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdFilters(getValidAppInstallOnlyFilters())
                        .build();
        final AdData identicalAdData =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdFilters(getValidAppInstallOnlyFilters())
                        .build();

        assertThat(originalAdData.hashCode()).isEqualTo(identicalAdData.hashCode());
    }

    @Test
    public void testHashCodeDifferentFilters_success() {
        final AdData originalAdData =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdFilters(getValidAppInstallOnlyFilters())
                        .build();
        final AdData differentAdData =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdFilters(null)
                        .build();

        assertThat(originalAdData.hashCode()).isNotEqualTo(differentAdData.hashCode());
    }

    private AdFilters getValidAppInstallOnlyFilters() {
        return new AdFilters.Builder()
                .setAppInstallFilters(AppInstallFiltersFixture.VALID_APP_INSTALL_FILTERS)
                .build();
    }
}
