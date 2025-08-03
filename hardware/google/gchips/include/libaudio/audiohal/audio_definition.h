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

#ifndef __EXYNOS_AUDIOHAL_DEFINITION_H__
#define __EXYNOS_AUDIOHAL_DEFINITION_H__

/* This header file has common definitions for AudioHAL and AudioProxy */

#define PREDEFINED_CAPTURE_DURATION     2   // 2ms
#define LOW_LATENCY_CAPTURE_SAMPLE_RATE 48000

#define MAX_MIXER_LENGTH    256
#define DEFAULT_MIXER_PATH  "/vendor/etc/"
#define DEFAULT_MIXER_FILE  "mixer_paths.xml"
#define MIXER_PATH_INFO     "/proc/device-tree/sound/mixer-paths"

// Duration for Normal Capture
#define PREDEFINED_MEDIA_CAPTURE_DURATION   20  // 20ms
#define PREDEFINED_LOW_CAPTURE_DURATION     4   // 4ms

// Duration for USB Playback and Capture
#define PREDEFINED_USB_PLAYBACK_DURATION    20  // 20ms
#define PREDEFINED_USB_CAPTURE_DURATION     10  // 10ms


#define DEFAULT_MEDIA_BIT_WIDTH         16
#define DEFAULT_MEDIA_SAMPLING_RATE     48000

#define UHQA_MEDIA_BIT_WIDTH            24
#define UHQA_MEDIA_SAMPLING_RATE        192000

#define SUHQA_MEDIA_BIT_WIDTH           32
#define SUHQA_MEDIA_SAMPLING_RATE       384000


/**
 ** Customization
 ** If these are defined at other header file, please disable this if block
 **/
#define AUDIO_PARAMETER_KEY_FMRADIO_MODE                "fm_mode"
#define AUDIO_PARAMETER_KEY_FMRADIO_VOLUME              "fm_radio_volume"

// Factory Mode
#define AUDIO_PARAMETER_KEY_FACTORY_RMS_TEST            "factory_test_mic_check"

#define AUDIO_PARAMETER_FACTORY_TEST_LOOPBACK           "factory_test_loopback"
#define AUDIO_PARAMETER_FACTORY_TEST_TYPE               "factory_test_type"
#define AUDIO_PARAMETER_FACTORY_TEST_PATH               "factory_test_path"
#define AUDIO_PARAMETER_FACTORY_TEST_ROUTE              "factory_test_route"

#define AUDIO_PARAMETER_SEAMLESS_VOICE                  "seamless_voice"

#endif  // __EXYNOS_AUDIOHAL_DEFINITION_H__
