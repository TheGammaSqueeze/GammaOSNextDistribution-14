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

package android.telephony.cts;

import static org.junit.Assert.assertEquals;

import android.net.LinkProperties;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.AccessNetworkConstants;
import android.telephony.PreciseDataConnectionState;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.EpsQos;
import android.telephony.data.NrQos;
import android.telephony.data.Qos;

import com.android.internal.telephony.flags.Flags;

import org.junit.Rule;
import org.junit.Test;

public class PreciseDataConnectionStateTest {

    private static final int TRANSPORT_TYPE = AccessNetworkConstants.TRANSPORT_TYPE_WWAN;
    private static final int DATA_CALL_ID = 1;
    private static final int CONNECTION_STATE = TelephonyManager.DATA_CONNECTED;
    private static final int NETWORK_TYPE = TelephonyManager.NETWORK_TYPE_LTE;
    private static final ApnSetting APN_SETTING;
    private static final LinkProperties LINK_PROPERTIES = new LinkProperties();
    private static final int DATA_FAIL_CAUSE = 0;
    private static final Qos DEFAULT_QOS;
    private static final Qos DEFAULT_NR_QOS;
    private static final int DEFAULT_NETWORK_VALIDATION_STATUS =
            PreciseDataConnectionState.NETWORK_VALIDATION_SUCCESS;

    static {
        APN_SETTING = new ApnSetting.Builder()
                .setApnTypeBitmask(ApnSetting.TYPE_DEFAULT)
                .setApnName("default")
                .setEntryName("default")
                .build();

        DEFAULT_QOS = new EpsQos(
                new Qos.QosBandwidth(100, 10),
                new Qos.QosBandwidth(200, 20),
                255 /* QCI */);

        DEFAULT_NR_QOS = new NrQos(
                new Qos.QosBandwidth(1000, 10),
                new Qos.QosBandwidth(2000, 20),
                1 /* flow id */,
                5 /* 5QI */,
                200 /* averaging window */);
    }

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private PreciseDataConnectionState makeTestPreciseDataConnectionState() {
        return makeTestPreciseDataConnectionState(DEFAULT_QOS);
    }

    private PreciseDataConnectionState makeTestPreciseDataConnectionState(Qos qos) {
        return new PreciseDataConnectionState.Builder()
                .setTransportType(TRANSPORT_TYPE)
                .setId(DATA_CALL_ID)
                .setState(CONNECTION_STATE)
                .setNetworkType(NETWORK_TYPE)
                .setApnSetting(APN_SETTING)
                .setLinkProperties(LINK_PROPERTIES)
                .setFailCause(DATA_FAIL_CAUSE)
                .setDefaultQos(DEFAULT_QOS)
                .build();
    }

    @Test
    public void testParcel() {
        for (Qos qos : new Qos[]{DEFAULT_QOS, DEFAULT_NR_QOS}) {
            PreciseDataConnectionState pdcs = makeTestPreciseDataConnectionState(qos);
            Parcel pdcsParcel = Parcel.obtain();
            pdcs.writeToParcel(pdcsParcel, 0);
            pdcsParcel.setDataPosition(0);

            final PreciseDataConnectionState unparceledPdcs =
                    PreciseDataConnectionState.CREATOR.createFromParcel(pdcsParcel);
            assertEquals(pdcs, unparceledPdcs);
        }
    }

    @Test
    public void testPreciseDataConnectionStateGetters() {
        final PreciseDataConnectionState pdcs = makeTestPreciseDataConnectionState();
        assertEquals(TRANSPORT_TYPE, pdcs.getTransportType());
        assertEquals(DATA_CALL_ID, pdcs.getId());
        assertEquals(CONNECTION_STATE, pdcs.getState());
        assertEquals(NETWORK_TYPE, pdcs.getNetworkType());
        assertEquals(APN_SETTING, pdcs.getApnSetting());
        assertEquals(LINK_PROPERTIES, pdcs.getLinkProperties());
        assertEquals(DATA_FAIL_CAUSE, pdcs.getLastCauseCode());
        assertEquals(DEFAULT_QOS, pdcs.getDefaultQos());
    }


    private PreciseDataConnectionState makeTestPreciseDataConnectionStateForNetworkValidation() {
        return new PreciseDataConnectionState.Builder()
                .setTransportType(TRANSPORT_TYPE)
                .setId(DATA_CALL_ID)
                .setState(CONNECTION_STATE)
                .setNetworkType(NETWORK_TYPE)
                .setApnSetting(APN_SETTING)
                .setLinkProperties(LINK_PROPERTIES)
                .setFailCause(DATA_FAIL_CAUSE)
                .setDefaultQos(DEFAULT_QOS)
                .setNetworkValidationStatus(DEFAULT_NETWORK_VALIDATION_STATUS)
                .build();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NETWORK_VALIDATION)
    public void testParcelForNetworkValidation() {
        PreciseDataConnectionState pdcs = makeTestPreciseDataConnectionStateForNetworkValidation();
        Parcel stateParcel = Parcel.obtain();
        pdcs.writeToParcel(stateParcel, 0);
        stateParcel.setDataPosition(0);

        final PreciseDataConnectionState stateFromParcel =
                PreciseDataConnectionState.CREATOR.createFromParcel(stateParcel);
        assertEquals(pdcs, stateFromParcel);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NETWORK_VALIDATION)
    public void testPreciseDataConnectionStateGettersForNetworkValidation() {
        PreciseDataConnectionState pdcs = makeTestPreciseDataConnectionStateForNetworkValidation();
        assertEquals(TRANSPORT_TYPE, pdcs.getTransportType());
        assertEquals(DATA_CALL_ID, pdcs.getId());
        assertEquals(CONNECTION_STATE, pdcs.getState());
        assertEquals(NETWORK_TYPE, pdcs.getNetworkType());
        assertEquals(APN_SETTING, pdcs.getApnSetting());
        assertEquals(LINK_PROPERTIES, pdcs.getLinkProperties());
        assertEquals(DATA_FAIL_CAUSE, pdcs.getLastCauseCode());
        assertEquals(DEFAULT_QOS, pdcs.getDefaultQos());
        assertEquals(DEFAULT_NETWORK_VALIDATION_STATUS, pdcs.getNetworkValidationStatus());
    }

}
