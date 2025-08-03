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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class EncoderLogicMetadataDaoTest {

    public static AdTechIdentifier BUYER_1 = CommonFixture.VALID_BUYER_1;
    public static AdTechIdentifier BUYER_2 = CommonFixture.VALID_BUYER_2;

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    private EncoderLogicMetadataDao mEncoderLogicMetadataDao;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Before
    public void setup() {
        mEncoderLogicMetadataDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, ProtectedSignalsDatabase.class)
                        .build()
                        .getEncoderLogicMetadataDao();
    }

    @Test
    public void testPersistEncoderLogic() {
        assertNull(
                "Initial state should have been empty",
                mEncoderLogicMetadataDao.getMetadata(BUYER_1));

        DBEncoderLogicMetadata logic = DBEncoderLogicFixture.anEncoderLogic();
        assertEquals(
                "One entry should have been inserted",
                1,
                mEncoderLogicMetadataDao.persistEncoderLogicMetadata(logic));
    }

    @Test
    public void testExistsAndGetAnEncoder() {
        assertNull(
                "Initial state should have been empty",
                mEncoderLogicMetadataDao.getMetadata(BUYER_1));

        DBEncoderLogicMetadata logic = DBEncoderLogicFixture.anEncoderLogic();
        assertEquals(
                "One entry should have been inserted",
                1,
                mEncoderLogicMetadataDao.persistEncoderLogicMetadata(logic));

        assertTrue(
                "Encoder for the buyer should have existed",
                mEncoderLogicMetadataDao.doesEncoderExist(BUYER_1));

        DBEncoderLogicMetadata retrieved = mEncoderLogicMetadataDao.getMetadata(BUYER_1);
        assertEquals(logic.getVersion(), retrieved.getVersion());
        assertEquals(logic.getBuyer(), retrieved.getBuyer());
    }

    @Test
    public void testPersistEncoderLogicOverwrites() {
        assertNull(
                "Initial state should have been empty",
                mEncoderLogicMetadataDao.getMetadata(BUYER_1));

        DBEncoderLogicMetadata v1 =
                DBEncoderLogicFixture.anEncoderLogicBuilder().setVersion(1).build();
        assertEquals(
                "One entry should have been inserted",
                1,
                mEncoderLogicMetadataDao.persistEncoderLogicMetadata(v1));
        assertEquals(
                "Version should have been 1",
                v1.getVersion(),
                mEncoderLogicMetadataDao.getMetadata(BUYER_1).getVersion());

        DBEncoderLogicMetadata v2 =
                DBEncoderLogicFixture.anEncoderLogicBuilder().setVersion(2).build();
        mEncoderLogicMetadataDao.persistEncoderLogicMetadata(v2);
        assertEquals(
                "Version should have been 2",
                v2.getVersion(),
                mEncoderLogicMetadataDao.getMetadata(BUYER_1).getVersion());
    }

    @Test
    public void testGetAllBuyersWithRegisteredEncoders() {
        assertNull(
                "Initial state should have been empty",
                mEncoderLogicMetadataDao.getMetadata(BUYER_1));
        assertNull(
                "Initial state should have been empty",
                mEncoderLogicMetadataDao.getMetadata(BUYER_2));

        DBEncoderLogicMetadata logic1 =
                DBEncoderLogicFixture.anEncoderLogicBuilder(BUYER_1).setVersion(1).build();
        DBEncoderLogicMetadata logic2 =
                DBEncoderLogicFixture.anEncoderLogicBuilder(BUYER_2).setVersion(1).build();

        assertEquals(
                "First entry should have been inserted",
                1,
                mEncoderLogicMetadataDao.persistEncoderLogicMetadata(logic1));
        assertEquals(
                "Second entry should have been inserted",
                2,
                mEncoderLogicMetadataDao.persistEncoderLogicMetadata(logic2));

        Set<AdTechIdentifier> actualRegisteredBuyers =
                new HashSet<>(mEncoderLogicMetadataDao.getAllBuyersWithRegisteredEncoders());
        Set<AdTechIdentifier> expectedRegisteredBuyers = Set.of(BUYER_1, BUYER_2);
        assertEquals(expectedRegisteredBuyers, actualRegisteredBuyers);
    }

    @Test
    public void testGetAllBuyersWithRegisteredBeforeTime() {
        DBEncoderLogicMetadata logic1 =
                DBEncoderLogicFixture.anEncoderLogicBuilder(BUYER_1).setVersion(1).build();
        DBEncoderLogicMetadata logic2 =
                DBEncoderLogicFixture.anEncoderLogicBuilder(BUYER_2).setVersion(1).build();
        DBEncoderLogicMetadata logic3 =
                DBEncoderLogicFixture.anEncoderLogicBuilder(AdTechIdentifier.fromString("buyer3"))
                        .setCreationTime(Instant.now().plus(10, ChronoUnit.DAYS))
                        .setVersion(1)
                        .build();
        assertEquals(
                "First entry should have been inserted",
                1,
                mEncoderLogicMetadataDao.persistEncoderLogicMetadata(logic1));
        assertEquals(
                "Second entry should have been inserted",
                2,
                mEncoderLogicMetadataDao.persistEncoderLogicMetadata(logic2));
        assertEquals(
                "Second entry should have been inserted",
                3,
                mEncoderLogicMetadataDao.persistEncoderLogicMetadata(logic3));

        Set<AdTechIdentifier> actualRegisteredBuyers =
                mEncoderLogicMetadataDao
                        .getBuyersWithEncodersBeforeTime(Instant.now().plus(1, ChronoUnit.DAYS))
                        .stream()
                        .collect(Collectors.toSet());
        Set<AdTechIdentifier> expectedRegisteredBuyers = Set.of(BUYER_1, BUYER_2);
        assertEquals(expectedRegisteredBuyers, actualRegisteredBuyers);
    }

    @Test
    public void testDeleteEncoder() {
        assertNull(
                "Initial state should have been empty",
                mEncoderLogicMetadataDao.getMetadata(BUYER_1));

        DBEncoderLogicMetadata logic = DBEncoderLogicFixture.anEncoderLogic();
        assertEquals(
                "One entry should have been inserted",
                1,
                mEncoderLogicMetadataDao.persistEncoderLogicMetadata(logic));

        mEncoderLogicMetadataDao.persistEncoderLogicMetadata(
                DBEncoderLogicFixture.anEncoderLogicBuilder(BUYER_2).build());

        assertTrue(
                "Encoder for the buyer should have existed",
                mEncoderLogicMetadataDao.doesEncoderExist(BUYER_1));
        assertTrue(
                "Encoder for the buyer should have existed",
                mEncoderLogicMetadataDao.doesEncoderExist(BUYER_2));

        mEncoderLogicMetadataDao.deleteEncoder(BUYER_1);

        assertFalse(
                "Encoder for the buyer should have been deleted",
                mEncoderLogicMetadataDao.doesEncoderExist(BUYER_1));
        assertTrue(
                "Encoder for the buyer should have remain untouched",
                mEncoderLogicMetadataDao.doesEncoderExist(BUYER_2));
    }

    @Test
    public void testUpdateFailedCount() {
        assertNull(
                "Initial state should have been empty",
                mEncoderLogicMetadataDao.getMetadata(BUYER_1));

        mEncoderLogicMetadataDao.updateEncoderFailedCount(BUYER_1, 2);

        assertNull(
                "Initial state should have been empty",
                mEncoderLogicMetadataDao.getMetadata(BUYER_1));

        DBEncoderLogicMetadata logic = DBEncoderLogicFixture.anEncoderLogic();
        mEncoderLogicMetadataDao.persistEncoderLogicMetadata(logic);
        assertEquals(logic, mEncoderLogicMetadataDao.getMetadata(BUYER_1));

        mEncoderLogicMetadataDao.updateEncoderFailedCount(BUYER_1, 2);
        assertEquals(
                DBEncoderLogicFixture.anEncoderLogicBuilder().setFailedEncodingCount(2).build(),
                mEncoderLogicMetadataDao.getMetadata(BUYER_1));
    }

    @Test
    public void testDeleteAllEncoders() {
        assertNull(
                "Initial state should have been empty",
                mEncoderLogicMetadataDao.getMetadata(BUYER_1));

        DBEncoderLogicMetadata logic = DBEncoderLogicFixture.anEncoderLogic();
        mEncoderLogicMetadataDao.persistEncoderLogicMetadata(logic);
        mEncoderLogicMetadataDao.persistEncoderLogicMetadata(
                DBEncoderLogicFixture.anEncoderLogicBuilder(BUYER_2).build());

        assertTrue(
                "Encoder for the buyer should have existed",
                mEncoderLogicMetadataDao.doesEncoderExist(BUYER_1));
        assertTrue(
                "Encoder for the buyer should have existed",
                mEncoderLogicMetadataDao.doesEncoderExist(BUYER_2));

        mEncoderLogicMetadataDao.deleteAllEncoders();

        assertFalse(
                "Encoder for all the buyers should have been deleted",
                mEncoderLogicMetadataDao.doesEncoderExist(BUYER_1));
        assertFalse(
                "Encoder for all the buyers should have been deleted",
                mEncoderLogicMetadataDao.doesEncoderExist(BUYER_2));
    }
}
