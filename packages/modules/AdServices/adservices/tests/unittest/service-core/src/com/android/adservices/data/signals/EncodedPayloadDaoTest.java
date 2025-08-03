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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class EncodedPayloadDaoTest {

    public static AdTechIdentifier BUYER_1 = CommonFixture.VALID_BUYER_1;
    public static AdTechIdentifier BUYER_2 = CommonFixture.VALID_BUYER_2;

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    private EncodedPayloadDao mEncodedPayloadDao;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Before
    public void setup() {
        mEncodedPayloadDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, ProtectedSignalsDatabase.class)
                        .build()
                        .getEncodedPayloadDao();
    }

    @Test
    public void persistEncodedPayload() {
        assertNull(
                "Initial state should have been empty",
                mEncodedPayloadDao.getEncodedPayload(BUYER_1));

        DBEncodedPayload payload = DBEncodedPayloadFixture.anEncodedPayload();
        assertEquals(
                "One entry should have been inserted",
                1,
                mEncodedPayloadDao.persistEncodedPayload(payload));
    }

    @Test
    public void testExistsAndGetAnEncodedPayload() {
        assertNull(
                "Initial state should have been empty",
                mEncodedPayloadDao.getEncodedPayload(BUYER_1));

        DBEncodedPayload payload = DBEncodedPayloadFixture.anEncodedPayload();
        assertEquals(
                "One entry should have been inserted",
                1,
                mEncodedPayloadDao.persistEncodedPayload(payload));

        assertTrue(
                "Encoded payload should've existed",
                mEncodedPayloadDao.doesEncodedPayloadExist(BUYER_1));

        DBEncodedPayload retrieved = mEncodedPayloadDao.getEncodedPayload(BUYER_1);

        for (int i = 0; i < payload.getEncodedPayload().length; i++) {
            assertEquals(payload.getEncodedPayload()[i], retrieved.getEncodedPayload()[i]);
        }
        assertEquals(payload.getVersion(), retrieved.getVersion());
        assertEquals(payload.getBuyer(), retrieved.getBuyer());
    }

    @Test
    public void testGetAllEncodedPayloads() {
        assertNull(
                "Initial state should have been empty",
                mEncodedPayloadDao.getEncodedPayload(BUYER_1));

        DBEncodedPayload payload = DBEncodedPayloadFixture.anEncodedPayload();
        mEncodedPayloadDao.persistEncodedPayload(payload);
        mEncodedPayloadDao.persistEncodedPayload(
                DBEncodedPayloadFixture.anEncodedPayloadBuilder().setBuyer(BUYER_2).build());

        List<DBEncodedPayload> encodedPayloadList = mEncodedPayloadDao.getAllEncodedPayloads();
        assertEquals(2, encodedPayloadList.size());

        Set<AdTechIdentifier> buyers =
                encodedPayloadList.stream().map(a -> a.getBuyer()).collect(Collectors.toSet());
        assertTrue(buyers.contains(BUYER_1));
        assertTrue(buyers.contains(BUYER_2));

        Set<AdTechIdentifier> allBuyers =
                new HashSet<>(mEncodedPayloadDao.getAllBuyersWithEncodedPayloads());
        assertTrue(allBuyers.contains(BUYER_1));
        assertTrue(allBuyers.contains(BUYER_2));
    }

    @Test
    public void testDeleteAllBuyersRegisteredBefore() {
        assertNull(
                "Initial state should have been empty",
                mEncodedPayloadDao.getEncodedPayload(BUYER_1));

        DBEncodedPayload payload =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder()
                        .setCreationTime(Instant.now())
                        .build();
        mEncodedPayloadDao.persistEncodedPayload(payload);
        mEncodedPayloadDao.persistEncodedPayload(
                DBEncodedPayloadFixture.anEncodedPayloadBuilder()
                        .setBuyer(BUYER_2)
                        .setCreationTime(Instant.now().plus(10, ChronoUnit.DAYS))
                        .build());

        List<DBEncodedPayload> encodedPayloadList = mEncodedPayloadDao.getAllEncodedPayloads();
        assertEquals(2, encodedPayloadList.size());

        mEncodedPayloadDao.deleteEncodedPayloadsBeforeTime(Instant.now().plus(1, ChronoUnit.DAYS));
        encodedPayloadList = mEncodedPayloadDao.getAllEncodedPayloads();
        assertEquals(1, encodedPayloadList.size());
        Set<AdTechIdentifier> buyers =
                encodedPayloadList.stream().map(a -> a.getBuyer()).collect(Collectors.toSet());
        assertTrue(buyers.contains(BUYER_2));
        assertFalse("Buyer 1 should have been deleted", buyers.contains(BUYER_1));
    }

    @Test
    public void testPersistEncodedPayloadOverwrites() {
        assertNull(
                "Initial state should have been empty",
                mEncodedPayloadDao.getEncodedPayload(BUYER_1));

        DBEncodedPayload v1 = DBEncodedPayloadFixture.anEncodedPayload();
        assertEquals(
                "One entry should have been inserted",
                1,
                mEncodedPayloadDao.persistEncodedPayload(v1));

        assertEquals(
                "Version should have been 1",
                v1.getVersion(),
                mEncodedPayloadDao.getEncodedPayload(BUYER_1).getVersion());

        DBEncodedPayload v2 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder().setVersion(2).build();
        mEncodedPayloadDao.persistEncodedPayload(v2);
        assertEquals(
                "Version should have been 2",
                v2.getVersion(),
                mEncodedPayloadDao.getEncodedPayload(BUYER_1).getVersion());
    }

    @Test
    public void testDeleteEncodedPayload() {
        assertNull(
                "Initial state should have been empty",
                mEncodedPayloadDao.getEncodedPayload(BUYER_1));

        DBEncodedPayload payload = DBEncodedPayloadFixture.anEncodedPayload();
        mEncodedPayloadDao.persistEncodedPayload(payload);
        mEncodedPayloadDao.persistEncodedPayload(
                DBEncodedPayloadFixture.anEncodedPayloadBuilder().setBuyer(BUYER_2).build());

        assertTrue(
                "Encoded payload should've existed",
                mEncodedPayloadDao.doesEncodedPayloadExist(BUYER_1));
        assertTrue(
                "Encoded payload should've existed",
                mEncodedPayloadDao.doesEncodedPayloadExist(BUYER_2));

        mEncodedPayloadDao.deleteEncodedPayload(BUYER_1);
        assertFalse(
                "Encoded payload shouldn't have existed",
                mEncodedPayloadDao.doesEncodedPayloadExist(BUYER_1));
        assertTrue(
                "Encoded payload should've existed",
                mEncodedPayloadDao.doesEncodedPayloadExist(BUYER_2));
    }

    @Test
    public void testDeleteAllEncodedPayloads() {
        assertNull(
                "Initial state should have been empty",
                mEncodedPayloadDao.getEncodedPayload(BUYER_1));

        DBEncodedPayload payload = DBEncodedPayloadFixture.anEncodedPayload();
        mEncodedPayloadDao.persistEncodedPayload(payload);
        mEncodedPayloadDao.persistEncodedPayload(
                DBEncodedPayloadFixture.anEncodedPayloadBuilder().setBuyer(BUYER_2).build());

        assertTrue(
                "Encoded payload should've existed",
                mEncodedPayloadDao.doesEncodedPayloadExist(BUYER_1));
        assertTrue(
                "Encoded payload should've existed",
                mEncodedPayloadDao.doesEncodedPayloadExist(BUYER_2));

        mEncodedPayloadDao.deleteAllEncodedPayloads();
        assertFalse(
                "Encoded payload shouldn't have existed",
                mEncodedPayloadDao.doesEncodedPayloadExist(BUYER_1));
        assertFalse(
                "Encoded payload shouldn't have existed",
                mEncodedPayloadDao.doesEncodedPayloadExist(BUYER_2));
    }
}
