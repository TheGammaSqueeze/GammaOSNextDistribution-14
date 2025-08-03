/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.telephony.qns;

import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.TelephonyManager;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class QnsConstantsTest {

    @Test
    public void testCallTypeToString() {
        String callType_str = null;

        callType_str = QnsConstants.callTypeToString(QnsConstants.CALL_TYPE_IDLE);
        Assert.assertEquals(callType_str, "IDLE");
        callType_str = QnsConstants.callTypeToString(QnsConstants.CALL_TYPE_VOICE);
        Assert.assertEquals(callType_str, "VOICE");
        callType_str = QnsConstants.callTypeToString(QnsConstants.CALL_TYPE_VIDEO);
        Assert.assertEquals(callType_str, "VIDEO");
        callType_str = QnsConstants.callTypeToString(QnsConstants.CALL_TYPE_EMERGENCY);
        Assert.assertEquals(callType_str, "SOS");
        callType_str = QnsConstants.callTypeToString(-1);
        Assert.assertEquals(callType_str, "");
    }

    @Test
    public void testCoverageToString() {
        String coverage_str = null;

        coverage_str = QnsConstants.coverageToString(QnsConstants.COVERAGE_HOME);
        Assert.assertEquals(coverage_str, "HOME");
        coverage_str = QnsConstants.coverageToString(QnsConstants.COVERAGE_ROAM);
        Assert.assertEquals(coverage_str, "ROAM");
        coverage_str = QnsConstants.coverageToString(2);
        Assert.assertEquals(coverage_str, "");
    }

    @Test
    public void testPreferenceToString() {
        String preference_str = null;

        preference_str = QnsConstants.preferenceToString(QnsConstants.WIFI_ONLY);
        Assert.assertEquals(preference_str, "WIFI_ONLY");
        preference_str = QnsConstants.preferenceToString(QnsConstants.CELL_PREF);
        Assert.assertEquals(preference_str, "CELL_PREF");
        preference_str = QnsConstants.preferenceToString(QnsConstants.WIFI_PREF);
        Assert.assertEquals(preference_str, "WIFI_PREF");

        preference_str = QnsConstants.preferenceToString(3);
        Assert.assertEquals(preference_str, "");
    }

    @Test
    public void testDirectionToString() {
        String direction_str = null;

        direction_str = QnsConstants.directionToString(QnsConstants.ROVE_IN);
        Assert.assertEquals(direction_str, "ROVE_IN");
        direction_str = QnsConstants.directionToString(QnsConstants.ROVE_OUT);
        Assert.assertEquals(direction_str, "ROVE_OUT");
    }

    @Test
    public void testGuardingToString() {
        String guarding_str = null;

        guarding_str = QnsConstants.guardingToString(QnsConstants.GUARDING_NONE);
        Assert.assertEquals(guarding_str, "GUARDING_NONE");
        guarding_str = QnsConstants.guardingToString(QnsConstants.GUARDING_CELLULAR);
        Assert.assertEquals(guarding_str, "GUARDING_CELL");
        guarding_str = QnsConstants.guardingToString(QnsConstants.GUARDING_WIFI);
        Assert.assertEquals(guarding_str, "GUARDING_WIFI");
        guarding_str = QnsConstants.guardingToString(QnsConstants.INVALID_ID);
        Assert.assertEquals(guarding_str, "");
    }

    @Test
    public void imsRegistrationEventToString() {
        String imsRegEvent_str = null;

        imsRegEvent_str =
                QnsConstants.imsRegistrationEventToString(
                        QnsConstants.IMS_REGISTRATION_CHANGED_UNREGISTERED);
        Assert.assertEquals(imsRegEvent_str, "IMS_REGISTRATION_CHANGED_UNREGISTERED");
        imsRegEvent_str =
                QnsConstants.imsRegistrationEventToString(
                        QnsConstants.IMS_REGISTRATION_CHANGED_ACCESS_NETWORK_CHANGE_FAILED);
        Assert.assertEquals(
                imsRegEvent_str, "IMS_REGISTRATION_CHANGED_ACCESS_NETWORK_CHANGE_FAILED");
        imsRegEvent_str =
                QnsConstants.imsRegistrationEventToString(
                        QnsConstants.IMS_REGISTRATION_CHANGED_REGISTERED);
        Assert.assertEquals(imsRegEvent_str, "IMS_REGISTRATION_CHANGED_REGISTERED");
        imsRegEvent_str = QnsConstants.imsRegistrationEventToString(QnsConstants.INVALID_ID);
        Assert.assertEquals(imsRegEvent_str, "");
    }

    @Test
    public void testAccessNetworkTypeToString() {
        String accessNetwork;

        accessNetwork = QnsConstants.accessNetworkTypeToString(AccessNetworkType.UNKNOWN);
        Assert.assertEquals("UNKNOWN", accessNetwork);
        accessNetwork = QnsConstants.accessNetworkTypeToString(AccessNetworkType.NGRAN);
        Assert.assertEquals("NGRAN", accessNetwork);
        accessNetwork = QnsConstants.accessNetworkTypeToString(AccessNetworkType.EUTRAN);
        Assert.assertEquals("EUTRAN", accessNetwork);
        accessNetwork = QnsConstants.accessNetworkTypeToString(AccessNetworkType.UTRAN);
        Assert.assertEquals("UTRAN", accessNetwork);
        accessNetwork = QnsConstants.accessNetworkTypeToString(AccessNetworkType.CDMA2000);
        Assert.assertEquals("CDMA2000", accessNetwork);
        accessNetwork = QnsConstants.accessNetworkTypeToString(AccessNetworkType.GERAN);
        Assert.assertEquals("GERAN", accessNetwork);
        accessNetwork = QnsConstants.accessNetworkTypeToString(AccessNetworkType.IWLAN);
        Assert.assertEquals("IWLAN", accessNetwork);
        accessNetwork = QnsConstants.accessNetworkTypeToString(-1);
        Assert.assertEquals("-1", accessNetwork);
    }

    @Test
    public void testAccessNetworkTypeFromString() {
        int accessNetwork;

        accessNetwork = QnsConstants.accessNetworkTypeFromString("TEST");
        Assert.assertEquals(AccessNetworkType.UNKNOWN, accessNetwork);
        accessNetwork = QnsConstants.accessNetworkTypeFromString("NGRAN");
        Assert.assertEquals(AccessNetworkType.NGRAN, accessNetwork);
        accessNetwork = QnsConstants.accessNetworkTypeFromString("EUTRAN");
        Assert.assertEquals(AccessNetworkType.EUTRAN, accessNetwork);
        accessNetwork = QnsConstants.accessNetworkTypeFromString("UTRAN");
        Assert.assertEquals(AccessNetworkType.UTRAN, accessNetwork);
        accessNetwork = QnsConstants.accessNetworkTypeFromString("CDMA2000");
        Assert.assertEquals(AccessNetworkType.CDMA2000, accessNetwork);
        accessNetwork = QnsConstants.accessNetworkTypeFromString("GERAN");
        Assert.assertEquals(AccessNetworkType.GERAN, accessNetwork);
        accessNetwork = QnsConstants.accessNetworkTypeFromString("IWLAN");
        Assert.assertEquals(AccessNetworkType.IWLAN, accessNetwork);
        accessNetwork = QnsConstants.accessNetworkTypeFromString("eutran");
        Assert.assertEquals(AccessNetworkType.EUTRAN, accessNetwork);
    }

    @Test
    public void testTransportTypeToString() {
        String transportType;
        transportType =
                QnsConstants.transportTypeToString(AccessNetworkConstants.TRANSPORT_TYPE_INVALID);
        Assert.assertEquals("INVALID", transportType);
        transportType =
                QnsConstants.transportTypeToString(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        Assert.assertEquals("WLAN", transportType);
        transportType =
                QnsConstants.transportTypeToString(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        Assert.assertEquals("WWAN", transportType);
        transportType = QnsConstants.transportTypeToString(0);
        Assert.assertEquals("0", transportType);
    }

    @Test
    public void testDataStateToString() {
        String dataState;
        dataState = QnsConstants.dataStateToString(TelephonyManager.DATA_DISCONNECTED);
        Assert.assertEquals("DISCONNECTED", dataState);
        dataState = QnsConstants.dataStateToString(TelephonyManager.DATA_CONNECTED);
        Assert.assertEquals("CONNECTED", dataState);
        dataState = QnsConstants.dataStateToString(TelephonyManager.DATA_CONNECTING);
        Assert.assertEquals("CONNECTING", dataState);
        dataState = QnsConstants.dataStateToString(TelephonyManager.DATA_DISCONNECTING);
        Assert.assertEquals("DISCONNECTING", dataState);
        dataState = QnsConstants.dataStateToString(TelephonyManager.DATA_SUSPENDED);
        Assert.assertEquals("SUSPENDED", dataState);
        dataState = QnsConstants.dataStateToString(TelephonyManager.DATA_HANDOVER_IN_PROGRESS);
        Assert.assertEquals("HANDOVERINPROGRESS", dataState);
        dataState = QnsConstants.dataStateToString(TelephonyManager.DATA_UNKNOWN);
        Assert.assertEquals("UNKNOWN", dataState);
        dataState = QnsConstants.dataStateToString(123);
        Assert.assertEquals("UNKNOWN(123)", dataState);
    }

    @Test
    public void testCallStateToString() {
        Assert.assertEquals(
                "CALL_STATE_IDLE",
                QnsConstants.callStateToString(TelephonyManager.CALL_STATE_IDLE));
        Assert.assertEquals(
                "CALL_STATE_RINGING",
                QnsConstants.callStateToString(TelephonyManager.CALL_STATE_RINGING));
        Assert.assertEquals(
                "CALL_STATE_OFFHOOK",
                QnsConstants.callStateToString(TelephonyManager.CALL_STATE_OFFHOOK));
        Assert.assertEquals("CALL_STATE_UNKNOWN_-1", QnsConstants.callStateToString(-1));
    }

    @Test
    public void testNetworkTypeToAccessNetworkType() {
        Assert.assertEquals(
                AccessNetworkType.GERAN,
                QnsConstants.networkTypeToAccessNetworkType(TelephonyManager.NETWORK_TYPE_GPRS));
        Assert.assertEquals(
                AccessNetworkType.GERAN,
                QnsConstants.networkTypeToAccessNetworkType(TelephonyManager.NETWORK_TYPE_EDGE));
        Assert.assertEquals(
                AccessNetworkType.GERAN,
                QnsConstants.networkTypeToAccessNetworkType(TelephonyManager.NETWORK_TYPE_GSM));

        Assert.assertEquals(
                AccessNetworkType.UTRAN,
                QnsConstants.networkTypeToAccessNetworkType(TelephonyManager.NETWORK_TYPE_UMTS));
        Assert.assertEquals(
                AccessNetworkType.UTRAN,
                QnsConstants.networkTypeToAccessNetworkType(TelephonyManager.NETWORK_TYPE_HSDPA));
        Assert.assertEquals(
                AccessNetworkType.UTRAN,
                QnsConstants.networkTypeToAccessNetworkType(TelephonyManager.NETWORK_TYPE_HSUPA));
        Assert.assertEquals(
                AccessNetworkType.UTRAN,
                QnsConstants.networkTypeToAccessNetworkType(TelephonyManager.NETWORK_TYPE_HSPAP));
        Assert.assertEquals(
                AccessNetworkType.UTRAN,
                QnsConstants.networkTypeToAccessNetworkType(TelephonyManager.NETWORK_TYPE_HSPA));
        Assert.assertEquals(
                AccessNetworkType.UTRAN,
                QnsConstants.networkTypeToAccessNetworkType(
                        TelephonyManager.NETWORK_TYPE_TD_SCDMA));

        Assert.assertEquals(
                AccessNetworkType.CDMA2000,
                QnsConstants.networkTypeToAccessNetworkType(TelephonyManager.NETWORK_TYPE_1xRTT));
        Assert.assertEquals(
                AccessNetworkType.CDMA2000,
                QnsConstants.networkTypeToAccessNetworkType(TelephonyManager.NETWORK_TYPE_CDMA));
        Assert.assertEquals(
                AccessNetworkType.CDMA2000,
                QnsConstants.networkTypeToAccessNetworkType(TelephonyManager.NETWORK_TYPE_EVDO_0));
        Assert.assertEquals(
                AccessNetworkType.CDMA2000,
                QnsConstants.networkTypeToAccessNetworkType(TelephonyManager.NETWORK_TYPE_EVDO_A));
        Assert.assertEquals(
                AccessNetworkType.CDMA2000,
                QnsConstants.networkTypeToAccessNetworkType(TelephonyManager.NETWORK_TYPE_EVDO_B));
        Assert.assertEquals(
                AccessNetworkType.CDMA2000,
                QnsConstants.networkTypeToAccessNetworkType(TelephonyManager.NETWORK_TYPE_EHRPD));

        Assert.assertEquals(
                AccessNetworkType.EUTRAN,
                QnsConstants.networkTypeToAccessNetworkType(TelephonyManager.NETWORK_TYPE_LTE));
        Assert.assertEquals(
                AccessNetworkType.EUTRAN,
                QnsConstants.networkTypeToAccessNetworkType(TelephonyManager.NETWORK_TYPE_LTE_CA));

        Assert.assertEquals(
                AccessNetworkType.NGRAN,
                QnsConstants.networkTypeToAccessNetworkType(TelephonyManager.NETWORK_TYPE_NR));
        Assert.assertEquals(
                AccessNetworkType.IWLAN,
                QnsConstants.networkTypeToAccessNetworkType(TelephonyManager.NETWORK_TYPE_IWLAN));

        Assert.assertEquals(
                AccessNetworkType.UNKNOWN,
                QnsConstants.networkTypeToAccessNetworkType(TelephonyManager.NETWORK_TYPE_UNKNOWN));
        Assert.assertEquals(
                AccessNetworkType.UNKNOWN, QnsConstants.networkTypeToAccessNetworkType(-1));
    }

    @Test
    public void testQnsSipDialogSessionPolicyToString() {
        String convertedString;

        convertedString = QnsConstants.qnsSipDialogSessionPolicyToString(
                QnsConstants.SIP_DIALOG_SESSION_POLICY_NONE);
        Assert.assertEquals(convertedString, "POLICY_NONE");
        convertedString = QnsConstants.qnsSipDialogSessionPolicyToString(
                QnsConstants.SIP_DIALOG_SESSION_POLICY_FOLLOW_VOICE_CALL);
        Assert.assertEquals(convertedString, "POLICY_VOICE");
        convertedString = QnsConstants.qnsSipDialogSessionPolicyToString(
                QnsConstants.SIP_DIALOG_SESSION_POLICY_FOLLOW_VIDEO_CALL);
        Assert.assertEquals(convertedString, "POLICY_VIDEO");
    }
}
