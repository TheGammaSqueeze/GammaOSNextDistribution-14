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

package android.app.appsearch;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.aidl.AppSearchAttributionSource;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;

public class AppSearchAttributionSourceUnitTest {

    @Test
    public void testSameAttributionSource() {
        AppSearchAttributionSource appSearchAttributionSource1 =
                new AppSearchAttributionSource("testPackageName1", /* callingUid= */ 1);
        AppSearchAttributionSource appSearchAttributionSource2 =
                new AppSearchAttributionSource("testPackageName1", /* callingUid= */ 1);
        assertThat(appSearchAttributionSource1.equals(appSearchAttributionSource2)).isTrue();
        assertThat(appSearchAttributionSource1.hashCode()).isEqualTo(
                appSearchAttributionSource2.hashCode());
    }

    @Test
    public void testDifferentAttributionSource() {
        AppSearchAttributionSource appSearchAttributionSource1 =
                new AppSearchAttributionSource("testPackageName1", /* callingUid= */ 1);
        AppSearchAttributionSource appSearchAttributionSource2 =
                new AppSearchAttributionSource("testPackageName2", /* callingUid= */ 2);
        assertThat(appSearchAttributionSource1.equals(appSearchAttributionSource2)).isFalse();
        assertThat(appSearchAttributionSource1.hashCode())
                .isNotEqualTo(appSearchAttributionSource2.hashCode());
    }

    @Test
    public void testPackageNamesNull() {
        AppSearchAttributionSource appSearchAttributionSource1 =
                new AppSearchAttributionSource(/* callingPackageName= */ null, /* callingUid= */ 1);
        AppSearchAttributionSource appSearchAttributionSource2 =
                new AppSearchAttributionSource(/* callingPackageName= */ null, /* callingUid= */ 1);
        assertThat(appSearchAttributionSource1.equals(appSearchAttributionSource2)).isTrue();
        assertThat(appSearchAttributionSource1.hashCode())
                .isEqualTo(appSearchAttributionSource2.hashCode());
    }

}
