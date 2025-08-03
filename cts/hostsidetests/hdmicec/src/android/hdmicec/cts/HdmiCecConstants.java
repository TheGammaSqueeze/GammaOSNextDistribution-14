/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.hdmicec.cts;

import androidx.annotation.IntDef;

import java.io.File;

public final class HdmiCecConstants {

    private HdmiCecConstants() {}

    /** @deprecated not used anymore **/
    @Deprecated
    public static final String PHYSICAL_ADDRESS_NAME = "cec-phy-addr";
    public static final int REBOOT_TIMEOUT = 60000;
    public static final int TIMEOUT_CEC_REINIT_SECONDS = 5;
    public static final int TIMEOUT_SAFETY_MS = 500;

    public static final int INVALID_VENDOR_ID = 0xFFFFFF;

    // Standard delay to allow the DUT to react to a CEC message or ADB command
    public static final int DEVICE_WAIT_TIME_SECONDS = 5;
    public static final int DEVICE_WAIT_TIME_MS = 5000;
    public static final int MAX_SLEEP_TIME_SECONDS = 8;
    public static final int SLEEP_TIMESTEP_SECONDS = 1;
    public static final int SLEEP_TIME_DELAY_SECONDS = 2;
    public static final int DEFAULT_PHYSICAL_ADDRESS = 0x1000;
    public static final int TV_PHYSICAL_ADDRESS = 0x0000;
    public static final int PHYSICAL_ADDRESS_LENGTH = 4; /* Num nibbles in CEC message */

    public static final int CEC_KEYCODE_SELECT = 0x00;
    public static final int CEC_KEYCODE_UP = 0x01;
    public static final int CEC_KEYCODE_DOWN = 0x02;
    public static final int CEC_KEYCODE_LEFT = 0x03;
    public static final int CEC_KEYCODE_RIGHT = 0x04;
    public static final int CEC_KEYCODE_ROOT_MENU = 0x09;
    public static final int CEC_KEYCODE_SETUP_MENU = 0x0A;
    public static final int CEC_KEYCODE_CONTENTS_MENU = 0x0B;
    public static final int CEC_KEYCODE_BACK = 0x0D;
    public static final int CEC_KEYCODE_MEDIA_TOP_MENU = 0x10;
    public static final int CEC_KEYCODE_MEDIA_CONTEXT_SENSITIVE_MENU = 0x11;
    public static final int CEC_KEYCODE_NUMBER_0_OR_NUMBER_10 = 0x20;
    public static final int CEC_KEYCODE_NUMBERS_1 = 0x21;
    public static final int CEC_KEYCODE_NUMBERS_2 = 0x22;
    public static final int CEC_KEYCODE_NUMBERS_3 = 0x23;
    public static final int CEC_KEYCODE_NUMBERS_4 = 0x24;
    public static final int CEC_KEYCODE_NUMBERS_5 = 0x25;
    public static final int CEC_KEYCODE_NUMBERS_6 = 0x26;
    public static final int CEC_KEYCODE_NUMBERS_7 = 0x27;
    public static final int CEC_KEYCODE_NUMBERS_8 = 0x28;
    public static final int CEC_KEYCODE_NUMBERS_9 = 0x29;
    public static final int CEC_KEYCODE_CHANNEL_UP = 0x30;
    public static final int CEC_KEYCODE_CHANNEL_DOWN = 0x31;
    public static final int CEC_KEYCODE_PREVIOUS_CHANNEL = 0x32;
    public static final int CEC_KEYCODE_DISPLAY_INFORMATION = 0x35;
    public static final int CEC_KEYCODE_POWER = 0x40;
    public static final int CEC_KEYCODE_VOLUME_UP = 0x41;
    public static final int CEC_KEYCODE_VOLUME_DOWN = 0x42;
    public static final int CEC_KEYCODE_MUTE = 0x43;
    public static final int CEC_KEYCODE_PLAY = 0x44;
    public static final int CEC_KEYCODE_STOP = 0x45;
    public static final int CEC_KEYCODE_PAUSE = 0x46;
    public static final int CEC_KEYCODE_RECORD = 0x47;
    public static final int CEC_KEYCODE_REWIND = 0x48;
    public static final int CEC_KEYCODE_FAST_FORWARD = 0x49;
    public static final int CEC_KEYCODE_EJECT = 0x4A;
    public static final int CEC_KEYCODE_FORWARD = 0x4B;
    public static final int CEC_KEYCODE_BACKWARD = 0x4C;
    public static final int CEC_KEYCODE_POWER_TOGGLE_FUNCTION = 0x6B;
    public static final int CEC_KEYCODE_POWER_OFF_FUNCTION = 0x6C;
    public static final int CEC_KEYCODE_POWER_ON_FUNCTION = 0x6D;
    public static final int CEC_KEYCODE_F1_BLUE = 0x71;
    public static final int CEC_KEYCODE_F2_RED = 0x72;
    public static final int CEC_KEYCODE_F3_GREEN = 0x73;
    public static final int CEC_KEYCODE_F4_YELLOW = 0x74;
    public static final int CEC_KEYCODE_DATA = 0x76;

    public static final int UNRECOGNIZED_OPCODE = 0x0;

    @IntDef(
            value = {
                CEC_DEVICE_TYPE_UNKNOWN,
                CEC_DEVICE_TYPE_TV,
                CEC_DEVICE_TYPE_RECORDER,
                CEC_DEVICE_TYPE_RESERVED,
                CEC_DEVICE_TYPE_TUNER,
                CEC_DEVICE_TYPE_PLAYBACK_DEVICE,
                CEC_DEVICE_TYPE_AUDIO_SYSTEM,
                CEC_DEVICE_TYPE_SWITCH
            })
    public @interface CecDeviceType {}

    public static final int CEC_DEVICE_TYPE_UNKNOWN = -1;
    public static final int CEC_DEVICE_TYPE_TV = 0;
    public static final int CEC_DEVICE_TYPE_RECORDER = 1;
    public static final int CEC_DEVICE_TYPE_RESERVED = 2;
    public static final int CEC_DEVICE_TYPE_TUNER = 3;
    public static final int CEC_DEVICE_TYPE_PLAYBACK_DEVICE = 4;
    public static final int CEC_DEVICE_TYPE_AUDIO_SYSTEM = 5;
    public static final int CEC_DEVICE_TYPE_SWITCH = 6;
    public static final int CEC_DEVICE_TYPE_VIDEO_PROCESSOR = 7;

    /** Feature Abort Reasons */
    public static final int ABORT_UNRECOGNIZED_MODE = 0;
    public static final int ABORT_NOT_IN_CORRECT_MODE = 1;
    public static final int ABORT_CANNOT_PROVIDE_SOURCE = 2;
    public static final int ABORT_INVALID_OPERAND = 3;
    public static final int ABORT_REFUSED = 4;
    public static final int ABORT_UNABLE_TO_DETERMINE = 5;

    // CEC versions
    public static final int CEC_VERSION_1_4 = 0x05;
    public static final int CEC_VERSION_2_0 = 0x06;

    /** CEC Power Status */
    public static final int CEC_POWER_STATUS_ON = 0x0;
    public static final int CEC_POWER_STATUS_STANDBY = 0x1;
    public static final int CEC_POWER_STATUS_IN_TRANSITION_TO_ON = 0x2;
    public static final int CEC_POWER_STATUS_IN_TRANSITION_TO_STANDBY = 0x3;

    /** PowerManager wakefulness states */
    public static final String WAKEFULNESS_AWAKE = "Awake";
    public static final String WAKEFULNESS_ASLEEP = "Asleep";

    /** Poll Message Success */
    public static final String POLL_SUCCESS = "POLL message sent";

    // CEC Device feature list
    public static final String HDMI_CEC_FEATURE = "feature:android.hardware.hdmi.cec";
    public static final String LEANBACK_FEATURE = "feature:android.software.leanback";

    // Device property list used in CTS
    public static final String HDMI_DEVICE_TYPE_PROPERTY = "ro.hdmi.device_type";
    public static final String PROPERTY_ARC_SUPPORT = "persist.sys.hdmi.property_arc_support";
    public static final String PROPERTY_BUILD_FINGERPRINT = "ro.system.build.fingerprint";

    /*
     * The default name of local directory into which the port to device mapping files are stored.
     */
    public static final File CEC_MAP_FOLDER =
            new File(System.getProperty("java.io.tmpdir"), "cec-cts-temp");

    // CEC Settings
    public static final String SETTING_VOLUME_CONTROL_ENABLED = "volume_control_enabled";
    public static final String SETTING_SOUNDBAR_MODE_ENABLED = "soundbar_mode";

    // CEC Settings Values
    public static final String VOLUME_CONTROL_ENABLED = "1";
    public static final String VOLUME_CONTROL_DISABLED = "0";
    public static final String SOUNDBAR_MODE_ENABLED = "1";
    public static final String SOUNDBAR_MODE_DISABLED = "0";
    // Power Control Modes for source devices
    public static final String POWER_CONTROL_MODE_BROADCAST = "broadcast";
    public static final String POWER_CONTROL_MODE_NONE = "none";
    public static final String POWER_CONTROL_MODE_TV = "to_tv";
    public static final String POWER_CONTROL_MODE_TV_AND_AUDIO_SYSTEM = "to_tv_and_audio_system";

    // Power State Change on Active Source Lost Settings values
    public static final String POWER_STATE_CHANGE_ON_ACTIVE_SOURCE_LOST_NONE = "none";
    public static final String POWER_STATE_CHANGE_ON_ACTIVE_SOURCE_LOST_STANDBY_NOW = "standby_now";

    // Short Audio Descriptors that can be queried
    public static final String QUERY_SAD_LPCM = "query_sad_lpcm";
    public static final String QUERY_SAD_DD = "query_sad_dd";
    public static final String QUERY_SAD_MPEG1 = "query_sad_mpeg1";
    public static final String QUERY_SAD_MP3 = "query_sad_mp3";
    public static final String QUERY_SAD_MPEG2 = "query_sad_mpeg2";
    public static final String QUERY_SAD_AAC = "query_sad_aac";
    public static final String QUERY_SAD_DTS = "query_sad_dts";
    public static final String QUERY_SAD_ATRAC = "query_sad_atrac";
    public static final String QUERY_SAD_ONEBITAUDIO = "query_sad_onebitaudio";
    public static final String QUERY_SAD_DDP = "query_sad_ddp";
    public static final String QUERY_SAD_DTSHD = "query_sad_dtshd";
    public static final String QUERY_SAD_TRUEHD = "query_sad_truehd";
    public static final String QUERY_SAD_DST = "query_sad_dst";
    public static final String QUERY_SAD_WMAPRO = "query_sad_wmapro";
    public static final String QUERY_SAD_MAX = "query_sad_max";

    // Whether to query an SAD or not
    public static final String QUERY_SAD_DISABLED = "0";
    public static final String QUERY_SAD_ENABLED = "1";

    // Audio codecs
    public static final int AUDIO_CODEC_NONE = 0x0;
    public static final int AUDIO_CODEC_LPCM = 0x1; // Support LPCMs
    public static final int AUDIO_CODEC_DD = 0x2; // Support DD
    public static final int AUDIO_CODEC_MPEG1 = 0x3; // Support MPEG1
    public static final int AUDIO_CODEC_MP3 = 0x4; // Support MP3
    public static final int AUDIO_CODEC_MPEG2 = 0x5; // Support MPEG2
    public static final int AUDIO_CODEC_AAC = 0x6; // Support AAC
    public static final int AUDIO_CODEC_DTS = 0x7; // Support DTS
    public static final int AUDIO_CODEC_ATRAC = 0x8; // Support ATRAC
    public static final int AUDIO_CODEC_ONEBITAUDIO = 0x9; // Support One-Bit Audio
    public static final int AUDIO_CODEC_DDP = 0xA; // Support DDP
    public static final int AUDIO_CODEC_DTSHD = 0xB; // Support DTSHD
    public static final int AUDIO_CODEC_TRUEHD = 0xC; // Support MLP/TRUE-HD
    public static final int AUDIO_CODEC_DST = 0xD; // Support DST
    public static final int AUDIO_CODEC_WMAPRO = 0xE; // Support WMA-Pro
    public static final int AUDIO_CODEC_MAX = 0xF;

    // CEC 2.0 Report Feature Bits
    public static final int FEATURES_SINK_SUPPORTS_ARC_TX_BIT = 0x4;
    public static final int FEATURES_SINK_SUPPORTS_ARC_RX_BIT = 0x2;

    // Audio device types from AudioDeviceInfo
    public static final int DEVICE_OUT_HDMI = 0x400;
    public static final int DEVICE_OUT_HDMI_ARC = 0x40000;
    public static final int DEVICE_OUT_HDMI_EARC = 0x40001;

    // Volume behavior constants from AudioManager
    public static final int DEVICE_VOLUME_BEHAVIOR_VARIABLE = 0;
    public static final int DEVICE_VOLUME_BEHAVIOR_FULL = 1;
    public static final int DEVICE_VOLUME_BEHAVIOR_FIXED = 2;
    public static final int DEVICE_VOLUME_BEHAVIOR_ABSOLUTE = 3;
}
