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
package android.adservices.topics;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Unit tests for {@link GetTopicsResult} */
@SmallTest
public final class GetTopicsResultTest {

    private static final byte[] BYTE_ARRAY_1 = new byte[] {1, 2, 3};
    private static final byte[] BYTE_ARRAY_2 = new byte[] {4, 5, 6};
    private static final byte[] BYTE_ARRAY_3 = new byte[] {7, 8, 9};
    private static final byte[] BYTE_ARRAY_4 = new byte[] {10, 11, 12};

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testWriteToParcel() throws Exception {
        GetTopicsResult response =
                new GetTopicsResult.Builder()
                        .setTaxonomyVersions(Arrays.asList(1L, 2L))
                        .setModelVersions(Arrays.asList(3L, 4L))
                        .setTopics(Arrays.asList(1, 2))
                        .setEncryptedTopics(Arrays.asList(BYTE_ARRAY_1, BYTE_ARRAY_2))
                        .setEncryptionKeys(Arrays.asList("Key1", "Key2"))
                        .setEncapsulatedKeys(Arrays.asList(BYTE_ARRAY_3, BYTE_ARRAY_4))
                        .build();
        Parcel p = Parcel.obtain();
        response.writeToParcel(p, 0);
        p.setDataPosition(0);

        GetTopicsResult fromParcel = GetTopicsResult.CREATOR.createFromParcel(p);

        assertThat(fromParcel.getTaxonomyVersions()).containsExactly(1L, 2L).inOrder();
        assertThat(fromParcel.getModelVersions()).containsExactly(3L, 4L).inOrder();
        assertThat(fromParcel.getTopics()).containsExactly(1, 2).inOrder();
        assertTrue(Arrays.equals(fromParcel.getEncryptedTopics().get(0), BYTE_ARRAY_1));
        assertTrue(Arrays.equals(fromParcel.getEncryptedTopics().get(1), BYTE_ARRAY_2));
        assertThat(fromParcel.getEncryptionKeys()).containsExactly("Key1", "Key2").inOrder();
        assertTrue(Arrays.equals(fromParcel.getEncapsulatedKeys().get(0), BYTE_ARRAY_3));
        assertTrue(Arrays.equals(fromParcel.getEncapsulatedKeys().get(1), BYTE_ARRAY_4));
    }

    @Test
    public void testWriteToParcel_emptyResponse() throws Exception {
        GetTopicsResult response = new GetTopicsResult.Builder().build();
        Parcel p = Parcel.obtain();
        response.writeToParcel(p, 0);
        p.setDataPosition(0);

        GetTopicsResult fromParcel = GetTopicsResult.CREATOR.createFromParcel(p);

        assertThat(fromParcel.getTaxonomyVersions()).isEmpty();
        assertThat(fromParcel.getModelVersions()).isEmpty();
        assertThat(fromParcel.getTopics()).isEmpty();
        assertThat(fromParcel.getEncryptedTopics()).isEmpty();
        assertThat(fromParcel.getEncryptionKeys()).isEmpty();
        assertThat(fromParcel.getEncapsulatedKeys()).isEmpty();
    }

    @Test
    public void testWriteToParcel_nullableThrows() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    GetTopicsResult unusedResponse =
                            new GetTopicsResult.Builder().setTopics(null).build();
                });
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    GetTopicsResult unusedResponse =
                            new GetTopicsResult.Builder().setEncryptedTopics(null).build();
                });

        // This should not throw.
        GetTopicsResult unusedResponse =
                new GetTopicsResult.Builder()
                        // Not setting anything default to empty.
                        .build();
        assertThat(
                        new GetTopicsResult.Builder()
                                // Not setting anything for encrypted topics list
                                .setTopics(List.of())
                                .build()
                                .getTopics())
                .isEmpty();
        assertThat(
                        new GetTopicsResult.Builder()
                                // Not setting anything for topics list
                                .setEncryptedTopics(List.of())
                                .build()
                                .getEncryptedTopics())
                .isEmpty();
    }

    @Test
    public void testWriteToParcel_misMatchSizeThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    GetTopicsResult unusedResponse =
                            new GetTopicsResult.Builder()
                                    .setTaxonomyVersions(Arrays.asList(1L))
                                    .setModelVersions(Arrays.asList(3L, 4L))
                                    .setTopics(Arrays.asList(1, 2))
                                    .build();
                });

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    GetTopicsResult unusedResponse =
                            new GetTopicsResult.Builder()
                                    // Not setting TaxonomyVersions implies empty.
                                    .setModelVersions(Arrays.asList(3L, 4L))
                                    .setTopics(Arrays.asList(1, 2))
                                    .build();
                });

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    GetTopicsResult unusedResponse =
                            new GetTopicsResult.Builder()
                                    .setTaxonomyVersions(Arrays.asList(1L, 2L))
                                    .setModelVersions(Arrays.asList(3L, 4L))
                                    .setTopics(Arrays.asList(1))
                                    .build();
                });

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    GetTopicsResult unusedResponse =
                            new GetTopicsResult.Builder()
                                    .setEncryptedTopics(Arrays.asList(BYTE_ARRAY_1, BYTE_ARRAY_2))
                                    .setEncryptionKeys(Arrays.asList("Key1", "Key2", "Key3"))
                                    .build();
                });

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    GetTopicsResult unusedResponse =
                            new GetTopicsResult.Builder()
                                    .setEncryptedTopics(Arrays.asList(BYTE_ARRAY_1, BYTE_ARRAY_2))
                                    .setEncryptionKeys(Arrays.asList("Key1", "Key2"))
                                    .setEncapsulatedKeys(Arrays.asList())
                                    .build();
                });
    }
}
