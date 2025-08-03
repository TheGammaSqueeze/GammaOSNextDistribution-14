/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.data.customaudience;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.net.Uri;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;

public class DBScheduledCustomAudienceUpdateTest extends AdServicesUnitTestCase {

    private static final Long UPDATE_ID = 1L;
    private static final String OWNER = CustomAudienceFixture.VALID_OWNER;
    private static final AdTechIdentifier BUYER = CommonFixture.VALID_BUYER_1;
    private static final Uri UPDATE_URI = CommonFixture.getUri(BUYER, "/updateUri");
    private static final Instant CREATION_TIME = CommonFixture.FIXED_NOW;
    private static final Instant SCHEDULED_TIME = CommonFixture.FIXED_NEXT_ONE_DAY;
    private static final boolean IS_DEBUGGABLE = false;

    @Test
    public void testBuildDBScheduledCustomAudienceUpdate_BuilderSuccess() {
        DBScheduledCustomAudienceUpdate dbScheduledCustomAudienceUpdate =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateId(UPDATE_ID)
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .setUpdateUri(UPDATE_URI)
                        .setScheduledTime(SCHEDULED_TIME)
                        .setCreationTime(CREATION_TIME)
                        .setIsDebuggable(IS_DEBUGGABLE)
                        .build();

        expect.withMessage("Scheduled Update Id")
                .that(dbScheduledCustomAudienceUpdate.getUpdateId())
                .isEqualTo(UPDATE_ID);
        expect.withMessage("Scheduled Update Owner")
                .that(dbScheduledCustomAudienceUpdate.getOwner())
                .isEqualTo(OWNER);
        expect.withMessage("Scheduled Update Buyer")
                .that(dbScheduledCustomAudienceUpdate.getBuyer())
                .isEqualTo(BUYER);
        expect.withMessage("Scheduled Update Update Uri")
                .that(dbScheduledCustomAudienceUpdate.getUpdateUri())
                .isEqualTo(UPDATE_URI);
        expect.withMessage("Scheduled Update Scheduled Time")
                .that(dbScheduledCustomAudienceUpdate.getScheduledTime())
                .isEqualTo(SCHEDULED_TIME);
        expect.withMessage("Scheduled Update Creation Time")
                .that(dbScheduledCustomAudienceUpdate.getCreationTime())
                .isEqualTo(CREATION_TIME);
        expect.withMessage("Scheduled Update Is Debuggable")
                .that(dbScheduledCustomAudienceUpdate.getIsDebuggable())
                .isEqualTo(IS_DEBUGGABLE);
    }

    @Test
    public void testBuildDBScheduledCustomAudienceUpdate_CreateSuccess() {
        DBScheduledCustomAudienceUpdate dbScheduledCustomAudienceUpdate =
                DBScheduledCustomAudienceUpdate.create(
                        UPDATE_ID,
                        OWNER,
                        BUYER,
                        UPDATE_URI,
                        SCHEDULED_TIME,
                        CREATION_TIME,
                        IS_DEBUGGABLE);
        expect.withMessage("Scheduled Update Id")
                .that(dbScheduledCustomAudienceUpdate.getUpdateId())
                .isEqualTo(UPDATE_ID);
        expect.withMessage("Scheduled Update Owner")
                .that(dbScheduledCustomAudienceUpdate.getOwner())
                .isEqualTo(OWNER);
        expect.withMessage("Scheduled Update Buyer")
                .that(dbScheduledCustomAudienceUpdate.getBuyer())
                .isEqualTo(BUYER);
        expect.withMessage("Scheduled Update Update Uri")
                .that(dbScheduledCustomAudienceUpdate.getUpdateUri())
                .isEqualTo(UPDATE_URI);
        expect.withMessage("Scheduled Update Scheduled Time")
                .that(dbScheduledCustomAudienceUpdate.getScheduledTime())
                .isEqualTo(SCHEDULED_TIME);
        expect.withMessage("Scheduled Update Creation Time")
                .that(dbScheduledCustomAudienceUpdate.getCreationTime())
                .isEqualTo(CREATION_TIME);
    }

    @Test
    public void testBuildDBScheduledCustomAudienceUpdate_NullOwner_ThrowsException() {
        Assert.assertThrows(
                IllegalStateException.class,
                () ->
                        DBScheduledCustomAudienceUpdate.builder()
                                .setUpdateId(UPDATE_ID)
                                .setBuyer(BUYER)
                                .setUpdateUri(UPDATE_URI)
                                .setScheduledTime(SCHEDULED_TIME)
                                .setCreationTime(CREATION_TIME)
                                .build());
    }

    @Test
    public void testBuildDBScheduledCustomAudienceUpdate_NullBuyer_ThrowsException() {
        Assert.assertThrows(
                IllegalStateException.class,
                () ->
                        DBScheduledCustomAudienceUpdate.builder()
                                .setUpdateId(UPDATE_ID)
                                .setOwner(OWNER)
                                .setUpdateUri(UPDATE_URI)
                                .setScheduledTime(SCHEDULED_TIME)
                                .setCreationTime(CREATION_TIME)
                                .build());
    }

    @Test
    public void testBuildDBScheduledCustomAudienceUpdate_NullUpdateUri_ThrowsException() {
        Assert.assertThrows(
                IllegalStateException.class,
                () ->
                        DBScheduledCustomAudienceUpdate.builder()
                                .setUpdateId(UPDATE_ID)
                                .setOwner(OWNER)
                                .setBuyer(BUYER)
                                .setScheduledTime(SCHEDULED_TIME)
                                .setCreationTime(CREATION_TIME)
                                .build());
    }

    @Test
    public void testBuildDBScheduledCustomAudienceUpdate_NullScheduledTime_ThrowsException() {
        Assert.assertThrows(
                IllegalStateException.class,
                () ->
                        DBScheduledCustomAudienceUpdate.builder()
                                .setUpdateId(UPDATE_ID)
                                .setOwner(OWNER)
                                .setBuyer(BUYER)
                                .setUpdateUri(UPDATE_URI)
                                .setCreationTime(CREATION_TIME)
                                .build());
    }

    @Test
    public void testBuildDBScheduledCustomAudienceUpdate_NullCreationTime_ThrowsException() {
        Assert.assertThrows(
                IllegalStateException.class,
                () ->
                        DBScheduledCustomAudienceUpdate.builder()
                                .setUpdateId(UPDATE_ID)
                                .setOwner(OWNER)
                                .setBuyer(BUYER)
                                .setUpdateUri(UPDATE_URI)
                                .setScheduledTime(SCHEDULED_TIME)
                                .build());
    }
}
