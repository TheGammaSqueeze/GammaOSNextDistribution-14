/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.telephony.data.DataCallResponse.HANDOVER_FAILURE_MODE_DO_FALLBACK;
import static android.telephony.data.DataCallResponse.HANDOVER_FAILURE_MODE_LEGACY;
import static android.telephony.data.NetworkSliceInfo.SLICE_SERVICE_TYPE_EMBB;
import static android.telephony.data.NetworkSliceInfo.SLICE_SERVICE_TYPE_MIOT;

import static com.google.common.truth.Truth.assertThat;

import android.net.InetAddresses;
import android.net.LinkAddress;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.PreciseDataConnectionState;
import android.telephony.data.ApnSetting;
import android.telephony.data.DataCallResponse;
import android.telephony.data.NetworkSliceInfo;
import android.telephony.data.TrafficDescriptor;

import com.android.internal.telephony.flags.Flags;

import org.junit.Rule;
import org.junit.Test;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

public class DataCallResponseTest {
    private static final int CAUSE = 0;
    private static final long RETRY = -1L;
    private static final int ID = 1;
    private static final int LINK_STATUS = 2;
    private static final int PROTOCOL_TYPE = ApnSetting.PROTOCOL_IP;
    private static final String IF_NAME = "IF_NAME";
    private static final List<LinkAddress> ADDRESSES = Arrays.asList(
            new LinkAddress(InetAddresses.parseNumericAddress("99.88.77.66"), 0));
    private static final List<InetAddress> DNSES =
            Arrays.asList(InetAddresses.parseNumericAddress("55.66.77.88"));
    private static final List<InetAddress> GATEWAYS =
            Arrays.asList(InetAddresses.parseNumericAddress("11.22.33.44"));
    private static final List<InetAddress> PCSCFS =
            Arrays.asList(InetAddresses.parseNumericAddress("22.33.44.55"));
    private static final int MTU_V4 = 1440;
    private static final int MTU_V6 = 1400;
    private static final int HANDOVER_FAILURE_MODE = HANDOVER_FAILURE_MODE_DO_FALLBACK;
    private static final int PDU_SESSION_ID = 5;
    private static final int TEST_SLICE_DIFFERENTIATOR = 1;
    private static final int TEST_SLICE_SERVICE_TYPE = SLICE_SERVICE_TYPE_EMBB;
    private static final int TEST_HPLMN_SLICE_DIFFERENTIATOR = 10;
    private static final int TEST_HPLMN_SLICE_SERVICE_TYPE = SLICE_SERVICE_TYPE_MIOT;
    private static final NetworkSliceInfo SLICE_INFO =
            new NetworkSliceInfo.Builder()
                .setSliceServiceType(TEST_SLICE_SERVICE_TYPE)
                .setSliceDifferentiator(TEST_SLICE_DIFFERENTIATOR)
                .setMappedHplmnSliceDifferentiator(TEST_HPLMN_SLICE_DIFFERENTIATOR)
                .setMappedHplmnSliceServiceType(TEST_HPLMN_SLICE_SERVICE_TYPE)
                .build();
    private static final String DNN = "DNN";
    // 97a498e3fc925c9489860333d06e4e470a454e5445525052495345.
    // [OsAppId.ANDROID_OS_ID, "ENTERPRISE", 1]
    private static final byte[] OS_APP_ID = {-105, -92, -104, -29, -4, -110, 92,
            -108, -119, -122, 3, 51, -48, 110, 78, 71, 10, 69, 78, 84, 69,
            82, 80, 82, 73, 83, 69};
    private static final List<TrafficDescriptor> TRAFFIC_DESCRIPTORS =
            Arrays.asList(new TrafficDescriptor(DNN, OS_APP_ID));
    private static final int TEST_VALIDATED = PreciseDataConnectionState.NETWORK_VALIDATION_SUCCESS;
    private static final int TEST_VALIDATE_FAILED =
            PreciseDataConnectionState.NETWORK_VALIDATION_FAILURE;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testConstructorAndGetters() {
        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(CAUSE)
                .setRetryDurationMillis(RETRY)
                .setId(ID)
                .setLinkStatus(LINK_STATUS)
                .setProtocolType(PROTOCOL_TYPE)
                .setInterfaceName(IF_NAME)
                .setAddresses(ADDRESSES)
                .setDnsAddresses(DNSES)
                .setGatewayAddresses(GATEWAYS)
                .setPcscfAddresses(PCSCFS)
                .setMtuV4(MTU_V4)
                .setMtuV6(MTU_V6)
                .setHandoverFailureMode(HANDOVER_FAILURE_MODE)
                .setPduSessionId(PDU_SESSION_ID)
                .setSliceInfo(SLICE_INFO)
                .setTrafficDescriptors(TRAFFIC_DESCRIPTORS)
                .build();

        assertThat(response.getCause()).isEqualTo(CAUSE);
        assertThat(response.getRetryDurationMillis()).isEqualTo(RETRY);
        assertThat(response.getId()).isEqualTo(ID);
        assertThat(response.getLinkStatus()).isEqualTo(LINK_STATUS);
        assertThat(response.getProtocolType()).isEqualTo(PROTOCOL_TYPE);
        assertThat(response.getInterfaceName()).isEqualTo(IF_NAME);
        assertThat(response.getAddresses()).isEqualTo(ADDRESSES);
        assertThat(response.getDnsAddresses()).isEqualTo(DNSES);
        assertThat(response.getGatewayAddresses()).isEqualTo(GATEWAYS);
        assertThat(response.getPcscfAddresses()).isEqualTo(PCSCFS);
        assertThat(response.getMtuV4()).isEqualTo(MTU_V4);
        assertThat(response.getMtuV6()).isEqualTo(MTU_V6);
        assertThat(response.getHandoverFailureMode()).isEqualTo(HANDOVER_FAILURE_MODE_DO_FALLBACK);
        assertThat(response.getPduSessionId()).isEqualTo(PDU_SESSION_ID);
        assertThat(response.getSliceInfo()).isEqualTo(SLICE_INFO);
        assertThat(response.getTrafficDescriptors()).isEqualTo(TRAFFIC_DESCRIPTORS);
    }

    @Test
    public void testEquals() {
        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(CAUSE)
                .setRetryDurationMillis(RETRY)
                .setId(ID)
                .setLinkStatus(LINK_STATUS)
                .setProtocolType(PROTOCOL_TYPE)
                .setInterfaceName(IF_NAME)
                .setAddresses(ADDRESSES)
                .setDnsAddresses(DNSES)
                .setGatewayAddresses(GATEWAYS)
                .setPcscfAddresses(PCSCFS)
                .setMtuV4(MTU_V4)
                .setMtuV6(MTU_V6)
                .setHandoverFailureMode(HANDOVER_FAILURE_MODE)
                .setPduSessionId(PDU_SESSION_ID)
                .setSliceInfo(SLICE_INFO)
                .setTrafficDescriptors(TRAFFIC_DESCRIPTORS)
                .build();

        DataCallResponse equalsResponse = new DataCallResponse.Builder()
                .setCause(CAUSE)
                .setRetryDurationMillis(RETRY)
                .setId(ID)
                .setLinkStatus(LINK_STATUS)
                .setProtocolType(PROTOCOL_TYPE)
                .setInterfaceName(IF_NAME)
                .setAddresses(ADDRESSES)
                .setDnsAddresses(DNSES)
                .setGatewayAddresses(GATEWAYS)
                .setPcscfAddresses(PCSCFS)
                .setMtuV4(MTU_V4)
                .setMtuV6(MTU_V6)
                .setHandoverFailureMode(HANDOVER_FAILURE_MODE)
                .setPduSessionId(PDU_SESSION_ID)
                .setSliceInfo(SLICE_INFO)
                .setTrafficDescriptors(TRAFFIC_DESCRIPTORS)
                .build();

        assertThat(response).isEqualTo(equalsResponse);
    }

    @Test
    public void testNotEquals() {
        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(CAUSE)
                .setRetryDurationMillis(RETRY)
                .setId(ID)
                .setLinkStatus(LINK_STATUS)
                .setProtocolType(PROTOCOL_TYPE)
                .setInterfaceName(IF_NAME)
                .setAddresses(ADDRESSES)
                .setDnsAddresses(DNSES)
                .setGatewayAddresses(GATEWAYS)
                .setPcscfAddresses(PCSCFS)
                .setMtuV4(MTU_V4)
                .setMtuV6(MTU_V6)
                .setHandoverFailureMode(HANDOVER_FAILURE_MODE)
                .setPduSessionId(PDU_SESSION_ID)
                .setSliceInfo(SLICE_INFO)
                .setTrafficDescriptors(TRAFFIC_DESCRIPTORS)
                .build();

        DataCallResponse notEqualsResponse = new DataCallResponse.Builder()
                .setCause(1)
                .setRetryDurationMillis(-1)
                .setId(1)
                .setLinkStatus(3)
                .setProtocolType(PROTOCOL_TYPE)
                .setInterfaceName(IF_NAME)
                .setAddresses(ADDRESSES)
                .setDnsAddresses(DNSES)
                .setGatewayAddresses(GATEWAYS)
                .setPcscfAddresses(PCSCFS)
                .setMtuV4(1441)
                .setMtuV6(1440)
                .setHandoverFailureMode(HANDOVER_FAILURE_MODE_LEGACY)
                .setPduSessionId(PDU_SESSION_ID)
                .setSliceInfo(SLICE_INFO)
                .setTrafficDescriptors(TRAFFIC_DESCRIPTORS)
                .build();

        assertThat(response).isNotEqualTo(notEqualsResponse);
        assertThat(response).isNotEqualTo(null);
        assertThat(response).isNotEqualTo(new String[1]);
    }

    @Test
    public void testParcel() {
        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(CAUSE)
                .setRetryDurationMillis(RETRY)
                .setId(ID)
                .setLinkStatus(LINK_STATUS)
                .setProtocolType(PROTOCOL_TYPE)
                .setInterfaceName(IF_NAME)
                .setAddresses(ADDRESSES)
                .setDnsAddresses(DNSES)
                .setGatewayAddresses(GATEWAYS)
                .setPcscfAddresses(PCSCFS)
                .setMtuV4(MTU_V4)
                .setMtuV6(MTU_V6)
                .setHandoverFailureMode(HANDOVER_FAILURE_MODE)
                .setPduSessionId(PDU_SESSION_ID)
                .setSliceInfo(SLICE_INFO)
                .setTrafficDescriptors(TRAFFIC_DESCRIPTORS)
                .build();

        Parcel stateParcel = Parcel.obtain();
        response.writeToParcel(stateParcel, 0);
        stateParcel.setDataPosition(0);

        DataCallResponse parcelResponse = DataCallResponse.CREATOR.createFromParcel(stateParcel);
        assertThat(response).isEqualTo(parcelResponse);
    }


    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NETWORK_VALIDATION)
    public void testConstructorAndGettersForNetworkValidation() {
        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(CAUSE)
                .setRetryDurationMillis(RETRY)
                .setId(ID)
                .setLinkStatus(LINK_STATUS)
                .setProtocolType(PROTOCOL_TYPE)
                .setInterfaceName(IF_NAME)
                .setAddresses(ADDRESSES)
                .setDnsAddresses(DNSES)
                .setGatewayAddresses(GATEWAYS)
                .setPcscfAddresses(PCSCFS)
                .setMtuV4(MTU_V4)
                .setMtuV6(MTU_V6)
                .setHandoverFailureMode(HANDOVER_FAILURE_MODE)
                .setPduSessionId(PDU_SESSION_ID)
                .setSliceInfo(SLICE_INFO)
                .setTrafficDescriptors(TRAFFIC_DESCRIPTORS)
                .setNetworkValidationStatus(TEST_VALIDATED)
                .build();

        assertThat(response.getCause()).isEqualTo(CAUSE);
        assertThat(response.getRetryDurationMillis()).isEqualTo(RETRY);
        assertThat(response.getId()).isEqualTo(ID);
        assertThat(response.getLinkStatus()).isEqualTo(LINK_STATUS);
        assertThat(response.getProtocolType()).isEqualTo(PROTOCOL_TYPE);
        assertThat(response.getInterfaceName()).isEqualTo(IF_NAME);
        assertThat(response.getAddresses()).isEqualTo(ADDRESSES);
        assertThat(response.getDnsAddresses()).isEqualTo(DNSES);
        assertThat(response.getGatewayAddresses()).isEqualTo(GATEWAYS);
        assertThat(response.getPcscfAddresses()).isEqualTo(PCSCFS);
        assertThat(response.getMtuV4()).isEqualTo(MTU_V4);
        assertThat(response.getMtuV6()).isEqualTo(MTU_V6);
        assertThat(response.getHandoverFailureMode()).isEqualTo(HANDOVER_FAILURE_MODE_DO_FALLBACK);
        assertThat(response.getPduSessionId()).isEqualTo(PDU_SESSION_ID);
        assertThat(response.getSliceInfo()).isEqualTo(SLICE_INFO);
        assertThat(response.getTrafficDescriptors()).isEqualTo(TRAFFIC_DESCRIPTORS);
        assertThat(response.getNetworkValidationStatus()).isEqualTo(TEST_VALIDATED);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NETWORK_VALIDATION)
    public void testEqualsForNetworkValidation() {
        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(CAUSE)
                .setRetryDurationMillis(RETRY)
                .setId(ID)
                .setLinkStatus(LINK_STATUS)
                .setProtocolType(PROTOCOL_TYPE)
                .setInterfaceName(IF_NAME)
                .setAddresses(ADDRESSES)
                .setDnsAddresses(DNSES)
                .setGatewayAddresses(GATEWAYS)
                .setPcscfAddresses(PCSCFS)
                .setMtuV4(MTU_V4)
                .setMtuV6(MTU_V6)
                .setHandoverFailureMode(HANDOVER_FAILURE_MODE)
                .setPduSessionId(PDU_SESSION_ID)
                .setSliceInfo(SLICE_INFO)
                .setTrafficDescriptors(TRAFFIC_DESCRIPTORS)
                .setNetworkValidationStatus(TEST_VALIDATED)
                .build();

        DataCallResponse equalsResponse = new DataCallResponse.Builder()
                .setCause(CAUSE)
                .setRetryDurationMillis(RETRY)
                .setId(ID)
                .setLinkStatus(LINK_STATUS)
                .setProtocolType(PROTOCOL_TYPE)
                .setInterfaceName(IF_NAME)
                .setAddresses(ADDRESSES)
                .setDnsAddresses(DNSES)
                .setGatewayAddresses(GATEWAYS)
                .setPcscfAddresses(PCSCFS)
                .setMtuV4(MTU_V4)
                .setMtuV6(MTU_V6)
                .setHandoverFailureMode(HANDOVER_FAILURE_MODE)
                .setPduSessionId(PDU_SESSION_ID)
                .setSliceInfo(SLICE_INFO)
                .setTrafficDescriptors(TRAFFIC_DESCRIPTORS)
                .setNetworkValidationStatus(TEST_VALIDATED)
                .build();

        assertThat(response).isEqualTo(equalsResponse);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NETWORK_VALIDATION)
    public void testNotEqualsForNetworkValidation() {
        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(CAUSE)
                .setRetryDurationMillis(RETRY)
                .setId(ID)
                .setLinkStatus(LINK_STATUS)
                .setProtocolType(PROTOCOL_TYPE)
                .setInterfaceName(IF_NAME)
                .setAddresses(ADDRESSES)
                .setDnsAddresses(DNSES)
                .setGatewayAddresses(GATEWAYS)
                .setPcscfAddresses(PCSCFS)
                .setMtuV4(MTU_V4)
                .setMtuV6(MTU_V6)
                .setHandoverFailureMode(HANDOVER_FAILURE_MODE)
                .setPduSessionId(PDU_SESSION_ID)
                .setSliceInfo(SLICE_INFO)
                .setTrafficDescriptors(TRAFFIC_DESCRIPTORS)
                .setNetworkValidationStatus(TEST_VALIDATED)
                .build();

        DataCallResponse notEqualsResponse = new DataCallResponse.Builder()
                .setCause(CAUSE)
                .setRetryDurationMillis(RETRY)
                .setId(ID)
                .setLinkStatus(LINK_STATUS)
                .setProtocolType(PROTOCOL_TYPE)
                .setInterfaceName(IF_NAME)
                .setAddresses(ADDRESSES)
                .setDnsAddresses(DNSES)
                .setGatewayAddresses(GATEWAYS)
                .setPcscfAddresses(PCSCFS)
                .setMtuV4(MTU_V4)
                .setMtuV6(MTU_V6)
                .setHandoverFailureMode(HANDOVER_FAILURE_MODE)
                .setPduSessionId(PDU_SESSION_ID)
                .setSliceInfo(SLICE_INFO)
                .setTrafficDescriptors(TRAFFIC_DESCRIPTORS)
                .setNetworkValidationStatus(TEST_VALIDATE_FAILED)
                .build();

        assertThat(response).isNotEqualTo(notEqualsResponse);
        assertThat(response).isNotEqualTo(null);
        assertThat(response).isNotEqualTo(new String[1]);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NETWORK_VALIDATION)
    public void testParcelForNetworkValidation() {
        DataCallResponse response = new DataCallResponse.Builder()
                .setCause(CAUSE)
                .setRetryDurationMillis(RETRY)
                .setId(ID)
                .setLinkStatus(LINK_STATUS)
                .setProtocolType(PROTOCOL_TYPE)
                .setInterfaceName(IF_NAME)
                .setAddresses(ADDRESSES)
                .setDnsAddresses(DNSES)
                .setGatewayAddresses(GATEWAYS)
                .setPcscfAddresses(PCSCFS)
                .setMtuV4(MTU_V4)
                .setMtuV6(MTU_V6)
                .setHandoverFailureMode(HANDOVER_FAILURE_MODE)
                .setPduSessionId(PDU_SESSION_ID)
                .setSliceInfo(SLICE_INFO)
                .setTrafficDescriptors(TRAFFIC_DESCRIPTORS)
                .setNetworkValidationStatus(TEST_VALIDATED)
                .build();

        Parcel stateParcel = Parcel.obtain();
        response.writeToParcel(stateParcel, 0);
        stateParcel.setDataPosition(0);

        DataCallResponse parcelResponse = DataCallResponse.CREATOR.createFromParcel(stateParcel);
        assertThat(response).isEqualTo(parcelResponse);
    }
}
