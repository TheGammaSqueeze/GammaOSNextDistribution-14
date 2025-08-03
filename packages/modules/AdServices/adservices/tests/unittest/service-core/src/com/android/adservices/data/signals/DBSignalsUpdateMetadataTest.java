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

package com.android.adservices.data.signals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

public class DBSignalsUpdateMetadataTest {

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Test
    public void testCreateSignalsUpdateMetadata() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        DBSignalsUpdateMetadata signalsUpdateMetadata =
                DBSignalsUpdateMetadata.create(buyer, CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI);
        assertEquals(buyer, signalsUpdateMetadata.getBuyer());
        assertEquals(
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                signalsUpdateMetadata.getLastSignalsUpdatedTime());
    }

    @Test
    public void testBuildSignalsUpdateMetadata() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        DBSignalsUpdateMetadata signalsUpdatedTime =
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(buyer)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build();
        assertEquals(buyer, signalsUpdatedTime.getBuyer());
        assertEquals(
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                signalsUpdatedTime.getLastSignalsUpdatedTime());
    }

    @Test
    public void testNullFails() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBSignalsUpdateMetadata.builder().build();
                });
    }

    @Test
    public void testEquals() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        DBSignalsUpdateMetadata signalsUpdateMetadata1 =
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(buyer)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build();
        DBSignalsUpdateMetadata signalsUpdateMetadata2 =
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(buyer)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build();
        assertEquals(signalsUpdateMetadata1, signalsUpdateMetadata2);
    }

    @Test
    public void testNotEquals() {
        AdTechIdentifier buyer1 = CommonFixture.VALID_BUYER_1;
        AdTechIdentifier buyer2 = CommonFixture.VALID_BUYER_2;
        DBSignalsUpdateMetadata signalsUpdateMetadata1 =
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(buyer1)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build();
        DBSignalsUpdateMetadata signalsUpdateMetadata2 =
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(buyer2)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build();
        assertNotEquals(signalsUpdateMetadata1, signalsUpdateMetadata2);
    }

    @Test
    public void testHashCode() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        DBSignalsUpdateMetadata signalsUpdateMetadata1 =
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(buyer)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build();
        DBSignalsUpdateMetadata signalsUpdateMetadata2 =
                DBSignalsUpdateMetadata.builder()
                        .setBuyer(buyer)
                        .setLastSignalsUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .build();
        assertEquals(signalsUpdateMetadata1.hashCode(), signalsUpdateMetadata2.hashCode());
    }
}
