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

package android.telecom.cts;

import static android.telecom.DisconnectCauseEnum.LOCAL;
import static android.telecom.DisconnectCauseEnum.UNKNOWN;

import static com.google.common.truth.Truth.assertThat;

import android.compat.cts.CompatChangeGatingTestCase;
import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.compatibility.common.util.NonApiTest;
import com.android.os.AtomsProto;
import com.android.os.StatsLog.EventMetricData;
import com.android.os.telecom.EmergencyNumberDialed;
import com.android.os.telecom.TelecomExtensionAtom;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;

import java.util.List;

/** Tests for Telecom metrics atom logging to statsd */
@NonApiTest(
        exemptionReasons = {},
        justification = "METRIC")
public class TelecomHostStatsTest extends CompatChangeGatingTestCase {

    private static final String TELECOM_CTS_TEST_PKG = "android.telecom.cts";
    private static final String FEATURE_TELECOM = "android.software.telecom";
    private static final String FEATURE_TELEPHONY = "android.hardware.telephony";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        super.tearDown();
    }

    // basic verification of CallStateChanged atom
    // being logged to statsd when a call is made
    public void testCallStateChangedAtom_basicTest() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_TELECOM) || !DeviceUtils.hasFeature(
                getDevice(), FEATURE_TELEPHONY)) {
            return;
        }

        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(), TELECOM_CTS_TEST_PKG, AtomsProto.Atom.CALL_STATE_CHANGED_FIELD_NUMBER);

        // run CTS test case for outgoing call
        DeviceUtils.runDeviceTests(
                getDevice(),
                TELECOM_CTS_TEST_PKG,
                ".OutgoingCallTest",
                "testStartCallWithSpeakerphoneTrue_SpeakerphoneOnInCall");

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        // Verify that we have three atoma for  callstatechange
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        AtomsProto.CallStateChanged callStateChangedAtom = null;
        CLog.d("metrics list size: " + data.size());

        assertEquals(data.size(), 3); // DIALING, CONNECTING, DISCONNECTED

        boolean state_dialing = false, state_connecting = false, state_disconnected = false;
        for (EventMetricData d : data) {
            callStateChangedAtom = d.getAtom().getCallStateChanged();

            // common checks
            assertThat(callStateChangedAtom.getEmergencyCall()).isFalse();
            assertThat(callStateChangedAtom.getSelfManaged()).isFalse();
            assertThat(callStateChangedAtom.getExternalCall()).isFalse();
            assertThat(callStateChangedAtom.getExistingCallCount()).isEqualTo(0);
            assertThat(callStateChangedAtom.getHeldCallCount()).isEqualTo(0);

            switch (callStateChangedAtom.getCallState()) {
                case CONNECTING:
                    assertThat(state_connecting).isFalse();
                    state_connecting = true;
                    assertThat(callStateChangedAtom.getDisconnectCause()).isEqualTo(UNKNOWN);
                    break;
                case DIALING:
                    assertThat(state_dialing).isFalse();
                    state_dialing = true;
                    assertThat(callStateChangedAtom.getDisconnectCause()).isEqualTo(UNKNOWN);
                    break;
                case DISCONNECTED:
                    assertThat(state_disconnected).isFalse();
                    state_disconnected = true;
                    assertThat(callStateChangedAtom.getDisconnectCause()).isEqualTo(LOCAL);
                    break;
                default:
            }
        }
        assertThat(state_connecting).isTrue();
        assertThat(state_dialing).isTrue();
        assertThat(state_disconnected).isTrue();
    }

    // Verification for EmergencyNumberDialed atom
    // being logged to statsd when a sos call is made
    public void testEmergencyNumberDialedAtom() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_TELECOM) || !DeviceUtils.hasFeature(
                getDevice(), FEATURE_TELEPHONY)) {
            return;
        }

        ConfigUtils.uploadConfigForPushedAtom(
                getDevice(),
                TELECOM_CTS_TEST_PKG,
                TelecomExtensionAtom.EMERGENCY_NUMBER_DIALED_FIELD_NUMBER);

        DeviceUtils.runDeviceTests(
                getDevice(), TELECOM_CTS_TEST_PKG, ".EmergencyCallTests", "testStartEmergencyCall");

        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        TelecomExtensionAtom.registerAllExtensions(registry);
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(), registry);
        CLog.d("metrics list size: " + data.size());
        assertEquals(data.size(), 1);
        EmergencyNumberDialed emergencyNumberDialedAtom =
                data.get(0).getAtom().getExtension(TelecomExtensionAtom.emergencyNumberDialed);
        assertThat(emergencyNumberDialedAtom.getNumber())
                .isEqualTo("5553637"); // Test emergency number
        assertThat(emergencyNumberDialedAtom.getSystemDialerPackage())
                .isEqualTo(TELECOM_CTS_TEST_PKG);
    }
}
