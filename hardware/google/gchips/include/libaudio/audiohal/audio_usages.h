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

#ifndef __EXYNOS_AUDIOHAL_USAGE_H__
#define __EXYNOS_AUDIOHAL_USAGE_H__

/**
 ** Audio Usages Definition
 **/
typedef enum {
    AUSAGE_PLAYBACK,
    AUSAGE_CAPTURE,
} audio_usage_type;

typedef enum {
    AUSAGE_MIN                 = 0,

    // Media Playback/Recording Usages
    // These audio usages are defined from stream own usage
    AUSAGE_MEDIA               = 0,
    AUSAGE_RECORDING,
    AUSAGE_CAMCORDER,

    // Call Usages
    // These audio usages are defined from Audio Mode and Voice Status
    AUSAGE_CPCALL_MIN,
    AUSAGE_VOICE_CALL_NB = AUSAGE_CPCALL_MIN,
    AUSAGE_VOICE_CALL_WB,
    AUSAGE_VOLTE_CALL_NB,
    AUSAGE_VOLTE_CALL_WB,
    AUSAGE_VOLTE_CALL_SWB,
    AUSAGE_VOLTE_VT_CALL_NB,
    AUSAGE_VOLTE_VT_CALL_WB,
    AUSAGE_VOLTE_VT_CALL_SWB,
    AUSAGE_TTY,
    AUSAGE_CPCALL_MAX = AUSAGE_TTY,

    AUSAGE_APCALL_MIN,
    AUSAGE_WIFI_CALL_NB = AUSAGE_APCALL_MIN,
    AUSAGE_WIFI_CALL_WB,
    AUSAGE_WIFI_CALL_SWB,
    AUSAGE_VIDEO_CALL,
    AUSAGE_VOIP_CALL,
    AUSAGE_COMMUNICATION,
    AUSAGE_AP_TTY,
    AUSAGE_APCALL_MAX = AUSAGE_AP_TTY,

    // Call Recording Usages
    AUSAGE_INCALL_UPLINK,
    AUSAGE_INCALL_DOWNLINK,
    AUSAGE_INCALL_UPLINK_DOWNLINK,

    // Voice Recognition Usages
    AUSAGE_RECOGNITION,

    // Other Audio Usages
    AUSAGE_FM_RADIO,

    // Voice WakeUp Usages
#ifdef SUPPORT_STHAL_INTERFACE
    AUSAGE_HOTWORD_SEAMLESS,
    AUSAGE_HOTWORD_RECORD,
#endif

    // Factory Mode Test Usages
    AUSAGE_LOOPBACK_MIN,
    AUSAGE_LOOPBACK = AUSAGE_LOOPBACK_MIN, //packet
    AUSAGE_LOOPBACK_NODELAY,               //packet_nodelay
    AUSAGE_LOOPBACK_REALTIME,
    AUSAGE_LOOPBACK_CODEC,
    AUSAGE_LOOPBACK_MAX = AUSAGE_LOOPBACK_CODEC,

    AUSAGE_RMS,                            //RMS Test

    AUSAGE_NONE,
    AUSAGE_MAX,
    AUSAGE_CNT            = AUSAGE_MAX
} audio_usage;

#endif  // __EXYNOS_AUDIOHAL_USAGE_H__
