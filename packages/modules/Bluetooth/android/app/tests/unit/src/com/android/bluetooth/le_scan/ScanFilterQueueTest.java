/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.le_scan;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.le.ScanFilter;
import android.os.ParcelUuid;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

/**
 * Test cases for {@link ScanFilterQueue}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ScanFilterQueueTest {

    @Test
    public void scanFilterQueueParams() {
        ScanFilterQueue queue = new ScanFilterQueue();

        String address = "address";
        byte type = 1;
        byte[] irk = new byte[]{0x02};
        queue.addDeviceAddress(address, type, irk);

        queue.addServiceChanged();

        UUID uuid = UUID.randomUUID();
        queue.addUuid(uuid);

        UUID uuidMask = UUID.randomUUID();
        queue.addUuid(uuid, uuidMask);

        UUID solicitUuid = UUID.randomUUID();
        UUID solicitUuidMask = UUID.randomUUID();
        queue.addSolicitUuid(solicitUuid, solicitUuidMask);

        String name = "name";
        queue.addName(name);

        int company = 2;
        byte[] data = new byte[]{0x04};
        queue.addManufacturerData(company, data);

        int companyMask = 2;
        byte[] dataMask = new byte[]{0x05};
        queue.addManufacturerData(company, companyMask, data, dataMask);

        byte[] serviceData = new byte[]{0x06};
        byte[] serviceDataMask = new byte[]{0x08};
        queue.addServiceData(serviceData, serviceDataMask);

        int adType = 3;
        byte[] adData = new byte[]{0x10};
        byte[] adDataMask = new byte[]{0x12};
        queue.addAdvertisingDataType(adType, adData, adDataMask);

        ScanFilterQueue.Entry[] entries = queue.toArray();
        int entriesLength = 10;
        assertThat(entries.length).isEqualTo(entriesLength);

        for (ScanFilterQueue.Entry entry : entries) {
            switch (entry.type) {
                case ScanFilterQueue.TYPE_DEVICE_ADDRESS:
                    assertThat(entry.address).isEqualTo(address);
                    assertThat(entry.addr_type).isEqualTo(type);
                    assertThat(entry.irk).isEqualTo(irk);
                    break;
                case ScanFilterQueue.TYPE_SERVICE_DATA_CHANGED:
                    assertThat(entry).isNotNull();
                    break;
                case ScanFilterQueue.TYPE_SERVICE_UUID:
                    assertThat(entry.uuid).isEqualTo(uuid);
                    break;
                case ScanFilterQueue.TYPE_SOLICIT_UUID:
                    assertThat(entry.uuid).isEqualTo(solicitUuid);
                    assertThat(entry.uuid_mask).isEqualTo(solicitUuidMask);
                    break;
                case ScanFilterQueue.TYPE_LOCAL_NAME:
                    assertThat(entry.name).isEqualTo(name);
                    break;
                case ScanFilterQueue.TYPE_MANUFACTURER_DATA:
                    assertThat(entry.company).isEqualTo(company);
                    assertThat(entry.data).isEqualTo(data);
                    break;
                case ScanFilterQueue.TYPE_SERVICE_DATA:
                    assertThat(entry.data).isEqualTo(serviceData);
                    assertThat(entry.data_mask).isEqualTo(serviceDataMask);
                    break;
                case ScanFilterQueue.TYPE_ADVERTISING_DATA_TYPE:
                    assertThat(entry.ad_type).isEqualTo(adType);
                    assertThat(entry.data).isEqualTo(adData);
                    assertThat(entry.data_mask).isEqualTo(adDataMask);
                    break;
            }
        }
    }

    @Test
    public void popEmpty() {
        ScanFilterQueue queue = new ScanFilterQueue();

        ScanFilterQueue.Entry entry = queue.pop();
        assertThat(entry).isNull();
    }

    @Test
    public void popFromQueue() {
        ScanFilterQueue queue = new ScanFilterQueue();

        byte[] serviceData = new byte[]{0x02};
        byte[] serviceDataMask = new byte[]{0x04};
        queue.addServiceData(serviceData, serviceDataMask);

        ScanFilterQueue.Entry entry = queue.pop();
        assertThat(entry.data).isEqualTo(serviceData);
        assertThat(entry.data_mask).isEqualTo(serviceDataMask);
    }

    @Test
    public void checkFeatureSelection() {
        ScanFilterQueue queue = new ScanFilterQueue();

        byte[] serviceData = new byte[]{0x02};
        byte[] serviceDataMask = new byte[]{0x04};
        queue.addServiceData(serviceData, serviceDataMask);

        int feature = 1 << ScanFilterQueue.TYPE_SERVICE_DATA;
        assertThat(queue.getFeatureSelection()).isEqualTo(feature);
    }

    @Test
    public void convertQueueToArray() {
        ScanFilterQueue queue = new ScanFilterQueue();

        byte[] serviceData = new byte[]{0x02};
        byte[] serviceDataMask = new byte[]{0x04};
        queue.addServiceData(serviceData, serviceDataMask);

        ScanFilterQueue.Entry[] entries = queue.toArray();
        int entriesLength = 1;
        assertThat(entries.length).isEqualTo(entriesLength);

        ScanFilterQueue.Entry entry = entries[0];
        assertThat(entry.data).isEqualTo(serviceData);
        assertThat(entry.data_mask).isEqualTo(serviceDataMask);
    }

    @Test
    public void queueAddScanFilter() {
        ScanFilterQueue queue = new ScanFilterQueue();

        String name = "name";
        String deviceAddress = "00:11:22:33:FF:EE";
        ParcelUuid serviceUuid = ParcelUuid.fromString(UUID.randomUUID().toString());
        ParcelUuid serviceSolicitationUuid = ParcelUuid.fromString(UUID.randomUUID().toString());
        int manufacturerId = 0;
        byte[] manufacturerData = new byte[0];
        ParcelUuid serviceDataUuid = ParcelUuid.fromString(UUID.randomUUID().toString());
        byte[] serviceData = new byte[0];
        int advertisingDataType = 1;

        ScanFilter filter = new ScanFilter.Builder()
                .setDeviceName(name)
                .setDeviceAddress(deviceAddress)
                .setServiceUuid(serviceUuid)
                .setServiceSolicitationUuid(serviceSolicitationUuid)
                .setManufacturerData(manufacturerId, manufacturerData)
                .setServiceData(serviceDataUuid, serviceData)
                .setAdvertisingDataType(advertisingDataType)
                .build();
        queue.addScanFilter(filter);

        int numOfEntries = 7;
        assertThat(queue.toArray().length).isEqualTo(numOfEntries);
    }
}
