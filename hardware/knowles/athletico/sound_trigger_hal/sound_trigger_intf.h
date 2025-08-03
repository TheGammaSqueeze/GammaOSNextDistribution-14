/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef SOUND_TRIGGER_INTF_H
#define SOUND_TRIGGER_INTF_H

#include <cutils/list.h>
#include <hardware/sound_trigger.h>
#include "tinyalsa/asoundlib.h"

/*-------------------- Begin: AHAL-STHAL Interface ---------------------------*/
/*
 * Maintain the proprietary interface between AHAL and STHAL locally to avoid
 * the compilation dependency of interface header file from STHAL.
 */

#define MAKE_HAL_VERSION(maj, min) ((((maj) & 0xff) << 8) | ((min) & 0xff))
#define MAJOR_VERSION(ver) (((ver) & 0xff00) >> 8)
#define MINOR_VERSION(ver) ((ver) & 0x00ff)

/* Proprietary interface version used for compatibility with STHAL */
#define STHAL_PROP_API_VERSION_2_0 MAKE_HAL_VERSION(2, 0)
#define STHAL_PROP_API_CURRENT_VERSION STHAL_PROP_API_VERSION_2_0

#define ST_EVENT_CONFIG_MAX_STR_VALUE 32

#define AUDIO_HAL_NAME_PREFIX "audio.primary"
#define SOUND_TRIGGER_PLATFORM "msmnile"

#ifdef __LP64__
    #define AUDIO_HAL_LIBRARY_PATH "/vendor/lib64/hw"
#else
    #define AUDIO_HAL_LIBRARY_PATH "/vendor/lib/hw"
#endif

const unsigned int sthal_prop_api_version = STHAL_PROP_API_CURRENT_VERSION;

#define SOUND_TRIGGER_SAMPLING_RATE     16000
#define SOUND_TRIGGER_CHANNEL               1
#define SOUND_TRIGGER_PERIOD_COUNT          2
#define SOUND_TRIGGER_PERIOD_DURATION_MSEC 20
#define SOUND_TRIGGER_PERIOD_SIZE   (SOUND_TRIGGER_SAMPLING_RATE * \
                                    SOUND_TRIGGER_CHANNEL * \
                                    SOUND_TRIGGER_PERIOD_DURATION_MSEC) / 1000

static struct pcm_config stdev_hotword_pcm_config = {
    .channels = SOUND_TRIGGER_CHANNEL,
    .rate = SOUND_TRIGGER_SAMPLING_RATE,
    .period_size = SOUND_TRIGGER_PERIOD_SIZE,
    .period_count = SOUND_TRIGGER_PERIOD_COUNT,
    .format = PCM_FORMAT_S16_LE,
};

typedef enum {
    ST_EVENT_SESSION_REGISTER,
    ST_EVENT_SESSION_DEREGISTER
} sound_trigger_event_type_t;

typedef enum {
    AUDIO_EVENT_CAPTURE_DEVICE_INACTIVE,
    AUDIO_EVENT_CAPTURE_DEVICE_ACTIVE,
    AUDIO_EVENT_PLAYBACK_STREAM_INACTIVE,
    AUDIO_EVENT_PLAYBACK_STREAM_ACTIVE,
    AUDIO_EVENT_STOP_LAB,
    AUDIO_EVENT_SSR,
    AUDIO_EVENT_NUM_ST_SESSIONS,
    AUDIO_EVENT_READ_SAMPLES,
    AUDIO_EVENT_DEVICE_CONNECT,
    AUDIO_EVENT_DEVICE_DISCONNECT,
    AUDIO_EVENT_SVA_EXEC_MODE,
    AUDIO_EVENT_SVA_EXEC_MODE_STATUS,
    AUDIO_EVENT_CAPTURE_STREAM_INACTIVE,
    AUDIO_EVENT_CAPTURE_STREAM_ACTIVE,
} audio_event_type_t;

typedef enum {
    USECASE_TYPE_PCM_PLAYBACK,
    USECASE_TYPE_PCM_CAPTURE,
    USECASE_TYPE_VOICE_CALL,
    USECASE_TYPE_VOIP_CALL,
} audio_stream_usecase_type_t;

enum ssr_event_status {
    SND_CARD_STATUS_OFFLINE,
    SND_CARD_STATUS_ONLINE,
    CPE_STATUS_OFFLINE,
    CPE_STATUS_ONLINE,
    SLPI_STATUS_OFFLINE,
    SLPI_STATUS_ONLINE
};

struct sound_trigger_session_info {
    void* p_ses; /* opaque pointer to st_session obj */
    int capture_handle;
    struct pcm *pcm;
    struct pcm_config config;
};

struct audio_read_samples_info {
    struct sound_trigger_session_info *ses_info;
    void *buf;
    size_t num_bytes;
};

struct audio_hal_usecase {
    audio_stream_usecase_type_t type;
};

struct sound_trigger_event_info {
    struct sound_trigger_session_info st_ses;
};

struct sound_trigger_get_param_data {
    char *param;
    int sm_handle;
    struct str_parms *reply;
};

struct audio_device_info {
    struct listnode list;
    audio_devices_t type;
    char address[AUDIO_DEVICE_MAX_ADDRESS_LEN];
};

struct sound_trigger_device_info {
    struct listnode devices;
};

struct audio_event_info {
    union {
        enum ssr_event_status status;
        int value;
        struct sound_trigger_session_info ses_info;
        struct audio_read_samples_info aud_info;
        char str_value[ST_EVENT_CONFIG_MAX_STR_VALUE];
        struct audio_hal_usecase usecase;
        bool audio_ec_ref_enabled;
        struct sound_trigger_get_param_data st_get_param_data;
    } u;
    struct sound_trigger_device_info device_info;
};

/* STHAL callback which is called by AHAL */
typedef int (*sound_trigger_hw_call_back_t)(audio_event_type_t,
                                struct audio_event_info*);

/* AHAL callback which is called by STHAL */
typedef void (*audio_hw_call_back_t)(sound_trigger_event_type_t,
                        struct sound_trigger_event_info*);

/*---------------- End: AHAL-STHAL Interface ----------------------------------*/
#endif /* SOUND_TRIGGER_INTF_H */
