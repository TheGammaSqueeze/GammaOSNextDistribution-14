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

package android.telephony.cts;

import static com.google.common.truth.Truth.assertThat;

import android.net.InetAddresses;
import android.net.LinkAddress;
import android.os.IBinder;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.telephony.data.DataProfile;
import android.telephony.data.DataServiceCallback;
import android.telephony.data.IDataServiceCallback;
import android.telephony.data.NetworkSliceInfo;
import android.telephony.data.TrafficDescriptor;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class DataServiceCallbackTest {
    private static final int RESULT = DataServiceCallback.RESULT_SUCCESS;
    private static final int ID = 1;
    private static final int PROTOCOL_TYPE = ApnSetting.PROTOCOL_IP;
    private static final int MTU_V4 = 1440;
    private static final int MTU_V6 = 1400;
    private static final TrafficDescriptor TRAFFIC_DESCRIPTOR = new TrafficDescriptor(
            "DNN", new byte[]{-105, -92, -104, -29, -4, -110, 92, -108, -119, -122, 3, 51, -48, 110,
                    78, 71, 10, 69, 78, 84, 69, 82, 80, 82, 73, 83, 69});
    private static final DataCallResponse DATA_CALL_RESPONSE = new DataCallResponse.Builder()
            .setCause(0)
            .setRetryDurationMillis(-1L)
            .setId(ID)
            .setLinkStatus(2)
            .setProtocolType(PROTOCOL_TYPE)
            .setInterfaceName("IF_NAME")
            .setAddresses(Arrays.asList(
                    new LinkAddress(InetAddresses.parseNumericAddress("99.88.77.66"), 0)))
            .setDnsAddresses(Arrays.asList(InetAddresses.parseNumericAddress("55.66.77.88")))
            .setGatewayAddresses(Arrays.asList(InetAddresses.parseNumericAddress("11.22.33.44")))
            .setPcscfAddresses(Arrays.asList(InetAddresses.parseNumericAddress("22.33.44.55")))
            .setMtuV4(MTU_V4)
            .setMtuV6(MTU_V6)
            .setHandoverFailureMode(DataCallResponse.HANDOVER_FAILURE_MODE_DO_FALLBACK)
            .setPduSessionId(5)
            .setSliceInfo(new NetworkSliceInfo.Builder()
                    .setSliceServiceType(NetworkSliceInfo.SLICE_SERVICE_TYPE_EMBB)
                    .setSliceDifferentiator(1)
                    .setMappedHplmnSliceDifferentiator(10)
                    .setMappedHplmnSliceServiceType(NetworkSliceInfo.SLICE_SERVICE_TYPE_MIOT)
                    .build())
            .setTrafficDescriptors(Arrays.asList(TRAFFIC_DESCRIPTOR))
            .build();
    private static final List<DataCallResponse> DATA_CALL_LIST = Arrays.asList(DATA_CALL_RESPONSE);
    private static final String APN = "FAKE_APN";
    private static final DataProfile DATA_PROFILE = new DataProfile.Builder()
            .setApnSetting(new ApnSetting.Builder()
                    .setEntryName(APN)
                    .setApnName(APN)
                    .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT)
                    .setAuthType(ApnSetting.AUTH_TYPE_NONE)
                    .setCarrierEnabled(true)
                    .setModemCognitive(true)
                    .setMtuV4(MTU_V4)
                    .setMtuV6(MTU_V6)
                    .setNetworkTypeBitmask(ApnSetting.TYPE_DEFAULT)
                    .setProfileId(ID)
                    .setPassword("PASSWORD")
                    .setProtocol(PROTOCOL_TYPE)
                    .setRoamingProtocol(PROTOCOL_TYPE)
                    .setUser("USER_NAME")
                    .build())
            .setPreferred(true)
            .setType(DataProfile.TYPE_COMMON)
            .setTrafficDescriptor(TRAFFIC_DESCRIPTOR)
            .build();

    DataServiceCallback mDataServiceCallback;
    int mResult;
    DataCallResponse mResponse;
    List<DataCallResponse> mDataCallList;
    String mApn;
    DataProfile mDataProfile;

    private class TestDataServiceCallback implements IDataServiceCallback {
        public void onSetupDataCallComplete(int result, DataCallResponse response) {
            mResult = result;
            mResponse = response;
        }

        public void onDeactivateDataCallComplete(int result) {
            mResult = result;
        }

        public void onSetInitialAttachApnComplete(int result) {
            mResult = result;
        }

        public void onSetDataProfileComplete(int result) {
            mResult = result;
        }

        public void onRequestDataCallListComplete(int result, List<DataCallResponse> dataCallList) {
            mResult = result;
            mDataCallList = dataCallList;
        }

        public void onDataCallListChanged(List<DataCallResponse> dataCallList) {
            mDataCallList = dataCallList;
        }

        public void onHandoverStarted(int result) {
            mResult = result;
        }

        public void onHandoverCancelled(int result) {
            mResult = result;
        }

        public void onApnUnthrottled(String apn) {
            mApn = apn;
        }

        public void onDataProfileUnthrottled(DataProfile dataProfile) {
            mDataProfile = dataProfile;
        }

        public IBinder asBinder() {
            return null;
        }
    }

    @Before
    public void setUp() {
        mDataServiceCallback = new DataServiceCallback(new TestDataServiceCallback());
    }

    @Test
    public void testOnSetupDataCallComplete() {
        mDataServiceCallback.onSetupDataCallComplete(RESULT, DATA_CALL_RESPONSE);
        assertThat(RESULT).isEqualTo(mResult);
        assertThat(DATA_CALL_RESPONSE).isEqualTo(mResponse);
    }

    @Test
    public void testOnDeactivateDataCallComplete() {
        mDataServiceCallback.onDeactivateDataCallComplete(RESULT);
        assertThat(RESULT).isEqualTo(mResult);
    }

    @Test
    public void testOnSetInitialAttachApnComplete() {
        mDataServiceCallback.onSetInitialAttachApnComplete(RESULT);
        assertThat(RESULT).isEqualTo(mResult);
    }

    @Test
    public void testOnSetDataProfileComplete() {
        mDataServiceCallback.onSetDataProfileComplete(RESULT);
        assertThat(RESULT).isEqualTo(mResult);
    }

    @Test
    public void testOnRequestDataCallListComplete() {
        mDataServiceCallback.onRequestDataCallListComplete(RESULT, DATA_CALL_LIST);
        assertThat(RESULT).isEqualTo(mResult);
        assertThat(DATA_CALL_LIST).isEqualTo(mDataCallList);
    }

    @Test
    public void testOnDataCallListChanged() {
        mDataServiceCallback.onDataCallListChanged(DATA_CALL_LIST);
        assertThat(DATA_CALL_LIST).isEqualTo(mDataCallList);
    }
    @Test
    public void testOnHandoverStarted() {
        mDataServiceCallback.onHandoverStarted(RESULT);
        assertThat(RESULT).isEqualTo(mResult);
    }

    @Test
    public void testOnHandoverCancelled() {
        mDataServiceCallback.onHandoverCancelled(RESULT);
        assertThat(RESULT).isEqualTo(mResult);
    }

    @Test
    public void testOnApnUnthrottled() {
        mDataServiceCallback.onApnUnthrottled(APN);
        assertThat(RESULT).isEqualTo(mResult);
        assertThat(APN).isEqualTo(mApn);
    }

    @Test
    public void testOnDataProfileUnthrottled() {
        mDataServiceCallback.onDataProfileUnthrottled(DATA_PROFILE);
        assertThat(RESULT).isEqualTo(mResult);
        assertThat(DATA_PROFILE).isEqualTo(mDataProfile);
    }
}
