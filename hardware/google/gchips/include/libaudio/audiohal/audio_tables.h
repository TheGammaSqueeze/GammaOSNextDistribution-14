/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef __EXYNOS_AUDIOHAL_TABLE_H__
#define __EXYNOS_AUDIOHAL_TABLE_H__

/*
 * Audio Streams Table for readable log messages
 */
char * stream_table[ASTREAM_CNT] = {
    [ASTREAM_PLAYBACK_PRIMARY]       = "primary_out",
    [ASTREAM_PLAYBACK_FAST]          = "fast_out",
    [ASTREAM_PLAYBACK_DEEP_BUFFER]   = "deep_out",
    [ASTREAM_PLAYBACK_LOW_LATENCY]   = "low_out",
    [ASTREAM_PLAYBACK_COMPR_OFFLOAD] = "offload_out",
    [ASTREAM_PLAYBACK_MMAP]          = "mmap_out",
    [ASTREAM_PLAYBACK_USB_DEVICE]    = "usb_out",
    [ASTREAM_PLAYBACK_AUX_DIGITAL]   = "aux_out",

    [ASTREAM_CAPTURE_PRIMARY]        = "primary_in",
    [ASTREAM_CAPTURE_CALL]           = "callrec_in",
    [ASTREAM_CAPTURE_LOW_LATENCY]    = "low_in",
    [ASTREAM_CAPTURE_MMAP]           = "mmap_in",
    [ASTREAM_CAPTURE_USB_DEVICE]     = "usb_in",
    [ASTREAM_CAPTURE_FM]             = "fmrec_in",
#ifdef SUPPORT_STHAL_INTERFACE
    [ASTREAM_CAPTURE_HOTWORD]        = "hotword_in",
#endif

    [ASTREAM_NONE]                   = "none"
};

/**
 ** Audio Usage Table for readable log messages
 **/
char * usage_table[AUSAGE_CNT] = {
    [AUSAGE_MEDIA]                  = "media",
    [AUSAGE_RECORDING]              = "recording",
    [AUSAGE_CAMCORDER]              = "camcoder",

    [AUSAGE_VOICE_CALL_NB]          = "voice_call_nb",
    [AUSAGE_VOICE_CALL_WB]          = "voice_call_wb",
    [AUSAGE_VOLTE_CALL_NB]          = "volte_call_nb",
    [AUSAGE_VOLTE_CALL_WB]          = "volte_call_wb",
    [AUSAGE_VOLTE_CALL_SWB]         = "volte_vt_call_swb",
    [AUSAGE_VOLTE_VT_CALL_NB]       = "volte_vt_call_nb",
    [AUSAGE_VOLTE_VT_CALL_WB]       = "volte_vt_call_wb",
    [AUSAGE_VOLTE_VT_CALL_SWB]      = "volte_call_swb",
    [AUSAGE_TTY]                    = "tty_mode",

    [AUSAGE_WIFI_CALL_NB]           = "vowifi_call_nb",
    [AUSAGE_WIFI_CALL_WB]           = "vowifi_call_wb",
    [AUSAGE_WIFI_CALL_SWB]          = "vowifi_call_swb",
    [AUSAGE_VIDEO_CALL]             = "video_call",
    [AUSAGE_VOIP_CALL]              = "voip_call",
    [AUSAGE_COMMUNICATION]          = "voip_call",
    [AUSAGE_AP_TTY]                 = "ap_tty_mode",

    [AUSAGE_INCALL_UPLINK]          = "callrecord_uplink",
    [AUSAGE_INCALL_DOWNLINK]        = "callrecord_downlink",
    [AUSAGE_INCALL_UPLINK_DOWNLINK] = "callrecord",

    [AUSAGE_RECOGNITION]            = "recognition",

    [AUSAGE_FM_RADIO]               = "fm_radio",

#ifdef SUPPORT_STHAL_INTERFACE
    [AUSAGE_HOTWORD_SEAMLESS]       = "hotword_seamless",
    [AUSAGE_HOTWORD_RECORD]         = "hotword_record",
#endif

    [AUSAGE_LOOPBACK]               = "factory_loopback",
    [AUSAGE_LOOPBACK_NODELAY]       = "factory_loopback_nodelay",
    [AUSAGE_LOOPBACK_REALTIME]      = "factory_loopback_realtime",
    [AUSAGE_LOOPBACK_CODEC]         = "factory_loopback_codec",
    [AUSAGE_RMS]                    = "factory_rms",

    [AUSAGE_NONE]                   = "none",
};

/**
 ** Usage Path(AP/CP to Codec) Configuration based on Audio Usage
 **/
char * usage_path_table[AUSAGE_CNT] = {
    [AUSAGE_MEDIA]                  = "media",
    [AUSAGE_RECORDING]              = "recording",
    [AUSAGE_CAMCORDER]              = "camcorder",

    [AUSAGE_VOICE_CALL_NB]          = "incall_nb",
    [AUSAGE_VOICE_CALL_WB]          = "incall_wb",
    [AUSAGE_VOLTE_CALL_NB]          = "volte_cp_nb",
    [AUSAGE_VOLTE_CALL_WB]          = "volte_cp_wb",
    [AUSAGE_VOLTE_CALL_SWB]         = "volte_cp_evs",
    [AUSAGE_VOLTE_VT_CALL_NB]       = "volte_vt_cp_nb",
    [AUSAGE_VOLTE_VT_CALL_WB]       = "volte_vt_cp_wb",
    [AUSAGE_VOLTE_VT_CALL_SWB]      = "volte_vt_cp_evs",
    [AUSAGE_TTY]                    = "tty_mode",

    [AUSAGE_WIFI_CALL_NB]           = "wificall_nb",
    [AUSAGE_WIFI_CALL_WB]           = "wificall_wb",
    [AUSAGE_WIFI_CALL_SWB]          = "wificall_evs",
    [AUSAGE_VIDEO_CALL]             = "video_call",
    [AUSAGE_VOIP_CALL]              = "voip",
    [AUSAGE_COMMUNICATION]          = "communication",
    [AUSAGE_AP_TTY]                 = "ap_tty_mode",

    [AUSAGE_INCALL_UPLINK]          = "callrecord_uplink",
    [AUSAGE_INCALL_DOWNLINK]        = "callrecord_downlink",
    [AUSAGE_INCALL_UPLINK_DOWNLINK] = "callrecord",

    [AUSAGE_RECOGNITION]            = "recognition",

    [AUSAGE_FM_RADIO]               = "fm_radio",

#ifdef SUPPORT_STHAL_INTERFACE
    [AUSAGE_HOTWORD_SEAMLESS]       = "hotword_seamless",    //dummy definition not used
    [AUSAGE_HOTWORD_RECORD]         = "hotword_record",    //dummy definition not used
#endif

    [AUSAGE_LOOPBACK]               = "loopback_packet",
    [AUSAGE_LOOPBACK_NODELAY]       = "loopback",
    [AUSAGE_LOOPBACK_REALTIME]      = "realtimeloopback",
    [AUSAGE_LOOPBACK_CODEC]         = "loopback_codec",
    [AUSAGE_RMS]                    = "echo_test",

    [AUSAGE_NONE]                   = "none",
};

/**
 ** Device Path(Codec to Device) Configuration based on Audio Input/Output Device
 **/
char * device_table[DEVICE_CNT] = {
    // Playback Devices
    [DEVICE_EARPIECE]               = "handset",
    [DEVICE_SPEAKER]                = "speaker",
    [DEVICE_HEADSET]                = "headset",
    [DEVICE_HEADPHONE]              = "headphone",
    [DEVICE_SPEAKER_AND_HEADSET]    = "speaker-headset",
    [DEVICE_SPEAKER_AND_HEADPHONE]  = "speaker-headphone",
    [DEVICE_BT_HEADSET]             = "bt-sco-headset",
    [DEVICE_FM_EXTERNAL]            = "external",
    [DEVICE_SPEAKER_AND_BT_HEADSET] = "speaker-bt-sco-headset",
    [DEVICE_USB_HEADSET]            = "usb-headset",
    [DEVICE_AUX_DIGITAL]            = "aux-digital",

    // Special Playback Devices
    [DEVICE_CALL_FWD]               = "",

    // Capture Devices
    [DEVICE_MAIN_MIC]               = "mic",
    [DEVICE_HEADSET_MIC]            = "headset-mic",
    [DEVICE_HEADSET_MAIN_MIC]       = "headset-main-mic",
    [DEVICE_BT_HEADSET_MIC]         = "bt-sco-headset-in",
    [DEVICE_BT_NREC_HEADSET_MIC]    = "bt-sco-nrec-headset-in",
    [DEVICE_USB_HEADSET_MIC]        = "usb-headset-mic",

    [DEVICE_HANDSET_MIC]            = "handset-mic",
    [DEVICE_SPEAKER_MIC]            = "speaker-mic",
    [DEVICE_HEADPHONE_MIC]          = "headphone-mic",

    [DEVICE_SUB_MIC]                = "2nd-mic",
    [DEVICE_FULL_MIC]               = "full-mic",
    [DEVICE_HCO_MIC]                = "hco-mic",
    [DEVICE_VCO_MIC]                = "vco-mic",

    [DEVICE_FM_TUNER]               = "fm-tuner",

    [DEVICE_NONE]                   = "none",
};

/**
 ** Sampling Rate Modifier Configuration based on Audio Input/Output Device
 **/
char * modifier_table[MODIFIER_MAX] = {
    /* RX modifier */
    [MODIFIER_BT_SCO_RX_NB]  = "set-bt-sco-rx-rate-nb",
    [MODIFIER_BT_SCO_RX_WB]  = "set-bt-sco-rx-rate-wb",

    /* TX modifier */
    [MODIFIER_BT_SCO_TX_NB]  = "set-bt-sco-tx-rate-nb",
    [MODIFIER_BT_SCO_TX_WB]  = "set-bt-sco-tx-rate-wb",

    [MODIFIER_NONE]            = "none",
};

/**
 ** Offload Message Table for readable log messages
 **/
char * offload_msg_table[OFFLOAD_MSG_MAX] = {
    [OFFLOAD_MSG_INVALID]            = "Offload Message_Invalid",
    [OFFLOAD_MSG_WAIT_WRITE]         = "Offload Message_Wait to write",
    [OFFLOAD_MSG_WAIT_DRAIN]         = "Offload Message_Wait to drain",
    [OFFLOAD_MSG_WAIT_PARTIAL_DRAIN] = "Offload Message_Wait to drain partially",
    [OFFLOAD_MSG_EXIT]               = "Offload Message_Wait to exit",
};


#endif  // __EXYNOS_AUDIOHAL_TABLE_H__
