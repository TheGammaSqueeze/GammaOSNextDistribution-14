/*
 *  Copyright (C) 2023 The Android Open Source Project.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  Provide extensions for the implementation of the Nfc Charging
 */

package com.android.nfc.wlc;

import android.app.Activity;
import android.content.Context;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.sysprop.NfcProperties;
import android.util.Log;

import com.android.nfc.DeviceHost;
import com.android.nfc.DeviceHost.TagEndpoint;
import com.android.nfc.NfcService;

import java.math.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class NfcCharging {
    static final boolean DBG = NfcProperties.debug_enabled().orElse(true);
    private static final String TAG = "NfcWlcChargingActivity";

    static final String VERSION = "1.0.0";

    private Context mContext;
    public static final byte[] WLCCAP = {0x57, 0x4c, 0x43, 0x43, 0x41, 0x50};
    public static final byte[] WLCCTL = {0x57, 0x4c, 0x43, 0x43, 0x54, 0x4C};
    public static final byte[] WLCSTAI = {0x57, 0x4c, 0x43, 0x53, 0x54, 0x41, 0x49};
    public static final byte[] USIWLC = {0x75, 0x73, 0x69, 0x3A, 0x77, 0x6C, 0x63};
    public static final byte[] WLCPI = {0x57, 0x4c, 0x43, 0x49, 0x4e, 0x46};

    public static final String BatteryLevel = "Battery Level";
    public static final String ReceivePower = "Receive Power";
    public static final String ReceiveVoltage = "Receive Voltage";
    public static final String ReceiveCurrent = "Receive Current";
    public static final String TemperatureBattery = "Temperature Battery";
    public static final String TemperatureListener = "Temperature Listener";
    public static final String VendorId = "Vendor Id";
    public static final String State = "State";
    public static final int DISCONNECTED = 0;
    public static final int CONNECTED_NOT_CHARGING = 1;
    public static final int CONNECTED_CHARGING = 2;

    static final byte MODE_REQ_STATIC = 0;
    static final byte MODE_REQ_NEGOTIATED = 1;
    static final byte MODE_REQ_BATTERY_FULL = 2;

    static final int MODE_NON_AUTONOMOUS_WLCP = 0;

    int mWatchdogTimeout = 1;
    int mUpdatedBatteryLevel = -1;
    int mLastState = -1;

    int WLCState = 0;

    // WLCCAP
    int WlcCap_ModeReq = 0;
    int Nwt_max = 0;
    int WlcCap_NegoWait = 0;
    int WlcCap_RdConf = 0;
    int TNdefRdWt = 0;

    int WlcCap_NdefRdWt = 0;
    int WlcCap_CapWt = 0;
    int TCapWt = 0;
    int WlcCap_NdefWrTo = 0;
    int TNdefWrTo = 0;
    int WlcCap_NdefWrWt = 0;
    int TNdefWrWt = 0;

    int mNwcc_retry = 0;
    int mNretry = 0;

    // WLCCTL
    int WlcCtl_ErrorFlag = 0;
    int WlcCtl_BatteryStatus = 0;
    int mCnt = -1;
    int WlcCtl_Cnt_new = 0;
    int WlcCtl_WptReq = 0;
    int WlcCtl_WptDuration = 0;
    int TWptDuration = 0;
    int WlcCtl_WptInfoReq = 0;
    int WlcCtl_PowerAdjReq = 0;
    int WlcCtl_BatteryLevel = 0xFF;
    int WlcCtl_HoldOffWt = 0;
    int THoldOffWt = 0;

    int WlcCtl_ReceivePower = 0;
    int WlcCtl_ReceiveVoltage = 0;
    int WlcCtl_TemperatureBattery = 0;
    int WlcCtl_TemperatureWlcl = 0;

    // WLCINF
    int Ptx = 100;

    // state machine
    private static final int STATE_2 = 0; // Read WLC_CAP
    private static final int STATE_6 = 1; // Static WPT
    private static final int STATE_8 = 2; // Handle NEGO_WAIT
    private static final int STATE_11 = 3; // Write WLCP_INFO
    private static final int STATE_12 = 4; // Read WLCL_CTL
    private static final int STATE_16 = 5; // Read confirmation
    private static final int STATE_17 = 6; // Check WPT requested
    private static final int STATE_21 = 7; // Handle WPT
    private static final int STATE_22 = 8; // Handle INFO_REQ
    private static final int STATE_24 = 9; // Handle removal detection
    private static final int STATE_21_1 = 10; // Handle WPT time completed
    private static final int STATE_21_2 = 11; // Handle FOD detection/removal

    private DeviceHost mNativeNfcManager;
    NdefMessage mNdefMessage;
    byte[] mNdefPayload;
    byte[] mNdefPayload2;
    byte[] mNdefType;
    TagEndpoint TagHandler;

    public boolean NfcChargingOnGoing = false;
    public boolean NfcChargingMode = false;
    public boolean WLCL_Presence = false;

    public boolean mFirstOccurrence = true;

    Map<String, Integer> WlcDeviceInfo = new HashMap<>();

    private native boolean startWlcPowerTransfer(int power_adj_req, int wpt_time_int);

    private native boolean enableWlc(int enable);

    private PresenceCheckWatchdog mWatchdogWlc;

    public NfcCharging(Context context, DeviceHost mDeviceHost) {
        if (DBG) Log.d(TAG, "NfcCharging - Constructor");
        mContext = context;
        mNativeNfcManager = mDeviceHost;

        resetInternalValues();

        mNdefMessage = null;
        mNdefPayload = null;
        mNdefPayload2 = null;
    }

    private static final char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public void resetInternalValues() {
        if (DBG) Log.d(TAG, "resetInternalValues");
        mCnt = -1;
        mNretry = 0;
        WlcCap_ModeReq = 0;
        WlcCtl_BatteryLevel = -1;
        WlcCtl_ReceivePower = -1;
        WlcCtl_ReceiveVoltage = -1;
        WlcCtl_TemperatureBattery = -1;
        WlcCtl_TemperatureWlcl = -1;
        mUpdatedBatteryLevel = -1;
        mLastState = -1;
        WlcDeviceInfo.put(BatteryLevel, -1);
        WlcDeviceInfo.put(ReceivePower, -1);
        WlcDeviceInfo.put(ReceiveVoltage, -1);
        WlcDeviceInfo.put(ReceiveCurrent, -1);
        WlcDeviceInfo.put(TemperatureBattery, -1);
        WlcDeviceInfo.put(TemperatureListener, -1);
        WlcDeviceInfo.put(VendorId, -1);
        WlcDeviceInfo.put(State, -1);

        WlcCtl_ErrorFlag = 0;
        mFirstOccurrence = true;
    }

    DeviceHost.TagDisconnectedCallback callbackTagDisconnection =
            new DeviceHost.TagDisconnectedCallback() {
                @Override
                public void onTagDisconnected(long handle) {
                    Log.d(TAG, "onTagDisconnected");
                    disconnectNfcCharging();
                    WLCState = STATE_2;
                    NfcChargingOnGoing = false;
                    if (WLCL_Presence == true) {
                        WLCL_Presence = false;
                        if (DBG) Log.d(TAG, "Nfc Charging Listener lost");
                    }
                    NfcService.getInstance().sendScreenMessageAfterNfcCharging();
                }
            };

    public boolean startNfcCharging(TagEndpoint t) {
        if (DBG) Log.d(TAG, "startNfcCharging " + VERSION);
        boolean NfcChargingEnabled = false;

        TagHandler = t;
        NfcChargingEnabled = enableWlc(MODE_NON_AUTONOMOUS_WLCP);
        if (DBG) Log.d(TAG, "NfcChargingEnabled is " + NfcChargingEnabled);

        if (NfcChargingEnabled) {
            WLCL_Presence = true;
            WLCState = STATE_2;
            startNfcChargingPresenceChecking(50);
            return true;
        } else {
            return false;
        }
    }

    public void stopNfcCharging() {
        if (DBG) Log.d(TAG, "stopNfcCharging " + VERSION);

        NfcChargingOnGoing = false;
        resetInternalValues();

        mLastState = DISCONNECTED;
        WlcDeviceInfo.put(State, mLastState);
        NfcService.getInstance().onWlcData(WlcDeviceInfo);
        disconnectPresenceCheck();

        NfcChargingMode = false;

        // Restart the polling loop

        TagHandler.disconnect();
        // Disable discovery and restart polling loop only if not screen state change pending
        if (!NfcService.getInstance().sendScreenMessageAfterNfcCharging()) {
            if (DBG) Log.d(TAG, "No pending screen state change, stop Nfc charging presence check");
            stopNfcChargingPresenceChecking();
        }
    }

    public boolean checkWlcCapMsg(NdefMessage ndefMsg) {
        if (DBG) Log.d(TAG, "checkWlcCapMsg: enter");
        boolean status = true;
        NdefRecord[] ndefRecords = null;
        long mDeviceId = 0;
        int mVendorId = 0;
        Byte ControlByte = 0;
        if (ndefMsg != null) {
            mNdefMessage = ndefMsg;
            try {
                ndefRecords = mNdefMessage.getRecords();
                if (ndefRecords != null && ndefRecords.length > 0) {
                    if (DBG)
                        Log.d(TAG, "checkWlcCapMsg: number of ndefRecords = " + ndefRecords.length);
                    mNdefType = ndefRecords[0].getType();

                    if (mNdefType != null) {
                        mNdefPayload = ndefRecords[0].getPayload();
                        if (mNdefPayload != null && mNdefType != null) {
                            if (!Arrays.equals(mNdefType, WLCCAP)) {
                                if (DBG) Log.d(TAG, "NdefType not WLC_CAP");
                                return (status = false);
                            }
                            if (DBG) Log.d(TAG, "mNdefType = " + bytesToHex(mNdefType));
                        } else {
                            return (status = false);
                        }
                    } else {
                        Log.e(TAG, "NdefType null");
                        return (status = false);
                    }
                } else {
                    Log.e(TAG, "ndefRecords == null or ndefRecords.length = 0)");
                    return (status = false);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in getRecords " + e);
                NfcChargingOnGoing = false;
                TagHandler.startPresenceChecking(125, callbackTagDisconnection);
            }

            if ((mNdefPayload[1] & 0xC0) == 0xC0) {
                if (DBG) Log.d(TAG, "Wrong Mode Req");
                return (status = false);
            }

            WlcCap_ModeReq = (mNdefPayload[1] >> 6) & 0x3;
            Nwt_max = (mNdefPayload[1] >> 2) & 0xF;
            WlcCap_NegoWait = (mNdefPayload[1] >> 1) & 0x1;
            if (DBG) Log.d(TAG, "WlcCap_NegoWait = " + WlcCap_NegoWait);
            if (DBG) Log.d(TAG, "Nwt_max = " + Nwt_max);
            WlcCap_RdConf = mNdefPayload[1] & 0x1;

            WlcCap_CapWt = (mNdefPayload[2] & 0x1F);
            if (WlcCap_CapWt > 0x13) WlcCap_CapWt = 0x13;
            TCapWt = (int) Math.pow(2, (WlcCap_CapWt + 3));
            if (TCapWt < 250) TCapWt = 250;
            if (DBG) Log.d(TAG, "TCapWt = " + TCapWt);
            TNdefRdWt = (int) (mNdefPayload[3] & 0xFF) * 10;
            if (mNdefPayload[3] == 0 || mNdefPayload[3] == (byte)0xFF) TNdefRdWt = 2540;
            if (DBG) Log.d(TAG, "TNdefRdWt = " + TNdefRdWt);
            WlcCap_NdefWrTo = mNdefPayload[4];
            if (WlcCap_NdefWrTo == 0 || WlcCap_NdefWrTo > 4) WlcCap_NdefWrTo = 4;
            TNdefWrTo = (int) Math.pow(2, (WlcCap_NdefWrTo + 5));
            if (DBG) Log.d(TAG, "TNdefWrTo = " + TNdefWrTo);
            TNdefWrWt = mNdefPayload[5];
            if (TNdefWrWt > 0x0A) TNdefWrWt = 0x0A;
            if (DBG) Log.d(TAG, "TNdefWrWt = " + TNdefWrWt);

            Log.d(TAG, " " + ndefRecords.length + " NdefRecords");
            if (ndefRecords != null && ndefRecords.length > 1) {
                for (int i = 1; i < ndefRecords.length; i++) {
                    mNdefType = ndefRecords[i].getType();
                    if (DBG) Log.d(TAG, "mNdefType = " + bytesToHex(mNdefType));
                    mNdefPayload2 = ndefRecords[i].getPayload();
                    if (mNdefPayload2 != null && mNdefType != null) {
                        if (Arrays.equals(mNdefType, WLCSTAI)) {
                            checkWlcStaiMsg(mNdefPayload2);
                        } else if (Arrays.equals(mNdefType, USIWLC)) {
                            if (DBG)
                                Log.d(
                                        TAG,
                                        "mNdefPayload USIWLC = "
                                                + bytesToHex(mNdefPayload2)
                                                + " length = "
                                                + mNdefPayload2.length);

                            if (mNdefPayload2.length > 8) {
                                mVendorId = (mNdefPayload2[8] << 8 | mNdefPayload2[7]) >> 4;
                                Log.d(TAG, "VendorId = " + Integer.toHexString(mVendorId));
                                WlcDeviceInfo.put(VendorId, mVendorId);
                                mDeviceId = (long) ((mNdefPayload2[7] & 0x0F)) << 48;
                                for (int j = 6; j > 0; j--) {
                                    mDeviceId |= (long) (mNdefPayload2[j] & 0xFF) << ((j - 1) * 8);
                                }
                                if (DBG) Log.d(TAG, "DeviceId = " + Long.toHexString(mDeviceId));
                            }
                        }
                    }
                }
            }
            NfcChargingOnGoing = true;
        } else {
            status = false;
        }
        if (WlcDeviceInfo.get(BatteryLevel) > (mUpdatedBatteryLevel + 5)) {
            NfcService.getInstance().onWlcData(WlcDeviceInfo);
            mUpdatedBatteryLevel = WlcDeviceInfo.get(BatteryLevel);
        }
        if (DBG) Log.d(TAG, "checkWlcCapMsg: exit, status = " + status);
        return status;
    }

    public boolean checkWlcCtlMsg(NdefMessage mNdefMessage) {
        if (DBG) Log.d(TAG, "checkWlcCtlMsg: enter");

        boolean status = true;
        NdefRecord[] ndefRecords = null;

        if (mNdefMessage != null) {
            if (DBG) Log.d(TAG, "ndefMessage non null");
            try {
                ndefRecords = mNdefMessage.getRecords();
                if (ndefRecords != null && ndefRecords.length > 0) {
                    mNdefType = ndefRecords[0].getType();
                    mNdefPayload = ndefRecords[0].getPayload();
                    if (mNdefPayload != null && mNdefType != null) {
                        if (!Arrays.equals(mNdefType, NfcCharging.WLCCTL)) {
                            return (status = false);
                        }
                        if (DBG) Log.d(TAG, "mNdefType = " + bytesToHex(mNdefType));
                    } else {
                        return (status = false);
                    }
                } else {
                    return (status = false);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in getRecords " + e);
                NfcChargingOnGoing = false;
                TagHandler.startPresenceChecking(125, callbackTagDisconnection);
            }
            WlcCtl_ErrorFlag = (mNdefPayload[0] >> 7);
            WlcCtl_BatteryStatus = (mNdefPayload[0] & 0x18) >> 3;
            WlcCtl_Cnt_new = (mNdefPayload[0] & 0x7);
            WlcCtl_WptReq = (mNdefPayload[1] & 0xC0) >> 6;
            if (WlcCtl_WptReq > 1) WlcCtl_WptReq = 0;

            WlcCtl_WptDuration = (mNdefPayload[1] & 0x3e) >> 1;
            if (WlcCtl_WptDuration > 0x13) WlcCtl_WptReq = 0x13;
            if (DBG) Log.d(TAG, "WlcCtl_WptDuration = " + WlcCtl_WptDuration);
            TWptDuration = (int) Math.pow(2, (WlcCtl_WptDuration + 3));
            WlcCtl_WptInfoReq = (mNdefPayload[1] & 0x1);
            if (WlcCtl_WptReq == 0) WlcCtl_WptInfoReq = 0;

            if ((mNdefPayload[2] <= 0x14) || (mNdefPayload[2] >= (byte)0xF6)) {
                WlcCtl_PowerAdjReq = mNdefPayload[2];
            } else {
                WlcCtl_PowerAdjReq = 0;
            }

            if (DBG) Log.d(TAG, "checkWlcCtlMsg WlcCtl_PowerAdjReq = " + WlcCtl_PowerAdjReq);

            if ((mNdefPayload[3] < 0x64) && (WlcCtl_BatteryStatus == 0x1)) {
                WlcCtl_BatteryLevel = mNdefPayload[3];
                WlcDeviceInfo.put(BatteryLevel, WlcCtl_BatteryLevel);
                if (DBG) Log.d(TAG, "checkWlcCtlMsg WlcCtl_BatteryLevel = " + WlcCtl_BatteryLevel);
            }

            if (mNdefPayload[5] > 0xF) {
                WlcCtl_HoldOffWt = 0xF;
            } else {
                WlcCtl_HoldOffWt = mNdefPayload[5];
            }
            THoldOffWt = (int) WlcCtl_HoldOffWt * 2;

            if (DBG) Log.d(TAG, " " + ndefRecords.length + " NdefRecords");
            if (ndefRecords != null && ndefRecords.length > 1) {
                for (int i = 1; i < ndefRecords.length; i++) {
                    mNdefType = ndefRecords[i].getType();
                    if (DBG) Log.d(TAG, "mNdefType = " + bytesToHex(mNdefType));
                    mNdefPayload2 = ndefRecords[i].getPayload();
                    if (mNdefPayload2 != null && mNdefType != null) {
                        if (Arrays.equals(mNdefType, WLCSTAI)) {
                            checkWlcStaiMsg(mNdefPayload2);
                        }
                    }
                }
            }

        } else {
            status = false;
        }

        if (WlcDeviceInfo.get(BatteryLevel) > (mUpdatedBatteryLevel + 5)) {
            NfcService.getInstance().onWlcData(WlcDeviceInfo);
            mUpdatedBatteryLevel = WlcDeviceInfo.get(BatteryLevel);
        }
        if (DBG) Log.d(TAG, "checkWlcCtlMsg status = " + status);
        return status;
    }

    public void checkWlcStaiMsg(byte[] mPayload) {
        Byte ControlByte = 0;
        if (DBG) Log.d(TAG, "mNdefPayload WLCSTAI = " + bytesToHex(mPayload));
        ControlByte = mPayload[0];
        int pos = 0;
        if (((ControlByte & 0x01) == 0x01) && pos < mPayload.length) {
            pos++;
            WlcCtl_BatteryLevel = mPayload[pos];
            WlcDeviceInfo.put(BatteryLevel, (int) mPayload[pos]);
            if (DBG) Log.d(TAG, "WlcCtl_BatteryLevel = " + WlcDeviceInfo.get(BatteryLevel));
        }
        if (((ControlByte & 0x02) == 0x02) && pos < mPayload.length) {
            pos++;
            WlcCtl_ReceivePower = mPayload[pos];
            WlcDeviceInfo.put(ReceivePower, (int) mPayload[pos]);
        }
        if (((ControlByte & 0x04) == 0x04) && pos < mPayload.length) {
            pos++;
            WlcCtl_ReceiveVoltage = mPayload[pos];
            WlcDeviceInfo.put(ReceiveVoltage, (int) mPayload[pos]);
        }
        if (((ControlByte & 0x08) == 0x08) && pos < mPayload.length) {
            pos++;
            WlcCtl_TemperatureBattery = mPayload[pos];
            WlcDeviceInfo.put(TemperatureBattery, (int) mPayload[pos]);
        }
        if (((ControlByte & 0x10) == 0x10) && pos < mPayload.length) {
            pos++;
            WlcCtl_TemperatureWlcl = mPayload[pos];
            WlcDeviceInfo.put(TemperatureListener, (int) mPayload[pos]);
        }
    }

    public void sendWLCPI(TagEndpoint tag, NdefMessage ndefMsg) {
        NdefMessage WLCP_INFO =
                constructWLCPI(
                        (byte) Ptx,
                        (byte) 0x00,
                        (byte) 0x00,
                        (byte) 0x00,
                        (byte) 0x00,
                        (byte) 0x00);
        if (tag.writeNdef(WLCP_INFO.toByteArray())) {
            Log.d(TAG, "Write NDEF success");
        } else {
            Log.d(TAG, "Write NDEF Error");
        }
    }

    public NdefMessage constructWLCPI(
            byte ptx, byte power_class, byte tps, byte cps, byte nmsi, byte nmsd) {
        byte[] WLCPI_payload = {ptx, power_class, tps, cps, nmsi, nmsd};

        NdefRecord WLCP_INFO_RECORD =
                new NdefRecord(NdefRecord.TNF_WELL_KNOWN, WLCPI, new byte[] {}, WLCPI_payload);

        NdefMessage WLCP_INFO = new NdefMessage(WLCP_INFO_RECORD);

        return WLCP_INFO;
    }

    public void sendEmptyNdef() {
        NdefRecord WLCP_RD_CONF_RECORD = new NdefRecord(NdefRecord.TNF_EMPTY, null, null, null);

        NdefMessage WLCP_RD_CONF = new NdefMessage(WLCP_RD_CONF_RECORD);
        if (TagHandler.writeNdef(WLCP_RD_CONF.toByteArray())) {
            Log.d(TAG, "Write NDEF success");
        } else {
            Log.d(TAG, "Write NDEF Error");
        }
    }

    public synchronized void stopNfcChargingPresenceChecking() {
        if (mWatchdogWlc != null) {
            mWatchdogWlc.end(true);
        }
    }

    public synchronized void startNfcChargingPresenceChecking(int presenceCheckDelay) {
        // Once we start presence checking, we allow the upper layers
        // to know the tag is in the field.
        if (mWatchdogWlc != null) {
            if (DBG) Log.d(TAG, "mWatchDog non null");
        }
        if (mWatchdogWlc == null) {
            if (DBG) Log.d(TAG, "mWatchdogWlc about to start...");
            mWatchdogWlc = new PresenceCheckWatchdog(presenceCheckDelay);
            mWatchdogWlc.start();
        }
    }

    class PresenceCheckWatchdog extends Thread {
        private int watchdogTimeout;

        private boolean isPresent = true;
        private boolean isStopped = false;
        private boolean isPaused = false;
        private boolean doCheck = true;
        private boolean isFull = false;

        public PresenceCheckWatchdog(int presenceCheckDelay) {
            watchdogTimeout = presenceCheckDelay;
        }

        public synchronized void pause() {
            isPaused = true;
            doCheck = false;
            this.notifyAll();
            if (DBG) Log.d(TAG, "pause - isPaused = " + isPaused);
        }

        public synchronized void setTimeout(int timeout) {
            if (DBG) Log.d(TAG, "PresenceCheckWatchdog watchdogTimeout " + timeout);
            watchdogTimeout = timeout;
        }

        public synchronized void full() {
            isFull = true;
            this.notifyAll();
        }

        public synchronized void lost() {
            isPresent = false;
            if (DBG) Log.d(TAG, "PresenceCheckWatchdog isPresent " + isPresent);
            doCheck = false;
            this.notifyAll();
        }

        public synchronized void doResume() {
            isPaused = false;
            // We don't want to resume presence checking immediately,
            // but go through at least one more wait period.
            doCheck = false;
            this.notifyAll();
            if (DBG) Log.d(TAG, "doResume - isPaused = " + isPaused);
        }

        public synchronized void end(boolean disableCallback) {
            isStopped = true;
            if (DBG) Log.d(TAG, "PresenceCheckWatchdog end isStopped = " + isStopped);
            doCheck = false;
            if (disableCallback) {
                //  tagDisconnectedCallback = null;
            }
            this.notifyAll();
        }

        @Override
        public void run() {
            synchronized (this) {
                if (DBG) Log.d(TAG, "Starting WLC flow");
                while (isPresent && !isStopped && !isFull) {
                    if (DBG)
                        Log.d(
                                TAG,
                                "isPresent= "
                                        + isPresent
                                        + " isStopped= "
                                        + isStopped
                                        + " isFull= "
                                        + isFull);
                    try {
                        if (watchdogTimeout > 0) {
                            this.wait(watchdogTimeout);
                        }

                        watchdogTimeout = HandleWLCState();
                        if (DBG) Log.d(TAG, "Next watchdog timeout : " + watchdogTimeout);
                    } catch (InterruptedException e) {
                        // Activity detected, loop
                        if (DBG) Log.d(TAG, "Interrupted thread: " + WLCState);
                    }
                }
            }
            synchronized (NfcCharging.this) {
                isPresent = false;
                NfcChargingOnGoing = false;
                if (DBG)
                    Log.d(
                            TAG,
                            "WLC state machine interrupted, NfcChargingOnGoing is "
                                    + NfcChargingOnGoing);
                resetInternalValues();
            }
            mLastState = DISCONNECTED;
            WlcDeviceInfo.put(State, mLastState);
            NfcService.getInstance().onWlcData(WlcDeviceInfo);
            disconnectPresenceCheck();
            if (DBG) Log.d(TAG, "disconnectPresenceCheck done");

            // Restart the polling loop
            NfcChargingMode = false;
            TagHandler.disconnect();
            // Disable discovery and restart polling loop only if not screen state change pending
            if (!NfcService.getInstance().sendScreenMessageAfterNfcCharging()) {
                if (DBG)
                    Log.d(TAG, "No pending screen state change, stop Nfc charging presence check");
                stopNfcChargingPresenceChecking();
            }

            if (DBG) Log.d(TAG, "Stopping background presence check");
        }
    }

    public boolean disconnectPresenceCheck() {
        boolean result = false;
        PresenceCheckWatchdog watchdog;
        if (DBG) Log.d(TAG, "disconnectPresenceCheck");
        synchronized (this) {
            watchdog = mWatchdogWlc;
        }
        if (watchdog != null) {
            // Watchdog has already disconnected or will do it
            watchdog.end(false);
            synchronized (this) {
                mWatchdogWlc = null;
            }
        }
        result = true;
        return result;
    }

    public int HandleWLCState() {
        int wt = 1;
        switch (WLCState) {
            case STATE_2:
                { // SM2
                    if (DBG)
                        Log.d(
                                TAG,
                                "HandleWLCState: STATE_2 (" + convert_state_2_str(STATE_2) + ")");
                    if (mLastState != CONNECTED_CHARGING) {
                        mLastState = CONNECTED_CHARGING;
                        WlcDeviceInfo.put(State, mLastState);
                        NfcService.getInstance().onWlcData(WlcDeviceInfo);
                    }
                    if (TagHandler != null) {
                        if (!mFirstOccurrence) {
                            mNdefMessage = TagHandler.getNdef();
                        }

                        if (mNdefMessage != null) {
                            if (!mFirstOccurrence) {
                                if (checkWlcCapMsg(mNdefMessage) == false) {
                                    if (mWatchdogWlc != null) {
                                        mWatchdogWlc.lost();
                                    }
                                    WLCL_Presence = false;
                                    Log.d(TAG, " WLC_CAP : Presence Check FAILED ");
                                    break;
                                }
                            } else {
                                mFirstOccurrence = false;
                            }

                            if (WlcCap_ModeReq == MODE_REQ_BATTERY_FULL) {
                                mWatchdogWlc.full();
                                NfcChargingOnGoing = false;
                                if (DBG)
                                    Log.d(
                                            TAG,
                                            "MODE_REQ is BATTERY_FULL, NfcChargingOnGoing is "
                                                    + NfcChargingOnGoing);
                                wt = TCapWt;

                                WLCState = STATE_24;
                                WlcDeviceInfo.put(BatteryLevel, 0x64);
                                mUpdatedBatteryLevel = WlcDeviceInfo.get(BatteryLevel);
                                WlcDeviceInfo.put(State, mLastState);
                                mLastState = CONNECTED_NOT_CHARGING;
                                NfcService.getInstance().onWlcData(WlcDeviceInfo);
                                if (DBG) Log.d(TAG, " Battery full");
                                break;

                            } else if (WlcCap_ModeReq == MODE_REQ_STATIC
                                    || mNativeNfcManager.isMultiTag() == true) {
                                if (DBG) Log.d(TAG, " Static mode");
                                wt = 0; // TCapWt;

                                WLCState = STATE_6;
                                break;

                            } else {
                                if (DBG) Log.d(TAG, " Negotiated mode");
                                wt = 5;

                                WLCState = STATE_8;
                                break;
                            }
                        } else {
                            if (mWatchdogWlc != null) {
                                mWatchdogWlc.lost();
                            }
                            WLCL_Presence = false;
                            if (DBG) Log.d(TAG, " WLC_CAP: Presence Check FAILED");
                        }
                    }
                    break;
                }

            case STATE_6:
                { // SM6
                    if (DBG)
                        Log.d(
                                TAG,
                                "HandleWLCState: STATE_6 (" + convert_state_2_str(STATE_6) + ")");

                    WLCState = STATE_2;
                    wt = TCapWt + 5000;
                    startWlcPowerTransfer(WlcCtl_PowerAdjReq, WlcCap_CapWt);
                    break;
                }

            case STATE_8:
                { // SM8
                    if (DBG)
                        Log.d(
                                TAG,
                                "HandleWLCState: STATE_8 (" + convert_state_2_str(STATE_8) + ")");

                    if (WlcCap_NegoWait == 1) {
                        if (mNretry > Nwt_max) {
                            if (mWatchdogWlc != null) {
                                mWatchdogWlc.lost();
                            }
                            WLCL_Presence = false;
                            if (DBG) Log.d(TAG, " WLCCAP :too much retry, conclude procedure ");
                            WLCState = STATE_2;
                            wt = 1;
                            break;
                        } else {
                            mNretry += 1;
                            if (DBG) Log.d(TAG, "mNretry = " + mNretry);
                            wt = TCapWt;
                            WLCState = STATE_2;
                            break;
                        }
                    }
                    WLCState = STATE_11;
                    wt = 5;

                    break;
                }

            case STATE_11:
                { // SM11
                    if (DBG)
                        Log.d(
                                TAG,
                                "HandleWLCState: STATE_11 (" + convert_state_2_str(STATE_11) + ")");

                    sendWLCPI(TagHandler, null);
                    if (DBG) Log.d(TAG, "end writing WLCP_INFO");
                    wt = TNdefRdWt + 20;
                    WLCState = STATE_12;
                    break;
                }

            case STATE_12:
                { // SM12-SM15
                    if (DBG)
                        Log.d(
                                TAG,
                                "HandleWLCState: STATE_12 (" + convert_state_2_str(STATE_12) + ")");

                    if (TagHandler != null) {
                        mNdefMessage = TagHandler.getNdef();
                        if (mNdefMessage != null) {
                            if (checkWlcCtlMsg(mNdefMessage)) {
                                if (DBG)
                                    Log.d(
                                            TAG,
                                            " WlcCtl_Cnt_new: "
                                                    + WlcCtl_Cnt_new
                                                    + "(mCnt +1)%8) = "
                                                    + ((mCnt + 1) % 7));

                                if (mCnt == -1) {
                                    mCnt = WlcCtl_Cnt_new;
                                } else if (WlcCtl_Cnt_new == mCnt) {
                                    if (mNwcc_retry < 3) {
                                        wt = 30; // Twcc,retry
                                        mNwcc_retry++;
                                        break;
                                    } else if (mNwcc_retry == 3) {
                                        // go to error
                                        if (DBG) Log.d(TAG, " WLCL_CTL : Max mNwcc_retry reached");
                                        mNwcc_retry = 0;
                                        if (mWatchdogWlc != null) {
                                            mWatchdogWlc.lost();
                                        }
                                        break;
                                    }
                                }
                                mNwcc_retry = 0;
                                mCnt = WlcCtl_Cnt_new;
                                if (WlcCap_RdConf == 1) {
                                    WLCState = STATE_16;
                                    wt = TNdefWrWt;
                                    break;
                                }
                                wt = 1;
                                WLCState = STATE_17;
                            } else {
                                if (mNwcc_retry < 3) {
                                    wt = 30; // Twcc,retry
                                    mNwcc_retry++;
                                    break;
                                } else if (mNwcc_retry == 3) {
                                    // go to error
                                    if (DBG)
                                        Log.d(TAG, " WLCL_CTL not valid: Max mNwcc_retry reached");
                                    mNwcc_retry = 0;
                                    if (mWatchdogWlc != null) {
                                        mWatchdogWlc.lost();
                                    }
                                    break;
                                }

                                WLCL_Presence = false;
                                if (DBG) Log.d(TAG, " WLCL_CTL : Presence Check Failed ");
                            }
                        } else {
                            // no more tag
                            if (mWatchdogWlc != null) {
                                mWatchdogWlc.lost();
                            }
                            WLCL_Presence = false;
                            if (DBG) Log.d(TAG, " WLCL_CTL : Presence Check Failed ");
                        }
                    } else {
                        // conclude - go to error
                    }
                    break;
                }

            case STATE_16:
                { // SM16
                    if (DBG)
                        Log.d(
                                TAG,
                                "HandleWLCState: STATE_16 (" + convert_state_2_str(STATE_16) + ")");

                    sendEmptyNdef();
                    WLCState = STATE_17;
                    wt = 1;
                    break;
                }

            case STATE_17:
                { // SM17
                    if (DBG)
                        Log.d(
                                TAG,
                                "HandleWLCState: STATE_17 (" + convert_state_2_str(STATE_17) + ")");

                    if (WlcCtl_WptReq == 0x0) {
                        // No Power transfer Required
                        if (DBG) Log.d(TAG, "No power transfer required");
                        // go to presence check SM24
                        WLCState = STATE_24;
                        wt = TWptDuration;
                        if (TWptDuration > 4000) {
                            TagHandler.startPresenceChecking(200, callbackTagDisconnection);
                        }
                        break;
                    }

                    // Adjust WPT
                    WLCState = STATE_21;
                    wt = 1 + THoldOffWt;
                    break;
                }

            case STATE_21:
                { // SM21
                    if (DBG)
                        Log.d(
                                TAG,
                                "HandleWLCState: STATE_21 (" + convert_state_2_str(STATE_21) + ")");

                    startWlcPowerTransfer(WlcCtl_PowerAdjReq, WlcCtl_WptDuration);
                    WLCState = STATE_22;
                    wt = TWptDuration + 5000;
                    break;
                }

            case STATE_22:
                { // SM22
                    if (DBG)
                        Log.d(
                                TAG,
                                "HandleWLCState: STATE_22 (" + convert_state_2_str(STATE_22) + ")");

                    if (WlcCtl_WptInfoReq == 1) {
                        WLCState = STATE_11;
                        break;
                    }
                    WLCState = STATE_12;
                    wt = 0;
                    break;
                }

            case STATE_24:
                { // SM24
                    if (DBG)
                        Log.d(
                                TAG,
                                "HandleWLCState: STATE_24 (" + convert_state_2_str(STATE_24) + ")");

                    TagHandler.stopPresenceChecking();
                    WLCState = STATE_2;
                    NfcChargingOnGoing = false;
                    if (mWatchdogWlc != null) {
                        mWatchdogWlc.lost();
                    }
                    wt = 1;
                    break;
                }
            case STATE_21_1:
                { // Stop WPT
                    if (DBG) Log.d(TAG, "HandleWLCState Time completed");
                    WLCState = STATE_22;
                    wt = 0;
                    break;
                }
            case STATE_21_2:
                { // Stop WPT
                    if (DBG) Log.d(TAG, "HandleWLCState: STATE_21_2 (exit)");
                    WLCState = STATE_2;
                    NfcChargingOnGoing = false;

                    if (mWatchdogWlc != null) {
                        mWatchdogWlc.lost();
                    }
                    wt = 0;
                    break;
                }
        }

        return wt;
    }

    public void disconnectNfcCharging() {
        Log.d(TAG, "disconnectNfcCharging");
        NfcChargingOnGoing = false;
        NfcChargingMode = false;
        resetInternalValues();
        disconnectPresenceCheck();
        if (TagHandler != null) {
            TagHandler.disconnect();
        }
    }

    public void onWlcStopped(int wpt_end_condition) {
        Log.d(TAG, "onWlcStopped");

        switch (wpt_end_condition) {
            case 0x0:
                // Time completed
                mWatchdogWlc.setTimeout(0);
                if (WlcCap_ModeReq == MODE_REQ_NEGOTIATED) {
                    WLCState = STATE_21_1;
                } else {
                    WLCState = STATE_2;
                }
                mWatchdogWlc.interrupt();
                if (DBG) Log.d(TAG, "Time completed");
                break;

            case 0x1:
                // FOD detection or Removal
                mWatchdogWlc.setTimeout(0);
                if (WlcCap_ModeReq == MODE_REQ_NEGOTIATED) {
                    WLCState = STATE_21_2;
                } else {
                    WLCState = STATE_2;
                }
                mWatchdogWlc.interrupt();
                if (DBG) Log.d(TAG, "FOD detection or removal");
                break;

            case 0x3:
            default:
                // Error
                mWatchdogWlc.setTimeout(0);
                WLCState = STATE_21_2;
                mWatchdogWlc.interrupt();
                if (DBG) Log.d(TAG, "FOD error detection");
                break;
        }
    }

    public String convert_state_2_str(int state) {
        switch (state) {
            case STATE_2:
                return "Read WLC_CAP";
            case STATE_6:
                return "Static WPT";
            case STATE_8:
                return "Handle NEGO_WAIT?";
            case STATE_11:
                return "Write WLCP_INFO";
            case STATE_12:
                return "Read WLCL_CTL";
            case STATE_16:
                return "Read confirmation?";
            case STATE_17:
                return "Check WPT requested?";
            case STATE_21:
                return "Handle WPT";
            case STATE_22:
                return "Handle INFO_REQ?";
            case STATE_24:
                return "Handle removal detection";
            case STATE_21_1:
                return "Handle WPT time completed";
            case STATE_21_2:
                return "Handle FOD detection/removal";

            default:
                return "Unknown";
        }
    }
}
