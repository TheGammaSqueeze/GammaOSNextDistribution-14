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

#ifndef __EXYNOS_AUDIOHAL_DEVICE_H__
#define __EXYNOS_AUDIOHAL_DEVICE_H__

/**
 ** Audio Input/Output Device based on Target Device
 **/
typedef enum {
    DEVICE_MIN                    = 0,

    // Playback Devices
    DEVICE_EARPIECE               = 0,   // handset or receiver
    DEVICE_SPEAKER,
    DEVICE_HEADSET,                      // headphone + mic
    DEVICE_HEADPHONE,                    // headphone or earphone
    DEVICE_SPEAKER_AND_HEADSET,
    DEVICE_SPEAKER_AND_HEADPHONE,
    DEVICE_BT_HEADSET,
    DEVICE_FM_EXTERNAL,
    DEVICE_SPEAKER_AND_BT_HEADSET,
    DEVICE_USB_HEADSET,
    DEVICE_AUX_DIGITAL,

    // Special Playback Devices
    DEVICE_CALL_FWD,

    // Capture Devices
    DEVICE_MAIN_MIC,
    DEVICE_HEADSET_MIC,
    DEVICE_HEADSET_MAIN_MIC,
    DEVICE_BT_HEADSET_MIC,
    DEVICE_BT_NREC_HEADSET_MIC,
    DEVICE_USB_HEADSET_MIC,

    DEVICE_HANDSET_MIC,
    DEVICE_SPEAKER_MIC,
    DEVICE_HEADPHONE_MIC,

    DEVICE_SUB_MIC,
    DEVICE_FULL_MIC,
    DEVICE_HCO_MIC,
    DEVICE_VCO_MIC,

    DEVICE_FM_TUNER,

    DEVICE_NONE,
    DEVICE_MAX,
    DEVICE_CNT                   = DEVICE_MAX
} device_type;


/**
 ** Audio Input/Output Sampling Rate Modifier
 **/
typedef enum {
    MODIFIER_MIN          = 0,

    /* RX modifier */
    MODIFIER_BT_SCO_RX_NB = 0,
    MODIFIER_BT_SCO_RX_WB,

    /* TX modifier */
    MODIFIER_BT_SCO_TX_NB,
    MODIFIER_BT_SCO_TX_WB,

    MODIFIER_NONE,
    MODIFIER_MAX,
    MODIFIER_CNT          = MODIFIER_MAX
} modifier_type;


#endif  // __EXYNOS_AUDIOHAL_DEVICE_H__
