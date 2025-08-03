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

package com.android.server.uwb.data;

import static com.android.server.uwb.data.ServiceProfileData.ServiceProfileInfo.ADF_STATUS_PROVISIONED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.proto.uwb.UwbConfigProto;
import com.android.server.uwb.data.ServiceProfileData.ServiceProfileInfo;
import com.android.server.uwb.util.ObjectIdentifier;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SmallTest
@RunWith(AndroidJUnit4.class)
@Presubmit
public class ServiceProfileDataTest {
    private MockDataSource mDataSource;
    private ServiceProfileData mServiceProfileData;

    @Before
    public void setUp() {
        mDataSource = new MockDataSource();
        mServiceProfileData = new ServiceProfileData(mDataSource);
    }

    @Test
    public void testSerializeData() {
        UwbConfigProto.UwbConfig.Builder builder = UwbConfigProto.UwbConfig.newBuilder();
        builder.setVersion(1);
        mServiceProfileData.serializeData(builder);

        UwbConfigProto.ServiceConfig serviceConfig = builder.getServiceConfig(0);
        UUID serviceInstanceID = new UUID(100, 500);

        assertEquals(serviceConfig.getServiceId(), 1);
        assertEquals(serviceConfig.getServiceInstanceId(), serviceInstanceID.toString());
        assertEquals(serviceConfig.getUid(), 1);
        assertEquals(serviceConfig.getPackageName(), "test");
        assertEquals(serviceConfig.getAdfStatus(), ADF_STATUS_PROVISIONED);
        assertEquals(serviceConfig.getServiceAdfOid(), ByteString.copyFrom(new byte[] {(byte) 1}));
        assertEquals(serviceConfig.getSecureBlob(), ByteString.EMPTY);

    }

    @Test
    public void testDeserializeData() throws InvalidProtocolBufferException {
        UwbConfigProto.UwbConfig.Builder builder = UwbConfigProto.UwbConfig.newBuilder();
        builder.setVersion(1);
        mServiceProfileData.serializeData(builder);
        byte[] dataBytes = builder.build().toByteArray();
        UwbConfigProto.UwbConfig uwbConfig = UwbConfigProto.UwbConfig.parseFrom(dataBytes);

        mServiceProfileData.deserializeData(uwbConfig);
        assertEquals(1, mDataSource.mData.size());

        mServiceProfileData.resetData();
        assertNull(mDataSource.mData);

        assertTrue(mServiceProfileData.hasNewDataToSerialize());

        assertEquals(mServiceProfileData.getName(), "ServiceProfileData");

        assertEquals(mServiceProfileData.getStoreFileId(), 1);
    }

    private static class MockDataSource implements ServiceProfileData.DataSource {

        public Map<UUID, ServiceProfileData.ServiceProfileInfo> mData =
                new HashMap<>();
        @Override
        public Map<UUID, ServiceProfileInfo> toSerialize() {
            Map<UUID, ServiceProfileInfo> mServiceProfileMap =
                    new HashMap<>();
            UUID serviceInstanceID = new UUID(100, 500);
            int uid = 1;
            String packageName = "test";
            int serviceID = 1;
            ServiceProfileInfo mServiceProfileInfo =
                    new ServiceProfileInfo(serviceInstanceID, uid, packageName, serviceID);
            mServiceProfileInfo.setAdfStatus(ADF_STATUS_PROVISIONED);
            mServiceProfileInfo.setServiceAdfOid(
                    ObjectIdentifier.fromBytes(new byte[] {(byte) 1}));
            mServiceProfileMap.put(serviceInstanceID, mServiceProfileInfo);
            return mServiceProfileMap;
        }

        @Override
        public void fromDeserialized(
                Map<UUID, ServiceProfileData.ServiceProfileInfo> serviceProfileData) {
            mData = serviceProfileData;
        }

        @Override
        public void reset() {
            mData = null;
        }

        @Override
        public boolean hasNewDataToSerialize() {
            return true;
        }
    }
}
