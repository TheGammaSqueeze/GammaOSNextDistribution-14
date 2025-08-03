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

package com.android.server.wifi.p2p;

import android.annotation.NonNull;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pWfdInfo;
import android.util.Log;

import com.android.server.wifi.util.NativeUtil;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Class used for processing all P2P callbacks.
 */
public class SupplicantP2pIfaceCallbackHidlV1_4Impl
        extends android.hardware.wifi.supplicant.V1_4.ISupplicantP2pIfaceCallback.Stub {
    private static final String TAG = "SupplicantP2pIfaceCallbackV1_4Impl";
    private static boolean sVerboseLoggingEnabled = true;

    private final SupplicantP2pIfaceHalHidlImpl mP2pIfaceHal;
    private final String mInterface;
    private final WifiP2pMonitor mMonitor;
    private final SupplicantP2pIfaceHalHidlImpl.SupplicantP2pIfaceCallback mCallbackV10;

    public SupplicantP2pIfaceCallbackHidlV1_4Impl(
            @NonNull SupplicantP2pIfaceHalHidlImpl p2pIfaceHal,
            @NonNull String iface, @NonNull WifiP2pMonitor monitor) {
        mP2pIfaceHal = p2pIfaceHal;
        mInterface = iface;
        mMonitor = monitor;
        // Create an older callback for function delegation,
        // and it would cascadingly create older one.
        mCallbackV10 = mP2pIfaceHal.new SupplicantP2pIfaceCallback(mInterface);
    }

    /**
     * Enable verbose logging for all sub modules.
     */
    public static void enableVerboseLogging(boolean verboseEnabled, boolean halVerboseEnabled) {
        sVerboseLoggingEnabled = verboseEnabled;
    }

    protected static void logd(String s) {
        if (sVerboseLoggingEnabled) Log.d(TAG, s, null);
    }

    /**
     * Used to indicate that a new network has been added.
     *
     * @param networkId Network ID allocated to the corresponding network.
     */
    public void onNetworkAdded(int networkId) {
        mCallbackV10.onNetworkAdded(networkId);
    }

    /**
     * Used to indicate that a network has been removed.
     *
     * @param networkId Network ID allocated to the corresponding network.
     */
    public void onNetworkRemoved(int networkId) {
        mCallbackV10.onNetworkRemoved(networkId);
    }

    /**
     * Used to indicate that a P2P device has been found.
     *
     * @param srcAddress MAC address of the device found. This must either
     *        be the P2P device address or the P2P interface address.
     * @param p2pDeviceAddress P2P device address.
     * @param primaryDeviceType Type of device. Refer to section B.1 of Wifi P2P
     *        Technical specification v1.2.
     * @param deviceName Name of the device.
     * @param configMethods Mask of WPS configuration methods supported by the
     *        device.
     * @param deviceCapabilities Refer to section 4.1.4 of Wifi P2P Technical
     *        specification v1.2.
     * @param groupCapabilities Refer to section 4.1.4 of Wifi P2P Technical
     *        specification v1.2.
     * @param wfdDeviceInfo WFD device info as described in section 5.1.2 of WFD
     *        technical specification v1.0.0.
     */
    public void onDeviceFound(byte[] srcAddress, byte[] p2pDeviceAddress, byte[] primaryDeviceType,
            String deviceName, short configMethods, byte deviceCapabilities, int groupCapabilities,
            byte[] wfdDeviceInfo) {
        mCallbackV10.onDeviceFound(srcAddress, p2pDeviceAddress, primaryDeviceType,
                deviceName, configMethods, deviceCapabilities, groupCapabilities, wfdDeviceInfo);
    }

    /**
     * Used to indicate that a P2P device has been lost.
     *
     * @param p2pDeviceAddress P2P device address.
     */
    public void onDeviceLost(byte[] p2pDeviceAddress) {
        mCallbackV10.onDeviceLost(p2pDeviceAddress);
    }

    /**
     * Used to indicate the termination of P2P find operation.
     */
    public void onFindStopped() {
        mCallbackV10.onFindStopped();
    }

    /**
     * Used to indicate the reception of a P2P Group Owner negotiation request.
     *
     * @param srcAddress MAC address of the device that initiated the GO
     *        negotiation request.
     * @param passwordId Type of password.
     */
    public void onGoNegotiationRequest(byte[] srcAddress, short passwordId) {
        mCallbackV10.onGoNegotiationRequest(srcAddress, passwordId);
    }

    /**
     * Used to indicate the completion of a P2P Group Owner negotiation request.
     *
     * @param status Status of the GO negotiation.
     */
    public void onGoNegotiationCompleted(int status) {
        mCallbackV10.onGoNegotiationCompleted(status);
    }

    /**
     * Used to indicate a successful formation of a P2P group.
     */
    public void onGroupFormationSuccess() {
        mCallbackV10.onGroupFormationSuccess();
    }

    /**
     * Used to indicate a failure to form a P2P group.
     *
     * @param failureReason Failure reason string for debug purposes.
     */
    public void onGroupFormationFailure(String failureReason) {
        mCallbackV10.onGroupFormationFailure(failureReason);
    }

    /**
     * Used to indicate the start of a P2P group.
     *
     * @param groupIfName Interface name of the group. (For ex: p2p-p2p0-1)
     * @param isGo Whether this device is owner of the group.
     * @param ssid SSID of the group.
     * @param frequency Frequency on which this group is created.
     * @param psk PSK used to secure the group.
     * @param passphrase PSK passphrase used to secure the group.
     * @param goDeviceAddress MAC Address of the owner of this group.
     * @param isPersistent Whether this group is persisted or not.
     */
    public void onGroupStarted(String groupIfName, boolean isGo, ArrayList<Byte> ssid,
            int frequency, byte[] psk, String passphrase, byte[] goDeviceAddress,
            boolean isPersistent) {
        mCallbackV10.onGroupStarted(groupIfName, isGo,
                ssid, frequency, psk, passphrase, goDeviceAddress, isPersistent);
    }

    /**
     * Used to indicate the removal of a P2P group.
     *
     * @param groupIfName Interface name of the group. (For ex: p2p-p2p0-1)
     * @param isGo Whether this device is owner of the group.
     */
    public void onGroupRemoved(String groupIfName, boolean isGo) {
        mCallbackV10.onGroupRemoved(groupIfName, isGo);
    }

    /**
     * Used to indicate the reception of a P2P invitation.
     *
     * @param srcAddress MAC address of the device that sent the invitation.
     * @param goDeviceAddress MAC Address of the owner of this group.
     * @param bssid Bssid of the group.
     * @param persistentNetworkId Persistent network Id of the group.
     * @param operatingFrequency Frequency on which the invitation was received.
     */
    public void onInvitationReceived(byte[] srcAddress, byte[] goDeviceAddress,
            byte[] bssid, int persistentNetworkId, int operatingFrequency) {
        mCallbackV10.onInvitationReceived(srcAddress, goDeviceAddress, bssid,
                persistentNetworkId, operatingFrequency);
    }

    /**
     * Used to indicate the result of the P2P invitation request.
     *
     * @param bssid Bssid of the group.
     * @param status Status of the invitation.
     */
    public void onInvitationResult(byte[] bssid, int status) {
        mCallbackV10.onInvitationResult(bssid, status);
    }

    /**
     * Used to indicate the completion of a P2P provision discovery request.
     *
     * @param p2pDeviceAddress P2P device address.
     * @param isRequest Whether we received or sent the provision discovery.
     * @param status Status of the provision discovery (SupplicantStatusCode).
     * @param configMethods Mask of WPS configuration methods supported.
     *                      Only one configMethod bit should be set per call.
     * @param generatedPin 8 digit pin generated.
     */
    public void onProvisionDiscoveryCompleted(byte[] p2pDeviceAddress, boolean isRequest,
            byte status, short configMethods, String generatedPin) {
        mCallbackV10.onProvisionDiscoveryCompleted(p2pDeviceAddress, isRequest,
                status, configMethods, generatedPin);
    }

    /**
     * Used to indicate the reception of a P2P service discovery response.
     *
     * @param srcAddress MAC address of the device that sent the service discovery.
     * @param updateIndicator Service update indicator. Refer to section 3.1.3 of
     *        Wifi P2P Technical specification v1.2.
     * @param tlvs Refer to section 3.1.3.1 of Wifi P2P Technical specification v1.2.
     */
    public void onServiceDiscoveryResponse(byte[] srcAddress, short updateIndicator,
            ArrayList<Byte> tlvs) {
        mCallbackV10.onServiceDiscoveryResponse(srcAddress, updateIndicator, tlvs);
    }

    /**
     * Used to indicate when a STA device is connected to this device.
     *
     * @param srcAddress MAC address of the device that was authorized.
     * @param p2pDeviceAddress P2P device address.
     */
    public void onStaAuthorized(byte[] srcAddress, byte[] p2pDeviceAddress) {
        mCallbackV10.onStaAuthorized(srcAddress, p2pDeviceAddress);
    }

    /**
     * Used to indicate when a STA device is disconnected from this device.
     *
     * @param srcAddress MAC address of the device that was deauthorized.
     * @param p2pDeviceAddress P2P device address.
     */
    public void onStaDeauthorized(byte[] srcAddress, byte[] p2pDeviceAddress) {
        mCallbackV10.onStaDeauthorized(srcAddress, p2pDeviceAddress);
    }

    /**
     * Used to indicate that a P2P WFD R2 device has been found.
     *
     * @param srcAddress MAC address of the device found. This must either
     *        be the P2P device address or the P2P interface address.
     * @param p2pDeviceAddress P2P device address.
     * @param primaryDeviceType Type of device. Refer to section B.1 of Wifi P2P
     *        Technical specification v1.2.
     * @param deviceName Name of the device.
     * @param configMethods Mask of WPS configuration methods supported by the
     *        device.
     * @param deviceCapabilities Refer to section 4.1.4 of Wifi P2P Technical
     *        specification v1.2.
     * @param groupCapabilities Refer to section 4.1.4 of Wifi P2P Technical
     *        specification v1.2.
     * @param wfdDeviceInfo WFD device info as described in section 5.1.2 of WFD
     *        technical specification v1.0.0.
     * @param wfdR2DeviceInfo WFD R2 device info as described in section 5.1.12 of WFD
     *        technical specification v2.1.
     */
    public void onR2DeviceFound(byte[] srcAddress, byte[] p2pDeviceAddress,
            byte[] primaryDeviceType, String deviceName, short configMethods,
            byte deviceCapabilities, int groupCapabilities, byte[] wfdDeviceInfo,
            byte[] wfdR2DeviceInfo) {
        WifiP2pDevice device = new WifiP2pDevice();
        device.deviceName = deviceName;
        if (deviceName == null) {
            Log.e(TAG, "Missing device name.");
            return;
        }

        try {
            device.deviceAddress = NativeUtil.macAddressFromByteArray(p2pDeviceAddress);
        } catch (Exception e) {
            Log.e(TAG, "Could not decode device address.", e);
            return;
        }

        try {
            device.primaryDeviceType = NativeUtil.wpsDevTypeStringFromByteArray(primaryDeviceType);
        } catch (Exception e) {
            Log.e(TAG, "Could not encode device primary type.", e);
            return;
        }

        device.deviceCapability = deviceCapabilities;
        device.groupCapability = groupCapabilities;
        device.wpsConfigMethodsSupported = configMethods;
        device.status = WifiP2pDevice.AVAILABLE;

        if (wfdDeviceInfo != null && wfdDeviceInfo.length >= 6) {
            device.wfdInfo = new WifiP2pWfdInfo(
                    ((wfdDeviceInfo[0] & 0xFF) << 8) + (wfdDeviceInfo[1] & 0xFF),
                    ((wfdDeviceInfo[2] & 0xFF) << 8) + (wfdDeviceInfo[3] & 0xFF),
                    ((wfdDeviceInfo[4] & 0xFF) << 8) + (wfdDeviceInfo[5] & 0xFF));
        }
        if (wfdR2DeviceInfo != null && wfdR2DeviceInfo.length >= 2) {
            device.wfdInfo.setR2DeviceInfo(
                    ((wfdR2DeviceInfo[0] & 0xFF) << 8) + (wfdR2DeviceInfo[1] & 0xFF));
        }

        logd("R2 Device discovered on " + mInterface + ": "
                + device + " R2 Info:" + Arrays.toString(wfdR2DeviceInfo));
        mMonitor.broadcastP2pDeviceFound(mInterface, device);
    }

}

