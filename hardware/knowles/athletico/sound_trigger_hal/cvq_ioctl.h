/*
 * Copyright (C) 2018 Knowles Electronics
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

#ifndef _CVQ_IOCTL_H
#define _CVQ_IOCTL_H

#include "iaxxx_odsp_hw.h"
#include <audio_route/audio_route.h>
#include <tinyalsa/asoundlib.h>

#define HOTWORD_MASK 0x1
#define AMBIENT_MASK 0x2
#define ENTITY_MASK  0x4
#define PLUGIN1_MASK (HOTWORD_MASK | WAKEUP_MASK)
#define PLUGIN2_MASK (AMBIENT_MASK | ENTITY_MASK)
#define WAKEUP_MASK  0x8
#define OSLO_MASK  0x10
#define CHRE_MASK  0x20

#define HOTWORD_EVT_SRC_ID            IAXXX_SYSID_PLUGIN_INSTANCE_0
#define AMBIENT_EVT_SRC_ID            IAXXX_SYSID_PLUGIN_INSTANCE_2
#define CHRE_EVT_SRC_ID               IAXXX_SYSID_PLUGIN_INSTANCE_5
// HOST1 Plugin instances start from 8 to 15
// plugin 8 is assigned for Oslo buffer and 9 for Oslo plugin
#define OSLO_EVT_SRC_ID               IAXXX_SYSID_PLUGIN_INSTANCE_9

#define HOTWORD_PKG_ID      11
#define HOTWORD_PLUGIN_IDX  0
#define HOTWORD_INSTANCE_ID 0
#define HOTWORD_PRIORITY    1

#define AMBIENT_PKG_ID          12
#define AMBIENT_PLUGIN_IDX      0
#define AMBIENT_INSTANCE_ID     2
#define AMBIENT_PRIORITY        1

#define SENSOR_PKG_ID           0
#define SENSOR_PLUGIN_IDX       0
#define SENSOR_INSTANCE_ID      9
#define SENSOR_PRIORITY         2
#define SENSOR_PRESENCE_MODE    0
#define SENSOR_DETECTED_MODE    1
#define SENSOR_MAX_MODE         2
#define OSLO_DATA_EVENT_ID      1
#define OSLO_CONFIGURED         (0x201)
#define OSLO_DESTROYED          (0x202)
#define OSLO_EP_DISCONNECT      (0x205)
#define CHRE_EP_DISCONNECT      (0x206)
#define OSLO_BUF_INSTANCE_ID    8
#define OSLO_BUF_PRIORITY       2

#define AEC_PKG_ID       7
#define AEC_PLUGIN_IDX   0
#define AEC_INSTANCE_ID  4
#define AEC_PRIORITY     1

#define CHRE_PLUGIN_IDX      0
#define CHRE_INSTANCE_ID     5
#define CHRE_EVT_ID          3
#define CHRE_EVT_PARAM_ID    8
#define CHRE_BUF_SIZE        64000 // 320B (160 samples) per 10ms -> 64000B per 2s
#define CHRE_CONFIGURED      (0x203)
#define CHRE_DESTROYED       (0x204)
#define CHRE_EVT_MASK        (0x1f)

#define IAXXX_SYSID_CTRL_MGR_CM4 0x2610
#define IAXXX_HOST1_WAKEUP_EVENT_ID 4

#define MIXER_PKG_ID       5
#define MIXER_PLUGIN_IDX   0
#define MIXER_INSTANCE_ID  7
#define MIXER_PRIORITY     1

#define SRC_PKG_ID       3
#define SRC_PLUGIN_IDX   0
#define SRC_PRIORITY     1
#define SRC_MIC_INSTANCE_ID  6
#define SRC_AMP_INSTANCE_ID  7

#define BUF_PKG_ID          4
#define BUF_PLUGIN_IDX      0
#define BUF_INSTANCE_ID     1
#define BUF_PRIORITY        1

#define DA_BUF_INSTANCE_ID    3

#define HOTWORD_DETECTION   0
#define AMBIENT_DETECTION   1
#define ENTITY_DETECTION    2
#define WAKEUP_DETECTION    3

#define HOTWORD_SLOT_ID     1
#define AMBIENT_SLOT_ID     3
#define ENTITY_SLOT_ID      5
#define WAKEUP_SLOT_ID      6

#define HOTWORD_UNLOAD_PARAM_ID          1
#define AMBIENT_UNLOAD_PARAM_ID          1
#define AMBIENT_RESET_PARAM_ID           2
#define AMBIENT_GET_MODEL_STATE_PARAM_ID 7
#define HOTWORD_GET_VERSION_PARAM_ID     8
#define AMBIENT_GET_VERSION_PARAM_ID     8
#define HOTWORD_DEFAULT_VER              0
#define AMBIENT_DEFAULT_VER              0

enum {
    PARAM_BLOCK_GET_MIN = 0,
    PARAM_BLOCK_GET_RECOGNITION_PAYLOAD = 100,
    PARAM_BLOCK_GET_DSP_IDENTIFIER,
    PARAM_BLOCK_GET_MAX,
};

#define BUFFER_PACKAGE              "BufferPackage.bin"
#define BUFFER_CONFIG_OSLO_VAL      "BufferConfigValOslo.bin"
#define BUFFER_CONFIG_VAL_MULTI_SEC "BufferConfigVal.bin"
#define BUFFER_CONFIG_VAL_2_SEC     "BufferConfigVal2Sec.bin"
#define BUFFER_CONFIG_VAL_CHRE      "BufferConfigValCHRE.bin"
#define OK_GOOGLE_PACKAGE           "OkGooglePackage.bin"
#define AMBIENT_EC_PACKAGE          "AmbientECPackage.bin"
#define AMBIENT_DA_PACKAGE          "AmbientDAPackage.bin"
#define SENSOR_PACKAGE              "OsloSensorPackage.bin"
#define SENSOR_CONFIG_VAL           "OsloSensorConfig.bin"
#define ECHOCANCELLER_PACKAGE       "EchoCancellerPackage.bin"
#define MIXER_PACKAGE               "AScalarSimpleMixerPackage.bin"
#define SRC_PACKAGE                 "SampleRateConverterPackage.bin"
#define SRC_CONFIG                  "SampleRateConverterCreateCfgBlkGen.bin"

#define PAYLOAD_MAX_STRING_LEN 64

/* The routing definition depend on the platform
   and the features plugin.
 */
enum {
    ST_ROUTE_MIN = 0,

/* Input Port routing definition */
    ST_MIC_ROUTE_INT_CLK = ST_ROUTE_MIN,
    ST_MIC_ROUTE_EXT_CLK,
    ST_BARGEIN_AMP_REF,
    ST_BARGEIN_AMP_REF_48K,

/* Plugin routing definition */
    ST_HOTWORD_WITHOUT_BARGEIN,
    ST_HOTWORD_WITH_BARGEIN,
    ST_HOTWORD_BUFFER_WITHOUT_BARGEIN,
    ST_HOTWORD_BUFFER_WITH_BARGEIN,
    ST_AMBIENT_WITHOUT_BARGEIN,
    ST_AMBIENT_WITH_BARGEIN,
    ST_AMBIENT_BUFFER_WITHOUT_BARGEIN,
    ST_AMBIENT_BUFFER_WITH_BARGEIN,
    ST_BARGEIN_ROUTE,
    ST_CHRE_WITHOUT_BARGEIN,
    ST_CHRE_WITH_BARGEIN,
    ST_SRC_ROUTE_MIC,
    ST_SRC_ROUTE_AMP_REF,
    ST_SENSOR_ROUTE,
    ST_ROUTE_MAX,
};

enum clock_type {
    INTERNAL_OSCILLATOR,
    EXTERNAL_OSCILLATOR
};

enum src_type {
    SRC_MIC,
    SRC_AMP_REF
};

enum strm_type {
    STRM_16K,
    STRM_48K
};

enum sthal_mode {
    IN_CALL,
    CON_DISABLED_ST,
    CON_DISABLED_CAPTURE,
    CON_ENABLED_ST,
    CON_ENABLED_CAPTURE_ST
};

typedef enum {
    TRANSIT_NONE,
    TRANSIT_SETUP_AEC
} transit_case_t;

#define PLUGIN_DEF_CONFIG_ID    0

int write_model(struct iaxxx_odsp_hw *odsp_hdl, unsigned char *data,
                int length, int kw_type);
int get_model_state(struct iaxxx_odsp_hw *odsp_hdl, const uint32_t inst_id,
                    const uint32_t param_val);
int get_event(struct iaxxx_odsp_hw *odsp_hdl,
            struct iaxxx_get_event_info *ge);
int setup_chip(struct iaxxx_odsp_hw *odsp_hdl);
int setup_buffer_package(struct iaxxx_odsp_hw *odsp_hdl);
int destroy_buffer_package(struct iaxxx_odsp_hw *odsp_hdl);
int setup_hotword_package(struct iaxxx_odsp_hw *odsp_hdl);
int destroy_hotword_package(struct iaxxx_odsp_hw *odsp_hdl);
int get_hotword_info(struct iaxxx_odsp_hw *odsp_hdl,
                     unsigned int *hotword_version, void *supported_model_arch);
int setup_ambient_package(struct iaxxx_odsp_hw *odsp_hdl);
int destroy_ambient_package(struct iaxxx_odsp_hw *odsp_hdl);
int get_ambient_info(struct iaxxx_odsp_hw *odsp_hdl,
                     unsigned int *ambient_version, void *supported_model_arch);
int setup_aec_package(struct iaxxx_odsp_hw *odsp_hdl);
int destroy_aec_package(struct iaxxx_odsp_hw *odsp_hdl);
int setup_chre_package(struct iaxxx_odsp_hw *odsp_hdl);
int destroy_chre_package(struct iaxxx_odsp_hw *odsp_hdl);
int setup_sensor_package(struct iaxxx_odsp_hw *odsp_hdl);
int destroy_sensor_package(struct iaxxx_odsp_hw *odsp_hdl);
int setup_mixer_package(struct iaxxx_odsp_hw *odsp_hdl);
int destroy_mixer_package(struct iaxxx_odsp_hw *odsp_hdl);
int setup_src_package(struct iaxxx_odsp_hw *odsp_hdl);
int destroy_src_package(struct iaxxx_odsp_hw *odsp_hdl);

int setup_music_buffer(struct iaxxx_odsp_hw *odsp_hdl);
int destroy_music_buffer(struct iaxxx_odsp_hw *odsp_hdl);
int setup_howord_buffer(struct iaxxx_odsp_hw *odsp_hdl);
int destroy_howord_buffer(struct iaxxx_odsp_hw *odsp_hdl);
int setup_src_plugin(struct iaxxx_odsp_hw *odsp_hdl, enum src_type st);
int destroy_src_plugin(struct iaxxx_odsp_hw *odsp_hdl, enum src_type st);
int set_hotword_buffer_route(struct audio_route *route_hdl, bool bargein);
int tear_hotword_buffer_route(struct audio_route *route_hdl, bool bargein);
int enable_mic_route(struct audio_route *route_hdl, bool enable,
                    enum clock_type ct);
int enable_amp_ref_route(struct audio_route *route_hdl, bool enable,
                         enum strm_type strmt);
int enable_src_route(struct audio_route *route_hdl, bool enable, enum src_type st);
int set_sensor_route(struct audio_route *route_hdl, bool enable);
int set_ambient_state(struct iaxxx_odsp_hw *odsp_hdl, unsigned int current);
int tear_ambient_state(struct iaxxx_odsp_hw *odsp_hdl, unsigned int current);
int set_ambient_route(struct audio_route *route_hdl, bool bargein);
int tear_ambient_route(struct audio_route *route_hdl, bool bargein);
int set_hotword_state(struct iaxxx_odsp_hw *odsp_hdl, unsigned int current);
int tear_hotword_state(struct iaxxx_odsp_hw *odsp_hdl, unsigned int current);
int set_hotword_route(struct audio_route *route_hdl, bool bargein);
int tear_hotword_route(struct audio_route *route_hdl, bool bargein);
int set_chre_audio_route(struct audio_route *route_hdl, bool bargein);
int tear_chre_audio_route(struct audio_route *route_hdl, bool bargein);
int reset_ambient_plugin(struct iaxxx_odsp_hw *odsp_hdl);
int enable_bargein_route(struct audio_route *route_hdl, bool enable);
int set_music_buffer_route(struct audio_route *route_hdl, bool downlink);
int tear_music_buffer_route(struct audio_route *route_hdl, bool downlink);

int flush_model(struct iaxxx_odsp_hw *odsp_hdl, int kw_type);
int get_entity_param_blk(struct iaxxx_odsp_hw *odsp_hdl, void *payload, unsigned int payload_size);
int get_wakeup_param_blk(struct iaxxx_odsp_hw *odsp_hdl, void *payload, unsigned int payload_size);
int set_default_apll_clk(struct mixer *mixer);
int get_fw_status(struct iaxxx_odsp_hw *odsp_hdl, unsigned int *status);
int reset_fw(struct iaxxx_odsp_hw *odsp_hdl);
int reset_all_route(struct audio_route *route_hdl);
int trigger_sensor_destroy_event(struct iaxxx_odsp_hw *odsp_hdl);
int trigger_chre_destroy_event(struct iaxxx_odsp_hw *odsp_hdl);
int setup_slpi_wakeup_event(struct iaxxx_odsp_hw *odsp_hdl, bool enabled);
#endif /* _CVQ_IOCTL_H */
