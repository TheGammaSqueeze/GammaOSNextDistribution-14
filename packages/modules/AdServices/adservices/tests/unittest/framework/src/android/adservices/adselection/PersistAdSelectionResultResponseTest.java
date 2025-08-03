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

package android.adservices.adselection;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.net.Uri;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

@SmallTest
public class PersistAdSelectionResultResponseTest {
    private static final Uri VALID_RENDER_URI =
            new Uri.Builder().path("valid.example.com/testing/hello").build();
    private static final Uri ANOTHER_VALID_RENDER_URI =
            new Uri.Builder().path("another-valid.example.com/testing/hello").build();
    private static final long TEST_AD_SELECTION_ID = 12345;
    private static final long ANOTHER_TEST_AD_SELECTION_ID = 6789;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildPersistAdSelectionResultResponse() {
        PersistAdSelectionResultResponse persistAdSelectionResultResponse =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .build();

        assertThat(persistAdSelectionResultResponse.getAdSelectionId())
                .isEqualTo(TEST_AD_SELECTION_ID);
        assertThat(persistAdSelectionResultResponse.getAdRenderUri()).isEqualTo(VALID_RENDER_URI);
    }

    @Test
    public void testParcelPersistAdSelectionResultResponse() {
        PersistAdSelectionResultResponse persistAdSelectionResultResponse =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .build();

        Parcel p = Parcel.obtain();
        persistAdSelectionResultResponse.writeToParcel(p, 0);
        p.setDataPosition(0);
        PersistAdSelectionResultResponse fromParcel =
                PersistAdSelectionResultResponse.CREATOR.createFromParcel(p);

        assertThat(fromParcel.getAdSelectionId()).isEqualTo(TEST_AD_SELECTION_ID);
        assertThat(fromParcel.getAdRenderUri()).isEqualTo(VALID_RENDER_URI);
    }

    @Test
    public void testFailsToBuildWithUnsetAdSelectionId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new PersistAdSelectionResultResponse.Builder()
                            // Not setting AdSelectionId making it null.
                            .setAdRenderUri(VALID_RENDER_URI)
                            .build();
                });
    }

    @Test
    public void testPersistAdSelectionResultResponseWithSameValuesAreEqual() {
        PersistAdSelectionResultResponse obj1 =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .build();

        PersistAdSelectionResultResponse obj2 =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .build();

        assertThat(obj1).isEqualTo(obj2);
    }

    @Test
    public void testPersistAdSelectionResultResponseWithDifferentValuesAreNotEqual() {
        PersistAdSelectionResultResponse obj1 =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .build();

        PersistAdSelectionResultResponse obj2 =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(ANOTHER_VALID_RENDER_URI)
                        .build();

        assertThat(obj1).isNotEqualTo(obj2);
    }

    @Test
    public void testPersistAdSelectionResultResponseDescribeContents() {
        PersistAdSelectionResultResponse obj =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .build();

        assertEquals(0, obj.describeContents());
    }

    @Test
    public void testEqualPersistAdSelectionResultResponsesHaveSameHashCode() {
        PersistAdSelectionResultResponse obj1 =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .build();
        PersistAdSelectionResultResponse obj2 =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .build();

        CommonFixture.assertHaveSameHashCode(obj1, obj2);
    }

    @Test
    public void testNotEqualPersistAdSelectionResultResponsesHaveDifferentHashCodes() {
        PersistAdSelectionResultResponse obj1 =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .build();
        PersistAdSelectionResultResponse obj2 =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(TEST_AD_SELECTION_ID)
                        .setAdRenderUri(ANOTHER_VALID_RENDER_URI)
                        .build();
        PersistAdSelectionResultResponse obj3 =
                new PersistAdSelectionResultResponse.Builder()
                        .setAdSelectionId(ANOTHER_TEST_AD_SELECTION_ID)
                        .setAdRenderUri(VALID_RENDER_URI)
                        .build();

        CommonFixture.assertDifferentHashCode(obj1, obj2, obj3);
    }
}
