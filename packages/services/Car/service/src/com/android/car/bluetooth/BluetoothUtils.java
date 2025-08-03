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

package com.android.car.bluetooth;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothA2dpSink;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothMapClient;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothPbapClient;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.le.AdvertisingSetCallback;
import android.os.ParcelUuid;
import android.util.SparseArray;

import java.util.HashMap;
import java.util.List;

/** Utils for Bluetooth */
public final class BluetoothUtils {
    private BluetoothUtils() {
        throw new UnsupportedOperationException();
    }

    public static final String A2DP_SOURCE_CONNECTION_STATE_CHANGED =
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED;
    public static final String A2DP_SINK_CONNECTION_STATE_CHANGED =
            BluetoothA2dpSink.ACTION_CONNECTION_STATE_CHANGED;
    public static final String HFP_CLIENT_CONNECTION_STATE_CHANGED =
            BluetoothHeadsetClient.ACTION_CONNECTION_STATE_CHANGED;
    public static final String MAP_CLIENT_CONNECTION_STATE_CHANGED =
            BluetoothMapClient.ACTION_CONNECTION_STATE_CHANGED;
    public static final String PAN_CONNECTION_STATE_CHANGED =
            BluetoothPan.ACTION_CONNECTION_STATE_CHANGED;
    public static final String PBAP_CLIENT_CONNECTION_STATE_CHANGED =
            BluetoothPbapClient.ACTION_CONNECTION_STATE_CHANGED;

    private static final ParcelUuid[] A2DP_SOURCE_UUIDS =
            new ParcelUuid[]{BluetoothUuid.A2DP_SOURCE};
    private static final ParcelUuid[] A2DP_SINK_UUIDS =
            new ParcelUuid[]{BluetoothUuid.A2DP_SINK};
    private static final ParcelUuid[] HFP_HF_UUIDS =
            new ParcelUuid[]{BluetoothUuid.HFP};
    private static final ParcelUuid[] HFP_AG_UUIDS =
            new ParcelUuid[]{BluetoothUuid.HFP_AG, BluetoothUuid.HSP_AG};
    private static final ParcelUuid[] MAP_CLIENT_UUIDS =
            new ParcelUuid[]{BluetoothUuid.MAP, BluetoothUuid.MNS};
    private static final ParcelUuid[] MAP_SERVER_UUIDS =
            new ParcelUuid[]{BluetoothUuid.MAS};
    private static final ParcelUuid[] PAN_UUIDS =
            new ParcelUuid[]{BluetoothUuid.PANU, BluetoothUuid.NAP};
    private static final ParcelUuid[] PBAP_CLIENT_UUIDS =
            new ParcelUuid[]{BluetoothUuid.PBAP_PCE};
    private static final ParcelUuid[] PBAP_SERVER_UUIDS =
            new ParcelUuid[]{BluetoothUuid.PBAP_PSE};

    /*
     * Maps of types and status to human readable strings
     */

    private static final SparseArray<String> sAdapterStates = new SparseArray<String>(4);
    private static final SparseArray<String> sBondStates = new SparseArray<String>(3);
    private static final SparseArray<String> sConnectionStates = new SparseArray<String>(4);
    private static final SparseArray<String> sScanModes = new SparseArray<String>(3);
    private static final SparseArray<String> sAdvertiseCallbackStatuses =
            new SparseArray<String>(3);
    private static final SparseArray<String> sProfileNames = new SparseArray<String>(6);
    private static final HashMap<String, Integer> sProfileActions = new HashMap<String, Integer>(5);
    static {
        // Bluetooth Adapter states
        sAdapterStates.put(BluetoothAdapter.STATE_ON, "On");
        sAdapterStates.put(BluetoothAdapter.STATE_OFF, "Off");
        sAdapterStates.put(BluetoothAdapter.STATE_TURNING_ON, "Turning On");
        sAdapterStates.put(BluetoothAdapter.STATE_TURNING_OFF, "Turning Off");
        sAdapterStates.put(BluetoothAdapter.ERROR, "Error");

        // Device Bonding states
        sBondStates.put(BluetoothDevice.BOND_BONDED, "Bonded");
        sBondStates.put(BluetoothDevice.BOND_BONDING, "Bonding");
        sBondStates.put(BluetoothDevice.BOND_NONE, "Unbonded");

        // Device and Profile Connection states
        sConnectionStates.put(BluetoothAdapter.STATE_CONNECTED, "Connected");
        sConnectionStates.put(BluetoothAdapter.STATE_DISCONNECTED, "Disconnected");
        sConnectionStates.put(BluetoothAdapter.STATE_CONNECTING, "Connecting");
        sConnectionStates.put(BluetoothAdapter.STATE_DISCONNECTING, "Disconnecting");

        // Scan Mode Names
        sScanModes.put(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE,
                "Connectable/Discoverable");
        sScanModes.put(BluetoothAdapter.SCAN_MODE_CONNECTABLE, "Connectable");
        sScanModes.put(BluetoothAdapter.SCAN_MODE_NONE, "None");
        sScanModes.put(BluetoothAdapter.ERROR, "Error");

        // Advertising Callback Status Codes
        sAdvertiseCallbackStatuses.put(AdvertisingSetCallback.ADVERTISE_FAILED_ALREADY_STARTED,
                "ADVERTISE_FAILED_ALREADY_STARTED");
        sAdvertiseCallbackStatuses.put(AdvertisingSetCallback.ADVERTISE_FAILED_DATA_TOO_LARGE,
                "ADVERTISE_FAILED_DATA_TOO_LARGE");
        sAdvertiseCallbackStatuses.put(AdvertisingSetCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED,
                "ADVERTISE_FAILED_FEATURE_UNSUPPORTED");
        sAdvertiseCallbackStatuses.put(AdvertisingSetCallback.ADVERTISE_FAILED_INTERNAL_ERROR,
                "ADVERTISE_FAILED_INTERNAL_ERROR");
        sAdvertiseCallbackStatuses.put(AdvertisingSetCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS,
                "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS");
        sAdvertiseCallbackStatuses.put(AdvertisingSetCallback.ADVERTISE_SUCCESS,
                "ADVERTISE_SUCCESS");

        // Profile Names
        sProfileNames.put(BluetoothProfile.PAN, "PAN");
        sProfileNames.put(BluetoothProfile.A2DP, "A2DP Source");
        sProfileNames.put(BluetoothProfile.A2DP_SINK, "A2DP Sink");
        sProfileNames.put(BluetoothProfile.AVRCP_CONTROLLER, "AVRCP Controller");
        sProfileNames.put(BluetoothProfile.HEADSET_CLIENT, "HFP Client");
        sProfileNames.put(BluetoothProfile.PBAP_CLIENT, "PBAP Client");
        sProfileNames.put(BluetoothProfile.MAP_CLIENT, "MAP Client");

        // Profile actions to ints
        sProfileActions.put(A2DP_SOURCE_CONNECTION_STATE_CHANGED, BluetoothProfile.A2DP);
        sProfileActions.put(A2DP_SINK_CONNECTION_STATE_CHANGED, BluetoothProfile.A2DP_SINK);
        sProfileActions.put(HFP_CLIENT_CONNECTION_STATE_CHANGED, BluetoothProfile.HEADSET_CLIENT);
        sProfileActions.put(MAP_CLIENT_CONNECTION_STATE_CHANGED, BluetoothProfile.MAP_CLIENT);
        sProfileActions.put(PAN_CONNECTION_STATE_CHANGED, BluetoothProfile.PAN);
        sProfileActions.put(PBAP_CLIENT_CONNECTION_STATE_CHANGED, BluetoothProfile.PBAP_CLIENT);
    }

    static byte[] getBytesFromAddress(String address) {
        int i, j = 0;
        byte[] output = new byte[6]; // 6 byte Bluetooth Address

        for (i = 0; i < address.length(); i++) {
            if (address.charAt(i) != ':') {
                output[j] = (byte) Integer.parseInt(address.substring(i, i + 2), 16 /* base 16 */);
                j++;
                i++;
            }
        }
        return output;
    }


    static String getDeviceDebugInfo(BluetoothDevice device) {
        if (device == null) {
            return "(null)";
        }
        return "(name = " + device.getName() + ", addr = " + device.getAddress() + ")";
    }

    static String getProfileName(int profile) {
        String name = sProfileNames.get(profile, "Unknown");
        return "(" + profile + ") " + name;
    }

    static String getConnectionStateName(int state) {
        String name = sConnectionStates.get(state, "Unknown");
        return "(" + state + ") " + name;
    }

    static String getBondStateName(int state) {
        String name = sBondStates.get(state, "Unknown");
        return "(" + state + ") " + name;
    }

    static String getAdapterStateName(int state) {
        String name = sAdapterStates.get(state, "Unknown");
        return "(" + state + ") " + name;
    }

    static String getScanModeName(int mode) {
        String name = sScanModes.get(mode, "Unknown");
        return "(" + mode + ") " + name;
    }

    static String getAdvertisingCallbackStatusName(int status) {
        String name = sAdvertiseCallbackStatuses.get(status, "Unknown");
        return "(" + status + ") " + name;
    }

    static String getConnectionPolicyName(int priority) {
        String name = "";
        switch (priority) {
            case BluetoothProfile.CONNECTION_POLICY_ALLOWED:
                name = "CONNECTION_POLICY_ALLOWED";
                break;
            case BluetoothProfile.CONNECTION_POLICY_FORBIDDEN:
                name = "CONNECTION_POLICY_FORBIDDEN";
                break;
            case BluetoothProfile.CONNECTION_POLICY_UNKNOWN:
                name = "CONNECTION_POLICY_UNKNOWN";
                break;
            default:
                name = "Unknown";
                break;
        }
        return "(" + priority + ") " + name;
    }

    static int getProfileFromConnectionAction(String action) {
        Integer profile = sProfileActions.get(action);
        return profile != null ? profile.intValue() : -1;
    }

    static boolean isProfileSupported(List<ParcelUuid> localUuids, BluetoothDevice device,
            int profile) {
        if (device == null || localUuids == null || localUuids.isEmpty()) {
            return false;
        }

        ParcelUuid[] ourUuids = localUuids.toArray(new ParcelUuid[localUuids.size()]);
        ParcelUuid[] uuids = device.getUuids();

        if (uuids == null || uuids.length == 0) {
            return false;
        }
        switch (profile) {
            case BluetoothProfile.A2DP:
                return BluetoothUuid.containsAnyUuid(ourUuids, A2DP_SOURCE_UUIDS)
                        && BluetoothUuid.containsAnyUuid(uuids, A2DP_SINK_UUIDS);
            case BluetoothProfile.A2DP_SINK:
                return BluetoothUuid.containsAnyUuid(ourUuids, A2DP_SINK_UUIDS)
                        && BluetoothUuid.containsAnyUuid(uuids, A2DP_SOURCE_UUIDS);
            case BluetoothProfile.HEADSET_CLIENT:
                return BluetoothUuid.containsAnyUuid(ourUuids, HFP_HF_UUIDS)
                        && BluetoothUuid.containsAnyUuid(uuids, HFP_AG_UUIDS);
            case BluetoothProfile.MAP_CLIENT:
                return BluetoothUuid.containsAnyUuid(ourUuids, MAP_CLIENT_UUIDS)
                        && BluetoothUuid.containsAnyUuid(uuids, MAP_SERVER_UUIDS);
            case BluetoothProfile.PAN:
                return BluetoothUuid.containsAnyUuid(ourUuids, PAN_UUIDS)
                        && BluetoothUuid.containsAnyUuid(uuids, PAN_UUIDS);
            case BluetoothProfile.PBAP_CLIENT:
                return BluetoothUuid.containsAnyUuid(ourUuids, PBAP_CLIENT_UUIDS)
                        && BluetoothUuid.containsAnyUuid(uuids, PBAP_SERVER_UUIDS);
            default:
                return false;
        }
    }

    static boolean isAProfileAction(String action) {
        return sProfileActions.containsKey(action);
    }

    static int[] getManagedProfilesIds() {
        int[] profileIds = new int[sProfileActions.size()];
        int i = 0;
        for (HashMap.Entry record : sProfileActions.entrySet()) {
            profileIds[i] = ((Integer) record.getValue()).intValue();
            i += 1;
        }
        return profileIds;
    }
}
