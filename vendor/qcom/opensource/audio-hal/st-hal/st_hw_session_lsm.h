/* st_hw_session_lsm.h
 *
 * Copyright (c) 2016-2021, The Linux Foundation. All rights reserved.
 * Copyright (c) 2023 Qualcomm Innovation Center, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#include <pthread.h>
#include <tinyalsa/asoundlib.h>
#include <audio_route/audio_route.h>
#include <sound/lsm_params.h>
#include <sound/asound.h>

#include "sound_trigger_platform.h"
#include "st_hw_session.h"
#include "st_common_defs.h"

struct sound_trigger_device;

#define SOUND_TRIGGER_MAX_EVNT_PAYLOAD_SIZE (256)

/*
 * Add extra to accomodate multiple LSM_CUSTOM_PARAMS and also
 * multiple LSM_MULTI_SND_MODEL_CONFIDENCE_LEVELS params for
 * multi SM usecase.
 */
#define LSM_SM_PARAMS_INFO_MAX (LSM_PARAMS_MAX * 2)

#define SOUND_TRIGGER_PCM_MAX_RETRY (10)
#define SOUND_TRIGGER_PCM_SLEEP_WAIT (1000)

#define SOUND_TRIGGER_APE_PERIOD_COUNT (4)
#define SOUND_TRIGGER_APE_PERIOD_SIZE \
    ((((SOUND_TRIGGER_SAMPLING_RATE_16000 * SOUND_TRIGGER_APE_BUFFER_DURATION_MS) \
       /(SOUND_TRIGGER_APE_PERIOD_COUNT * 1000)) + 0x1f) & ~0x1f)

/*
 * The chosen theshold for determining FTRT vs. RT data is one tenth of the
 * buffer duration. There can be frames received that are partially FTRT and
 * partially RT, so the threshold should be less than the full buffer duration
 * to account for that usecase. However, if the threshold is too small, then
 * some issue in the lower layers could lead to false identification of RT
 * data.
 */
#define APE_MAX_LAB_FTRT_FRAME_RD_TIME_NS \
    ((SOUND_TRIGGER_APE_BUFFER_DURATION_MS * NSECS_PER_MSEC)\
    / (10 * SOUND_TRIGGER_APE_PERIOD_COUNT))

#define LSM_ABORT_RETRY_COUNT (5)
#define LSM_ABORT_WAIT_TIMEOUT_NS (30 * NSECS_PER_MSEC)

#define MAX_MULTI_SOUND_MODELS (8)
#define MAX_MULTI_SM_CONF_LEVELS (8)

#ifdef LSM_EVENT_TIMESTAMP_MODE_SUPPORT
  typedef struct snd_lsm_event_status_v3 st_lsm_event_status_t;
#else
  typedef struct snd_lsm_event_status st_lsm_event_status_t;
#endif

#ifdef LSM_POLLING_ENABLE_SUPPORT
  typedef struct snd_lsm_poll_enable st_lsm_poll_enable_t;
#else
  typedef void* st_lsm_poll_enable_t;
#endif

#ifdef LSM_DET_EVENT_TYPE_GENERIC
  typedef struct snd_lsm_det_event_type st_lsm_det_event_type_t;
#else
  typedef void* st_lsm_det_event_type_t;
#endif

enum st_lsm_ape_control {
    ST_LSM_START = 1,
    ST_LSM_STOP_LAB,
    ST_LSM_ABORT_EVENT,
    ST_LSM_STOP
};

struct multi_sm_conf_levels {
    uint32_t model_id;
    uint32_t num_conf_levels;
    uint32_t conf_levels[MAX_MULTI_SM_CONF_LEVELS];
};

struct multi_sm_reg_sm {
    uint32_t model_id;
    uint32_t model_size;
};

#if (SNDRV_LSM_VERSION >= SNDRV_PROTOCOL_VERSION(0, 3, 2))
#define LSM_MULTI_SM_SUPPORT (1)
#else
#define LSM_MULTI_SM_SUPPORT (0)
#endif

#ifdef LSM_MULTI_SM_SUPPORT
  typedef struct multi_sm_reg_sm st_lsm_reg_sm_header_t;
  typedef struct multi_sm_conf_levels st_lsm_conf_levels_t;
#else
  typedef void* st_lsm_reg_sm_header_t;
  typedef void* st_lsm_conf_levels_t;
#endif

#define DET_EVENT_CONFIDENCE_LEVELS_BIT (1 << 0)
#define DET_EVENT_KEYWORD_INDEX_BIT (1 << 1)
#define DET_EVENT_TIMESTAMP_INFO_BIT (1 << 2)
#define DET_EVENT_CHANNEL_INDEX_INFO_BIT (1 << 3)
#define DET_EVENT_MULTI_MODEL_RESULT_INFO_BIT (1 << 4)

struct lsm_param_smm_th_config {
    uint32_t minor_version;
    uint32_t smm_threshold;
}__packed;

struct lsm_setparam_payload {
    uint32_t data_payload_size;
    uint32_t data_payload_addr_lsw;
    uint32_t data_payload_addr_msw;
    uint32_t mem_map_handle;
}__packed;

struct lsm_param_payload {
    uint32_t module_id;
    uint32_t param_id;
    uint32_t p_size;
}__packed;

struct lsm_param_payload_v2 {
    uint32_t module_id;
    uint16_t instance_id;
    uint16_t reserved;
    uint32_t param_id;
    uint32_t p_size;
}__packed;

struct lsm_param_custom_config {
    struct lsm_setparam_payload set_param;
    struct lsm_param_payload common;
}__packed;

struct version_arch_payload {
    unsigned int version;
    char arch[64];
}__packed;

struct st_hw_session_lsm {
    st_hw_session_t common;
    struct st_lsm_params lsm_usecase;
    pthread_t callback_thread;
    bool exit_callback_thread;

    pthread_t buffer_thread;
    bool exit_buffer_thread;

    int pcm_id;
    struct pcm *pcm;
    struct pcm_config  lab_config;

    bool use_topology_intf;

    struct st_hw_session *transit_ses;

    /* Buffers used in FTRT mode */
    unsigned char *lab_drv_buf; /* small buffer to hold one chunk received from
                                   pcm read */
    unsigned int lab_drv_buf_size;
    unsigned int unread_bytes;
    pthread_cond_t cond;
    pthread_mutex_t lock;

    bool exit_lab_processing;
    bool lab_processing_active;
    pthread_cond_t callback_thread_cond;
    pthread_mutex_t callback_thread_lock;

    bool lab_buffers_allocated;
    bool lab_on_detection;

    unsigned int bytes_written;
    long first_stage_det_event_timestamp;
    bool move_client_ptr;
    uint16_t num_stages;

    char *use_case;
    char *port_ctrl;
};

typedef struct st_hw_session_lsm st_hw_session_lsm_t;

int st_hw_sess_lsm_init(st_hw_session_t *const p_ses,
   hw_ses_event_callback_t cb, void *cookie, st_exec_mode_t exec_mode,
   struct st_vendor_info *v_info, sound_model_handle_t sm_handle,
   struct sound_trigger_device *stdev);
void st_hw_sess_lsm_deinit(st_hw_session_t *const p_ses);
