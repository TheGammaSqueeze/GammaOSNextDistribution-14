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

package com.android.server.nearby.managers;

import static android.nearby.PresenceCredential.IDENTITY_TYPE_PRIVATE;

import static com.google.common.truth.Truth.assertThat;

import android.nearby.DataElement;
import android.nearby.PresenceScanFilter;
import android.nearby.PublicCredential;
import android.nearby.ScanFilter;
import android.nearby.ScanRequest;
import android.util.ArraySet;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/**
 * Unit test for {@link MergedDiscoveryRequest} class.
 */
public class MergedDiscoveryRequestTest {

    @Test
    public void test_addScanType() {
        MergedDiscoveryRequest.Builder builder = new MergedDiscoveryRequest.Builder();
        builder.addScanType(ScanRequest.SCAN_TYPE_FAST_PAIR);
        builder.addScanType(ScanRequest.SCAN_TYPE_NEARBY_PRESENCE);
        MergedDiscoveryRequest request = builder.build();

        assertThat(request.getScanTypes()).isEqualTo(new ArraySet<>(
                Arrays.asList(ScanRequest.SCAN_TYPE_FAST_PAIR,
                        ScanRequest.SCAN_TYPE_NEARBY_PRESENCE)));
    }

    @Test
    public void test_addActions() {
        MergedDiscoveryRequest.Builder builder = new MergedDiscoveryRequest.Builder();
        builder.addActions(new ArrayList<>(Arrays.asList(1, 2, 3)));
        builder.addActions(new ArraySet<>(Arrays.asList(2, 3, 4)));
        builder.addActions(new ArraySet<>(Collections.singletonList(5)));

        MergedDiscoveryRequest request = builder.build();
        assertThat(request.getActions()).isEqualTo(new ArraySet<>(new Integer[]{1, 2, 3, 4, 5}));
    }

    @Test
    public void test_addFilters() {
        final int rssi = -40;
        final int action = 123;
        final byte[] secreteId = new byte[]{1, 2, 3, 4};
        final byte[] authenticityKey = new byte[]{0, 1, 1, 1};
        final byte[] publicKey = new byte[]{1, 1, 2, 2};
        final byte[] encryptedMetadata = new byte[]{1, 2, 3, 4, 5};
        final byte[] metadataEncryptionKeyTag = new byte[]{1, 1, 3, 4, 5};
        final int key = 3;
        final byte[] value = new byte[]{1, 1, 1, 1};

        PublicCredential mPublicCredential = new PublicCredential.Builder(secreteId,
                authenticityKey, publicKey, encryptedMetadata,
                metadataEncryptionKeyTag).setIdentityType(IDENTITY_TYPE_PRIVATE).build();
        PresenceScanFilter scanFilterBuilder = new PresenceScanFilter.Builder().setMaxPathLoss(
                rssi).addCredential(mPublicCredential).addPresenceAction(
                action).addExtendedProperty(new DataElement(key, value)).build();

        MergedDiscoveryRequest.Builder builder = new MergedDiscoveryRequest.Builder();
        builder.addScanFilters(Collections.singleton(scanFilterBuilder));
        MergedDiscoveryRequest request = builder.build();

        Set<ScanFilter> expectedResult = new ArraySet<>();
        expectedResult.add(scanFilterBuilder);
        assertThat(request.getScanFilters()).isEqualTo(expectedResult);
    }

    @Test
    public void test_addMedium() {
        MergedDiscoveryRequest.Builder builder = new MergedDiscoveryRequest.Builder();
        builder.addMedium(MergedDiscoveryRequest.Medium.BLE);
        builder.addMedium(MergedDiscoveryRequest.Medium.BLE);
        MergedDiscoveryRequest request = builder.build();

        Set<Integer> expectedResult = new ArraySet<>();
        expectedResult.add(MergedDiscoveryRequest.Medium.BLE);
        assertThat(request.getMediums()).isEqualTo(expectedResult);
    }
}
