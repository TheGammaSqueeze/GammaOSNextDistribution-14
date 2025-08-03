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

package com.android.server.uwb.params;

import static com.android.server.uwb.config.CapabilityParam.RADAR_SUPPORT;
import static com.android.server.uwb.config.CapabilityParam.RADAR_SWEEP_SAMPLES_SUPPORTED;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.base.ProtocolVersion;
import com.google.uwb.support.radar.RadarParams;
import com.google.uwb.support.radar.RadarSpecificationParams;

/** Radar decoder */
public class RadarDecoder extends TlvDecoder {
    @Override
    public <T extends Params> T getParams(TlvDecoderBuffer tlvs, Class<T> paramsType,
            ProtocolVersion protocolVersion)
            throws IllegalArgumentException {
        if (RadarSpecificationParams.class.equals(paramsType)) {
            return (T) getRadarSpecificationParamsFromTlvBuffer(tlvs);
        }
        return null;
    }

    private static boolean isBitSet(int flags, int mask) {
        return (flags & mask) != 0;
    }

    private RadarSpecificationParams getRadarSpecificationParamsFromTlvBuffer(
            TlvDecoderBuffer tlvs) {
        RadarSpecificationParams.Builder builder = new RadarSpecificationParams.Builder();

        byte radarCapabilities = tlvs.getByte(RADAR_SUPPORT);
        if (isBitSet(radarCapabilities, RADAR_SWEEP_SAMPLES_SUPPORTED)) {
            builder.addRadarCapability(
                    RadarParams.RadarCapabilityFlag.HAS_RADAR_SWEEP_SAMPLES_SUPPORT);
        }
        return builder.build();
    }
}
