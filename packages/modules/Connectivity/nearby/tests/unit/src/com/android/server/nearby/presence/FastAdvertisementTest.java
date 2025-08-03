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

package com.android.server.nearby.presence;

import static com.google.common.truth.Truth.assertThat;

import android.nearby.BroadcastRequest;
import android.nearby.PresenceBroadcastRequest;
import android.nearby.PresenceCredential;
import android.nearby.PrivateCredential;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

/**
 * Unit test for {@link FastAdvertisement}.
 */
public class FastAdvertisementTest {

    private static final int IDENTITY_TYPE = PresenceCredential.IDENTITY_TYPE_PRIVATE;
    private static final byte[] IDENTITY = new byte[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    private static final int MEDIUM_TYPE_BLE = 0;
    private static final byte[] SALT = {2, 3};
    private static final byte TX_POWER = 4;
    private static final int PRESENCE_ACTION = 123;
    private static final byte[] SECRET_ID = new byte[]{1, 2, 3, 4};
    private static final byte[] AUTHENTICITY_KEY = new byte[]{12, 13, 14};
    private static final byte[] EXPECTED_ADV_BYTES =
            new byte[]{2, 2, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 4, 123};
    private static final String DEVICE_NAME = "test_device";

    private PresenceBroadcastRequest.Builder mBuilder;
    private PrivateCredential mCredential;

    @Before
    public void setUp() {
        mCredential =
                new PrivateCredential.Builder(SECRET_ID, AUTHENTICITY_KEY, IDENTITY, DEVICE_NAME)
                        .setIdentityType(PresenceCredential.IDENTITY_TYPE_PRIVATE)
                        .build();
        mBuilder =
                new PresenceBroadcastRequest.Builder(Collections.singletonList(MEDIUM_TYPE_BLE),
                        SALT, mCredential)
                        .setTxPower(TX_POWER)
                        .setVersion(BroadcastRequest.PRESENCE_VERSION_V0)
                        .addAction(PRESENCE_ACTION);
    }

    @Test
    public void testFastAdvertisementCreateFromRequest() {
        FastAdvertisement originalAdvertisement = FastAdvertisement.createFromRequest(
                mBuilder.build());

        assertThat(originalAdvertisement.getActions()).containsExactly(PRESENCE_ACTION);
        assertThat(originalAdvertisement.getIdentity()).isEqualTo(IDENTITY);
        assertThat(originalAdvertisement.getIdentityType()).isEqualTo(IDENTITY_TYPE);
        assertThat(originalAdvertisement.getLtvFieldCount()).isEqualTo(4);
        assertThat(originalAdvertisement.getLength()).isEqualTo(19);
        assertThat(originalAdvertisement.getVersion()).isEqualTo(
                BroadcastRequest.PRESENCE_VERSION_V0);
        assertThat(originalAdvertisement.getSalt()).isEqualTo(SALT);
        assertThat(originalAdvertisement.getTxPower()).isEqualTo(TX_POWER);
        assertThat(originalAdvertisement.toString())
                .isEqualTo("FastAdvertisement:<VERSION: 0, length: 19,"
                        + " ltvFieldCount: 4,"
                        + " identityType: 1,"
                        + " identity: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1],"
                        + " salt: [2, 3],"
                        + " actions: [123],"
                        + " txPower: 4");
    }

    @Test
    public void testFastAdvertisementSerialization() {
        FastAdvertisement originalAdvertisement = FastAdvertisement.createFromRequest(
                mBuilder.build());
        byte[] bytes = originalAdvertisement.toBytes();

        assertThat(bytes).hasLength(originalAdvertisement.getLength());
        assertThat(bytes).isEqualTo(EXPECTED_ADV_BYTES);
    }
}
