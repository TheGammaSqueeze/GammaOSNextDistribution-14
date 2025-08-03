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

package com.google.uwb.support.ccc;

import android.os.Build.VERSION_CODES;
import android.os.PersistableBundle;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.RequiresApi;

import com.google.uwb.support.base.Params;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Defines parameters for CCC operation */
@RequiresApi(VERSION_CODES.LOLLIPOP)
public abstract class CccParams extends Params {
    public static final String PROTOCOL_NAME = "ccc";

    @Override
    public final String getProtocolName() {
        return PROTOCOL_NAME;
    }

    public static boolean isCorrectProtocol(PersistableBundle bundle) {
        return isProtocol(bundle, PROTOCOL_NAME);
    }

    public static boolean isCorrectProtocol(String protocolName) {
        return protocolName.equals(PROTOCOL_NAME);
    }

    public static final CccProtocolVersion PROTOCOL_VERSION_1_0 = new CccProtocolVersion(1, 0);

    /** Pulse Shapse (details below) */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE,
                PULSE_SHAPE_PRECURSOR_FREE,
                PULSE_SHAPE_PRECURSOR_FREE_SPECIAL
            })
    public @interface PulseShape {}

    /**
     * Indicates the symmetrical root raised cosine pulse shape as defined by Digital Key R3 Section
     * 21.5.
     */
    public static final int PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE = 0x0;

    /** Indicates the precursor-free pulse shape as defined by Digital Key R3 Section 21.5. */
    public static final int PULSE_SHAPE_PRECURSOR_FREE = 0x1;

    /**
     * Indicates a special case of the precursor-free pulse shape as defined by Digital Key R3
     * Section 21.5.
     */
    public static final int PULSE_SHAPE_PRECURSOR_FREE_SPECIAL = 0x2;

    /** Config (details below) */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                UWB_CONFIG_0,
                UWB_CONFIG_1,
            })
    public @interface UwbConfig {}

    /** Indicates UWB Config 0 as defined by Digital Key R3 Section 21.4. */
    public static final int UWB_CONFIG_0 = 0;

    /** Indicates UWB Config 1 as defined by Digital Key R3 Section 21.4. */
    public static final int UWB_CONFIG_1 = 1;

    /** Channels */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                UWB_CHANNEL_5,
                UWB_CHANNEL_9,
            })
    public @interface Channel {}

    public static final int UWB_CHANNEL_5 = 5;
    public static final int UWB_CHANNEL_9 = 9;

    /** Sync Codes */
    @Retention(RetentionPolicy.SOURCE)
    @IntRange(from = 1, to = 32)
    public @interface SyncCodeIndex {}

    /** Hopping Config */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                HOPPING_CONFIG_MODE_NONE,
                HOPPING_CONFIG_MODE_CONTINUOUS,
                HOPPING_CONFIG_MODE_ADAPTIVE,
            })
    public @interface HoppingConfigMode {}

    public static final int HOPPING_CONFIG_MODE_NONE = 0;
    public static final int HOPPING_CONFIG_MODE_CONTINUOUS = 1;
    public static final int HOPPING_CONFIG_MODE_ADAPTIVE = 2;

    /** Hopping Sequence */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                HOPPING_SEQUENCE_DEFAULT,
                HOPPING_SEQUENCE_AES,
            })
    public @interface HoppingSequence {}

    public static final int HOPPING_SEQUENCE_DEFAULT = 0;
    public static final int HOPPING_SEQUENCE_AES = 1;

    /** Chaps per Slot (i.e. slot duration) */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                CHAPS_PER_SLOT_3,
                CHAPS_PER_SLOT_4,
                CHAPS_PER_SLOT_6,
                CHAPS_PER_SLOT_8,
                CHAPS_PER_SLOT_9,
                CHAPS_PER_SLOT_12,
                CHAPS_PER_SLOT_24,
            })
    public @interface ChapsPerSlot {}

    public static final int CHAPS_PER_SLOT_3 = 3;
    public static final int CHAPS_PER_SLOT_4 = 4;
    public static final int CHAPS_PER_SLOT_6 = 6;
    public static final int CHAPS_PER_SLOT_8 = 8;
    public static final int CHAPS_PER_SLOT_9 = 9;
    public static final int CHAPS_PER_SLOT_12 = 12;
    public static final int CHAPS_PER_SLOT_24 = 24;

    /** Slots per Round */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                SLOTS_PER_ROUND_6,
                SLOTS_PER_ROUND_8,
                SLOTS_PER_ROUND_9,
                SLOTS_PER_ROUND_12,
                SLOTS_PER_ROUND_16,
                SLOTS_PER_ROUND_18,
                SLOTS_PER_ROUND_24,
                SLOTS_PER_ROUND_32,
                SLOTS_PER_ROUND_36,
                SLOTS_PER_ROUND_48,
                SLOTS_PER_ROUND_72,
                SLOTS_PER_ROUND_96,
            })
    public @interface SlotsPerRound {}

    public static final int SLOTS_PER_ROUND_6 = 6;
    public static final int SLOTS_PER_ROUND_8 = 8;
    public static final int SLOTS_PER_ROUND_9 = 9;
    public static final int SLOTS_PER_ROUND_12 = 12;
    public static final int SLOTS_PER_ROUND_16 = 16;
    public static final int SLOTS_PER_ROUND_18 = 18;
    public static final int SLOTS_PER_ROUND_24 = 24;
    public static final int SLOTS_PER_ROUND_32 = 32;
    public static final int SLOTS_PER_ROUND_36 = 36;
    public static final int SLOTS_PER_ROUND_48 = 48;
    public static final int SLOTS_PER_ROUND_72 = 72;
    public static final int SLOTS_PER_ROUND_96 = 96;

    /** Error Reason */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                PROTOCOL_ERROR_UNKNOWN,
                PROTOCOL_ERROR_SE_BUSY,
                PROTOCOL_ERROR_LIFECYCLE,
                PROTOCOL_ERROR_NOT_FOUND,
            })
    public @interface ProtocolError {}

    public static final int PROTOCOL_ERROR_UNKNOWN = 0;
    public static final int PROTOCOL_ERROR_SE_BUSY = 1;
    public static final int PROTOCOL_ERROR_LIFECYCLE = 2;
    public static final int PROTOCOL_ERROR_NOT_FOUND = 3;

    /** Session Type */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {SESSION_TYPE_CCC})
    public @interface SessionType {}

    /** Range Data Notification Config */
    @IntDef(
            value = {
                RANGE_DATA_NTF_CONFIG_DISABLE,
                RANGE_DATA_NTF_CONFIG_ENABLE,
                RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_LEVEL_TRIG,
                RANGE_DATA_NTF_CONFIG_ENABLE_AOA_LEVEL_TRIG,
                RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_LEVEL_TRIG,
                RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_EDGE_TRIG,
                RANGE_DATA_NTF_CONFIG_ENABLE_AOA_EDGE_TRIG,
                RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_EDGE_TRIG,
            })
    public @interface RangeDataNtfConfig {}

    public static final int RANGE_DATA_NTF_CONFIG_DISABLE = 0;
    public static final int RANGE_DATA_NTF_CONFIG_ENABLE = 1;
    public static final int RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_LEVEL_TRIG = 2;
    public static final int RANGE_DATA_NTF_CONFIG_ENABLE_AOA_LEVEL_TRIG = 3;
    public static final int RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_LEVEL_TRIG = 4;
    public static final int RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_EDGE_TRIG = 5;
    public static final int RANGE_DATA_NTF_CONFIG_ENABLE_AOA_EDGE_TRIG = 6;
    public static final int RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_EDGE_TRIG = 7;

    public static final int RANGE_DATA_NTF_PROXIMITY_NEAR_DEFAULT = 0;
    public static final int RANGE_DATA_NTF_PROXIMITY_FAR_DEFAULT = 20000;
    public static final double RANGE_DATA_NTF_AOA_AZIMUTH_LOWER_DEFAULT = -Math.PI;
    public static final double RANGE_DATA_NTF_AOA_AZIMUTH_UPPER_DEFAULT = Math.PI;
    public static final double RANGE_DATA_NTF_AOA_ELEVATION_LOWER_DEFAULT = -Math.PI / 2;
    public static final double RANGE_DATA_NTF_AOA_ELEVATION_UPPER_DEFAULT = Math.PI / 2;

    public static final int SESSION_TYPE_CCC = 160;

    public static final long UWB_INITIATION_TIME_MS_UNSET = 0;

    public static final int STS_INDEX_UNSET = 0;
}
