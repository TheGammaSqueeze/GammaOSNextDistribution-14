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
package com.android.server.uwb;

import android.util.Log;
import android.util.Pair;

import com.android.server.uwb.data.UwbConfigStatusData;
import com.android.server.uwb.data.UwbTlvData;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.jni.NativeUwbManager;
import com.android.server.uwb.params.TlvBuffer;
import com.android.server.uwb.params.TlvDecoder;
import com.android.server.uwb.params.TlvDecoderBuffer;
import com.android.server.uwb.params.TlvEncoder;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.base.ProtocolVersion;
import com.google.uwb.support.radar.RadarParams;

public class UwbConfigurationManager {
    private static final String TAG = "UwbConfManager";

    private final NativeUwbManager mNativeUwbManager;
    private final UwbInjector mUwbInjector;

    public UwbConfigurationManager(NativeUwbManager nativeUwbManager, UwbInjector uwbInjector) {
        mNativeUwbManager = nativeUwbManager;
        mUwbInjector = uwbInjector;
    }

    /**
     * Set app configurations.
     */
    public int setAppConfigurations(int sessionId, Params params, String chipId,
                                    ProtocolVersion protocolVersion) {
        int status = UwbUciConstants.STATUS_CODE_FAILED;
        TlvBuffer tlvBuffer = null;

        Log.d(TAG, "setAppConfigurations for protocol: " + params.getProtocolName());
        TlvEncoder encoder = TlvEncoder.getEncoder(params.getProtocolName(), mUwbInjector);
        if (encoder == null) {
            Log.d(TAG, "unsupported encoder protocol type");
            return status;
        }

        tlvBuffer = encoder.getTlvBuffer(params, protocolVersion);

        if (tlvBuffer.getNoOfParams() != 0) {
            byte[] tlvByteArray = tlvBuffer.getByteArray();
            UwbConfigStatusData appConfig;
            if (params.getProtocolName().equals(RadarParams.PROTOCOL_NAME)) {
                appConfig = mNativeUwbManager.setRadarAppConfigurations(sessionId,
                    tlvBuffer.getNoOfParams(),
                    tlvByteArray.length, tlvByteArray, chipId);
            } else {
                appConfig = mNativeUwbManager.setAppConfigurations(sessionId,
                    tlvBuffer.getNoOfParams(),
                    tlvByteArray.length, tlvByteArray, chipId);
            }
            if (appConfig != null) {
                Log.i(TAG, "setAppConfigurations respData: " + appConfig);
                status = appConfig.getStatus();
            } else {
                Log.e(TAG, "appConfigList is null or size of appConfigList is zero");
                status = UwbUciConstants.STATUS_CODE_FAILED;
            }
        } else {
            // Number of reconfig params FiraRangingReconfigureParams can be null
            status = UwbUciConstants.STATUS_CODE_OK;
        }
        return status;
    }

    /**
     * Retrieve app configurations from UWBS.
     */
    public <T extends Params> Pair<Integer, T> getAppConfigurations(int sessionId,
            String protocolName, byte[] appConfigIds, Class<T> paramType, String chipId,
            ProtocolVersion protocolVersion) {

        Log.d(TAG, "getAppConfigurations for protocol: " + protocolName);
        UwbTlvData getAppConfig = mNativeUwbManager.getAppConfigurations(sessionId,
                    appConfigIds.length, appConfigIds.length, appConfigIds, chipId);
        Log.i(TAG, "getAppConfigurations respData: "
                + (getAppConfig != null ? getAppConfig.toString() : "null"));
        return decodeTLV(protocolName, getAppConfig, paramType, protocolVersion);
    }

    /**
     * Retrieve capability information from UWBS.
     */
    public <T extends Params> Pair<Integer, T> getCapsInfo(String protocolName,
            Class<T> paramType, String chipId, ProtocolVersion protocolVersion) {

        Log.d(TAG, "getCapsInfo for protocol: " + protocolName);
        UwbTlvData capsInfo = mNativeUwbManager.getCapsInfo(chipId);
        Log.i(TAG, "getCapsInfo respData: "
                + (capsInfo != null ? capsInfo.toString() : "null"));
        return decodeTLV(protocolName, capsInfo, paramType, protocolVersion);
    }

    /**
     * Common decode TLV function based on protocol
     */
    public <T extends Params> Pair<Integer, T> decodeTLV(String protocolName,
            UwbTlvData tlvData, Class<T> paramType, ProtocolVersion protocolVersion) {
        int status;
        if (tlvData != null) {
            status = tlvData.getStatus();
        } else {
            Log.e(TAG, "TlvData is null or size of TlvData is zero");
            return Pair.create(UwbUciConstants.STATUS_CODE_FAILED, null);
        }
        TlvDecoder decoder = TlvDecoder.getDecoder(protocolName, mUwbInjector);
        if (decoder == null) {
            Log.d(TAG, "unsupported decoder protocol type");
            return Pair.create(status, null);
        }

        int numOfTlvs = tlvData.getLength();
        TlvDecoderBuffer tlvs = new TlvDecoderBuffer(tlvData.getTlv(), numOfTlvs);
        if (!tlvs.parse()) {
            Log.e(TAG, "Failed to parse tlvs");
            return Pair.create(UwbUciConstants.STATUS_CODE_FAILED, null);
        }
        T params = null;
        try {
            params = decoder.getParams(tlvs, paramType, protocolVersion);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to decode", e);
        }
        if (params == null) {
            Log.d(TAG, "Failed to get params from tlvs");
            return Pair.create(UwbUciConstants.STATUS_CODE_FAILED, null);
        }
        return Pair.create(UwbUciConstants.STATUS_CODE_OK, params);
    }
}
