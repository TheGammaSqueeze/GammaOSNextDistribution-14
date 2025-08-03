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

package android.media.bettertogether.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.media.RouteDiscoveryPreference;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.NonMainlineTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
@NonMainlineTest
public class RouteDiscoveryPreferenceTest {

    private static final String TEST_FEATURE_1 = "TEST_FEATURE_1";
    private static final String TEST_FEATURE_2 = "TEST_FEATURE_2";

    private static final String TEST_PACKAGE_1 = "TEST_PACKAGE_1";
    private static final String TEST_PACKAGE_2 = "TEST_PACKAGE_2";
    private static final String TEST_PACKAGE_3 = "TEST_PACKAGE_3";

    @Test
    public void testBuilderConstructorWithNull() {
        // Tests null preferredFeatures
        assertThrows(NullPointerException.class,
                () -> new RouteDiscoveryPreference.Builder(null, true));

        // Tests null RouteDiscoveryPreference
        assertThrows(NullPointerException.class,
                () -> new RouteDiscoveryPreference.Builder((RouteDiscoveryPreference) null));
    }

    @Test
    public void testBuilderSetPreferredFeaturesWithNull() {
        RouteDiscoveryPreference.Builder builder =
                new RouteDiscoveryPreference.Builder(new ArrayList<>(), true);

        assertThrows(NullPointerException.class, () -> builder.setPreferredFeatures(null));
    }

    @Test
    public void testDefaultValues() {
        List<String> preferredFeatures = List.of(TEST_FEATURE_1, TEST_FEATURE_2);

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(preferredFeatures, true /* isActiveScan */)
                        .build();
        assertThat(preference.getPreferredFeatures()).isEqualTo(preferredFeatures);
        assertThat(preference.shouldPerformActiveScan()).isTrue();

        assertThat(preference.describeContents()).isEqualTo(0);
    }

    @Test
    public void testGetters() {
        List<String> preferredFeatures = List.of(TEST_FEATURE_1, TEST_FEATURE_2);

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(preferredFeatures, true /* isActiveScan */)
                        .build();
        assertThat(preference.getPreferredFeatures()).isEqualTo(preferredFeatures);
        assertThat(preference.shouldPerformActiveScan()).isTrue();
        assertThat(preference.describeContents()).isEqualTo(0);
    }

    @Test
    public void testBuilderSetPreferredFeatures() {
        final List<String> features = new ArrayList<>();
        features.add(TEST_FEATURE_1);
        features.add(TEST_FEATURE_2);

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(features, true /* isActiveScan */).build();

        final List<String> newFeatures = new ArrayList<>();
        newFeatures.add(TEST_FEATURE_1);

        // Using copy constructor, we only change the setPreferredFeatures.
        RouteDiscoveryPreference newPreference = new RouteDiscoveryPreference.Builder(preference)
                .setPreferredFeatures(newFeatures)
                .build();

        assertThat(newPreference.getPreferredFeatures()).isEqualTo(newFeatures);
        assertThat(newPreference.shouldPerformActiveScan()).isTrue();
    }

    @Test
    public void testBuilderSetActiveScan() {
        final List<String> features = new ArrayList<>();
        features.add(TEST_FEATURE_1);
        features.add(TEST_FEATURE_2);

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(features, true /* isActiveScan */).build();

        // Using copy constructor, we only change the activeScan to 'false'.
        RouteDiscoveryPreference newPreference = new RouteDiscoveryPreference.Builder(preference)
                .setShouldPerformActiveScan(false)
                .build();

        assertThat(newPreference.getPreferredFeatures()).isEqualTo(features);
        assertThat(newPreference.shouldPerformActiveScan()).isFalse();
    }

    @Test
    public void testEqualsCreatedWithSameArguments() {
        List<String> preferredFeatures = List.of(TEST_FEATURE_1, TEST_FEATURE_2);

        RouteDiscoveryPreference preference1 =
                new RouteDiscoveryPreference.Builder(preferredFeatures, true /* isActiveScan */)
                        .build();

        RouteDiscoveryPreference preference2 =
                new RouteDiscoveryPreference.Builder(preferredFeatures, true /* isActiveScan */)
                        .build();

        assertThat(preference2).isEqualTo(preference1);
    }

    @Test
    public void testEqualsCreatedWithBuilderCopyConstructor() {
        List<String> preferredFeatures = List.of(TEST_FEATURE_1, TEST_FEATURE_2);

        RouteDiscoveryPreference preference1 =
                new RouteDiscoveryPreference.Builder(preferredFeatures, true /* isActiveScan */)
                        .build();

        RouteDiscoveryPreference preference2 =
                new RouteDiscoveryPreference.Builder(preference1).build();

        assertThat(preference2).isEqualTo(preference1);
    }

    @Test
    public void testEqualsReturnFalse() {
        List<String> preferredFeatures = List.of(TEST_FEATURE_1, TEST_FEATURE_2);

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(preferredFeatures, true /* isActiveScan */)
                        .build();

        RouteDiscoveryPreference preferenceWithDifferentFeatures =
                new RouteDiscoveryPreference.Builder(new ArrayList<>(), true /* isActiveScan */)
                        .build();
        assertThat(preferenceWithDifferentFeatures).isNotEqualTo(preference);

        RouteDiscoveryPreference preferenceWithDifferentActiveScan =
                new RouteDiscoveryPreference.Builder(preferredFeatures, false /* isActiveScan */)
                        .build();
        assertThat(preferenceWithDifferentActiveScan).isNotEqualTo(preference);
    }

    @Test
    public void testEqualsReturnFalseWithCopyConstructor() {
        List<String> preferredFeatures = List.of(TEST_FEATURE_1, TEST_FEATURE_2);

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(preferredFeatures, true /* isActiveScan */)
                        .build();

        final List<String> newFeatures = new ArrayList<>();
        newFeatures.add(TEST_FEATURE_1);
        RouteDiscoveryPreference preferenceWithDifferentFeatures =
                new RouteDiscoveryPreference.Builder(preference)
                        .setPreferredFeatures(newFeatures)
                        .build();
        assertThat(preferenceWithDifferentFeatures).isNotEqualTo(preference);

        RouteDiscoveryPreference preferenceWithDifferentActiveScan =
                new RouteDiscoveryPreference.Builder(preference)
                        .setShouldPerformActiveScan(false)
                        .build();
        assertThat(preferenceWithDifferentActiveScan).isNotEqualTo(preference);
    }

    @Test
    public void testParcelingAndUnParceling() {
        List<String> preferredFeatures = List.of(TEST_FEATURE_1, TEST_FEATURE_2);

        RouteDiscoveryPreference preference =
                new RouteDiscoveryPreference.Builder(preferredFeatures, true /* isActiveScan */)
                        .build();

        Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(preference, 0);
        parcel.setDataPosition(0);

        RouteDiscoveryPreference preferenceFromParcel = parcel.readParcelable(null);
        assertThat(preferenceFromParcel).isEqualTo(preference);
        parcel.recycle();

        // In order to mark writeToParcel as tested, we let's just call it directly.
        Parcel dummyParcel = Parcel.obtain();
        preference.writeToParcel(dummyParcel, 0);
        dummyParcel.recycle();
    }
}
