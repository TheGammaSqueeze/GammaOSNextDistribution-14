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

import android.nearby.DataElement;
import android.nearby.NearbyDeviceParcelable;
import android.nearby.PresenceCredential;
import android.nearby.PresenceDevice;
import android.nearby.PresenceScanFilter;
import android.nearby.PublicCredential;

import androidx.test.filters.SdkSuppress;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link PresenceDiscoveryResult}.
 */
public class PresenceDiscoveryResultTest {
    private static final int DATA_TYPE_ACCOUNT_KEY = 9;
    private static final int DATA_TYPE_INTENT = 6;
    private static final int PRESENCE_ACTION = 123;
    private static final int TX_POWER = -1;
    private static final int RSSI = -41;
    private static final byte[] SALT = new byte[]{12, 34};
    private static final byte[] SECRET_ID = new byte[]{1, 2, 3, 4};
    private static final byte[] AUTHENTICITY_KEY = new byte[]{12, 13, 14};
    private static final byte[] PUBLIC_KEY = new byte[]{1, 1, 2, 2};
    private static final byte[] ENCRYPTED_METADATA = new byte[]{1, 2, 3, 4, 5};
    private static final byte[] METADATA_ENCRYPTION_KEY_TAG = new byte[]{1, 1, 3, 4, 5};
    private static final byte[] META_DATA_ENCRYPTION_KEY =
            new byte[] {-39, -55, 115, 78, -57, 40, 115, 0, -112, 86, -86, 7, -42, 68, 11, 12};

    private PresenceDiscoveryResult.Builder mBuilder;
    private PublicCredential mCredential;

    @Before
    public void setUp() {
        mCredential =
                new PublicCredential.Builder(SECRET_ID, AUTHENTICITY_KEY, PUBLIC_KEY,
                        ENCRYPTED_METADATA, METADATA_ENCRYPTION_KEY_TAG)
                        .setIdentityType(PresenceCredential.IDENTITY_TYPE_PRIVATE)
                        .build();
        mBuilder = new PresenceDiscoveryResult.Builder()
                .setPublicCredential(mCredential)
                .setSalt(SALT)
                .setTxPower(TX_POWER)
                .setRssi(RSSI)
                .setEncryptedIdentityTag(METADATA_ENCRYPTION_KEY_TAG)
                .addPresenceAction(PRESENCE_ACTION);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testFromDevice() {
        NearbyDeviceParcelable.Builder builder = new NearbyDeviceParcelable.Builder();
        builder.setTxPower(TX_POWER)
                .setRssi(RSSI)
                .setEncryptionKeyTag(METADATA_ENCRYPTION_KEY_TAG)
                .setSalt(SALT)
                .setPublicCredential(mCredential);

        PresenceDiscoveryResult discoveryResult =
                PresenceDiscoveryResult.fromDevice(builder.build());
        PresenceScanFilter scanFilter = new PresenceScanFilter.Builder()
                .setMaxPathLoss(80)
                .addCredential(mCredential)
                .build();

        assertThat(discoveryResult.matches(scanFilter)).isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testFromDevice_presenceDeviceAvailable() {
        NearbyDeviceParcelable.Builder builder = new NearbyDeviceParcelable.Builder();
        PresenceDevice presenceDevice =
                new PresenceDevice.Builder("123", SALT, SECRET_ID, META_DATA_ENCRYPTION_KEY)
                        .addExtendedProperty(new DataElement(
                                DATA_TYPE_INTENT, new byte[]{(byte) PRESENCE_ACTION}))
                        .build();
        builder.setTxPower(TX_POWER)
                .setRssi(RSSI)
                .setEncryptionKeyTag(METADATA_ENCRYPTION_KEY_TAG)
                .setPresenceDevice(presenceDevice)
                .setPublicCredential(mCredential);

        PresenceDiscoveryResult discoveryResult =
                PresenceDiscoveryResult.fromDevice(builder.build());
        PresenceScanFilter scanFilter = new PresenceScanFilter.Builder()
                .setMaxPathLoss(80)
                .addPresenceAction(PRESENCE_ACTION)
                .addCredential(mCredential)
                .build();

        assertThat(discoveryResult.matches(scanFilter)).isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testAccountMatches() {
        DataElement accountKey = new DataElement(DATA_TYPE_ACCOUNT_KEY, new byte[]{1, 2, 3, 4});
        mBuilder.addExtendedProperties(List.of(accountKey));
        PresenceDiscoveryResult discoveryResult = mBuilder.build();

        List<DataElement> extendedProperties = new ArrayList<>();
        extendedProperties.add(new DataElement(DATA_TYPE_ACCOUNT_KEY, new byte[]{1, 2, 3, 4}));
        extendedProperties.add(new DataElement(DATA_TYPE_INTENT,
                new byte[]{(byte) PRESENCE_ACTION}));
        assertThat(discoveryResult.accountKeyMatches(extendedProperties)).isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testMatches() {
        PresenceScanFilter scanFilter = new PresenceScanFilter.Builder()
                .setMaxPathLoss(80)
                .addPresenceAction(PRESENCE_ACTION)
                .addCredential(mCredential)
                .build();

        PresenceDiscoveryResult discoveryResult = mBuilder.build();
        assertThat(discoveryResult.matches(scanFilter)).isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_notMatches() {
        PresenceDiscoveryResult.Builder builder = new PresenceDiscoveryResult.Builder()
                .setPublicCredential(mCredential)
                .setSalt(SALT)
                .setTxPower(TX_POWER)
                .setRssi(RSSI)
                .setEncryptedIdentityTag(new byte[]{5, 4, 3, 2, 1})
                .addPresenceAction(PRESENCE_ACTION);

        PresenceScanFilter scanFilter = new PresenceScanFilter.Builder()
                .setMaxPathLoss(80)
                .addPresenceAction(PRESENCE_ACTION)
                .addCredential(mCredential)
                .build();

        PresenceDiscoveryResult discoveryResult = builder.build();
        assertThat(discoveryResult.matches(scanFilter)).isFalse();
    }
}
