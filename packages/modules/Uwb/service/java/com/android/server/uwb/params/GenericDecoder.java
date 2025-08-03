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

package com.android.server.uwb.params;

import static com.android.server.uwb.config.CapabilityParam.SUPPORTED_POWER_STATS_QUERY;

import android.util.Log;

import com.android.server.uwb.UwbInjector;

import com.google.uwb.support.aliro.AliroParams;
import com.google.uwb.support.aliro.AliroSpecificationParams;
import com.google.uwb.support.base.Params;
import com.google.uwb.support.base.ProtocolVersion;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccSpecificationParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraSpecificationParams;
import com.google.uwb.support.generic.GenericSpecificationParams;
import com.google.uwb.support.radar.RadarParams;
import com.google.uwb.support.radar.RadarSpecificationParams;

public class GenericDecoder extends TlvDecoder {
    private final UwbInjector mUwbInjector;

    public GenericDecoder(UwbInjector uwbInjector) {
        mUwbInjector = uwbInjector;
    }
    private static final String TAG = "GenericDecoder";

    @Override
    public <T extends Params> T getParams(TlvDecoderBuffer tlvs, Class<T> paramType,
                    ProtocolVersion protocolVersion) {
        if (GenericSpecificationParams.class.equals(paramType)) {
            return (T) getSpecificationParamsFromTlvBuffer(tlvs, protocolVersion);
        }
        return null;
    }

    private GenericSpecificationParams getSpecificationParamsFromTlvBuffer(TlvDecoderBuffer tlvs,
                    ProtocolVersion protocolVersion) {
        GenericSpecificationParams.Builder builder = new GenericSpecificationParams.Builder();
        try {
            FiraSpecificationParams firaSpecificationParams =
                    TlvDecoder.getDecoder(FiraParams.PROTOCOL_NAME, mUwbInjector).getParams(
                            tlvs, FiraSpecificationParams.class, protocolVersion);
            builder.setFiraSpecificationParams(firaSpecificationParams);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to decode FIRA capabilities", e);
        }
        try {
            CccSpecificationParams cccSpecificationParams =
                    TlvDecoder.getDecoder(CccParams.PROTOCOL_NAME, mUwbInjector).getParams(
                            tlvs, CccSpecificationParams.class, protocolVersion);
            builder.setCccSpecificationParams(cccSpecificationParams);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to decode CCC capabilities", e);
        }
        try {
            AliroSpecificationParams aliroSpecificationParams =
                    TlvDecoder.getDecoder(AliroParams.PROTOCOL_NAME, mUwbInjector).getParams(
                            tlvs, AliroSpecificationParams.class, protocolVersion);
            builder.setAliroSpecificationParams(aliroSpecificationParams);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to decode ALIRO capabilities", e);
        }
        try {
            RadarSpecificationParams radarSpecificationParams =
                    TlvDecoder.getDecoder(RadarParams.PROTOCOL_NAME, mUwbInjector)
                            .getParams(tlvs, RadarSpecificationParams.class, protocolVersion);
            builder.setRadarSpecificationParams(radarSpecificationParams);
        } catch (IllegalArgumentException e) {
            Log.v(TAG, "Failed to decode Radar capabilities", e);
        }
        try {
            byte supported_power_stats_query = tlvs.getByte(SUPPORTED_POWER_STATS_QUERY);
            if (supported_power_stats_query != 0) {
                builder.hasPowerStatsSupport(true);
            }
        } catch (IllegalArgumentException e) {
            // Do nothing. By default, hasPowerStatsSupport() returns false.
        }
        return builder.build();
    }
}
