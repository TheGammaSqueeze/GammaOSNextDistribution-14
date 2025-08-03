/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi.aware;

import android.net.wifi.aware.Characteristics;
import android.os.Bundle;

import com.android.server.wifi.DeviceConfigFacade;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A container class for Aware (vendor) implementation capabilities (or
 * limitations). Filled-in by the firmware.
 */
public class Capabilities {
    public int maxConcurrentAwareClusters;
    public int maxPublishes;
    public int maxSubscribes;
    public int maxServiceNameLen;
    public int maxMatchFilterLen;
    public int maxTotalMatchFilterLen;
    public int maxServiceSpecificInfoLen;
    public int maxExtendedServiceSpecificInfoLen;
    public int maxNdiInterfaces;
    public int maxNdpSessions;
    public int maxAppInfoLen;
    public int maxQueuedTransmitMessages;
    public int maxSubscribeInterfaceAddresses;
    public int supportedDataPathCipherSuites;
    public int supportedPairingCipherSuites;
    public boolean isInstantCommunicationModeSupported;
    public boolean isNanPairingSupported;
    public boolean isSetClusterIdSupported;
    public boolean isSuspensionSupported;
    public boolean is6gSupported;
    public boolean isHeSupported;

    /**
     * Converts the internal capabilities to a parcelable & potentially app-facing
     * characteristics bundle. Only some of the information is exposed.
     */
    public Characteristics toPublicCharacteristics(DeviceConfigFacade deviceConfigFacade) {
        Bundle bundle = new Bundle();
        bundle.putInt(Characteristics.KEY_MAX_SERVICE_NAME_LENGTH, maxServiceNameLen);
        bundle.putInt(
                Characteristics.KEY_MAX_SERVICE_SPECIFIC_INFO_LENGTH,
                Math.max(maxExtendedServiceSpecificInfoLen, maxServiceSpecificInfoLen));
        bundle.putInt(Characteristics.KEY_MAX_MATCH_FILTER_LENGTH, maxMatchFilterLen);
        bundle.putInt(Characteristics.KEY_SUPPORTED_DATA_PATH_CIPHER_SUITES,
                supportedDataPathCipherSuites);
        bundle.putInt(Characteristics.KEY_SUPPORTED_PAIRING_CIPHER_SUITES,
                supportedPairingCipherSuites);
        bundle.putBoolean(Characteristics.KEY_IS_INSTANT_COMMUNICATION_MODE_SUPPORTED,
                isInstantCommunicationModeSupported);
        bundle.putInt(Characteristics.KEY_MAX_NDP_NUMBER, maxNdpSessions);
        bundle.putInt(Characteristics.KEY_MAX_NDI_NUMBER, maxNdiInterfaces);
        bundle.putInt(Characteristics.KEY_MAX_PUBLISH_NUMBER, maxPublishes);
        bundle.putInt(Characteristics.KEY_MAX_SUBSCRIBE_NUMBER, maxSubscribes);
        bundle.putBoolean(Characteristics.KEY_SUPPORT_NAN_PAIRING, isNanPairingSupported);
        bundle.putBoolean(Characteristics.KEY_SUPPORT_SUSPENSION,
                deviceConfigFacade.isAwareSuspensionEnabled() && isSuspensionSupported);
        return new Characteristics(bundle);
    }

    JSONObject toJSON() throws JSONException {
        JSONObject j = new JSONObject();
        j.put("maxConcurrentAwareClusters", maxConcurrentAwareClusters);
        j.put("maxPublishes", maxPublishes);
        j.put("maxSubscribes", maxSubscribes);
        j.put("maxServiceNameLen", maxServiceNameLen);
        j.put("maxMatchFilterLen", maxMatchFilterLen);
        j.put("maxServiceSpecificInfoLen", maxServiceSpecificInfoLen);
        j.put("maxExtendedServiceSpecificInfoLen", maxExtendedServiceSpecificInfoLen);
        j.put("maxNdiInterfaces", maxNdiInterfaces);
        j.put("maxNdpSessions", maxNdpSessions);
        j.put("maxAppInfoLen", maxAppInfoLen);
        j.put("maxQueuedTransmitMessages", maxQueuedTransmitMessages);
        j.put("maxSubscribeInterfaceAddresses", maxSubscribeInterfaceAddresses);
        j.put("supportedCipherSuites", supportedDataPathCipherSuites);
        j.put("isInstantCommunicationModeSupported", isInstantCommunicationModeSupported);
        j.put("isSetClusterIdSupported", isSetClusterIdSupported);
        j.put("isNanPairingSupported", isNanPairingSupported);
        j.put("isSuspensionSupported", isSuspensionSupported);
        return j;
    }

    @Override
    public String toString() {
        return "Capabilities [maxConcurrentAwareClusters="
                + maxConcurrentAwareClusters
                + ", maxPublishes="
                + maxPublishes
                + ", maxSubscribes="
                + maxSubscribes
                + ", maxServiceNameLen="
                + maxServiceNameLen
                + ", maxMatchFilterLen="
                + maxMatchFilterLen
                + ", maxTotalMatchFilterLen="
                + maxTotalMatchFilterLen
                + ", maxServiceSpecificInfoLen="
                + maxServiceSpecificInfoLen
                + ", maxExtendedServiceSpecificInfoLen="
                + maxExtendedServiceSpecificInfoLen
                + ", maxNdiInterfaces="
                + maxNdiInterfaces
                + ", maxNdpSessions="
                + maxNdpSessions
                + ", maxAppInfoLen="
                + maxAppInfoLen
                + ", maxQueuedTransmitMessages="
                + maxQueuedTransmitMessages
                + ", maxSubscribeInterfaceAddresses="
                + maxSubscribeInterfaceAddresses
                + ", supportedCipherSuites="
                + supportedDataPathCipherSuites
                + ", isInstantCommunicationModeSupport="
                + isInstantCommunicationModeSupported
                + ", isNanPairingSupported="
                + isNanPairingSupported
                + ", isSetClusterIdSupported="
                + isSetClusterIdSupported
                + ", isSuspensionSupported="
                + isSuspensionSupported
                + ", is6gSupported="
                + is6gSupported
                + ", isHeSupported="
                + isHeSupported
                + "]";
    }
}
