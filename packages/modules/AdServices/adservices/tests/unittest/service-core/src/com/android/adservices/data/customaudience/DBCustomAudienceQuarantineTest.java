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

package com.android.adservices.data.customaudience;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;

public class DBCustomAudienceQuarantineTest {
    private static final Instant QUARANTINE_EXPIRATION = Instant.now();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildDBFetchCustomAudienceQuarantineSucceeds() {
        DBCustomAudienceQuarantine dbCustomAudienceQuarantine =
                DBCustomAudienceQuarantine.builder()
                        .setOwner(CustomAudienceFixture.VALID_OWNER)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setQuarantineExpirationTime(QUARANTINE_EXPIRATION)
                        .build();

        assertEquals(CustomAudienceFixture.VALID_OWNER, dbCustomAudienceQuarantine.getOwner());
        assertEquals(CommonFixture.VALID_BUYER_1, dbCustomAudienceQuarantine.getBuyer());
        assertEquals(
                QUARANTINE_EXPIRATION, dbCustomAudienceQuarantine.getQuarantineExpirationTime());
    }

    @Test
    public void testCreateDBFetchCustomAudienceQuarantineSucceeds() {
        DBCustomAudienceQuarantine dbCustomAudienceQuarantine =
                DBCustomAudienceQuarantine.create(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        QUARANTINE_EXPIRATION);

        assertEquals(CustomAudienceFixture.VALID_OWNER, dbCustomAudienceQuarantine.getOwner());
        assertEquals(CommonFixture.VALID_BUYER_1, dbCustomAudienceQuarantine.getBuyer());
        assertEquals(
                QUARANTINE_EXPIRATION, dbCustomAudienceQuarantine.getQuarantineExpirationTime());
    }

    @Test
    public void testBuildDBFetchCustomAudienceQuarantineThrowsExceptionWithNullOwner() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBCustomAudienceQuarantine.builder()
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setQuarantineExpirationTime(QUARANTINE_EXPIRATION)
                                .build());
    }

    @Test
    public void testBuildDBFetchCustomAudienceQuarantineThrowsExceptionWithNullBuyer() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBCustomAudienceQuarantine.builder()
                                .setOwner(CustomAudienceFixture.VALID_OWNER)
                                .setQuarantineExpirationTime(QUARANTINE_EXPIRATION)
                                .build());
    }

    @Test
    public void
            testBuildDBFetchCustomAudienceQuarantineThrowsExceptionWithNullQuarantineExpiration() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBCustomAudienceQuarantine.builder()
                                .setOwner(CustomAudienceFixture.VALID_OWNER)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .build());
    }
}
