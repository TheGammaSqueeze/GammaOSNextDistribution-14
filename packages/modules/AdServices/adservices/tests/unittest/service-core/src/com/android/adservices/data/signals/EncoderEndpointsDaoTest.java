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
import static org.junit.Assert.assertNull;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class EncoderEndpointsDaoTest {
    public static AdTechIdentifier BUYER_1 = CommonFixture.VALID_BUYER_1;

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    private EncoderEndpointsDao mEncoderEndpointsDao;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Before
    public void setup() {
        mEncoderEndpointsDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, ProtectedSignalsDatabase.class)
                        .build()
                        .getEncoderEndpointsDao();
    }

    @Test
    public void testRegisterEncoderEndPoint() {
        assertNull(
                "Initial state of the table should be empty",
                mEncoderEndpointsDao.getEndpoint(BUYER_1));

        DBEncoderEndpoint encoderEndpoint = DBEncoderEndpointFixture.anEndpoint();
        assertEquals(
                "One entry should have been inserted",
                1,
                mEncoderEndpointsDao.registerEndpoint(encoderEndpoint));
    }

    @Test
    public void testDeleteEncoderEndPoint() {
        assertNull(
                "Initial state of the table should be empty",
                mEncoderEndpointsDao.getEndpoint(BUYER_1));

        DBEncoderEndpoint encoderEndpoint = DBEncoderEndpointFixture.anEndpoint();
        assertEquals(
                "One entry should have been inserted",
                1,
                mEncoderEndpointsDao.registerEndpoint(encoderEndpoint));

        mEncoderEndpointsDao.deleteEncoderEndpoint(BUYER_1);
        assertNull("Endpoint should have been deleted", mEncoderEndpointsDao.getEndpoint(BUYER_1));
    }

    @Test
    public void testQueryEncoderEndPoint() {
        assertNull(
                "Initial state of the table should be empty",
                mEncoderEndpointsDao.getEndpoint(BUYER_1));

        DBEncoderEndpoint inserted = DBEncoderEndpointFixture.anEndpointBuilder(BUYER_1).build();
        assertEquals(
                "One entry should have been inserted",
                1,
                mEncoderEndpointsDao.registerEndpoint(inserted));
        DBEncoderEndpoint retrieved = mEncoderEndpointsDao.getEndpoint(BUYER_1);

        assertEquals(inserted.getBuyer(), retrieved.getBuyer());
        assertEquals(inserted.getDownloadUri(), retrieved.getDownloadUri());
    }

    @Test
    public void testRegisterEndPointReplacesExisting() {
        assertNull(
                "Initial state of the table should be empty",
                mEncoderEndpointsDao.getEndpoint(BUYER_1));

        DBEncoderEndpoint.Builder anEndpointBuilder = DBEncoderEndpointFixture.anEndpointBuilder();
        DBEncoderEndpoint previous = anEndpointBuilder.build();
        mEncoderEndpointsDao.registerEndpoint(previous);

        DBEncoderEndpoint retrieved = mEncoderEndpointsDao.getEndpoint(BUYER_1);
        assertEquals(previous.getDownloadUri(), retrieved.getDownloadUri());

        String newPath = "/updated/downloadPath";
        DBEncoderEndpoint updated =
                anEndpointBuilder.setDownloadUri(CommonFixture.getUri(BUYER_1, newPath)).build();

        mEncoderEndpointsDao.registerEndpoint(updated);
        retrieved = mEncoderEndpointsDao.getEndpoint(BUYER_1);
        assertNotEquals(previous.getDownloadUri(), retrieved.getDownloadUri());
        assertEquals(
                "The Uri should have been updated",
                updated.getDownloadUri(),
                retrieved.getDownloadUri());
    }
}
