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

import java.time.Instant;

public class DBEncoderLogicMetadataTest {

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Test
    public void testCreateEncodingLogic() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        int version = 1;
        Instant time = CommonFixture.FIXED_NOW;
        int failCount = 2;

        DBEncoderLogicMetadata logicEntry =
                DBEncoderLogicMetadata.create(buyer, version, time, failCount);
        assertEquals(buyer, logicEntry.getBuyer());
        assertEquals(version, (int) logicEntry.getVersion());
        assertEquals(time, logicEntry.getCreationTime());
        assertEquals(failCount, logicEntry.getFailedEncodingCount());
    }

    @Test
    public void testBuildEncodingLogic() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        int version = 1;
        Instant time = CommonFixture.FIXED_NOW;
        int failCount = 2;

        DBEncoderLogicMetadata logicEntry =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(buyer)
                        .setVersion(version)
                        .setCreationTime(time)
                        .setFailedEncodingCount(failCount)
                        .build();
        assertEquals(buyer, logicEntry.getBuyer());
        assertEquals(version, (int) logicEntry.getVersion());
        assertEquals(time, logicEntry.getCreationTime());
        assertEquals(failCount, logicEntry.getFailedEncodingCount());
    }

    @Test
    public void testNullFails() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBEncoderLogicMetadata.builder().build();
                });
    }

    @Test
    public void testEquals() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        int version = 1;
        Instant time = CommonFixture.FIXED_NOW;
        int failedCount = 2;

        DBEncoderLogicMetadata logicEntry1 =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(buyer)
                        .setVersion(version)
                        .setCreationTime(time)
                        .setFailedEncodingCount(failedCount)
                        .build();
        DBEncoderLogicMetadata logicEntry2 =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(buyer)
                        .setVersion(version)
                        .setCreationTime(time)
                        .setFailedEncodingCount(failedCount)
                        .build();

        assertEquals(logicEntry1, logicEntry2);
    }

    @Test
    public void testNotEquals() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        int version = 1;
        Instant time = CommonFixture.FIXED_NOW;
        int failedCount = 2;

        DBEncoderLogicMetadata logicEntry1 =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(buyer)
                        .setVersion(version)
                        .setCreationTime(time)
                        .setFailedEncodingCount(failedCount)
                        .build();
        DBEncoderLogicMetadata logicEntry2 =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(buyer)
                        .setVersion(version + 1)
                        .setCreationTime(time)
                        .setFailedEncodingCount(failedCount)
                        .build();

        assertNotEquals(logicEntry1, logicEntry2);
    }

    @Test
    public void testHashCode() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        int version = 1;
        Instant time = CommonFixture.FIXED_NOW;
        int failedCount = 2;

        DBEncoderLogicMetadata logicEntry1 =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(buyer)
                        .setVersion(version)
                        .setCreationTime(time)
                        .setFailedEncodingCount(failedCount)
                        .build();
        DBEncoderLogicMetadata logicEntry2 =
                DBEncoderLogicMetadata.builder()
                        .setBuyer(buyer)
                        .setVersion(version)
                        .setCreationTime(time)
                        .setFailedEncodingCount(failedCount)
                        .build();

        assertEquals(logicEntry1.hashCode(), logicEntry2.hashCode());
    }
}
