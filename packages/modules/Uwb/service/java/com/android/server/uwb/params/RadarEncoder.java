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

import static com.android.server.uwb.config.ConfigParam.BITS_PER_SAMPLE_KEY;
import static com.android.server.uwb.config.ConfigParam.CHANNEL_NUMBER_KEY;
import static com.android.server.uwb.config.ConfigParam.NUMBER_OF_BURSTS_KEY;
import static com.android.server.uwb.config.ConfigParam.PREAMBLE_CODE_INDEX_KEY;
import static com.android.server.uwb.config.ConfigParam.PREAMBLE_DURATION_KEY;
import static com.android.server.uwb.config.ConfigParam.PRF_MODE_KEY;
import static com.android.server.uwb.config.ConfigParam.RADAR_DATA_TYPE_KEY;
import static com.android.server.uwb.config.ConfigParam.RADAR_TIMING_PARAMS_KEY;
import static com.android.server.uwb.config.ConfigParam.RFRAME_CONFIG_KEY;
import static com.android.server.uwb.config.ConfigParam.SAMPLES_PER_SWEEP_KEY;
import static com.android.server.uwb.config.ConfigParam.SESSION_PRIORITY_KEY;
import static com.android.server.uwb.config.ConfigParam.SWEEP_OFFSET_KEY;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.base.ProtocolVersion;
import com.google.uwb.support.radar.RadarOpenSessionParams;

import java.nio.ByteBuffer;

/** Radar encoder */
public class RadarEncoder extends TlvEncoder {
    @Override
    public TlvBuffer getTlvBuffer(Params param, ProtocolVersion protocolVersion) {
        if (param instanceof RadarOpenSessionParams) {
            return getTlvBufferFromRadarOpenSessionParams(param);
        }
        return null;
    }

    private TlvBuffer getTlvBufferFromRadarOpenSessionParams(Params baseParam) {
        RadarOpenSessionParams params = (RadarOpenSessionParams) baseParam;

        TlvBuffer.Builder tlvBufferBuilder =
                new TlvBuffer.Builder()
                        .putByteArray(
                                RADAR_TIMING_PARAMS_KEY,
                                getRadarTimingParams(params)) // RADAR_TIMING_PARAMS
                        .putByte(
                                SAMPLES_PER_SWEEP_KEY,
                                (byte) params.getSamplesPerSweep()) // SAMPLES_PER_SWEEP
                        .putByte(
                                CHANNEL_NUMBER_KEY,
                                (byte) params.getChannelNumber()) // CHANNEL_NUMBER
                        .putShort(SWEEP_OFFSET_KEY, (short) params.getSweepOffset()) // SWEEP_OFFSET
                        .putByte(
                                RFRAME_CONFIG_KEY, (byte) params.getRframeConfig()) // RFRAME_CONFIG
                        .putByte(
                                PREAMBLE_DURATION_KEY,
                                (byte) params.getPreambleDuration()) // PREAMBLE_DURATION
                        .putByte(
                                PREAMBLE_CODE_INDEX_KEY,
                                (byte) params.getPreambleCodeIndex()) // PREAMBLE_CODE_INDEX
                        .putByte(
                                SESSION_PRIORITY_KEY,
                                (byte) params.getSessionPriority()) // SESSION_PRIORITY
                        .putByte(
                                BITS_PER_SAMPLE_KEY,
                                (byte) params.getBitsPerSample()) // BITS_PER_SAMPLE
                        .putByte(PRF_MODE_KEY, (byte) params.getPrfMode()) // PRF_MODE
                        .putShort(
                                NUMBER_OF_BURSTS_KEY,
                                (short) params.getNumberOfBursts()) // NUMBER_OF_BURSTS
                        .putByte(
                                RADAR_DATA_TYPE_KEY,
                                (byte) params.getRadarDataType()); // RADAR_DATA_TYPE
        return tlvBufferBuilder.build();
    }

    private byte[] getRadarTimingParams(RadarOpenSessionParams params) {
        ByteBuffer buffer = ByteBuffer.allocate(7);
        buffer.put(TlvUtil.getLeBytes(params.getBurstPeriod()));
        buffer.put(TlvUtil.getLeBytes((short) params.getSweepPeriod()));
        buffer.put((byte) params.getSweepsPerBurst());
        return buffer.array();
    }
}
