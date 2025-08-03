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

package com.google.uwb.support.radar;

import static com.google.uwb.support.fira.FiraParams.PREAMBLE_DURATION_T32_SYMBOLS;
import static com.google.uwb.support.fira.FiraParams.PREAMBLE_DURATION_T64_SYMBOLS;

import android.os.PersistableBundle;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;

import com.google.uwb.support.base.FlagEnum;
import com.google.uwb.support.base.Params;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Defines parameters for radar operation */
public abstract class RadarParams extends Params {
    public static final String PROTOCOL_NAME = "radar";

    @Override
    public final String getProtocolName() {
        return PROTOCOL_NAME;
    }

    /** Checks if the {@link PersistableBundle} is based on the radar protocol. */
    public static boolean isCorrectProtocol(PersistableBundle bundle) {
        return isProtocol(bundle, PROTOCOL_NAME);
    }

    /** Checks if the protocolName is radar . */
    public static boolean isCorrectProtocol(String protocolName) {
        return protocolName.equals(PROTOCOL_NAME);
    }

    /** Session Type */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {SESSION_TYPE_RADAR})
    public @interface SessionType {}

    public static final int SESSION_TYPE_RADAR = 0xA1;

    @Retention(RetentionPolicy.SOURCE)
    @IntRange(from = 0)
    public @interface BurstPeriod {}

    @Retention(RetentionPolicy.SOURCE)
    @IntRange(from = 0, to = 65535)
    public @interface SweepPeriod {}

    @Retention(RetentionPolicy.SOURCE)
    @IntRange(from = 0, to = 255)
    public @interface SweepsPerBurst {}

    @Retention(RetentionPolicy.SOURCE)
    @IntRange(from = 0)
    public @interface SamplesPerSweep {}

    public static final int SAMPLES_PER_SWEEP_DEFAULT = 64;

    @Retention(RetentionPolicy.SOURCE)
    @IntRange(from = -32768, to = 32767)
    public @interface SweepOffset {}

    public static final int SWEEP_OFFSET_DEFAULT = 0;

    /** Preamble duration: Default is 128 symbols */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                PREAMBLE_DURATION_T32_SYMBOLS,
                PREAMBLE_DURATION_T64_SYMBOLS,
                PREAMBLE_DURATION_T128_SYMBOLS,
                PREAMBLE_DURATION_T256_SYMBOLS,
                PREAMBLE_DURATION_T512_SYMBOLS,
                PREAMBLE_DURATION_T1024_SYMBOLS,
                PREAMBLE_DURATION_T2048_SYMBOLS,
                PREAMBLE_DURATION_T4096_SYMBOLS,
                PREAMBLE_DURATION_T8192_SYMBOLS,
                PREAMBLE_DURATION_T16384_SYMBOLS,
                PREAMBLE_DURATION_T32768_SYMBOLS,
            })
    public @interface PreambleDuration {}

    public static final int PREAMBLE_DURATION_T128_SYMBOLS = 0x2;
    public static final int PREAMBLE_DURATION_T256_SYMBOLS = 0x3;
    public static final int PREAMBLE_DURATION_T512_SYMBOLS = 0x4;
    public static final int PREAMBLE_DURATION_T1024_SYMBOLS = 0x5;
    public static final int PREAMBLE_DURATION_T2048_SYMBOLS = 0x6;
    public static final int PREAMBLE_DURATION_T4096_SYMBOLS = 0x7;
    public static final int PREAMBLE_DURATION_T8192_SYMBOLS = 0x8;
    public static final int PREAMBLE_DURATION_T16384_SYMBOLS = 0x9;
    public static final int PREAMBLE_DURATION_T32768_SYMBOLS = 0xA;

    /** UWB Channel selections */
    @Retention(RetentionPolicy.SOURCE)
    @IntRange(from = 9, to = 127)
    public @interface PreambleCodeIndex {}

    @Retention(RetentionPolicy.SOURCE)
    @IntRange(from = 1, to = 255)
    public @interface SessionPriority {}

    public static final int SESSION_PRIORITY_DEFAULT = 50;

    /** Unlimited number of bursts */
    public static final int NUMBER_OF_BURSTS_DEFAULT = 0;

    /** Bits Per Sample (details below) */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                BITS_PER_SAMPLES_32,
                BITS_PER_SAMPLES_48,
                BITS_PER_SAMPLES_64,
            })
    public @interface BitsPerSample {}

    public static final int BITS_PER_SAMPLES_32 = 0x0;
    public static final int BITS_PER_SAMPLES_48 = 0x1;
    public static final int BITS_PER_SAMPLES_64 = 0x2;

    @Retention(RetentionPolicy.SOURCE)
    @IntRange(from = 0, to = 65535)
    public @interface NumberOfBursts {}

    /** Radar Data Type (details below) */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES,
            })
    public @interface RadarDataType {}

    public static final int RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES = 0;

    public enum RadarCapabilityFlag implements FlagEnum {
        HAS_RADAR_SWEEP_SAMPLES_SUPPORT(1);

        private final long mValue;

        RadarCapabilityFlag(long value) {
            mValue = value;
        }

        @Override
        public long getValue() {
            return mValue;
        }
    }
}
