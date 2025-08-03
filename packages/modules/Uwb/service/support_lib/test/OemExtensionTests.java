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

import static com.google.uwb.support.fira.FiraParams.MULTI_NODE_MODE_UNICAST;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_ROLE_INITIATOR;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_CONTROLLER;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;

import static org.junit.Assert.assertEquals;

import android.uwb.UwbAddress;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.oemextension.AdvertisePointedTarget;
import com.google.uwb.support.oemextension.DeviceStatus;
import com.google.uwb.support.oemextension.RangingReportMetadata;
import com.google.uwb.support.oemextension.SessionConfigParams;
import com.google.uwb.support.oemextension.SessionStatus;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class OemExtensionTests {

    @Test
    public void testDeviceState() {
        int state = 1;
        String chipId = "TEST_CHIP_ID";

        DeviceStatus deviceState = new DeviceStatus.Builder()
                .setDeviceState(state)
                .setChipId(chipId)
                .build();

        assertEquals(deviceState.getDeviceState(), state);
        assertEquals(deviceState.getChipId(), chipId);

        DeviceStatus fromBundle = DeviceStatus.fromBundle(deviceState.toBundle());

        assertEquals(fromBundle.getDeviceState(), state);
        assertEquals(fromBundle.getChipId(), chipId);
    }

    @Test
    public void testSessionStatus() {
        long sessionId = 1;
        int state = 0;
        int reasonCode = 0;
        String appPackageName = "test_app";
        int sessionToken = 1000;

        SessionStatus sessionStatus = new SessionStatus.Builder()
                .setSessionId(sessionId)
                .setState(state)
                .setReasonCode(reasonCode)
                .setAppPackageName(appPackageName)
                .setSessiontoken(sessionToken)
                .build();

        assertEquals(sessionStatus.getSessionId(), sessionId);
        assertEquals(sessionStatus.getState(), state);
        assertEquals(sessionStatus.getReasonCode(), reasonCode);
        assertEquals(sessionStatus.getAppPackageName(), appPackageName);
        assertEquals(sessionStatus.getSessionToken(), sessionToken);

        SessionStatus fromBundle = SessionStatus.fromBundle(sessionStatus.toBundle());

        assertEquals(fromBundle.getSessionId(), sessionId);
        assertEquals(fromBundle.getState(), state);
        assertEquals(fromBundle.getReasonCode(), reasonCode);
        assertEquals(fromBundle.getAppPackageName(), appPackageName);
        assertEquals(fromBundle.getSessionToken(), sessionToken);
    }

    @Test
    public void testRangingReportMetadata() {
        byte[] testRawDataNtf = {0x0a, 0x0b, 0x10, 0x20, 0x6f};
        long sessionId = 3;
        RangingReportMetadata rangingReportMetadata = new RangingReportMetadata.Builder()
                .setSessionId(sessionId)
                .setRawNtfData(testRawDataNtf)
                .build();

        assertEquals(rangingReportMetadata.getRawNtfData(), testRawDataNtf);

        RangingReportMetadata fromBundle = RangingReportMetadata
                .fromBundle(rangingReportMetadata.toBundle());

        assertEquals(fromBundle.getSessionId(), sessionId);
        assertEquals(Arrays.toString(fromBundle.getRawNtfData()), Arrays.toString(testRawDataNtf));
    }

    @Test
    public void testAdvertisePointedTarget() {
        byte[] macAddress = {0x0a, 0x0b};
        boolean advertisePointingResult = true;

        AdvertisePointedTarget advertisePointedTarget = new AdvertisePointedTarget.Builder()
                .setMacAddress(macAddress)
                .setAdvertisePointingResult(advertisePointingResult)
                .build();
        assertEquals(advertisePointedTarget.getMacAddress().toBytes(), macAddress);
        assertEquals(advertisePointedTarget.isAdvertisePointingResult(), advertisePointingResult);

        AdvertisePointedTarget fromBundle = AdvertisePointedTarget.fromBundle(
                advertisePointedTarget.toBundle());

        assertEquals(Arrays.toString(fromBundle.getMacAddress().toBytes()),
                Arrays.toString(macAddress));
        assertEquals(fromBundle.isAdvertisePointingResult(), advertisePointingResult);
    }

    @Test
    public void testSessionConfigParams() {
        long sessionId = 100;
        int sessionToken = 50;
        FiraOpenSessionParams firaOpenSessionParams = new FiraOpenSessionParams.Builder()
                .setProtocolVersion(FiraParams.PROTOCOL_VERSION_1_1)
                .setSessionId((int) sessionId)
                .setSessionType(FiraParams.SESSION_TYPE_RANGING)
                .setChannelNumber(FiraParams.UWB_CHANNEL_9)
                .setDeviceType(RANGING_DEVICE_TYPE_CONTROLLER)
                .setDeviceRole(RANGING_DEVICE_ROLE_INITIATOR)
                .setDeviceAddress(UwbAddress.fromBytes(new byte[]{0x4, 0x6}))
                .setDestAddressList(Arrays.asList(UwbAddress.fromBytes(new byte[]{0x4, 0x6})))
                .setMultiNodeMode(MULTI_NODE_MODE_UNICAST)
                .setRangingRoundUsage(RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE)
                .setVendorId(new byte[]{0x8, 0x7})
                .setStaticStsIV(new byte[]{0x1, 0x2, 0x3, 0x4, 0x5, 0x6})
                .build();

        SessionConfigParams sessionConfigParams = new SessionConfigParams.Builder()
                .setSessionId(sessionId)
                .setSessiontoken(sessionToken)
                .setOpenSessionParamsBundle(firaOpenSessionParams.toBundle())
                .build();

        assertEquals(sessionConfigParams.getSessionId(), sessionId);
        assertEquals(sessionConfigParams.getSessionToken(), sessionToken);

        SessionConfigParams fromBundle = SessionConfigParams.fromBundle(
                sessionConfigParams.toBundle());

        assertEquals(fromBundle.getSessionId(), sessionId);
        assertEquals(fromBundle.getSessionToken(), sessionToken);
        FiraOpenSessionParams params = FiraOpenSessionParams.fromBundle(
                fromBundle.getFiraOpenSessionParamsBundle());
        assertEquals(params.getSessionId(), (int) sessionId);
        assertEquals(params.getDeviceRole(), RANGING_DEVICE_ROLE_INITIATOR);
        assertEquals(params.getRangingRoundUsage(), RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE);
    }
}
