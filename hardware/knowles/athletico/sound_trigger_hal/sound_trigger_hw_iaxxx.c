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

#define LOG_TAG "SoundTriggerHAL"
#define LOG_NDEBUG 0

#include <errno.h>
#include <fcntl.h>
#include <malloc.h>
#include <poll.h>
#include <pthread.h>
#include <sys/ioctl.h>
#include <sys/prctl.h>
#include <log/log.h>
#include <cutils/uevent.h>
#include <cutils/properties.h>
#include <math.h>
#include <dlfcn.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <sys/timerfd.h>

#include <hardware/hardware.h>
#include <hardware_legacy/power.h>

#include "cvq_ioctl.h"
#include "sound_trigger_hw_iaxxx.h"
#include "sound_trigger_intf.h"

#define MAX_GENERIC_SOUND_MODELS    (9)
#define MAX_KEY_PHRASES             (1)
#define MAX_MODELS                  (MAX_GENERIC_SOUND_MODELS + MAX_KEY_PHRASES)

#define MAX_USERS                   (1)
#define MAX_BUFFER_MS               (3000)
#define POWER_CONSUMPTION           (0) // TBD
#define ST_HAL_VERSION              (1)

#define UEVENT_MSG_LEN              (1024)

#define OK_GOOGLE_KW_ID             (0)
#define AMBIENT_KW_ID               (1)
#define ENTITY_KW_ID                (2)
#define WAKEUP_KW_ID                (3)
#define USELESS_KW_ID               (999)

#define CVQ_ENDPOINT                    (IAXXX_SYSID_PLUGIN_1_OUT_EP_0)
#define MUSIC_BUF_ENDPOINT              (IAXXX_SYSID_PLUGIN_3_OUT_EP_1)

#define IAXXX_VQ_EVENT_STR          "IAXXX_VQ_EVENT"
#define IAXXX_RECOVERY_EVENT_STR    "IAXXX_RECOVERY_EVENT"
#define IAXXX_FW_DWNLD_SUCCESS_STR  "IAXXX_FW_DWNLD_SUCCESS"
#define IAXXX_FW_CRASH_EVENT_STR    "IAXXX_CRASH_EVENT"

#define WAKE_LOCK_NAME "sthal_wake_lock"

#define CARD_NAME                          "iaxxx"
#define SOUND_TRIGGER_MIXER_PATH_BASE      "/vendor/etc/sound_trigger_mixer_paths"
#define SOUND_TRIGGER_MIXER_PATH_XML       "/vendor/etc/sound_trigger_mixer_paths_default.xml"

#define MAX_SND_CARD    (8)
#define RETRY_NUMBER    (40)
#define RETRY_US        (1000000)
#define TUNNEL_TIMEOUT  5

#define SENSOR_CREATE_WAIT_TIME_IN_S   (1)
#define SENSOR_CREATE_WAIT_MAX_COUNT   (5)

#define CHRE_CREATE_WAIT_TIME_IN_S   (1)
#define CHRE_CREATE_WAIT_MAX_COUNT   (5)

#define FIRMWARE_READY_WAIT_TIME_IN_S   (4)

#define ST_DEVICE_HANDSET_MIC 1

#ifdef __LP64__
#define ADNC_STRM_LIBRARY_PATH "/vendor/lib64/hw/adnc_strm.primary.default.so"
#else
#define ADNC_STRM_LIBRARY_PATH "/vendor/lib/hw/adnc_strm.primary.default.so"
#endif

static struct sound_trigger_properties_extended_1_3 hw_properties = {
    {
        SOUND_TRIGGER_DEVICE_API_VERSION_1_3, //ST version
        sizeof(struct sound_trigger_properties_extended_1_3)
    },
    {
        "Knowles Electronics",      // implementor
        "Continuous VoiceQ",        // description
        0,                          // library version
        // Version UUID
        { 0x80f7dcd5, 0xbb62, 0x4816, 0xa931, {0x9c, 0xaa, 0x52, 0x5d, 0xf5, 0xc7}},
        MAX_MODELS,                 // max_sound_models
        MAX_KEY_PHRASES,            // max_key_phrases
        MAX_USERS,                  // max_users
        RECOGNITION_MODE_VOICE_TRIGGER | // recognition_mode
        RECOGNITION_MODE_GENERIC_TRIGGER,
        true,                       // capture_transition
        MAX_BUFFER_MS,              // max_capture_ms
        true,                       // concurrent_capture
        false,                      // trigger_in_event
        POWER_CONSUMPTION           // power_consumption_mw
    },
    "", //supported arch
    0,                                      // audio capability
};

struct model_info {
    void *recognition_cookie;
    void *sound_model_cookie;
    sound_model_handle_t model_handle;
    sound_trigger_uuid_t uuid;
    recognition_callback_t recognition_callback;
    sound_model_callback_t sound_model_callback;
    struct sound_trigger_recognition_config *config;
    int kw_id;
    sound_trigger_sound_model_type_t type;

    void *data;
    int data_sz;
    bool is_loaded;
    bool is_active;
    bool is_state_query;
};

struct knowles_sound_trigger_device {
    struct sound_trigger_hw_device device;
    struct model_info models[MAX_MODELS];
    sound_trigger_uuid_t authkw_model_uuid;
    pthread_t callback_thread;
    pthread_t monitor_thread;
    pthread_t transitions_thread;
    pthread_mutex_t lock;
    pthread_cond_t transition_cond;
    pthread_cond_t tunnel_create;
    pthread_cond_t sensor_create;
    pthread_cond_t chre_create;
    pthread_cond_t firmware_ready_cond;
    int opened;
    int send_sock;
    int recv_sock;

    // Information about streaming
    int is_streaming;
    void *adnc_cvq_strm_lib;
    int (*adnc_strm_open)(bool, int, int);
    size_t (*adnc_strm_read)(long, void *, size_t);
    int (*adnc_strm_close)(long);
    long adnc_strm_handle[MAX_MODELS];
    struct timespec adnc_strm_last_read[MAX_MODELS];

    sound_trigger_uuid_t hotword_model_uuid;
    sound_trigger_uuid_t sensor_model_uuid;
    sound_trigger_uuid_t ambient_model_uuid;
    sound_trigger_uuid_t chre_model_uuid;
    sound_trigger_uuid_t entity_model_uuid;
    sound_trigger_uuid_t wakeup_model_uuid;

    bool is_mic_route_enabled;
    bool is_bargein_route_enabled;
    bool is_chre_loaded;
    bool is_buffer_package_loaded;
    bool is_sensor_route_enabled;
    bool is_src_package_loaded;
    bool is_st_hal_ready;
    int hotword_buffer_enable;
    int music_buffer_enable;
    bool is_sensor_destroy_in_prog;
    bool is_chre_destroy_in_prog;

    // conditions indicate AHAL and mic concurrency status
    bool is_concurrent_capture;
    bool is_con_mic_route_enabled;
    bool is_ahal_media_recording;
    bool is_ahal_in_voice_voip_mode;
    bool is_ahal_voice_voip_stop;
    bool is_ahal_voice_voip_start;

    unsigned int current_enable;
    unsigned int recover_model_list;
    unsigned int rx_active_count;
    transit_case_t transit_case;

    struct audio_route *route_hdl;
    struct mixer *mixer;
    struct iaxxx_odsp_hw *odsp_hdl;

    void *audio_hal_handle;
    audio_hw_call_back_t audio_hal_cb;
    unsigned int sthal_prop_api_version;

    int snd_crd_num;
    char mixer_path_xml[NAME_MAX_SIZE];
    bool fw_reset_done_by_hal;

    // sensor stop signal event
    timer_t ss_timer;
    bool ss_timer_created;

    // Chre stop signal event
    timer_t chre_timer;
    bool chre_timer_created;
    unsigned int hotword_version;
};

/*
 * Since there's only ever one sound_trigger_device, keep it as a global so
 * that other people can dlopen this lib to get at the streaming audio.
 */

static struct knowles_sound_trigger_device g_stdev =
{
    .lock = PTHREAD_MUTEX_INITIALIZER,
    .transition_cond = PTHREAD_COND_INITIALIZER,
    .tunnel_create = PTHREAD_COND_INITIALIZER,
    .sensor_create = PTHREAD_COND_INITIALIZER,
    .chre_create = PTHREAD_COND_INITIALIZER,
    .firmware_ready_cond = PTHREAD_COND_INITIALIZER
};

static struct timespec reset_time = {0};

static enum sthal_mode get_sthal_mode(struct knowles_sound_trigger_device *stdev)
{
    enum sthal_mode stmode = CON_DISABLED_ST;

    if (stdev->is_ahal_in_voice_voip_mode == true) {
        stmode = IN_CALL;
        goto exit;
    }

    if (stdev->is_concurrent_capture == false) {
        if (stdev->is_ahal_media_recording == true)
          stmode = CON_DISABLED_CAPTURE;
        else
          stmode = CON_DISABLED_ST;
        goto exit;
    }

    if (stdev->is_con_mic_route_enabled == true ) {
        stmode = CON_ENABLED_CAPTURE_ST;
        goto exit;
    } else {
        stmode = CON_ENABLED_ST;
        goto exit;
    }

    ALOGW("%s: Invalid ST mode, use defualt mode", __func__);

exit:
    //ALOGV("%s: ST mode is %d", __func__, stmode);
    return stmode;
}

static bool can_enable_chre(struct knowles_sound_trigger_device *stdev)
{
    bool ret = false;
    enum sthal_mode stm = get_sthal_mode(stdev);

    if (stm == CON_ENABLED_CAPTURE_ST ||
        stm == CON_ENABLED_ST ||
        stm == CON_DISABLED_ST)
        ret = true;
    return ret;
}

static bool can_update_recover_list(struct knowles_sound_trigger_device *stdev)
{
    bool ret = false;
    enum sthal_mode stm = get_sthal_mode(stdev);

    if (stm == IN_CALL || stm == CON_DISABLED_CAPTURE)
        ret = true;
    return ret;
}

static bool is_mic_controlled_by_ahal(struct knowles_sound_trigger_device *stdev)
{
    bool ret = false;

    if (get_sthal_mode(stdev) == CON_ENABLED_CAPTURE_ST)
        ret = true;
    return ret;
}

static bool check_uuid_equality(sound_trigger_uuid_t uuid1,
                                sound_trigger_uuid_t uuid2)
{
    if (uuid1.timeLow != uuid2.timeLow ||
        uuid1.timeMid != uuid2.timeMid ||
        uuid1.timeHiAndVersion != uuid2.timeHiAndVersion ||
        uuid1.clockSeq != uuid2.clockSeq) {
        return false;
    }

    for (int i = 0; i < 6; i++) {
        if(uuid1.node[i] != uuid2.node[i]) {
            return false;
        }
    }

    return true;
}

bool str_to_uuid(char* uuid_str, sound_trigger_uuid_t* uuid)
{
    if (uuid_str == NULL) {
        ALOGI("Invalid str_to_uuid input.");
        return false;
    }

    int tmp[10];
    if (sscanf(uuid_str, "%08x-%04x-%04x-%04x-%02x%02x%02x%02x%02x%02x",
            tmp, tmp+1, tmp+2, tmp+3, tmp+4, tmp+5,
            tmp+6, tmp+7, tmp+8, tmp+9) < 10) {
        ALOGI("Invalid UUID, got: %s", uuid_str);
        return false;
    }
    uuid->timeLow = (unsigned int)tmp[0];
    uuid->timeMid = (unsigned short)tmp[1];
    uuid->timeHiAndVersion = (unsigned short)tmp[2];
    uuid->clockSeq = (unsigned short)tmp[3];
    uuid->node[0] = (unsigned char)tmp[4];
    uuid->node[1] = (unsigned char)tmp[5];
    uuid->node[2] = (unsigned char)tmp[6];
    uuid->node[3] = (unsigned char)tmp[7];
    uuid->node[4] = (unsigned char)tmp[8];
    uuid->node[5] = (unsigned char)tmp[9];

    return true;
}

static int find_empty_model_slot(struct knowles_sound_trigger_device *st_dev)
{
    int i = -1;
    for (i = 0; i < MAX_MODELS; i++) {
        if (st_dev->models[i].is_loaded == false)
            break;
    }

    if (i >= MAX_MODELS) {
        i = -1;
    }

    return i;
}

static int find_handle_for_kw_id(
                        struct knowles_sound_trigger_device *st_dev, int kw_id)
{
    int i = 0;
    for (i = 0; i < MAX_MODELS; i++) {
        if (kw_id == st_dev->models[i].kw_id)
            break;
    }

    return i;
}

static int find_handle_for_uuid(
                        struct knowles_sound_trigger_device *stdev,
                        sound_trigger_uuid_t uuid)
{
    int i = 0;
    for (i = 0; i < MAX_MODELS; i++) {
        if (check_uuid_equality(uuid, stdev->models[i].uuid))
            break;
    }

    if (i == MAX_MODELS)
        return -1;
    else
        return i;
}

static bool is_any_model_active(struct knowles_sound_trigger_device *stdev) {
    int i = 0;
    for (i = 0; i < MAX_MODELS; i++) {
        if (stdev->models[i].is_active == true) {
            break;
        }
    }

    if (i == MAX_MODELS)
        return false;
    else
        return true;
}

static bool is_any_model_loaded(struct knowles_sound_trigger_device *stdev) {
    int i = 0;
    for (i = 0; i < MAX_MODELS; i++) {
        if (stdev->models[i].is_loaded == true) {
            break;
        }
    }

    if (i == MAX_MODELS)
        return false;
    else
        return true;
}

static void reg_hal_event_session(
                                struct sound_trigger_recognition_config *config,
                                sound_model_handle_t handle)
{
    struct knowles_sound_trigger_device *stdev = &g_stdev;
    struct sound_trigger_event_info event_info;
    /*
     * Register config and capture_handle of trigger sound model to audio hal
     * It only register while request capturing buffer.
     */
    if (config->capture_requested && stdev->audio_hal_cb) {
        ALOGD("%s: ST_EVENT_SESSION_REGISTER capture_handle %d model %p",
            __func__, config->capture_handle, &stdev->models[handle]);
        event_info.st_ses.p_ses = (void *)&stdev->models[handle];
        event_info.st_ses.config = stdev_hotword_pcm_config;
        event_info.st_ses.capture_handle = config->capture_handle;
        event_info.st_ses.pcm = NULL;
        stdev->audio_hal_cb(ST_EVENT_SESSION_REGISTER, &event_info);
    }
}

static void dereg_hal_event_session(
                                struct sound_trigger_recognition_config *config,
                                sound_model_handle_t handle)
{
    struct knowles_sound_trigger_device *stdev = &g_stdev;
    struct sound_trigger_event_info event_info;
    /*
     * Indicate to audio hal that streaming is stopped.
     * Stop capturing data from STHAL.
     */
    if (config->capture_requested && stdev->audio_hal_cb) {
        ALOGD("%s: ST_EVENT_SESSION_DEREGISTER capture_handle %d model %p",
            __func__, config->capture_handle, &stdev->models[handle]);
        event_info.st_ses.p_ses = (void *)&stdev->models[handle];
        event_info.st_ses.capture_handle = config->capture_handle;
        event_info.st_ses.pcm = NULL;
        stdev->audio_hal_cb(ST_EVENT_SESSION_DEREGISTER, &event_info);
    }
}


static char *stdev_keyphrase_event_alloc(sound_model_handle_t handle,
                                struct sound_trigger_recognition_config *config,
                                int recognition_status)
{
    char *data;
    struct sound_trigger_phrase_recognition_event *event;
    data = (char *)calloc(1,
                        sizeof(struct sound_trigger_phrase_recognition_event));
    if (!data)
        return NULL;
    event = (struct sound_trigger_phrase_recognition_event *)data;
    event->common.status = recognition_status;
    event->common.type = SOUND_MODEL_TYPE_KEYPHRASE;
    event->common.model = handle;
    event->common.capture_available = false;

    if (config) {
        unsigned int i;

        event->num_phrases = config->num_phrases;
        if (event->num_phrases > SOUND_TRIGGER_MAX_PHRASES)
            event->num_phrases = SOUND_TRIGGER_MAX_PHRASES;
        for (i = 0; i < event->num_phrases; i++)
            memcpy(&event->phrase_extras[i],
                &config->phrases[i],
                sizeof(struct sound_trigger_phrase_recognition_extra));
    }

    event->num_phrases = 1;
    event->phrase_extras[0].confidence_level = 100;
    event->phrase_extras[0].num_levels = 1;
    event->phrase_extras[0].levels[0].level = 100;
    event->phrase_extras[0].levels[0].user_id = 0;
    /*
     * Signify that all the data is comming through streaming
     * and not through the buffer.
     */
    event->common.capture_available = true;
    event->common.capture_delay_ms = 0;
    event->common.capture_preamble_ms = 0;
    event->common.audio_config = AUDIO_CONFIG_INITIALIZER;
    event->common.audio_config.sample_rate = 16000;
    event->common.audio_config.channel_mask = AUDIO_CHANNEL_IN_MONO;
    event->common.audio_config.format = AUDIO_FORMAT_PCM_16_BIT;

    return data;
}

static char *stdev_generic_event_alloc(int model_handle, void *payload,
                                    unsigned int payload_size,
                                    int recognition_status)
{
    char *data;
    struct sound_trigger_generic_recognition_event *event;

    data = (char *)calloc(1,
                        sizeof(struct sound_trigger_generic_recognition_event) +
                        payload_size);
    if (!data) {
        ALOGE("%s: Failed to allocate memory for recog event", __func__);
        return NULL;
    }

    event = (struct sound_trigger_generic_recognition_event *)data;
    event->common.status = recognition_status;
    event->common.type = SOUND_MODEL_TYPE_GENERIC;
    event->common.model = model_handle;

    /*
     * Signify that all the data is comming through streaming and
     * not through the buffer.
     */
    event->common.capture_available = true;
    event->common.audio_config = AUDIO_CONFIG_INITIALIZER;
    event->common.audio_config.sample_rate = 16000;
    event->common.audio_config.channel_mask = AUDIO_CHANNEL_IN_MONO;
    event->common.audio_config.format = AUDIO_FORMAT_PCM_16_BIT;

    if (payload && payload_size > 0) {
        event->common.data_size = payload_size;
        event->common.data_offset =
                        sizeof(struct sound_trigger_generic_recognition_event);

        memcpy((data + event->common.data_offset), payload, payload_size);
    }

    return data;
}

static void stdev_close_term_sock(struct knowles_sound_trigger_device *stdev)
{
    if (stdev->send_sock >= 0) {
        close(stdev->send_sock);
        stdev->send_sock = -1;
    }

    if (stdev->recv_sock >= 0) {
        close(stdev->recv_sock);
        stdev->recv_sock = -1;
    }
}

static bool is_uuid_in_recover_list(struct knowles_sound_trigger_device *stdev,
                                    sound_model_handle_t handle)
{
    int mask = 0;
    sound_trigger_uuid_t target_uuid = stdev->models[handle].uuid;

    if (check_uuid_equality(target_uuid, stdev->chre_model_uuid)) {
        mask = CHRE_MASK;
    } else if (check_uuid_equality(target_uuid, stdev->hotword_model_uuid) ||
               check_uuid_equality(target_uuid, stdev->wakeup_model_uuid)) {
        mask = PLUGIN1_MASK;
    } else if (check_uuid_equality(target_uuid, stdev->ambient_model_uuid) ||
               check_uuid_equality(target_uuid, stdev->entity_model_uuid)) {
        mask = PLUGIN2_MASK;
    } else {
       //ALOGV("%s: Invalid uuid.", __func__);
    }

    return (stdev->recover_model_list & mask) ? true : false;
}

static void update_recover_list(struct knowles_sound_trigger_device *stdev,
                                sound_model_handle_t handle,
                                bool enable)
{
    int mask = 0;

    sound_trigger_uuid_t target_uuid = stdev->models[handle].uuid;

    ALOGD("%s: handle %d enable %d", __func__, handle, enable);
    if (check_uuid_equality(target_uuid, stdev->chre_model_uuid)) {
        mask = CHRE_MASK;
    } else if (check_uuid_equality(target_uuid, stdev->hotword_model_uuid) ||
               check_uuid_equality(target_uuid, stdev->wakeup_model_uuid)) {
        mask = PLUGIN1_MASK;
    } else if (check_uuid_equality(target_uuid, stdev->ambient_model_uuid) ||
               check_uuid_equality(target_uuid, stdev->entity_model_uuid)) {
        mask = PLUGIN2_MASK;
    } else {
       //ALOGV("%s: Invalid uuid.", __func__);
    }

    if (enable)
        stdev->recover_model_list |= mask;
    else
        stdev->recover_model_list &= ~mask;

    return;
}

int check_firmware_ready(struct knowles_sound_trigger_device *stdev)
{
    int err = 0;

    if (stdev->is_st_hal_ready == false) {
        struct timespec ts;
        ALOGW("%s: ST HAL is not ready yet", __func__);
        clock_gettime(CLOCK_REALTIME, &ts);
        ts.tv_sec += FIRMWARE_READY_WAIT_TIME_IN_S;
        err = pthread_cond_timedwait(&stdev->firmware_ready_cond,
                               &stdev->lock, &ts);
        if (err == ETIMEDOUT) {
            ALOGE("%s: WARNING: firmware downloading timed out after %ds",
                  __func__, FIRMWARE_READY_WAIT_TIME_IN_S);
            err = -ENODEV;
        } else if (err != 0)
            err = -EINVAL;
    }

    return err;
}

static int check_and_setup_src_package(
                                    struct knowles_sound_trigger_device *stdev)
{
    int err = 0;

    if (stdev->is_src_package_loaded == false) {
        err = setup_src_package(stdev->odsp_hdl);
        stdev->is_src_package_loaded = true;
    } else {
        ALOGD("%s: SRC package is already loaded", __func__);
    }

    return err;
}

static int check_and_destroy_src_package(
                                    struct knowles_sound_trigger_device *stdev)
{
    int err = 0;

    if (!is_any_model_active(stdev) && stdev->is_src_package_loaded == true) {
        err = destroy_src_package(stdev->odsp_hdl);
        stdev->is_src_package_loaded = false;
    } else {
        ALOGD("%s: Can't destroy package due to active status", __func__);
    }

    return err;
}

static int check_and_setup_buffer_package(
                                    struct knowles_sound_trigger_device *stdev)
{
    int err = 0;

    if (stdev->is_buffer_package_loaded == false) {
        err = setup_buffer_package(stdev->odsp_hdl);
        stdev->is_buffer_package_loaded = true;
    } else {
        ALOGD("%s: Buffer package is already loaded", __func__);
    }

    return err;
}

static int check_and_destroy_buffer_package(
                                    struct knowles_sound_trigger_device *stdev)
{
    int err = 0;

    if (!is_any_model_active(stdev) &&
        stdev->is_buffer_package_loaded &&
        (!stdev->is_sensor_destroy_in_prog &&
        !stdev->is_sensor_route_enabled) &&
        (!stdev->is_chre_destroy_in_prog &&
        !stdev->is_chre_loaded)) {

        err = destroy_buffer_package(stdev->odsp_hdl);
        stdev->is_buffer_package_loaded = false;
    } else {
        ALOGD("%s: Can't destroy package due to active status", __func__);
    }

    return err;
}

static int setup_package(struct knowles_sound_trigger_device *stdev,
                        struct model_info *model)
{
    int err = 0;

    if (check_uuid_equality(model->uuid, stdev->chre_model_uuid)) {
        if (!(stdev->current_enable & CHRE_MASK)) {
            err = setup_chre_package(stdev->odsp_hdl);
            if (err != 0) {
                ALOGE("Failed to load CHRE package");
            }
        }
        stdev->current_enable = stdev->current_enable | CHRE_MASK;
    } else if (check_uuid_equality(model->uuid, stdev->hotword_model_uuid)) {
        if (!(stdev->current_enable & PLUGIN1_MASK)) {
            err = setup_hotword_package(stdev->odsp_hdl);
            if (err != 0) {
                ALOGE("Failed to load Hotword package");
            }
        }
        err = write_model(stdev->odsp_hdl, model->data, model->data_sz,
                        model->kw_id);
        if (err != 0) {
            ALOGE("Failed to write Hotword model");
        }

        //setup model state.
        stdev->current_enable = stdev->current_enable | HOTWORD_MASK;
        err = set_hotword_state(stdev->odsp_hdl, stdev->current_enable);
        if (err != 0) {
            ALOGE("Failed to set Hotword state");
        }
    } else if (check_uuid_equality(model->uuid, stdev->wakeup_model_uuid)) {
        if (!(stdev->current_enable & PLUGIN1_MASK)) {
            err = setup_hotword_package(stdev->odsp_hdl);
            if (err != 0) {
                ALOGE("Failed to load Hotword package");
            }
        }
        err = write_model(stdev->odsp_hdl, model->data, model->data_sz,
                        model->kw_id);
        if (err != 0) {
            ALOGE("Failed to write Wakeup model");
        }

        //setup model state.
        stdev->current_enable = stdev->current_enable | WAKEUP_MASK;
        err = set_hotword_state(stdev->odsp_hdl, stdev->current_enable);
        if (err != 0) {
            ALOGE("Failed to set Wakeup state");
        }
    } else if (check_uuid_equality(model->uuid, stdev->ambient_model_uuid)) {
        if (!(stdev->current_enable & PLUGIN2_MASK)) {
            err = setup_ambient_package(stdev->odsp_hdl);
            if (err != 0) {
                ALOGE("Failed to load Ambient package");
            }
        } else {
            // tear down plugin2 for writing new model data.
            err = tear_ambient_state(stdev->odsp_hdl,
                                    stdev->current_enable);
        }
        err = write_model(stdev->odsp_hdl, model->data,
                        model->data_sz, model->kw_id);
        if (err != 0) {
            ALOGE("Failed to write Ambient model");
        }

        //setup model state.
        stdev->current_enable = stdev->current_enable | AMBIENT_MASK;
        err = set_ambient_state(stdev->odsp_hdl, stdev->current_enable);
        if (err != 0) {
            ALOGE("Failed to set Ambient state");
        }

    } else if (check_uuid_equality(model->uuid, stdev->entity_model_uuid)) {
        if (!(stdev->current_enable & PLUGIN2_MASK)) {
            err = setup_ambient_package(stdev->odsp_hdl);
            if (err != 0) {
                ALOGE("Failed to load Ambient package");
            }
        } else {
            // tear down plugin2 for writing new model data.
            err = tear_ambient_state(stdev->odsp_hdl,
                                    stdev->current_enable);
        }
        err = write_model(stdev->odsp_hdl, model->data,
                        model->data_sz, model->kw_id);
        if (err != 0) {
            ALOGE("Failed to write Entity model");
        }

        //setup model state.
        stdev->current_enable = stdev->current_enable | ENTITY_MASK;
        err = set_ambient_state(stdev->odsp_hdl, stdev->current_enable);
        if (err != 0) {
            ALOGE("Failed to set Entity state");
        }
    }

    return err;
}

static int setup_buffer(struct knowles_sound_trigger_device *stdev,
                        struct model_info *model,
                        bool enabled)
{
    int err = 0;
    if (enabled) {
        if ((check_uuid_equality(model->uuid, stdev->hotword_model_uuid))
            || (check_uuid_equality(model->uuid, stdev->wakeup_model_uuid))) {

            stdev->hotword_buffer_enable++;
            if (stdev->hotword_buffer_enable > 1) {
                ALOGD("%d models are using hotword buffer",
                      stdev->hotword_buffer_enable);
                goto exit;
            }
            err = setup_howord_buffer(stdev->odsp_hdl);
            if (err != 0) {
                stdev->hotword_buffer_enable--;
                ALOGE("Failed to create the buffer plugin");
            }
        } else if ((check_uuid_equality(model->uuid, stdev->ambient_model_uuid))
            || (check_uuid_equality(model->uuid, stdev->entity_model_uuid))) {

            stdev->music_buffer_enable++;
            if (stdev->music_buffer_enable > 1) {
                ALOGD("%d models are using music buffer",
                      stdev->music_buffer_enable);
                goto exit;
            }
            err = setup_music_buffer(stdev->odsp_hdl);
            if (err != 0) {
                stdev->music_buffer_enable--;
                ALOGE("Failed to load music buffer package");
            }
        }
    } else {
        if ((check_uuid_equality(model->uuid, stdev->hotword_model_uuid))
           || (check_uuid_equality(model->uuid, stdev->wakeup_model_uuid))) {
            if (stdev->hotword_buffer_enable == 0) {
                ALOGW("Invalid call for setup buffer");
                goto exit;
            }
            stdev->hotword_buffer_enable--;
            if (stdev->hotword_buffer_enable != 0) {
                ALOGD("hotword buffer is still used by %d models",
                      stdev->hotword_buffer_enable);
                goto exit;
            }
            err = destroy_howord_buffer(stdev->odsp_hdl);

            if (err != 0) {
                ALOGE("Failed to unload hotword buffer package");
            }

        } else if ((check_uuid_equality(model->uuid, stdev->ambient_model_uuid))
           || (check_uuid_equality(model->uuid, stdev->entity_model_uuid))) {
            if (stdev->music_buffer_enable == 0) {
                ALOGW("Invalid call for setup buffer");
                goto exit;
            }
            stdev->music_buffer_enable--;
            if (stdev->music_buffer_enable != 0) {
                ALOGD("music buffer is still used by %d models",
                      stdev->music_buffer_enable);
                goto exit;
            }
            err = destroy_music_buffer(stdev->odsp_hdl);
            if (err != 0) {
                ALOGE("Failed to unload music buffer package");
            }
       }
    }

exit:
    return err;
}

static int destroy_package(struct knowles_sound_trigger_device *stdev,
                        struct model_info *model)
{
    int err = 0;

    if (check_uuid_equality(model->uuid, stdev->chre_model_uuid)) {
        stdev->current_enable = stdev->current_enable & ~CHRE_MASK;
        if (!(stdev->current_enable & CHRE_MASK)) {
            err = destroy_chre_package(stdev->odsp_hdl);
            if (err != 0) {
                ALOGE("Failed to destroy CHRE package");
            }
        }
    } else if (check_uuid_equality(model->uuid, stdev->hotword_model_uuid)) {
        err = tear_hotword_state(stdev->odsp_hdl, HOTWORD_MASK);
        if (err != 0) {
            ALOGE("Failed to tear Hotword state");
        }

        err = flush_model(stdev->odsp_hdl, model->kw_id);
        if (err != 0) {
            ALOGE("Failed to flush Hotword model");
        }
        stdev->current_enable = stdev->current_enable & ~HOTWORD_MASK;

        if (!(stdev->current_enable & PLUGIN1_MASK)) {
            err = destroy_hotword_package(stdev->odsp_hdl);
            if (err != 0) {
                ALOGE("Failed to destroy Hotword package");
            }
        }

    } else if (check_uuid_equality(model->uuid, stdev->wakeup_model_uuid)) {
        err = tear_hotword_state(stdev->odsp_hdl, WAKEUP_MASK);
        if (err != 0) {
            ALOGE("Failed to tear Wakeup state");
        }

        err = flush_model(stdev->odsp_hdl, model->kw_id);
        if (err != 0) {
            ALOGE("Failed to flush Wakeup model");
        }
        stdev->current_enable = stdev->current_enable & ~WAKEUP_MASK;

        if (!(stdev->current_enable & PLUGIN1_MASK)) {
            err = destroy_hotword_package(stdev->odsp_hdl);
            if (err != 0) {
                ALOGE("Failed to destroy Hotword package");
            }
        }

    } else if (check_uuid_equality(model->uuid, stdev->ambient_model_uuid)) {
        err = tear_ambient_state(stdev->odsp_hdl, AMBIENT_MASK);
        if (err != 0) {
            ALOGE("Failed to tear Ambient state");
        }

        err = flush_model(stdev->odsp_hdl, model->kw_id);
        if (err != 0) {
            ALOGE("Failed to flush Ambient model");
        }
        stdev->current_enable = stdev->current_enable & ~AMBIENT_MASK;

        if (!(stdev->current_enable & PLUGIN2_MASK)) {
            err = destroy_ambient_package(stdev->odsp_hdl);
            if (err != 0) {
                ALOGE("Failed to destroy Ambient package");
            }
        }
    } else if (check_uuid_equality(model->uuid, stdev->entity_model_uuid)) {
        err = tear_ambient_state(stdev->odsp_hdl, ENTITY_MASK);
        if (err != 0) {
            ALOGE("Failed to tear Entity state");
        }

        err = flush_model(stdev->odsp_hdl, model->kw_id);
        if (err != 0) {
            ALOGE("Failed to flush Entity model");
        }
        stdev->current_enable = stdev->current_enable & ~ENTITY_MASK;

        if (!(stdev->current_enable & PLUGIN2_MASK)) {
            err = destroy_ambient_package(stdev->odsp_hdl);
            if (err != 0) {
                ALOGE("Failed to destroy Ambient package");
            }
        }
    }
    return err;
}

static int set_package_route(struct knowles_sound_trigger_device *stdev,
                            sound_trigger_uuid_t uuid,
                            bool bargein)
{
    int ret = 0;
    /*
     *[TODO] Add correct error return value for package route
     * b/119390722 for tracing.
     */
    if (check_uuid_equality(uuid, stdev->chre_model_uuid)) {
        if (stdev->is_chre_loaded == true) {
            set_chre_audio_route(stdev->route_hdl, bargein);
        }
    } else if (check_uuid_equality(uuid, stdev->hotword_model_uuid)) {
        if (!((stdev->current_enable & PLUGIN1_MASK) & ~HOTWORD_MASK)) {
            set_hotword_route(stdev->route_hdl, bargein);
        }
    } else if (check_uuid_equality(uuid, stdev->wakeup_model_uuid)) {
        if (!((stdev->current_enable & PLUGIN1_MASK) & ~WAKEUP_MASK)) {
            set_hotword_route(stdev->route_hdl, bargein);
        }
    } else if (check_uuid_equality(uuid, stdev->ambient_model_uuid)) {
        if (!((stdev->current_enable & PLUGIN2_MASK) & ~AMBIENT_MASK)) {
            set_ambient_route(stdev->route_hdl, bargein);
        }
    } else if (check_uuid_equality(uuid, stdev->entity_model_uuid)) {
        if (!((stdev->current_enable & PLUGIN2_MASK) & ~ENTITY_MASK)) {
            set_ambient_route(stdev->route_hdl, bargein);
        }
    }

    return ret;
}

static int tear_package_route(struct knowles_sound_trigger_device *stdev,
                            sound_trigger_uuid_t uuid,
                            bool bargein)
{
    int ret = 0;
    /*
     *[TODO] Add correct error return value for package route
     * b/119390722 for tracing.
     */
    if (check_uuid_equality(uuid, stdev->chre_model_uuid)) {
        if (stdev->is_chre_loaded == true) {
            tear_chre_audio_route(stdev->route_hdl, bargein);
        }
    } else if (check_uuid_equality(uuid, stdev->hotword_model_uuid)) {
        if (!((stdev->current_enable & PLUGIN1_MASK) & ~HOTWORD_MASK))
            tear_hotword_route(stdev->route_hdl, bargein);
    } else if (check_uuid_equality(uuid, stdev->wakeup_model_uuid)) {
        if (!((stdev->current_enable & PLUGIN1_MASK) & ~WAKEUP_MASK))
            tear_hotword_route(stdev->route_hdl, bargein);
    } else if (check_uuid_equality(uuid, stdev->ambient_model_uuid)) {
        if (!((stdev->current_enable & PLUGIN2_MASK) & ~AMBIENT_MASK))
            tear_ambient_route(stdev->route_hdl, bargein);
    } else if (check_uuid_equality(uuid, stdev->entity_model_uuid)) {
        if (!((stdev->current_enable & PLUGIN2_MASK) & ~ENTITY_MASK))
            tear_ambient_route(stdev->route_hdl, bargein);
    }

    return ret;
}

static int async_setup_aec(struct knowles_sound_trigger_device *stdev)
{
    int ret = 0;
    if (stdev->rx_active_count > 0 &&
        stdev->is_bargein_route_enabled != true &&
        stdev->is_mic_route_enabled != false) {
        ALOGD("%s: Bargein enabling", __func__);
        if (is_mic_controlled_by_ahal(stdev) == false) {
            ret = enable_mic_route(stdev->route_hdl, false,
                                INTERNAL_OSCILLATOR);
            if (ret != 0) {
                ALOGE("Failed to disable mic route with INT OSC");
            }
        }
        ret = setup_src_plugin(stdev->odsp_hdl, SRC_AMP_REF);
        if (ret != 0) {
            ALOGE("Failed to load SRC-amp package");
        }
        ret = enable_src_route(stdev->route_hdl, true, SRC_AMP_REF);
        if (ret != 0) {
            ALOGE("Failed to enable SRC-amp route");
        }

        ret = setup_aec_package(stdev->odsp_hdl);
        if (ret != 0) {
            ALOGE("Failed to load AEC package");
        }
        ret = enable_bargein_route(stdev->route_hdl, true);
        if (ret != 0) {
            ALOGE("Failed to enable buffer route");
        }

        if (is_mic_controlled_by_ahal(stdev) == false) {
            ret = enable_amp_ref_route(stdev->route_hdl, true, STRM_16K);
            if (ret != 0) {
                ALOGE("Failed to enable amp-ref route");
            }
            ret = enable_mic_route(stdev->route_hdl, true,
                                EXTERNAL_OSCILLATOR);
            if (ret != 0) {
                ALOGE("Failed to enable mic route with EXT OSC");
            }
        } else {
            // main mic is turned by media recording
            ret = enable_amp_ref_route(stdev->route_hdl, true, STRM_48K);
            if (ret != 0) {
                ALOGE("Failed to enable amp-ref route");
            }
        }
        stdev->is_bargein_route_enabled = true;

        if (stdev->hotword_buffer_enable) {
            ret = tear_hotword_buffer_route(stdev->route_hdl,
                                !stdev->is_bargein_route_enabled);
            if (ret != 0) {
                ALOGE("Failed to tear old buffer route");
            }
            ret = set_hotword_buffer_route(stdev->route_hdl,
                                stdev->is_bargein_route_enabled);
            if (ret != 0) {
                ALOGE("Failed to enable buffer route");
            }
        }

        if (stdev->music_buffer_enable) {
            ret = tear_music_buffer_route(stdev->route_hdl,
                                !stdev->is_bargein_route_enabled);
            if (ret != 0) {
                ALOGE("Failed to tear old music buffer route");
            }
            ret = set_music_buffer_route(stdev->route_hdl,
                                stdev->is_bargein_route_enabled);
            if (ret != 0) {
                ALOGE("Failed to enable buffer route");
            }
        }

        // Check each model, if it is active then update it's route
        for (int i = 0; i < MAX_MODELS; i++) {
            if (stdev->models[i].is_active == true) {
                // teardown the package route without bargein
                ret = tear_package_route(stdev,
                                        stdev->models[i].uuid,
                                        !stdev->is_bargein_route_enabled);
                if (ret != 0) {
                    ALOGE("Failed to tear old package route");
                }
                // resetup the package route with bargein
                ret = set_package_route(stdev,
                                        stdev->models[i].uuid,
                                        stdev->is_bargein_route_enabled);
                if (ret != 0) {
                    ALOGE("Failed to enable package route");
                }
            }
        }
    } else {
        ALOGD("%s: Bargein is already enabled", __func__);
    }

    return ret;
}

static int handle_input_source(struct knowles_sound_trigger_device *stdev,
                            bool enable)
{
    int err = 0;
    enum clock_type ct = INTERNAL_OSCILLATOR;
    enum strm_type strmt = STRM_16K;

    if (stdev->rx_active_count > 0) {
        ct = EXTERNAL_OSCILLATOR;
    }

    if (is_mic_controlled_by_ahal(stdev) == true) {
        strmt = STRM_48K;
    }

    if (enable) {
        /*
         * Setup the sources include microphone, SampleRate Converter
         * and Barge-in if necessary.
         * The SRC-mic and SRC-amp must be enabled before barge-in route.
         * That avoid the frame alignment conflict of AEC module.
         */
        if (stdev->is_mic_route_enabled == false) {
            err = check_and_setup_src_package(stdev);
            if (err != 0) {
                ALOGE("Failed to load SRC package");
            }
            err = setup_src_plugin(stdev->odsp_hdl, SRC_MIC);
            if (err != 0) {
                ALOGE("Failed to create SRC-mic plugin");
            }
            err = enable_src_route(stdev->route_hdl, true, SRC_MIC);
            if (err != 0) {
                ALOGE("Failed to enable SRC-mic route");
            }
        }
        if (stdev->rx_active_count > 0 &&
            stdev->is_bargein_route_enabled == false) {
            err = setup_src_plugin(stdev->odsp_hdl, SRC_AMP_REF);
            if (err != 0) {
                ALOGE("Failed to create SRC-amp plugin");
            }
            err = enable_src_route(stdev->route_hdl, true, SRC_AMP_REF);
            if (err != 0) {
                ALOGE("Failed to enable SRC-amp route");
            }

            err = setup_aec_package(stdev->odsp_hdl);
            if (err != 0) {
                ALOGE("Failed to load AEC package");
            }

            err = enable_bargein_route(stdev->route_hdl, true);
            if (err != 0) {
                ALOGE("Failed to enable barge-in route");
            }
            err = enable_amp_ref_route(stdev->route_hdl, true, strmt);
            if (err != 0) {
                ALOGE("Failed to enable amp-ref route");
            }
            stdev->is_bargein_route_enabled = true;
        }
        if (stdev->is_mic_route_enabled == false) {
            if (is_mic_controlled_by_ahal(stdev) == false) {
                err = enable_mic_route(stdev->route_hdl, true, ct);
                if (err != 0) {
                    ALOGE("Failed to enable mic route");
                }
            }
            stdev->is_mic_route_enabled = true;
        }
    } else {
        if (!is_any_model_active(stdev)) {
            ALOGD("None of model are active");
            if (stdev->rx_active_count > 0 &&
                stdev->is_bargein_route_enabled == true) {
                err = enable_bargein_route(stdev->route_hdl, false);
                if (err != 0) {
                    ALOGE("Failed to disable barge-in route");
                }
                err = destroy_aec_package(stdev->odsp_hdl);
                if (err != 0) {
                    ALOGE("Failed to destroy AEC package");
                }
                err = enable_src_route(stdev->route_hdl, false, SRC_AMP_REF);
                if (err != 0) {
                    ALOGE("Failed to disable SRC-amp route");
                }
                err = destroy_src_plugin(stdev->odsp_hdl, SRC_AMP_REF);
                if (err != 0) {
                    ALOGE("Failed to destroy SRC-amp package");
                }
                err = enable_amp_ref_route(stdev->route_hdl, false, strmt);
                if (err != 0) {
                    ALOGE("Failed to amp-ref route");
                }
                stdev->is_bargein_route_enabled = false;
           }
           if (stdev->is_mic_route_enabled == true) {
               err = enable_src_route(stdev->route_hdl, false, SRC_MIC);
               if (err != 0) {
                   ALOGE("Failed to disable SRC-mic route");
               }
               err = destroy_src_plugin(stdev->odsp_hdl, SRC_MIC);
               if (err != 0) {
                   ALOGE("Failed to destroy SRC-mic package");
               }
               if (is_mic_controlled_by_ahal(stdev) == false) {
                   err = enable_mic_route(stdev->route_hdl, false, ct);
                   if (err != 0) {
                       ALOGE("Failed to disable mic route");
                   }
               }
               stdev->is_mic_route_enabled = false;
               err = check_and_destroy_src_package(stdev);
               if (err != 0) {
                   ALOGE("Failed to destroy src package");
               }
           }
       }
    }

    return err;
}

// Helper functions for audio_device_info

int list_length(const struct listnode *list)
{
    struct listnode *node;
    int length = 0;

    if (list == NULL) {
        return 0;
    }

    list_for_each (node, list) {
        ++length;
    }
    return length;
}

/*
 * If single device in devices list is equal to passed type
 * type should represent a single device.
 */
bool is_single_device_type_equal(struct listnode *devices,
                                 audio_devices_t type)
{
    if (devices == NULL)
        return false;

    if (list_length(devices) == 1) {
        struct listnode *node = devices->next;
        struct audio_device_info *item = node_to_item(node, struct audio_device_info, list);
        if (item != NULL && (item->type == type))
            return true;
    }
    return false;
}

/*
 * Check if a device with given type is present in devices list
 */
bool compare_device_type(struct listnode *devices, audio_devices_t device_type)
{
    struct listnode *node;
    struct audio_device_info *item = NULL;

    if (devices == NULL)
        return false;

    list_for_each (node, devices) {
        item = node_to_item(node, struct audio_device_info, list);
        if (item != NULL && (item->type == device_type)) {
            ALOGV("%s: device types %d match", __func__, device_type);
            return true;
        }
    }
    return false;
}

// End of helper functions for audio_device_info

static void update_rx_conditions(struct knowles_sound_trigger_device *stdev,
                                 audio_event_type_t event,
                                 struct audio_event_info *config)
{
    if (event == AUDIO_EVENT_PLAYBACK_STREAM_INACTIVE) {
        if (stdev->rx_active_count > 0) {
            stdev->rx_active_count--;
        } else {
            ALOGW("%s: unexpected rx inactive event", __func__);
        }
    } else if (event == AUDIO_EVENT_PLAYBACK_STREAM_ACTIVE) {
        if (!compare_device_type(&config->device_info.devices, AUDIO_DEVICE_OUT_SPEAKER)) {
            ALOGD("%s: Playback device doesn't include SPEAKER.",
                  __func__);
        } else {
            stdev->rx_active_count++;
        }
    } else {
        ALOGW("%s: invalid event %d", __func__, event);
    }
    ALOGD("%s: updated rx_concurrency as %d", __func__,
          stdev->rx_active_count);
}


static void update_sthal_conditions(struct knowles_sound_trigger_device *stdev,
                                    audio_event_type_t event,
                                    struct audio_event_info *config)
{
    if (event == AUDIO_EVENT_CAPTURE_DEVICE_INACTIVE) {
        // get correct voice/voip mode in sthal
        if (stdev->is_ahal_in_voice_voip_mode == false &&
            stdev->is_ahal_voice_voip_stop == true &&
            stdev->is_ahal_media_recording == true) {
            ALOGD("%s: voice/voip didn't start, treat it as media recording inactive",
                  __func__);
            stdev->is_ahal_voice_voip_stop = false;
            stdev->is_ahal_media_recording = false;
        } else if (stdev->is_ahal_voice_voip_stop == true) {
            ALOGD("%s: voice/voip device is inactive", __func__);
            stdev->is_ahal_in_voice_voip_mode = false;
            stdev->is_ahal_voice_voip_stop = false;
        } else if (stdev->is_ahal_in_voice_voip_mode == true &&
                   stdev->is_ahal_voice_voip_stop == false &&
                   stdev->is_ahal_voice_voip_start == false) {
            ALOGD("%s: voice/voip usecase didn't start in incall mode, treat it as voice/voip is inactive",
                  __func__);
            stdev->is_ahal_in_voice_voip_mode = false;
        }

        if (stdev->is_concurrent_capture == true &&
            stdev->is_ahal_in_voice_voip_mode == false) {
            if (stdev->is_ahal_media_recording == true)
                stdev->is_con_mic_route_enabled = true;
            else
                stdev->is_con_mic_route_enabled = false;
            ALOGD("%s: update mic con %d", __func__,
                  stdev->is_con_mic_route_enabled);
        }
    } else if (event == AUDIO_EVENT_CAPTURE_DEVICE_ACTIVE) {
        if (stdev->is_ahal_in_voice_voip_mode == false &&
                (config->u.usecase.type == USECASE_TYPE_VOICE_CALL ||
                 config->u.usecase.type == USECASE_TYPE_VOIP_CALL)) {
            ALOGD("%s: voice/voip is actvie, close ST mic and don't use mic concurrently",
                  __func__);
            stdev->is_ahal_in_voice_voip_mode = true;
        }
        if (config->u.usecase.type == USECASE_TYPE_PCM_CAPTURE) {
            stdev->is_ahal_media_recording = true;
        }
        if (stdev->is_concurrent_capture == true &&
            stdev->is_ahal_in_voice_voip_mode == false &&
            stdev->is_con_mic_route_enabled == false &&
            is_single_device_type_equal(&config->device_info.devices, ST_DEVICE_HANDSET_MIC)) {
            ALOGD("%s: enable mic concurrency", __func__);
                  stdev->is_con_mic_route_enabled = true;
        }
    } else if (event == AUDIO_EVENT_CAPTURE_STREAM_INACTIVE) {
        if (stdev->is_ahal_voice_voip_start == true &&
                (config->u.usecase.type == USECASE_TYPE_VOICE_CALL ||
                 config->u.usecase.type == USECASE_TYPE_VOIP_CALL)) {
            stdev->is_ahal_voice_voip_stop = true;
            stdev->is_ahal_voice_voip_start = false;
        } else if (config->u.usecase.type == USECASE_TYPE_PCM_CAPTURE)
            stdev->is_ahal_media_recording = false;
    } else if (event == AUDIO_EVENT_CAPTURE_STREAM_ACTIVE) {
        if (config->u.usecase.type == USECASE_TYPE_VOICE_CALL ||
            config->u.usecase.type == USECASE_TYPE_VOIP_CALL) {
            stdev->is_ahal_voice_voip_start = true;
        }
    }
}

static bool do_handle_functions(struct knowles_sound_trigger_device *stdev,
                                enum sthal_mode pre_mode,
                                enum sthal_mode cur_mode,
                                audio_event_type_t event)
{
    int ret = 0;
    int i = 0;

    ALOGD("+%s+: pre %d, cur %d, event %d", __func__, pre_mode, cur_mode, event);

    // handle event AUDIO_EVENT_CAPTURE_DEVICE_ACTIVE
    if (event == AUDIO_EVENT_CAPTURE_DEVICE_ACTIVE) {
        if ((pre_mode == CON_DISABLED_ST && cur_mode == CON_DISABLED_CAPTURE) ||
            (pre_mode == CON_DISABLED_ST && cur_mode == IN_CALL) ||
            (pre_mode == CON_DISABLED_CAPTURE && cur_mode == IN_CALL) ||
            (pre_mode == CON_ENABLED_CAPTURE_ST && cur_mode == IN_CALL) ||
            (pre_mode == CON_ENABLED_ST && cur_mode == IN_CALL)) {
            // disable all ST
            // if tunnel is active, close it first
            for (i = 0; i < MAX_MODELS; i++) {
                if (stdev->adnc_strm_handle[i] != 0) {
                    ALOGD("%s: stop tunnling for index:%d", __func__, i);
                    stdev->adnc_strm_close(stdev->adnc_strm_handle[i]);
                    stdev->adnc_strm_handle[i] = 0;
                    stdev->adnc_strm_last_read[i] = reset_time;
                }
            }
            stdev->is_streaming = 0;

            for (i = 0; i < MAX_MODELS; i++) {
                if (stdev->models[i].is_active == true) {
                    update_recover_list(stdev, i, true);
                    tear_package_route(stdev, stdev->models[i].uuid,
                                       stdev->is_bargein_route_enabled);
                    stdev->models[i].is_active = false;
                    if (!check_uuid_equality(stdev->models[i].uuid,
                                             stdev->chre_model_uuid))
                        destroy_package(stdev, &stdev->models[i]);

                    if ((stdev->hotword_buffer_enable) &&
                        !(stdev->current_enable & PLUGIN1_MASK)) {
                        tear_hotword_buffer_route(stdev->route_hdl,
                                                  stdev->is_bargein_route_enabled);
                    }

                    if ((stdev->music_buffer_enable) &&
                        !(stdev->current_enable & PLUGIN2_MASK)) {
                        tear_music_buffer_route(stdev->route_hdl,
                                                stdev->is_bargein_route_enabled);
                    }

                    setup_buffer(stdev, &stdev->models[i], false);
                }
            }
            handle_input_source(stdev, false);
            check_and_destroy_buffer_package(stdev);
        } else if (pre_mode == CON_ENABLED_ST && cur_mode == CON_ENABLED_CAPTURE_ST) {
            //reconfig mic
            if (stdev->is_mic_route_enabled == true) {
                if (stdev->is_bargein_route_enabled == true) {
                    // close amp-ref first and reconfig it again with 48K after
                    // main mic is turned on by media recording
                    ret = enable_amp_ref_route(stdev->route_hdl, false, STRM_16K);
                    if (ret != 0) {
                        ALOGE("Failed to disable amp-ref route");
                    }
                    ret = enable_mic_route(stdev->route_hdl, false,
                                           EXTERNAL_OSCILLATOR);
                    if (ret != 0) {
                        ALOGE("Failed to disable mic route with EXT OSC");
                    }

                    ret = enable_amp_ref_route(stdev->route_hdl, true, STRM_48K);
                    if (ret != 0) {
                        ALOGE("Failed to enable amp-ref route");
                    }
                } else {
                    ret = enable_mic_route(stdev->route_hdl, false,
                                           INTERNAL_OSCILLATOR);
                    if (ret != 0) {
                        ALOGE("Failed to disable mic route with INT OSC");
                    }
                }
            } else {
                ALOGD("%s: ST mic isn't enabled, recording mic is turned on",
                       __func__);
            }
        }
    }

    // handle event AUDIO_EVENT_CAPTURE_DEVICE_INACTIVE
    if (event == AUDIO_EVENT_CAPTURE_DEVICE_INACTIVE) {
        if ((pre_mode == IN_CALL && cur_mode == CON_DISABLED_ST) ||
            (pre_mode == IN_CALL && cur_mode == CON_DISABLED_CAPTURE) ||
            (pre_mode == IN_CALL && cur_mode == CON_ENABLED_ST) ||
            (pre_mode == IN_CALL && cur_mode == CON_ENABLED_CAPTURE_ST) ||
            (pre_mode == CON_DISABLED_CAPTURE && cur_mode == CON_DISABLED_ST)) {
            //recover all STs
            for (i = 0; i < MAX_MODELS; i++) {
                // recover all models from list
                if (is_uuid_in_recover_list(stdev, i)) {
                    if (stdev->models[i].is_active == false) {
                        check_and_setup_buffer_package(stdev);
                        stdev->models[i].is_active = true;
                        handle_input_source(stdev, true);

                        setup_buffer(stdev, &stdev->models[i], true);
                        if (stdev->hotword_buffer_enable &&
                            !(stdev->current_enable & PLUGIN1_MASK)) {
                            set_hotword_buffer_route(stdev->route_hdl,
                                                     stdev->is_bargein_route_enabled);
                        }
                        if (stdev->music_buffer_enable &&
                            !(stdev->current_enable & PLUGIN2_MASK)) {
                            set_music_buffer_route(stdev->route_hdl,
                                                   stdev->is_bargein_route_enabled);
                        }

                        if (!check_uuid_equality(stdev->models[i].uuid,
                                                 stdev->chre_model_uuid))
                            setup_package(stdev, &stdev->models[i]);
                        set_package_route(stdev, stdev->models[i].uuid,
                                          stdev->is_bargein_route_enabled);
                    }
                }
            }
            stdev->recover_model_list = 0;
        } else if (pre_mode == CON_ENABLED_CAPTURE_ST && cur_mode == CON_ENABLED_ST) {
            // reconfig mic
            if (stdev->is_mic_route_enabled == true) {
                if (stdev->is_bargein_route_enabled == true) {
                    ret = enable_amp_ref_route(stdev->route_hdl, false, STRM_48K);
                    if (ret != 0) {
                        ALOGE("Failed to disable amp-ref route");
                    }
                    ret = enable_mic_route(stdev->route_hdl, true,
                                           EXTERNAL_OSCILLATOR);
                    if (ret != 0) {
                        ALOGE("Failed to enable mic route with EXT OSC");
                    }
                    // turn on amp-ref with 16khz
                    ret = enable_amp_ref_route(stdev->route_hdl, true, STRM_16K);
                    if (ret != 0) {
                        ALOGE("Failed to enable amp-ref route");
                    }
                } else {
                    ret = enable_mic_route(stdev->route_hdl, true,
                                           INTERNAL_OSCILLATOR);
                    if (ret != 0) {
                        ALOGE("Failed to enable mic route with INT OSC");
                    }
                }
            } else {
                ALOGD("%s: ST mic isn't enabled, recording mic is turned off",
                      __func__);
            }
        }
    }

    ALOGD("-%s-: pre %d, cur %d, event %d", __func__, pre_mode, cur_mode, event);
    return ret;
}

// stdev needs to be locked before calling this function
static int restart_recognition(struct knowles_sound_trigger_device *stdev)
{
    int err = 0;
    int i = 0;
    enum strm_type strmt = STRM_16K;
    enum clock_type ct = INTERNAL_OSCILLATOR;
    /*
     * The libaudioroute library doesn't set the mixer controls if previously
     * applied values are the same or the active_count > 0, so we need to
     * teardown the route so that it can clear up the value and active_count.
     * Then we could setup the routes again.
     */

    stdev->current_enable = 0;
    stdev->hotword_buffer_enable = 0;
    stdev->music_buffer_enable = 0;

    if (stdev->rx_active_count == 0 &&
        stdev->is_bargein_route_enabled == true) {
        /* rx stream is disabled during codec recovery.
         * Need to reset the enabled flag
         */
        stdev->is_bargein_route_enabled = false;
    }

    if (stdev->is_buffer_package_loaded == true) {
        err = setup_buffer_package(stdev->odsp_hdl);
        if (err != 0) {
            ALOGE("%s: Failed to restart Buffer package", __func__);
        }
    }

    if (stdev->is_src_package_loaded == true) {
        err = setup_src_package(stdev->odsp_hdl);
        if (err != 0) {
            ALOGE("%s: Failed to restart SRC package", __func__);
        }
    }

    /*
     * If ST mode is IN_CALL, make sure mic route as false,
     * that would be reloaded after ending the call
     */
    if (get_sthal_mode(stdev) == IN_CALL) {
        ALOGD("%s: ST mode is in_call, reset all routes", __func__);

        stdev->is_mic_route_enabled = false;
        stdev->is_bargein_route_enabled = false;
        for (i = 0; i < MAX_MODELS; i++) {
            if (stdev->models[i].is_active == true) {
                update_recover_list(stdev, i, true);
                stdev->models[i].is_active = false;
            }
        }

        // if chre enabled before crash during call, need to setup package for SLPI.
        if (stdev->is_chre_loaded == true) {
            err = setup_chre_package(stdev->odsp_hdl);
            if (err != 0) {
                ALOGE("Failed to load CHRE package");
            }
            stdev->current_enable = stdev->current_enable | CHRE_MASK;
        }
        goto reload_oslo;
    }


    /*
     * Reset mic and src package if sound trigger recording is active
     * The src-mic, src-amp must be enable before AEC enable, because
     * the endpoint sequence control.
     */
    if (stdev->is_mic_route_enabled == true) {
        // recover src package if sound trigger recording is active
        err = setup_src_plugin(stdev->odsp_hdl, SRC_MIC);
        if (err != 0) {
            ALOGE("failed to load SRC package");
        }
        err = enable_src_route(stdev->route_hdl, true, SRC_MIC);
        if (err != 0) {
            ALOGE("Failed to restart SRC-mic route");
        }
        /*
         * RX stream was enabled during codec recovery.
         * Need to setup the barge-in package and routing.
         */
        if (stdev->rx_active_count > 0) {
            stdev->is_bargein_route_enabled = true;
            ct = EXTERNAL_OSCILLATOR;
            if (is_mic_controlled_by_ahal(stdev) == true) {
                strmt = STRM_48K;
            }
            err = setup_src_plugin(stdev->odsp_hdl, SRC_AMP_REF);
            if (err != 0) {
                ALOGE("failed to load SRC package");
            }
            err = enable_src_route(stdev->route_hdl, true, SRC_AMP_REF);
            if (err != 0) {
                ALOGE("Failed to restart SRC-amp route");
            }

            err = setup_aec_package(stdev->odsp_hdl);
            if (err != 0) {
                ALOGE("Failed to restart AEC package");
            }
            err = enable_bargein_route(stdev->route_hdl, true);
            if (err != 0) {
                ALOGE("Failed to restart bargein route");
            }
            err = enable_amp_ref_route(stdev->route_hdl, true, strmt);
            if (err != 0) {
                ALOGE("Failed to restart amp-ref route");
            }
        }
        // The stream 0 should be enable at last moment for the data alignment.
        if (is_mic_controlled_by_ahal(stdev) == false) {
            err = enable_mic_route(stdev->route_hdl, true, ct);
            if (err != 0) {
                ALOGE("failed to restart mic route");
            }
        }
    }

    // Download all the keyword models files that were previously loaded
    for (i = 0; i < MAX_MODELS; i++) {
        if (stdev->models[i].is_active == true) {
            if (stdev->is_buffer_package_loaded == true) {
                setup_buffer(stdev, &stdev->models[i], true);
            }
            if (check_uuid_equality(stdev->models[i].uuid, stdev->hotword_model_uuid) ||
                (check_uuid_equality(stdev->models[i].uuid, stdev->wakeup_model_uuid))) {
                if ((stdev->hotword_buffer_enable) &&
                    (!(stdev->current_enable & HOTWORD_MASK) ||
                      (stdev->current_enable & WAKEUP_MASK))) {
                    set_hotword_buffer_route(stdev->route_hdl,
                                            stdev->is_bargein_route_enabled);
                }
            }
            if (check_uuid_equality(stdev->models[i].uuid, stdev->ambient_model_uuid) ||
                (check_uuid_equality(stdev->models[i].uuid, stdev->entity_model_uuid))) {
                if ((stdev->music_buffer_enable) &&
                    (!(stdev->current_enable & AMBIENT_MASK) ||
                      (stdev->current_enable & ENTITY_MASK))) {
                    set_music_buffer_route(stdev->route_hdl,
                                        stdev->is_bargein_route_enabled);
                }
            }
            setup_package(stdev, &stdev->models[i]);
            set_package_route(stdev, stdev->models[i].uuid,
                            stdev->is_bargein_route_enabled);
        }
    }

reload_oslo:
    // reload Oslo part after every package loaded to avoid HMD memory overlap
    // issue, b/128914464
    for (i = 0; i < MAX_MODELS; i++) {
        if (stdev->models[i].is_loaded == true) {
            if (check_uuid_equality(stdev->models[i].uuid,
                                    stdev->sensor_model_uuid)) {
                // setup the sensor route
                err = setup_sensor_package(stdev->odsp_hdl);
                if (err != 0) {
                    ALOGE("%s: setup Sensor package failed", __func__);
                    goto exit;
                }

                err = set_sensor_route(stdev->route_hdl, true);
                if (err != 0) {
                    ALOGE("%s: Sensor route fail", __func__);
                    goto exit;
                }
                stdev->is_sensor_route_enabled = true;
            }
        }
    }
    ALOGD("%s: recovery done", __func__);
exit:
    return err;
}

// stdev needs to be locked before calling this function
static int crash_recovery(struct knowles_sound_trigger_device *stdev)
{
    int err = 0;

    set_default_apll_clk(stdev->mixer);
    setup_slpi_wakeup_event(stdev->odsp_hdl, true);

    // Redownload the keyword model files and start recognition
    err = restart_recognition(stdev);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to download the keyword models and restarting"
            " recognition", __func__);
        goto exit;
    }

    // Reset the flag only after successful recovery
    stdev->is_st_hal_ready = true;

exit:
    return err;
}

static void sensor_crash_handler(struct knowles_sound_trigger_device *stdev)
{
    int i;

    if (stdev->is_sensor_destroy_in_prog == false)
        return;

    if (stdev->ss_timer_created) {
        timer_delete(stdev->ss_timer);
        stdev->ss_timer_created = false;
    }

    if (stdev->is_sensor_route_enabled == true) {
        for (i = 0; i < MAX_MODELS; i++) {
            if (check_uuid_equality(stdev->models[i].uuid,
                                    stdev->sensor_model_uuid) &&
                stdev->models[i].is_loaded == true) {
                stdev->models[i].is_loaded = false;
                memset(&stdev->models[i].uuid, 0,
                       sizeof(sound_trigger_uuid_t));
                break;
            }
        }
        stdev->is_sensor_route_enabled = false;
        stdev->current_enable &= ~OSLO_MASK;
    }
    stdev->is_sensor_destroy_in_prog = false;

    // There could be another thread waiting for us to destroy
    // so signal that thread, if no one is waiting then this signal
    // will have no effect
    pthread_cond_signal(&stdev->sensor_create);
}

static void destroy_sensor_model(struct knowles_sound_trigger_device *stdev)
{
    int ret, i;
    ALOGD("+%s+", __func__);

    if (stdev->is_sensor_route_enabled == true) {
        ret = set_sensor_route(stdev->route_hdl, false);
        if (ret != 0) {
            ALOGE("%s: tear Sensor route fail", __func__);
        }
        stdev->is_sensor_route_enabled = false;

        ret = destroy_sensor_package(stdev->odsp_hdl);
        if (ret != 0) {
            ALOGE("%s: destroy Sensor package failed %d",
                  __func__, ret);
        }
        stdev->current_enable = stdev->current_enable & ~OSLO_MASK;
    }

    // now we can change the flag
    for (i = 0 ; i < MAX_MODELS ; i++) {
        if (check_uuid_equality(stdev->models[i].uuid,
                                stdev->sensor_model_uuid) &&
            stdev->models[i].is_loaded == true) {
            memset(&stdev->models[i].uuid, 0, sizeof(sound_trigger_uuid_t));
            stdev->models[i].is_loaded = false;
            break;
        }
    }

    stdev->is_sensor_destroy_in_prog = false;
    check_and_destroy_buffer_package(stdev);

    // There could be another thread waiting for us to destroy so signal that
    // thread, if no one is waiting then this signal will have no effect
    pthread_cond_signal(&stdev->sensor_create);

    ALOGD("-%s-", __func__);
}

static void sensor_timeout_recover()
{
    int err = 0;
    ALOGD("+%s+", __func__);

    struct knowles_sound_trigger_device *stdev = &g_stdev;
    pthread_mutex_lock(&stdev->lock);
    // We are here because we timed out so check if we still need to destroy
    // the sensor package, if yes then reset the firmware
    if (stdev->is_sensor_destroy_in_prog == true) {
        if (stdev->is_st_hal_ready) {
            stdev->is_st_hal_ready = false;
            // reset the firmware and wait for firmware download complete
            err = reset_fw(stdev->odsp_hdl);
            if (err == -1) {
                ALOGE("%s: ERROR: Failed to reset the firmware %d(%s)",
                      __func__, errno, strerror(errno));
            }
            reset_all_route(stdev->route_hdl);
            sensor_crash_handler(stdev);
        }
    }
    pthread_mutex_unlock(&stdev->lock);
    ALOGD("-%s-", __func__);
}

static int start_sensor_model(struct knowles_sound_trigger_device * stdev)
{
    struct timespec ts;
    int wait_counter = 0, err = 0;

    while (stdev->is_sensor_destroy_in_prog == true &&
           wait_counter < SENSOR_CREATE_WAIT_MAX_COUNT) {
        // We wait for 1sec * MAX_COUNT times for the HOST 1 to respond, if
        // within that time we don't get any response, we will go ahead with the
        // sensor model creation. Note this might result in an error which would
        // be better than blocking the thread indefinitely.
        clock_gettime(CLOCK_REALTIME, &ts);
        ts.tv_sec += SENSOR_CREATE_WAIT_TIME_IN_S;
        err = pthread_cond_timedwait(&stdev->sensor_create, &stdev->lock, &ts);
        if (err == ETIMEDOUT) {
            ALOGE("%s: WARNING: Sensor create timed out after %ds",
                  __func__, SENSOR_CREATE_WAIT_TIME_IN_S);
            wait_counter++;
        }
    }

    // If firmware crashed when we are waiting
    if (stdev->is_st_hal_ready == false) {
        err = -ENODEV;
        goto exit;
    }

    if (stdev->is_sensor_destroy_in_prog == true) {
        ALOGE("%s: ERROR: Waited for %ds but we didn't get the event from "
              "Host 1, and fw reset is not yet complete", __func__,
              SENSOR_CREATE_WAIT_TIME_IN_S * SENSOR_CREATE_WAIT_MAX_COUNT);
        err = -ENODEV;
        goto exit;
    }

    // setup the sensor route
    err = check_and_setup_buffer_package(stdev);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to load the buffer package", __func__);
        goto exit;
    }

    if(stdev->is_sensor_route_enabled == false) {
        err = setup_sensor_package(stdev->odsp_hdl);
        if (err) {
            ALOGE("%s: Failed to setup sensor package", __func__);
            goto exit;
        }
        // Don't download the keyword model file, just setup the
        // sensor route
        err = set_sensor_route(stdev->route_hdl, true);
        if (err) {
            ALOGE("%s: Sensor route fail", __func__);
            goto exit;
        }
        stdev->is_sensor_route_enabled = true;
        stdev->current_enable = stdev->current_enable | OSLO_MASK;
    }

exit:
    return err;
}

static void chre_crash_handler(struct knowles_sound_trigger_device *stdev)
{
    int i;

    if (stdev->is_chre_destroy_in_prog == false)
        return;

    if (stdev->chre_timer_created) {
        timer_delete(stdev->chre_timer);
        stdev->chre_timer_created = false;
    }

    if (stdev->is_chre_loaded == true) {
        for (i = 0; i < MAX_MODELS; i++) {
            if (check_uuid_equality(stdev->models[i].uuid,
                                    stdev->chre_model_uuid)) {
                stdev->models[i].is_active = false;
                stdev->models[i].is_loaded = false;
                memset(&stdev->models[i].uuid, 0,
                       sizeof(sound_trigger_uuid_t));
                break;
            }
        }
        stdev->is_chre_loaded = false;
        stdev->current_enable &= ~CHRE_MASK;
    }
    stdev->is_chre_destroy_in_prog = false;

    // There could be another thread waiting for us to destroy
    // so signal that thread, if no one is waiting then this signal
    // will have no effect
    pthread_cond_signal(&stdev->chre_create);
}

static int start_chre_model(struct knowles_sound_trigger_device *stdev,
                            int model_id)
{
    struct timespec ts;
    int wait_counter = 0, err = 0;

    while (stdev->is_chre_destroy_in_prog == true &&
           wait_counter < CHRE_CREATE_WAIT_MAX_COUNT) {
        // We wait for 1sec * MAX_COUNT times for the HOST 1 to respond, if
        // within that time we don't get any response, we will go ahead with the
        // sensor model creation. Note this might result in an error which would
        // be better than blocking the thread indefinitely.
        clock_gettime(CLOCK_REALTIME, &ts);
        ts.tv_sec += CHRE_CREATE_WAIT_TIME_IN_S;
        err = pthread_cond_timedwait(&stdev->chre_create, &stdev->lock, &ts);
        if (err == ETIMEDOUT) {
            ALOGE("%s: WARNING: CHRE create timed out after %ds",
                    __func__, CHRE_CREATE_WAIT_TIME_IN_S);
            wait_counter++;
        }
    }

    // If firmware crashed when we are waiting
    if (stdev->is_st_hal_ready == false) {
        err = -ENODEV;
        goto exit;
    }

    if (stdev->is_chre_destroy_in_prog == true) {
        ALOGE("%s: ERROR: Waited for %ds but we didn't get the event from "
              "Host 1, and fw reset is not yet complete", __func__,
              CHRE_CREATE_WAIT_TIME_IN_S * CHRE_CREATE_WAIT_MAX_COUNT);
        err = -ENODEV;
        goto exit;
    }

    err = check_and_setup_buffer_package(stdev);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to load the buffer package", __func__);
        goto exit;
    }

    // add chre to recover list
    if (can_enable_chre(stdev)) {
        if(stdev->is_chre_loaded == false) {
            stdev->models[model_id].is_active = true;
            handle_input_source(stdev, true);
            setup_chre_package(stdev->odsp_hdl);
            set_chre_audio_route(stdev->route_hdl,
                               stdev->is_bargein_route_enabled);
            stdev->is_chre_loaded = true;
            stdev->current_enable = stdev->current_enable | CHRE_MASK;
        }
    } else {
        ALOGW("%s: device is in call, setup CHRE for SLPI",
              __func__);
        //Setup CHRE package and allow SLPI connect
        //during in-call mode.
        if (stdev->is_chre_loaded == false) {
            setup_chre_package(stdev->odsp_hdl);
            stdev->models[model_id].uuid = stdev->chre_model_uuid;
            stdev->is_chre_loaded = true;
            stdev->current_enable = stdev->current_enable | CHRE_MASK;
            if (can_update_recover_list(stdev) == true)
                update_recover_list(stdev, model_id, true);
        }
    }

exit:
    return err;
}

static void destroy_chre_model(struct knowles_sound_trigger_device *stdev)
{
    int err = 0;
    ALOGD("+%s+", __func__);

    if (stdev->is_chre_loaded == true) {
        int i;
        tear_chre_audio_route(stdev->route_hdl,
                              stdev->is_bargein_route_enabled);
        err = destroy_chre_package(stdev->odsp_hdl);
        if (err != 0) {
            ALOGE("%s: ERROR: Failed to destroy chre package", __func__);
        }

        //Need force reset the flag for chre due to in-call state
        //The model is inactive, but need to clean if user disable it
        //during call.
        for (i = 0; i < MAX_MODELS; i++) {
            if (check_uuid_equality(stdev->models[i].uuid,
                                    stdev->chre_model_uuid)) {
                stdev->models[i].is_active = false;
                stdev->models[i].is_loaded = false;
                memset(&stdev->models[i].uuid, 0,
                       sizeof(sound_trigger_uuid_t));
                break;
            }
        }
        handle_input_source(stdev, false);
        stdev->is_chre_loaded = false;
        stdev->current_enable = stdev->current_enable & ~CHRE_MASK;
    }

    stdev->is_chre_destroy_in_prog = false;

    // setup the sensor route
    err = check_and_destroy_buffer_package(stdev);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to destroy buffer package", __func__);
    }

    // There could be another thread waiting for us to destroy so signal that
    // thread, if no one is waiting then this signal will have no effect
    pthread_cond_signal(&stdev->chre_create);

    ALOGD("-%s-", __func__);
}

static void chre_timeout_recover()
{
    int err = 0;
    ALOGD("+%s+", __func__);

    struct knowles_sound_trigger_device *stdev = &g_stdev;
    pthread_mutex_lock(&stdev->lock);
    // We are here because we timed out so check if we still need to destroy
    // the chre package, if yes then reset the firmware
    if (stdev->is_chre_destroy_in_prog == true) {
        if (stdev->is_st_hal_ready) {
            stdev->is_st_hal_ready = false;
            // reset the firmware and wait for firmware download complete
            err = reset_fw(stdev->odsp_hdl);
            if (err == -1) {
                ALOGE("%s: ERROR: Failed to reset the firmware %d(%s)",
                      __func__, errno, strerror(errno));
            }
            reset_all_route(stdev->route_hdl);
            chre_crash_handler(stdev);
        }
    }
    pthread_mutex_unlock(&stdev->lock);
    ALOGD("-%s-", __func__);
}

static void *transitions_thread_loop(void *context)
{
    struct knowles_sound_trigger_device *stdev =
        (struct knowles_sound_trigger_device *)context;

    int err = 0;
    pthread_mutex_lock(&stdev->lock);
    while (1) {
            if (stdev->transit_case == TRANSIT_NONE)
                pthread_cond_wait(&stdev->transition_cond, &stdev->lock);

            acquire_wake_lock(PARTIAL_WAKE_LOCK, WAKE_LOCK_NAME);
            switch (stdev->transit_case) {
                case TRANSIT_NONE:
                    break;
                case TRANSIT_SETUP_AEC:
                    err = async_setup_aec(stdev);
                    break;
            }
            stdev->transit_case = TRANSIT_NONE;
            release_wake_lock(WAKE_LOCK_NAME);
    }
    pthread_mutex_unlock(&stdev->lock);
}


static void *monitor_thread_loop(void *context)
{
    struct knowles_sound_trigger_device *stdev =
        (struct knowles_sound_trigger_device *)context;
    struct timespec now;
    double diff;

    pthread_mutex_lock(&stdev->lock);
    while (1) {
            if (!stdev->is_streaming)
                pthread_cond_wait(&stdev->tunnel_create, &stdev->lock);
            pthread_mutex_unlock(&stdev->lock);

            sleep(TUNNEL_TIMEOUT);

            pthread_mutex_lock(&stdev->lock);

            acquire_wake_lock(PARTIAL_WAKE_LOCK, WAKE_LOCK_NAME);
            clock_gettime(CLOCK_REALTIME, &now);
            for (int i = 0; i < MAX_MODELS; i++) {
                if (stdev->adnc_strm_handle[i] != 0) {
                    diff = (now.tv_sec - stdev->adnc_strm_last_read[i].tv_sec)
                        + (double) ((now.tv_nsec) - (stdev->adnc_strm_last_read[i].tv_nsec))
                          / 1000000000.0;

                    if (diff > TUNNEL_TIMEOUT) {
                        ALOGE("%s: Waiting timeout for %f sec", __func__, diff);
                        stdev->adnc_strm_close(stdev->adnc_strm_handle[i]);
                        stdev->adnc_strm_handle[i] = 0;
                        stdev->is_streaming--;
                        stdev->adnc_strm_last_read[i] = reset_time;
                    }
                }
            }
            release_wake_lock(WAKE_LOCK_NAME);
    }
    pthread_mutex_unlock(&stdev->lock);
}

static void *callback_thread_loop(void *context)
{
    struct knowles_sound_trigger_device *stdev =
        (struct knowles_sound_trigger_device *)context;
    struct pollfd fds[2];
    char msg[UEVENT_MSG_LEN];
    int exit_sockets[2];
    int err = 0;
    int i, n;
    int kwid = 0;
    struct iaxxx_get_event_info ge;
    void *payload = NULL;
    unsigned int payload_size = 0, fw_status = IAXXX_FW_IDLE;
    int fw_status_retries = 0;

    ALOGI("%s", __func__);
    prctl(PR_SET_NAME, (unsigned long)"sound trigger callback", 0, 0, 0);

    pthread_mutex_lock(&stdev->lock);

    if (socketpair(AF_UNIX, SOCK_STREAM, 0, exit_sockets) == -1) {
        ALOGE("%s: Failed to create termination socket", __func__);
        goto exit;
    }

    stdev_close_term_sock(stdev);
    stdev->send_sock = exit_sockets[0];
    stdev->recv_sock = exit_sockets[1];

    memset(fds, 0, 2 * sizeof(struct pollfd));
    int timeout = -1; // Wait for event indefinitely
    fds[0].events = POLLIN;
    fds[0].fd = uevent_open_socket(64*1024, true);
    if (fds[0].fd == -1) {
        ALOGE("Error opening socket for hotplug uevent errno %d(%s)",
            errno, strerror(errno));
        goto exit;
    }
    fds[1].events = POLLIN;
    fds[1].fd = stdev->recv_sock;

    ge.event_id = -1;

    // Try to get the firmware status, if we fail, try for 10 times with a gap
    // of 500ms, if we are unable to get the status after that then exit
    do {
        err = get_fw_status(stdev->odsp_hdl, &fw_status);
        if (err == -1) {
            ALOGE("%s: ERROR: Failed to get the firmware status %d(%s)",
                    __func__, errno, strerror(errno));
            usleep(RETRY_US);
            fw_status_retries++;
        }
    } while (err != 0 && fw_status_retries < RETRY_NUMBER);

    if (err != 0) {
        ALOGE("%s: ERROR: Failed to get firmware status after %d tries",
                __func__, RETRY_NUMBER);
        goto exit;
    }

    if (fw_status == IAXXX_FW_ACTIVE) {
        stdev->is_st_hal_ready = false;
        // query version during reset progress.
        get_hotword_info(stdev->odsp_hdl,
                        &stdev->hotword_version,
                        &hw_properties.supported_model_arch);
        // reset the firmware and wait for firmware download complete
        err = reset_fw(stdev->odsp_hdl);
        if (err == -1) {
            ALOGE("%s: ERROR: Failed to reset the firmware %d(%s)",
                    __func__, errno, strerror(errno));
        }
        stdev->fw_reset_done_by_hal = true;
    } else if (fw_status == IAXXX_FW_CRASH) {
        // Firmware has crashed wait till it recovers
        stdev->is_st_hal_ready = false;
    } else if (fw_status == IAXXX_FW_IDLE) {
        err = get_hotword_info(stdev->odsp_hdl,
                            &stdev->hotword_version,
                            &hw_properties.supported_model_arch);
        if (stdev->hotword_version == HOTWORD_DEFAULT_VER) {
            /* This is unlikely use-case, the codec is abnormal at the beginning
             * reset_fw the firmware to recovery.
             */
            stdev->is_st_hal_ready = false;
            err = reset_fw(stdev->odsp_hdl);
            if (err == -1) {
                ALOGE("%s: ERROR: Failed to reset the firmware %d(%s)",
                      __func__, errno, strerror(errno));
            }
            stdev->fw_reset_done_by_hal = true;
        } else {
            stdev->route_hdl = audio_route_init(stdev->snd_crd_num,
                                            stdev->mixer_path_xml);
            if (stdev->route_hdl == NULL) {
                ALOGE("Failed to init the audio_route library");
                goto exit;
            }
            set_default_apll_clk(stdev->mixer);
            setup_slpi_wakeup_event(stdev->odsp_hdl, true);
            stdev->is_st_hal_ready = true;
        }
    }
    pthread_mutex_unlock(&stdev->lock);

    while (1) {
        err = poll(fds, 2, timeout);

        pthread_mutex_lock(&stdev->lock);
        if (err < 0) {
            ALOGE("%s: Error in poll: %d (%s)",
                __func__, errno, strerror(errno));
            break;
        }

        if (fds[0].revents & POLLIN) {
            n = uevent_kernel_multicast_recv(fds[0].fd, msg, UEVENT_MSG_LEN);
            if (n <= 0) {
                pthread_mutex_unlock(&stdev->lock);
                continue;
            }
            for (i = 0; i < n;) {
                if (strstr(msg + i, IAXXX_VQ_EVENT_STR)) {
                    ALOGI("%s", IAXXX_VQ_EVENT_STR);

                    err = get_event(stdev->odsp_hdl, &ge);
                    if (err == 0) {
                        if (ge.event_id == OK_GOOGLE_KW_ID) {
                            kwid = OK_GOOGLE_KW_ID;
                        } else if (ge.event_id == AMBIENT_KW_ID) {
                            kwid = AMBIENT_KW_ID;
                            reset_ambient_plugin(stdev->odsp_hdl);
                        } else if (ge.event_id == OSLO_EP_DISCONNECT) {
                            ALOGD("Eventid received is OSLO_EP_DISCONNECT %d",
                                  OSLO_EP_DISCONNECT);
                            if (stdev->is_sensor_destroy_in_prog == true) {
                                // A timer would have been created during stop,
                                // check and delete it
                                if (stdev->ss_timer_created) {
                                    timer_delete(stdev->ss_timer);
                                    stdev->ss_timer_created = false;
                                }

                                destroy_sensor_model(stdev);
                            } else {
                                ALOGE("Unexpected OSLO_EP_DISCONNECT received"
                                      ", ignoring..");
                            }
                            break;
                        } else if (ge.event_id == CHRE_EP_DISCONNECT) {
                            ALOGD("Eventid received is CHRE_EP_DISCONNECT %d",
                                  CHRE_EP_DISCONNECT);
                            if (stdev->is_chre_destroy_in_prog == true) {
                                // A timer would have been created during stop,
                                // check and delete it
                                if (stdev->chre_timer_created) {
                                    timer_delete(stdev->chre_timer);
                                    stdev->chre_timer_created = false;
                                }

                                destroy_chre_model(stdev);
                            } else {
                                ALOGE("Unexpected CHRE_EP_DISCONNECT received"
                                      ", ignoring..");
                            }
                            break;
                        } else if (ge.event_id == ENTITY_KW_ID) {
                            kwid = ENTITY_KW_ID;
                        } else if (ge.event_id == WAKEUP_KW_ID) {
                            kwid = WAKEUP_KW_ID;
                        } else {
                            ALOGE("Unknown event id received, ignoring %d",
                                ge.event_id);
                        }
                        break;
                    } else {
                        ALOGE("get_event failed with error %d", err);
                    }
                } else if (strstr(msg + i, IAXXX_RECOVERY_EVENT_STR)) {
                    /* If the ST HAL did the firmware reset then that means
                     * that the userspace crashed so we need to reinit the audio
                     * route library, if we didn't reset the firmware, then it
                     * was genuine firmware crash and we don't need to reinit
                     * the audio route library.
                     */
                    ALOGD("Firmware has redownloaded, start the recovery");
                    if (stdev->fw_reset_done_by_hal == true) {
                        stdev->route_hdl = audio_route_init(stdev->snd_crd_num,
                                                            stdev->mixer_path_xml);
                        if (stdev->route_hdl == NULL) {
                            ALOGE("Failed to init the audio_route library");
                            goto exit;
                        }

                        stdev->fw_reset_done_by_hal = false;
                    }

                    int err = crash_recovery(stdev);
                    if (err != 0) {
                        ALOGE("Crash recovery failed");
                    }
                    pthread_cond_signal(&stdev->firmware_ready_cond);
                } else if (strstr(msg + i, IAXXX_FW_DWNLD_SUCCESS_STR)) {
                    ALOGD("Firmware downloaded successfully");
                    stdev->is_st_hal_ready = true;
                    set_default_apll_clk(stdev->mixer);
                } else if (strstr(msg + i, IAXXX_FW_CRASH_EVENT_STR)) {
                    ALOGD("Firmware has crashed");
                    // Don't allow any op on ST HAL until recovery is complete
                    stdev->is_st_hal_ready = false;
                    reset_all_route(stdev->route_hdl);
                    stdev->is_streaming = 0;

                    // Firmware crashed, clear CHRE/Oslo timer and flags here
                    sensor_crash_handler(stdev);
                    chre_crash_handler(stdev);
                }

                i += strlen(msg + i) + 1;
            }

            if (ge.event_id == OK_GOOGLE_KW_ID ||
                ge.event_id == AMBIENT_KW_ID ||
                ge.event_id == ENTITY_KW_ID ||
                ge.event_id == WAKEUP_KW_ID) {
                ALOGD("%s: Keyword ID %d", __func__, kwid);

                if (ge.data != 0) {
                    ALOGD("Size of payload is %d", ge.data);
                    payload_size = ge.data;
                    payload = malloc(payload_size);
                    if (payload != NULL) {
                        if (ge.event_id == AMBIENT_KW_ID ||
                            ge.event_id == ENTITY_KW_ID)
                            err = get_entity_param_blk(stdev->odsp_hdl,
                                                    payload,
                                                    payload_size);
                        else if (ge.event_id == OK_GOOGLE_KW_ID ||
                            ge.event_id == WAKEUP_KW_ID)
                            err = get_wakeup_param_blk(stdev->odsp_hdl,
                                                    payload,
                                                    payload_size);
                        if (err != 0) {
                            ALOGE("Failed to get payload data");
                            free(payload);
                            payload = NULL;
                            payload_size = 0;
                        }
                    } else {
                        ALOGE("Failed to allocate memory for payload");
                    }
                }
                int idx = find_handle_for_kw_id(stdev, kwid);
                if (idx < MAX_MODELS && stdev->models[idx].is_active == true) {
                    int recognition_status = RECOGNITION_STATUS_SUCCESS;
                    if (stdev->models[idx].is_state_query == true) {
                        recognition_status =
                            RECOGNITION_STATUS_GET_STATE_RESPONSE;

                        // We need to send this only once, so reset now
                        stdev->models[idx].is_state_query = false;
                    }
                    if (stdev->models[idx].type == SOUND_MODEL_TYPE_KEYPHRASE &&
                        stdev->adnc_strm_handle[idx] == 0) {
                        struct sound_trigger_phrase_recognition_event *event;
                        event = (struct sound_trigger_phrase_recognition_event*)
                                    stdev_keyphrase_event_alloc(
                                                stdev->models[idx].model_handle,
                                                stdev->models[idx].config,
                                                recognition_status);
                        if (event) {
                            struct model_info *model;
                            model = &stdev->models[idx];

                            ALOGD("Sending recognition callback for id %d",
                                kwid);
                            /* Exit the critical section of interaction with
                             * firmware, release the lock to avoid deadlock
                             * when processing recognition event */
                            pthread_mutex_unlock(&stdev->lock);
                            model->recognition_callback(&event->common,
                                                    model->recognition_cookie);
                            pthread_mutex_lock(&stdev->lock);

                            free(event);
                        } else {
                            ALOGE("Failed to allocate memory for the event");
                        }
                    } else if (stdev->models[idx].type == SOUND_MODEL_TYPE_GENERIC &&
                               stdev->adnc_strm_handle[idx] == 0) {
                        struct sound_trigger_generic_recognition_event *event;
                        event = (struct sound_trigger_generic_recognition_event*)
                                stdev_generic_event_alloc(
                                            stdev->models[idx].model_handle,
                                            payload,
                                            payload_size,
                                            recognition_status);
                        if (event) {
                            struct model_info *model;
                            model = &stdev->models[idx];

                            ALOGD("Sending recognition callback for id %d",
                                kwid);
                            /* Exit the critical section of interaction with
                             * firmware, release the lock to avoid deadlock
                             * when processing recognition event */
                            pthread_mutex_unlock(&stdev->lock);
                            model->recognition_callback(&event->common,
                                                    model->recognition_cookie);
                            pthread_mutex_lock(&stdev->lock);
                            free(event);
                        } else {
                            ALOGE("Failed to allocate memory for the event");
                        }
                    } else if (stdev->adnc_strm_handle[idx] != 0) {
                        ALOGD("model %d is streaming.", idx);
                    }
                } else {
                    ALOGE("Invalid id or keyword is not active, Subsume the event");
                }
                // Reset all event related data
                ge.event_id = -1;
                ge.data = 0;
                kwid = -1;
            }
            // Free the payload data
            if (payload) {
                free(payload);
                payload = NULL;
                payload_size = 0;
            }
        } else if (fds[1].revents & POLLIN) {
            read(fds[1].fd, &n, sizeof(n)); /* clear the socket */
            ALOGD("%s: Termination message", __func__);
            break;
        }
        else {
            ALOGI("%s: Message ignored", __func__);
        }
        pthread_mutex_unlock(&stdev->lock);
    }

exit:
    stdev_close_term_sock(stdev);
    pthread_mutex_unlock(&stdev->lock);

    return (void *)(long)err;
}

static int stdev_get_properties(
                            const struct sound_trigger_hw_device *dev __unused,
                            struct sound_trigger_properties *properties)
{
    ALOGV("+%s+", __func__);
    if (properties == NULL)
        return -EINVAL;
    memcpy(properties, &hw_properties.base, sizeof(struct sound_trigger_properties));
    ALOGV("-%s-", __func__);
    return 0;
}

/*  Insert a decimal numeral for Prefix into the beginning of String.
    Length specifies the total number of bytes available at str.
*/
static int int_prefix_str(char *str, size_t str_len, int prefix, const char *prefix_format)
{
    int prefix_len = snprintf(NULL, 0, prefix_format, prefix);
    size_t str_cur_len = strlen(str);

    if (str_len < prefix_len + str_cur_len + 1)
    {
        ALOGE("%s: Error, not enough space in string to insert prefix: %d, %s\n",
                __func__, prefix, str);
        return -1;
    }

    //  Move the string to make room for the prefix.
    memmove(str + prefix_len, str, str_cur_len + 1);

    /*  Remember the first character, because snprintf will overwrite it with a
        null character.
    */
    char tmp = str[0];

    snprintf(str, prefix_len + 1, prefix_format, prefix);
    str[prefix_len] = tmp;
    return 0;
}

static const struct sound_trigger_properties_header* stdev_get_properties_extended(
                            const struct sound_trigger_hw_device *dev __unused)
{
    struct knowles_sound_trigger_device *stdev =
          (struct knowles_sound_trigger_device *)dev;
    ALOGV("+%s+", __func__);
    pthread_mutex_lock(&stdev->lock);
    if (hw_properties.header.version >= SOUND_TRIGGER_DEVICE_API_VERSION_1_3) {
        hw_properties.base.version = stdev->hotword_version;

        /* Per GMS requirement new to Android R, the supported_model_arch field
           must be the Google hotword firmware version comma separated with the
           supported_model_arch platform identifier.
        */
        int_prefix_str(hw_properties.supported_model_arch, SOUND_TRIGGER_MAX_STRING_LEN,
                hw_properties.base.version, "%u,");
        ALOGW("SoundTrigger supported model arch identifier is %s",
                hw_properties.supported_model_arch);
    } else {
        ALOGE("STHAL Version is %u", hw_properties.header.version);
        pthread_mutex_unlock(&stdev->lock);
        return NULL;
    }

    pthread_mutex_unlock(&stdev->lock);
    ALOGV("-%s-", __func__);
    return &hw_properties.header;
}


static int stop_recognition(struct knowles_sound_trigger_device *stdev,
                            sound_model_handle_t handle)
{
    int status = 0;
    struct model_info *model = &stdev->models[handle];

    if (model->config != NULL) {
        dereg_hal_event_session(model->config, handle);
        free(model->config);
        model->config = NULL;
    }

    model->recognition_callback = NULL;
    model->recognition_cookie = NULL;
    if (check_uuid_equality(model->uuid, stdev->chre_model_uuid) ||
        check_uuid_equality(model->uuid, stdev->sensor_model_uuid)) {
        // This avoids any processing of chre/oslo.
        goto exit;
    }
    if (can_update_recover_list(stdev) == true) {
        update_recover_list(stdev, handle, false);
        model->is_active = false;
        goto exit;
    }
    if (model->is_active == false) {
        ALOGW("%s: the model was disabled already", __func__);
        goto exit;
    }

    if (stdev->adnc_strm_handle[handle] != 0) {
        ALOGD("%s: stop tunnling for index:%d", __func__, handle);
        stdev->adnc_strm_close(stdev->adnc_strm_handle[handle]);
        stdev->adnc_strm_handle[handle] = 0;
        stdev->is_streaming--;
        stdev->adnc_strm_last_read[handle] = reset_time;
    }

    model->is_active = false;

    tear_package_route(stdev, model->uuid, stdev->is_bargein_route_enabled);

    destroy_package(stdev, model);


    if (check_uuid_equality(model->uuid, stdev->hotword_model_uuid) ||
        (check_uuid_equality(model->uuid, stdev->wakeup_model_uuid))) {
        if ((stdev->hotword_buffer_enable) &&
            !(stdev->current_enable & PLUGIN1_MASK)) {
            tear_hotword_buffer_route(stdev->route_hdl,
                                    stdev->is_bargein_route_enabled);
        }
    }

    if (check_uuid_equality(model->uuid, stdev->ambient_model_uuid) ||
        (check_uuid_equality(model->uuid, stdev->entity_model_uuid))) {
        if ((stdev->music_buffer_enable) &&
            !(stdev->current_enable & PLUGIN2_MASK)) {
            tear_music_buffer_route(stdev->route_hdl,
                                stdev->is_bargein_route_enabled);
        }
    }

    setup_buffer(stdev, model, false);

    handle_input_source(stdev, false);

    check_and_destroy_buffer_package(stdev);

exit:
    return status;
}

static int stdev_load_sound_model(const struct sound_trigger_hw_device *dev,
                                struct sound_trigger_sound_model *sound_model,
                                sound_model_callback_t callback,
                                void *cookie,
                                sound_model_handle_t *handle)
{
    struct knowles_sound_trigger_device *stdev =
        (struct knowles_sound_trigger_device *)dev;
    int ret = 0;
    int kw_model_sz = 0;
    int i = 0;
    sound_trigger_uuid_t empty_uuid = {0};
    unsigned char *kw_buffer = NULL;

    ALOGD("+%s+", __func__);
    pthread_mutex_lock(&stdev->lock);

    ret = check_firmware_ready(stdev);
    if (ret != 0)
        goto exit;

    if (handle == NULL || sound_model == NULL) {
        ALOGE("%s: handle/sound_model is NULL", __func__);
        ret = -EINVAL;
        goto exit;
    }

    if (sound_model->data_size == 0 ||
        sound_model->data_offset < sizeof(struct sound_trigger_sound_model)) {
        ALOGE("%s: Invalid sound model data", __func__);
        ret = -EINVAL;
        goto exit;
    }

    if (check_uuid_equality(sound_model->vendor_uuid, empty_uuid)) {
        ALOGE("%s Invalid vendor uuid", __func__);
        ret = -EINVAL;
        goto exit;
    }

    // When a delayed CHRE/Oslo destroy process is in progress,
    // we should not skip the new model and return the existing handle
    // which will be destroyed soon.
    if ((check_uuid_equality(sound_model->vendor_uuid,
                             stdev->chre_model_uuid) &&
            stdev->is_chre_destroy_in_prog) ||
            (check_uuid_equality(sound_model->vendor_uuid,
                                 stdev->sensor_model_uuid) &&
            stdev->is_sensor_destroy_in_prog)) {
        ALOGD("%s: CHRE/Oslo destroy in progress, skipped handle check.",
              __func__);
    } else {
        i = find_handle_for_uuid(stdev, sound_model->vendor_uuid);
        if (i != -1) {
            ALOGW("%s: model is existed at index %d", __func__, i);
            *handle = i;
            goto exit;
        }
    }

    // Find an empty slot to load the model
    i = find_empty_model_slot(stdev);
    if (i == -1) {
        ALOGE("%s: Can't load model no free slots available", __func__);
        ret = -ENOSYS;
        goto exit;
    }

    kw_buffer = (unsigned char *) sound_model + sound_model->data_offset;
    kw_model_sz = sound_model->data_size;
    ALOGV("%s: kw_model_sz %d", __func__, kw_model_sz);

    stdev->models[i].data = malloc(kw_model_sz);
    if (stdev->models[i].data == NULL) {
        stdev->models[i].data_sz = 0;
        ALOGE("%s: could not allocate memory for keyword model data",
            __func__);
        ret = -ENOMEM;
        goto exit;
    } else {
        memcpy(stdev->models[i].data, kw_buffer, kw_model_sz);
        stdev->models[i].data_sz = kw_model_sz;
    }

    // Send the keyword model to the chip only for hotword and ambient audio
    if (check_uuid_equality(sound_model->vendor_uuid,
                            stdev->hotword_model_uuid)) {
        stdev->models[i].kw_id = OK_GOOGLE_KW_ID;
    } else if (check_uuid_equality(sound_model->vendor_uuid,
                                stdev->wakeup_model_uuid)) {
        stdev->models[i].kw_id = WAKEUP_KW_ID;
    } else if (check_uuid_equality(sound_model->vendor_uuid,
                                stdev->ambient_model_uuid)) {
        stdev->models[i].kw_id = AMBIENT_KW_ID;
    } else if (check_uuid_equality(sound_model->vendor_uuid,
                                stdev->entity_model_uuid)) {
        stdev->models[i].kw_id = ENTITY_KW_ID;
    } else if (check_uuid_equality(sound_model->vendor_uuid,
                                stdev->sensor_model_uuid)) {
        ret = start_sensor_model(stdev);
        if (ret) {
            ALOGE("%s: ERROR: Failed to start sensor model", __func__);
            goto error;
        }
        stdev->models[i].kw_id = USELESS_KW_ID;
    } else if (check_uuid_equality(sound_model->vendor_uuid,
                                stdev->chre_model_uuid)) {
        ret = start_chre_model(stdev, i);
        if (ret) {
            ALOGE("%s: ERROR: Failed to start chre model", __func__);
            goto error;
        }
        stdev->models[i].kw_id = USELESS_KW_ID;
    } else {
        ALOGE("%s: ERROR: unknown model uuid", __func__);
        ret = -EINVAL;
        goto error;
    }

    *handle = i;
    ALOGV("%s: Loading model handle(%d) type(%d)", __func__,
        *handle, sound_model->type);
    // This will need to be replaced with UUID once they are fixed
    stdev->models[i].model_handle = *handle;
    stdev->models[i].type = sound_model->type;
    stdev->models[i].uuid = sound_model->vendor_uuid;
    stdev->models[i].sound_model_callback = callback;
    stdev->models[i].sound_model_cookie = cookie;
    stdev->models[i].recognition_callback = NULL;
    stdev->models[i].recognition_cookie = NULL;

    stdev->models[i].is_loaded = true;

error:
    if (ret != 0) {
        if (stdev->models[i].data) {
            free(stdev->models[i].data);
            stdev->models[i].data = NULL;
            stdev->models[i].data_sz = 0;
        }
        if (!is_any_model_loaded(stdev) && stdev->is_buffer_package_loaded) {
            destroy_buffer_package(stdev->odsp_hdl);
            stdev->is_buffer_package_loaded = false;
        }
    }

exit:
    pthread_mutex_unlock(&stdev->lock);
    ALOGD("-%s handle %d-", __func__, *handle);
    return ret;
}

static int stdev_unload_sound_model(const struct sound_trigger_hw_device *dev,
                                    sound_model_handle_t handle)
{
    struct knowles_sound_trigger_device *stdev =
        (struct knowles_sound_trigger_device *)dev;
    int ret = 0;
    ALOGD("+%s handle %d+", __func__, handle);
    pthread_mutex_lock(&stdev->lock);

    ret = check_firmware_ready(stdev);
    if (ret != 0)
        goto exit;

    // Just confirm the model was previously loaded
    if (stdev->models[handle].is_loaded == false) {
        ALOGE("%s: Invalid model(%d) being called for unload",
                __func__, handle);
        ret = -EINVAL;
        goto exit;
    }

    if (stdev->models[handle].is_active == true) {
        ret = stop_recognition(stdev, handle);
        if (ret)
            goto exit;
    }

    if (check_uuid_equality(stdev->models[handle].uuid,
                                stdev->sensor_model_uuid)) {
        // Inform the Host 1 that sensor route/packages are about to be
        // torndown and then wait for confirmation from Host 1 that it can be
        // torndown. Also start a timer for 5 seconds, if the Host 1 doesn't
        // send us the event within 5 seconds we force remove the sensor pkgs
        if (stdev->is_sensor_route_enabled == true) {
            struct itimerspec ss_timer_spec;
            struct sigevent ss_sigevent;

            // Inform the host 1
            stdev->is_sensor_destroy_in_prog = true;
            trigger_sensor_destroy_event(stdev->odsp_hdl);

            // Start timer for 5 seconds
            ss_sigevent.sigev_notify = SIGEV_THREAD;
            ss_sigevent.sigev_notify_function = sensor_timeout_recover;
            ss_sigevent.sigev_notify_attributes = NULL;

            ss_timer_spec.it_interval.tv_sec = 0;
            ss_timer_spec.it_interval.tv_nsec = 0;
            ss_timer_spec.it_value.tv_sec =
                    SENSOR_CREATE_WAIT_TIME_IN_S * SENSOR_CREATE_WAIT_MAX_COUNT;
            ss_timer_spec.it_value.tv_nsec = 0;

            if (stdev->ss_timer_created) {
                timer_delete(stdev->ss_timer);
                stdev->ss_timer_created = false;
            }

            if (timer_create(CLOCK_REALTIME,
                             &ss_sigevent, &stdev->ss_timer) == -1) {
                ALOGE("%s: Timer Create Failed", __func__);
            } else {
                stdev->ss_timer_created = true;
                if (timer_settime(stdev->ss_timer,
                                  0, &ss_timer_spec, NULL) == -1) {
                    ALOGE("%s: Timer Set Failed", __func__);
                }
            }
        }
    } else if (check_uuid_equality(stdev->models[handle].uuid,
                                   stdev->chre_model_uuid)) {
        // remove chre from recover list
        if (can_update_recover_list(stdev) == true)
            update_recover_list(stdev, handle, false);

         // Disable the CHRE route
        if (stdev->is_chre_loaded == true) {
            struct itimerspec chre_timer_spec;
            struct sigevent chre_sigevent;

            // Inform the host 1
            stdev->is_chre_destroy_in_prog = true;
            trigger_chre_destroy_event(stdev->odsp_hdl);

            // Start timer for 5 seconds
            chre_sigevent.sigev_notify = SIGEV_THREAD;
            chre_sigevent.sigev_notify_function = chre_timeout_recover;
            chre_sigevent.sigev_notify_attributes = NULL;

            chre_timer_spec.it_interval.tv_sec = 0;
            chre_timer_spec.it_interval.tv_nsec = 0;
            chre_timer_spec.it_value.tv_sec =
                    CHRE_CREATE_WAIT_TIME_IN_S * CHRE_CREATE_WAIT_MAX_COUNT;
            chre_timer_spec.it_value.tv_nsec = 0;

            if (stdev->chre_timer_created) {
                timer_delete(stdev->chre_timer);
                stdev->chre_timer_created = false;
            }

            if (timer_create(CLOCK_REALTIME,
                             &chre_sigevent, &stdev->chre_timer) == -1) {
                ALOGE("%s: Timer Create Failed", __func__);
            } else {
                stdev->chre_timer_created = true;
                if (timer_settime(stdev->chre_timer,
                                  0, &chre_timer_spec, NULL) == -1) {
                    ALOGE("%s: Timer Set Failed", __func__);
                }
            }
        }
    }

    stdev->models[handle].sound_model_callback = NULL;
    stdev->models[handle].sound_model_cookie = NULL;

    if (!(check_uuid_equality(stdev->models[handle].uuid,
                              stdev->sensor_model_uuid) &&
            stdev->is_sensor_destroy_in_prog) &&
        !(check_uuid_equality(stdev->models[handle].uuid,
                              stdev->chre_model_uuid) &&
            stdev->is_chre_destroy_in_prog)) {
        memset(&stdev->models[handle].uuid, 0, sizeof(sound_trigger_uuid_t));
        stdev->models[handle].is_loaded = false;
    }

    if (stdev->models[handle].data) {
        free(stdev->models[handle].data);
        stdev->models[handle].data = NULL;
        stdev->models[handle].data_sz = 0;
    }

    ALOGD("%s: Successfully unloaded the model, handle - %d",
        __func__, handle);
exit:
    pthread_mutex_unlock(&stdev->lock);
    ALOGD("-%s handle %d-", __func__, handle);
    return ret;
}

static int stdev_start_recognition(
                        const struct sound_trigger_hw_device *dev,
                        sound_model_handle_t handle,
                        const struct sound_trigger_recognition_config *config,
                        recognition_callback_t callback,
                        void *cookie)
{
    struct knowles_sound_trigger_device *stdev =
        (struct knowles_sound_trigger_device *)dev;
    int status = 0;
    struct model_info *model = &stdev->models[handle];

    ALOGD("%s stdev %p, sound model %d", __func__, stdev, handle);
    pthread_mutex_lock(&stdev->lock);

    status = check_firmware_ready(stdev);
    if (status != 0)
        goto exit;

    if (callback == NULL) {
        ALOGE("%s: recognition_callback is null", __func__);
        status = -EINVAL;
        goto exit;
    }

    if (model->config != NULL) {
        dereg_hal_event_session(model->config, handle);
        free(model->config);
        model->config = NULL;
    }

    if (config != NULL) {
        model->config = (struct sound_trigger_recognition_config *)
                            malloc(sizeof(*config));
        if (model->config == NULL) {
            ALOGE("%s: Failed to allocate memory for model config", __func__);
            status = -ENOMEM;
            goto exit;
        }

        memcpy(model->config, config, sizeof(*config));
        reg_hal_event_session(model->config, handle);

        ALOGD("%s: Is capture requested %d",
            __func__, config->capture_requested);
    } else {
        ALOGD("%s: config is null", __func__);
        model->config = NULL;
    }

    model->recognition_callback = callback;
    model->recognition_cookie = cookie;
    if (check_uuid_equality(model->uuid, stdev->chre_model_uuid) ||
        check_uuid_equality(model->uuid, stdev->sensor_model_uuid)) {
        // This avoids any processing of chre/oslo.
        goto exit;
    }
    if (model->is_active == true) {
        // This model is already active, do nothing except updating callbacks,
        // configs and cookie
        goto exit;
    }
    if (can_update_recover_list(stdev) == true) {
        // Device is in voice/VoIP call, add model to recover list first
        // recover model once voice/VoIP is ended.
        update_recover_list(stdev, handle, true);
        goto exit;
    }

    status = check_and_setup_buffer_package(stdev);
    if (status != 0) {
        ALOGE("%s: ERROR: Failed to load the buffer package", __func__);
    }

    model->is_active = true;

    handle_input_source(stdev, true);

    if (stdev->is_buffer_package_loaded == true) {
        setup_buffer(stdev, model, true);
    }

    if (check_uuid_equality(model->uuid, stdev->hotword_model_uuid) ||
        (check_uuid_equality(model->uuid, stdev->wakeup_model_uuid))) {
        if ((stdev->hotword_buffer_enable) &&
            (!(stdev->current_enable & HOTWORD_MASK) ||
              (stdev->current_enable & WAKEUP_MASK))) {
            set_hotword_buffer_route(stdev->route_hdl,
                                    stdev->is_bargein_route_enabled);
        }
    }

    if (check_uuid_equality(model->uuid, stdev->ambient_model_uuid) ||
        (check_uuid_equality(model->uuid, stdev->entity_model_uuid))) {
        if ((stdev->music_buffer_enable) &&
            (!(stdev->current_enable & AMBIENT_MASK) ||
              (stdev->current_enable & ENTITY_MASK))) {
            set_music_buffer_route(stdev->route_hdl,
                                stdev->is_bargein_route_enabled);
        }
    }

    setup_package(stdev, model);

    set_package_route(stdev, model->uuid, stdev->is_bargein_route_enabled);

exit:
    pthread_mutex_unlock(&stdev->lock);
    ALOGD("-%s sound model %d-", __func__, handle);
    return status;
}

static int stdev_start_recognition_extended(
                        const struct sound_trigger_hw_device *dev,
                        sound_model_handle_t sound_model_handle,
                        const struct sound_trigger_recognition_config_header *header,
                        recognition_callback_t callback,
                        void *cookie)
{
    struct sound_trigger_recognition_config_extended_1_3 *config_1_3 =
                 (struct sound_trigger_recognition_config_extended_1_3 *)header;
    int status = 0;

    if (header->version >= SOUND_TRIGGER_DEVICE_API_VERSION_1_3) {
        /* Use old version before we have real usecase */
        ALOGD("%s: Running 2_3", __func__);
        status = stdev_start_recognition(dev, sound_model_handle,
                                        &config_1_3->base,
                                        callback,
                                        cookie);
    } else {
        /* Rollback into old start recognition */
        ALOGD("%s: Running 2_1", __func__);
        status = stdev_start_recognition(dev, sound_model_handle,
                                        &config_1_3->base,
                                        callback,
                                        cookie);
    }

    return status;
}


static int stdev_stop_recognition(
                        const struct sound_trigger_hw_device *dev,
                        sound_model_handle_t handle)
{
    struct knowles_sound_trigger_device *stdev =
        (struct knowles_sound_trigger_device *)dev;
    int status = 0;
    ALOGD("+%s sound model %d+", __func__, handle);
    pthread_mutex_lock(&stdev->lock);

    status = check_firmware_ready(stdev);
    if (status != 0)
        goto exit;

    status = stop_recognition(stdev, handle);

    if (status != 0)
        goto exit;

    ALOGD("-%s sound model %d-", __func__, handle);
exit:
    pthread_mutex_unlock(&stdev->lock);

    return status;
}

/**
 * Get the state of a given model.
 * The model state is returned asynchronously as a RecognitionEvent via
 * the callback that was registered in StartRecognition().
 * @param modelHandle The handle of the sound model whose state is being
 *                    queried.
 * @return retval Operation completion status: 0 in case of success,
 *                -ENOSYS in case of invalid model handle,
 *                -ENOMEM in case of memory allocation failure,
 *                -ENODEV in case of initialization error,
 *                -EINVAL in case where a recognition event is already
 *                        being processed.
 */
static int stdev_get_model_state(const struct sound_trigger_hw_device *dev,
                               sound_model_handle_t sound_model_handle) {
    struct knowles_sound_trigger_device *stdev =
        (struct knowles_sound_trigger_device *)dev;
    struct model_info *model = &stdev->models[sound_model_handle];
    int ret = 0;
    ALOGD("+%s+", __func__);
    pthread_mutex_lock(&stdev->lock);

    ret = check_firmware_ready(stdev);
    if (ret != 0)
        goto exit;

    if (!stdev->opened) {
        ALOGE("%s: stdev isn't initialized", __func__);
        ret = -ENODEV;
        goto exit;
    }

    if (model->is_active == false &&
        !is_uuid_in_recover_list(stdev, sound_model_handle)) {
        ALOGE("%s: ERROR: %d model is not active",
            __func__, sound_model_handle);
        ret = -ENOSYS;
        goto exit;
    } else if (is_uuid_in_recover_list(stdev, sound_model_handle)) {
        ALOGD("%s: Ignore %d model request due to call active",
            __func__, sound_model_handle);
        goto exit;
    }

    if (model->is_state_query == true) {
        ALOGE("%s: ERROR: model %d is already processing",
            __func__, sound_model_handle);
        ret = -EINVAL;
        goto exit;
    }

    model->is_state_query = true;

    if (check_uuid_equality(model->uuid, stdev->hotword_model_uuid))
        ret = get_model_state(stdev->odsp_hdl, HOTWORD_INSTANCE_ID,
                            HOTWORD_SLOT_ID);
    else if (check_uuid_equality(model->uuid, stdev->wakeup_model_uuid))
        ret = get_model_state(stdev->odsp_hdl, HOTWORD_INSTANCE_ID,
                            WAKEUP_SLOT_ID);
    else if (check_uuid_equality(model->uuid, stdev->ambient_model_uuid))
        ret = get_model_state(stdev->odsp_hdl, AMBIENT_INSTANCE_ID,
                            AMBIENT_SLOT_ID);
    else if (check_uuid_equality(model->uuid, stdev->entity_model_uuid)) {
        ret = get_model_state(stdev->odsp_hdl, AMBIENT_INSTANCE_ID,
                            ENTITY_SLOT_ID);
    } else {
        ALOGE("%s: ERROR: %d model is not supported",
            __func__, sound_model_handle);
        ret = -ENOSYS;
    }

    if (ret != 0) {
        model->is_state_query = false;
        ALOGE("%s: ERROR: Failed to get the model state", __func__);
    }

exit:
    pthread_mutex_unlock(&stdev->lock);
    ALOGD("-%s-", __func__);
    return ret;
}

static int stdev_query_parameter(
                    const struct sound_trigger_hw_device *dev __unused,
                    sound_model_handle_t sound_model_handle __unused,
                    sound_trigger_model_parameter_t model_param __unused,
                    sound_trigger_model_parameter_range_t* param_range)
{
    ALOGW("%s: NOT SUPPORTED", __func__);
    param_range->is_supported = false;
    return 0;
}

static int stdev_set_parameter(
                           const struct sound_trigger_hw_device *dev __unused,
                           sound_model_handle_t sound_model_handle __unused,
                           sound_trigger_model_parameter_t model_param __unused,
                           int32_t value __unused)
{
    ALOGW("%s: NOT SUPPORTED", __func__);
    return -EINVAL;
}

static int stdev_get_parameter(
                           const struct sound_trigger_hw_device *dev __unused,
                           sound_model_handle_t sound_model_handle __unused,
                           sound_trigger_model_parameter_t model_param __unused,
                           int32_t* value __unused)
{
    ALOGW("%s: NOT SUPPORTED", __func__);
    return -EINVAL;
}


static int stdev_close(hw_device_t *device)
{
    struct knowles_sound_trigger_device *stdev =
        (struct knowles_sound_trigger_device *)device;
    int ret = 0;
    ALOGD("+%s+", __func__);
    pthread_mutex_lock(&stdev->lock);

    ret = check_firmware_ready(stdev);
    if (ret != 0)
        goto exit;

    if (!stdev->opened) {
        ALOGE("%s: device already closed", __func__);
        ret = -EFAULT;
        goto exit;
    }

    setup_slpi_wakeup_event(stdev->odsp_hdl, false);

    stdev->opened = false;

    if (stdev->send_sock >= 0)
        write(stdev->send_sock, "T", 1);
    pthread_join(stdev->callback_thread, (void **)NULL);

    if (stdev->route_hdl)
        audio_route_free(stdev->route_hdl);
    if (stdev->odsp_hdl)
        iaxxx_odsp_deinit(stdev->odsp_hdl);

    if (stdev->ss_timer_created) {
        timer_delete(stdev->ss_timer);
        stdev->ss_timer_created = false;
    }

exit:
    pthread_mutex_unlock(&stdev->lock);
    ALOGD("-%s-", __func__);
    return ret;
}

static int open_streaming_lib(struct knowles_sound_trigger_device *stdev) {
    int ret = 0;

    if (access(ADNC_STRM_LIBRARY_PATH, R_OK) == 0) {
        stdev->adnc_cvq_strm_lib = dlopen(ADNC_STRM_LIBRARY_PATH, RTLD_NOW);
        if (stdev->adnc_cvq_strm_lib == NULL) {
            char const *err_str = dlerror();
            ALOGE("%s: module = %s error = %s", __func__,
                ADNC_STRM_LIBRARY_PATH, err_str ? err_str : "unknown");
            ALOGE("%s: DLOPEN failed for %s", __func__, ADNC_STRM_LIBRARY_PATH);
        } else {
            ALOGV("%s: DLOPEN successful for %s",
                __func__, ADNC_STRM_LIBRARY_PATH);
            for (int index = 0; index < MAX_MODELS; index++) {
                stdev->adnc_strm_handle[index] = 0;
                stdev->adnc_strm_last_read[index] = reset_time;
            }
            stdev->adnc_strm_open =
                (int (*)(bool, int, int))dlsym(stdev->adnc_cvq_strm_lib,
                "adnc_strm_open");
            stdev->adnc_strm_read =
               (size_t (*)(long, void *, size_t))dlsym(stdev->adnc_cvq_strm_lib,
                "adnc_strm_read");
            stdev->adnc_strm_close =
                (int (*)(long))dlsym(stdev->adnc_cvq_strm_lib,
                "adnc_strm_close");
            if (!stdev->adnc_strm_open || !stdev->adnc_strm_read ||
                !stdev->adnc_strm_close) {
                ALOGE("%s: Error grabbing functions in %s", __func__,
                    ADNC_STRM_LIBRARY_PATH);
                stdev->adnc_strm_open = 0;
                stdev->adnc_strm_read = 0;
                stdev->adnc_strm_close = 0;
            }
        }
    }

    return ret;
}

static struct mixer* find_stdev_mixer_path(int card_num, char *mixer_path_xml)
{
    struct mixer *mixer = NULL;
    const char *in_snd_card_name;
    char *snd_card_name = NULL;
    char *tmp = NULL;
    char *platform = NULL;
    char *snd_card = NULL;
    char *device = NULL;

    mixer = mixer_open(card_num);

    if (!mixer) {
        ALOGE("%s: Unable to open the mixer: %d", __func__,
            card_num);
        return NULL;
    }

    in_snd_card_name = mixer_get_name(mixer);
    snd_card_name = strdup(in_snd_card_name);

    if (snd_card_name == NULL) {
        ALOGE("%s: snd_card_name is NULL", __func__);
        goto on_error;
    }

    platform = strtok_r(snd_card_name, "-", &tmp);
    if (platform == NULL) {
        ALOGE("%s: snd card is invalid", __func__);
        goto on_error;
    }

    snd_card = strtok_r(NULL, "-", &tmp);
    if (snd_card == NULL) {
        ALOGE("%s: snd card is invalid", __func__);
        goto on_error;
    }

    device = strtok_r(NULL, "-", &tmp);
    if (device != NULL) {
        snprintf(mixer_path_xml, NAME_MAX_SIZE, "%s_%s.xml",
                SOUND_TRIGGER_MIXER_PATH_BASE, device);
    } else {
        ALOGE("%s: Unknown device, try to use default xml", __func__);
        snprintf(mixer_path_xml, NAME_MAX_SIZE, "%s",
                SOUND_TRIGGER_MIXER_PATH_XML);
    }

    ALOGD("%s: using %s", __func__, mixer_path_xml);

on_error:
    if (snd_card_name)
        free(snd_card_name);
    return mixer;
}

static int find_sound_card() {
    int retry_num = 0, snd_card_num = 0, ret = -1;
    const char *snd_card_name;
    struct mixer *mixer = NULL;
    bool card_verifed[MAX_SND_CARD] = {false};
    const int retry_limit = property_get_int32("vendor.audio.snd_card.open.retries",
                                            RETRY_NUMBER);
    ALOGD("+%s+", __func__);

    for (;;) {
        if (snd_card_num >= MAX_SND_CARD) {
            if (retry_num++ >= retry_limit) {
                ALOGE("%s: iaxxx sound card not found", __func__);
                goto exit;
            }
            snd_card_num = 0;
            usleep(RETRY_US);
            continue;
        }
        if (card_verifed[snd_card_num]) {
            snd_card_num++;
            continue;
        }

        mixer = mixer_open(snd_card_num);
        if (!mixer) {
            snd_card_num++;
            continue;
        }

        snd_card_name = mixer_get_name(mixer);
        if (strstr(snd_card_name, CARD_NAME)) {
            ALOGD("%s: find card %d has iaxxx - %s",
                __func__, snd_card_num, snd_card_name);
            ret = snd_card_num;
            break;
        }

        ALOGD("%s: sound card %s does NOT have iaxxx", __func__, snd_card_name);
        mixer_close(mixer);
        mixer = NULL;
        card_verifed[snd_card_num] = true;
        snd_card_num++;
    }

exit:
    if (mixer)
        mixer_close(mixer);

    ALOGD("-%s-", __func__);
    return ret;
}

static int load_audio_hal()
{
    char audio_hal_lib[100];
    void *sthal_prop_api_version = NULL;
    struct knowles_sound_trigger_device *stdev = &g_stdev;
    int ret = 0;

    snprintf(audio_hal_lib, sizeof(audio_hal_lib), "%s/%s.%s.so",
            AUDIO_HAL_LIBRARY_PATH, AUDIO_HAL_NAME_PREFIX,
            SOUND_TRIGGER_PLATFORM);
    if (access(audio_hal_lib, R_OK)) {
        ALOGE("%s: ERROR. %s not found", __func__, audio_hal_lib);
        return -ENOENT;
    }

    stdev->audio_hal_handle = dlopen(audio_hal_lib, RTLD_NOW);
    if (stdev->audio_hal_handle == NULL) {
        ALOGE("%s: ERROR. %s", __func__, dlerror());
        return -ENODEV;
    }

    stdev->audio_hal_cb = dlsym(stdev->audio_hal_handle, "audio_hw_call_back");
    if (stdev->audio_hal_cb == NULL) {
        ALOGE("%s: ERROR. %s", __func__, dlerror());
        ret = -ENODEV;
        goto error;
    }

    sthal_prop_api_version = dlsym(stdev->audio_hal_handle,
                                "sthal_prop_api_version");
    if (sthal_prop_api_version == NULL) {
        stdev->sthal_prop_api_version = 0;
        ret = 0; /* passthru for backward compability */
    } else {
        stdev->sthal_prop_api_version = *(int *)sthal_prop_api_version;
        if (MAJOR_VERSION(stdev->sthal_prop_api_version) !=
            MAJOR_VERSION(STHAL_PROP_API_CURRENT_VERSION)) {
            ALOGE("%s: Incompatible API versions sthal:0x%x != ahal:0x%x",
                __func__, STHAL_PROP_API_CURRENT_VERSION,
                stdev->sthal_prop_api_version);
            goto error;
        }
        ALOGD("%s: ahal is using proprietary API version 0x%04x", __func__,
            stdev->sthal_prop_api_version);
    }

    ALOGD("%s: load AHAL successfully.", __func__);
    return ret;

error:
    dlclose(stdev->audio_hal_handle);
    stdev->audio_hal_handle = NULL;
    return ret;
}

static int stdev_open(const hw_module_t *module, const char *name,
        hw_device_t **device)
{
    struct knowles_sound_trigger_device *stdev;
    int ret = 0, i = 0;
    int snd_card_num = 0;

    ALOGE("!! Knowles SoundTrigger v1!!");

    if (strcmp(name, SOUND_TRIGGER_HARDWARE_INTERFACE) != 0)
        return -EINVAL;

    if (device == NULL)
        return -EINVAL;

    stdev = &g_stdev;
    pthread_mutex_lock(&stdev->lock);

    snd_card_num = find_sound_card();
    if (snd_card_num == -1) {
        ALOGE("%s: Unable to find the sound card %s", __func__, CARD_NAME);
        ret = -EAGAIN;
        goto exit;
    }

    if (stdev->opened) {
        ALOGE("%s: Only one sountrigger can be opened at a time", __func__);
        ret = -EBUSY;
        goto exit;
    }

    ret = open_streaming_lib(stdev);
    if (ret != 0) {
        ALOGE("%s: Couldnot open the streaming library", __func__);
        goto error;
    }

    ret = load_audio_hal();
    if (ret != 0) {
        ALOGE("%s: Couldn't load AHAL", __func__);
        goto error;
    }

    stdev->device.common.tag = HARDWARE_DEVICE_TAG;
    stdev->device.common.version = SOUND_TRIGGER_DEVICE_API_VERSION_1_3;
    stdev->device.common.module = (struct hw_module_t *)module;
    stdev->device.common.close = stdev_close;
    stdev->device.get_properties = stdev_get_properties;
    stdev->device.load_sound_model = stdev_load_sound_model;
    stdev->device.unload_sound_model = stdev_unload_sound_model;
    stdev->device.start_recognition = stdev_start_recognition;
    stdev->device.start_recognition_extended = stdev_start_recognition_extended;
    stdev->device.stop_recognition = stdev_stop_recognition;
    stdev->device.get_model_state = stdev_get_model_state;
    stdev->device.query_parameter = stdev_query_parameter;
    stdev->device.set_parameter = stdev_set_parameter;
    stdev->device.get_parameter = stdev_get_parameter;
    stdev->device.get_properties_extended = stdev_get_properties_extended;

    stdev->opened = true;
    stdev->send_sock = -1;
    stdev->recv_sock = -1;

    /* Initialize all member variable */
    for (i = 0; i < MAX_MODELS; i++) {
        stdev->models[i].type = SOUND_MODEL_TYPE_UNKNOWN;
        memset(&stdev->models[i].uuid, 0, sizeof(sound_trigger_uuid_t));
        stdev->models[i].config = NULL;
        stdev->models[i].data = NULL;
        stdev->models[i].data_sz = 0;
        stdev->models[i].is_loaded = false;
        stdev->models[i].is_active = false;
        stdev->models[i].is_state_query = false;
    }

    stdev->is_mic_route_enabled = false;
    stdev->is_con_mic_route_enabled = false;
    stdev->is_ahal_in_voice_voip_mode = false;
    stdev->is_ahal_voice_voip_stop = false;
    stdev->is_ahal_voice_voip_start = false;
    stdev->is_bargein_route_enabled = false;
    stdev->is_chre_loaded = false;
    stdev->is_buffer_package_loaded = false;
    stdev->hotword_buffer_enable = 0;
    stdev->music_buffer_enable = 0;
    stdev->current_enable = 0;
    stdev->is_sensor_route_enabled = false;
    stdev->recover_model_list = 0;
    stdev->rx_active_count = 0;
    stdev->is_ahal_media_recording = false;
    stdev->is_concurrent_capture = hw_properties.base.concurrent_capture;

    stdev->is_sensor_destroy_in_prog = false;
    stdev->ss_timer_created = false;

    stdev->is_chre_destroy_in_prog = false;
    stdev->chre_timer_created = false;

    stdev->snd_crd_num = snd_card_num;
    stdev->fw_reset_done_by_hal = false;
    stdev->hotword_version = 0;

    str_to_uuid(HOTWORD_AUDIO_MODEL, &stdev->hotword_model_uuid);
    str_to_uuid(WAKEUP_MODEL, &stdev->wakeup_model_uuid);
    str_to_uuid(SENSOR_MANAGER_MODEL, &stdev->sensor_model_uuid);
    str_to_uuid(AMBIENT_AUDIO_MODEL, &stdev->ambient_model_uuid);
    str_to_uuid(CHRE_AUDIO_MODEL, &stdev->chre_model_uuid);
    str_to_uuid(ENTITY_AUDIO_MODEL, &stdev->entity_model_uuid);

    stdev->odsp_hdl = iaxxx_odsp_init();
    if (stdev->odsp_hdl == NULL) {
        ALOGE("%s: Failed to get handle to ODSP HAL", __func__);
        ret = -EIO;
        goto error;
    }
    stdev->mixer = find_stdev_mixer_path(stdev->snd_crd_num, stdev->mixer_path_xml);
    if (stdev->mixer == NULL) {
        ALOGE("Failed to init the mixer");
        ret = -EAGAIN;
        goto error;
    }

    ALOGD("stdev before pthread_create %p", stdev);
    // Create a thread to handle all events from kernel
    pthread_create(&stdev->callback_thread, (const pthread_attr_t *) NULL,
                callback_thread_loop, stdev);

    pthread_create(&stdev->monitor_thread, (const pthread_attr_t *) NULL,
                monitor_thread_loop, stdev);

    pthread_create(&stdev->transitions_thread, (const pthread_attr_t *) NULL,
                transitions_thread_loop, stdev);

    *device = &stdev->device.common; /* same address as stdev */
exit:
    pthread_mutex_unlock(&stdev->lock);
    return ret;

error:
    if (stdev->adnc_cvq_strm_lib)
        dlclose(stdev->adnc_cvq_strm_lib);
    if (stdev->audio_hal_handle)
        dlclose(stdev->audio_hal_handle);
    if (stdev->route_hdl)
        audio_route_free(stdev->route_hdl);
    if (stdev->odsp_hdl)
        iaxxx_odsp_deinit(stdev->odsp_hdl);
    if (stdev->mixer)
        mixer_close(stdev->mixer);

    pthread_mutex_unlock(&stdev->lock);
    return ret;
}

/* AHAL calls this callback to communicate with STHAL */
int sound_trigger_hw_call_back(audio_event_type_t event,
                            struct audio_event_info *config)
{
    int ret = 0;
    int i = 0;
    int index = -1;
    struct knowles_sound_trigger_device *stdev = &g_stdev;
    enum sthal_mode pre_mode, cur_mode;

    if (!stdev)
        return -ENODEV;

    if (!stdev->opened) {
        ALOGE("%s: Error SoundTrigger has not been opened", __func__);
        return -EINVAL;
    }

    pthread_mutex_lock(&stdev->lock);

    // update conditions for mic concurrency whatever firmware status may be.
    if (event == AUDIO_EVENT_CAPTURE_DEVICE_INACTIVE ||
        event == AUDIO_EVENT_CAPTURE_DEVICE_ACTIVE ||
        event == AUDIO_EVENT_CAPTURE_STREAM_INACTIVE ||
        event == AUDIO_EVENT_CAPTURE_STREAM_ACTIVE) {
        pre_mode = get_sthal_mode(stdev);
        update_sthal_conditions(stdev, event, config);
        cur_mode = get_sthal_mode(stdev);
    }

    // update conditions for bargein whatever firmware status may be.
    if (event == AUDIO_EVENT_PLAYBACK_STREAM_INACTIVE ||
        event == AUDIO_EVENT_PLAYBACK_STREAM_ACTIVE) {
        update_rx_conditions(stdev, event, config);
    }

    if (stdev->is_st_hal_ready == false) {
        ALOGE("%s: ST HAL is not ready yet", __func__);
        ret = -EINVAL;
        goto exit;
    }

    switch (event) {
    case AUDIO_EVENT_CAPTURE_DEVICE_INACTIVE:
    case AUDIO_EVENT_CAPTURE_DEVICE_ACTIVE:
        ALOGD("%s: handle capture device event %d", __func__, event);

        //handle capture device on/off event
        do_handle_functions(stdev, pre_mode, cur_mode, event);

        break;
    case AUDIO_EVENT_CAPTURE_STREAM_INACTIVE:
    case AUDIO_EVENT_CAPTURE_STREAM_ACTIVE:
        ALOGD("%s: handle capture stream event %d, usecase %d",
              __func__, event, config->u.usecase.type);

        break;
    case AUDIO_EVENT_PLAYBACK_STREAM_INACTIVE:
        ALOGD("%s: handle playback stream inactive", __func__);

        if (stdev->rx_active_count == 0) {
            if (stdev->is_mic_route_enabled != false) {
                // Atleast one keyword model is active so update the routes
                // Check if the bargein route is enabled if not enable bargein route
                // Check each model, if it is active then update it's route
                if (stdev->is_bargein_route_enabled != false) {
                    ALOGD("Bargein disabling");
                    stdev->is_bargein_route_enabled = false;
                    // Check each model, if it is active then update it's route
                    // Disable the bargein route
                    for (i = 0; i < MAX_MODELS; i++) {
                        if (stdev->models[i].is_active == true) {
                            // teardown the package route with bargein
                            ret = tear_package_route(stdev,
                                                    stdev->models[i].uuid,
                                                    !stdev->is_bargein_route_enabled);
                            if (ret != 0) {
                                ALOGE("Failed to tear old package route");
                            }
                            // resetup the package route with out bargein
                            ret = set_package_route(stdev,
                                                    stdev->models[i].uuid,
                                                    stdev->is_bargein_route_enabled);
                            if (ret != 0) {
                                ALOGE("Failed to enable package route");
                            }
                        }
                    }

                    //Switch buffer input source
                    if (stdev->hotword_buffer_enable) {
                        ret = tear_hotword_buffer_route(stdev->route_hdl,
                                            !stdev->is_bargein_route_enabled);
                        if (ret != 0) {
                            ALOGE("Failed to tear old buffer route");
                        }
                        ret = set_hotword_buffer_route(stdev->route_hdl,
                                            stdev->is_bargein_route_enabled);
                        if (ret != 0) {
                            ALOGE("Failed to enable buffer route");
                        }
                    }

                    if (stdev->music_buffer_enable) {
                        ret = tear_music_buffer_route(stdev->route_hdl,
                                            !stdev->is_bargein_route_enabled);
                        if (ret != 0) {
                            ALOGE("Failed to tear old music buffer route");
                        }
                        ret = set_music_buffer_route(stdev->route_hdl,
                                            stdev->is_bargein_route_enabled);
                        if (ret != 0) {
                            ALOGE("Failed to enable buffer route");
                        }
                    }

                    ret = enable_bargein_route(stdev->route_hdl, false);
                    if (ret != 0) {
                        ALOGE("Failed to enable buffer route");
                    }

                    ret = destroy_aec_package(stdev->odsp_hdl);
                    if (ret != 0) {
                        ALOGE("Failed to destroy AEC package");
                    }

                    ret = enable_src_route(stdev->route_hdl, false, SRC_AMP_REF);
                    if (ret != 0) {
                        ALOGE("Failed to disable SRC-amp route");
                    }

                    ret = destroy_src_plugin(stdev->odsp_hdl, SRC_AMP_REF);
                    if (ret != 0) {
                        ALOGE("Failed to destroy SRC-amp package");
                    }

                    if (is_mic_controlled_by_ahal(stdev) == false) {
                        ret = enable_amp_ref_route(stdev->route_hdl, false, STRM_16K);
                        if (ret != 0) {
                            ALOGE("Failed to disable amp-ref route");
                        }
                        ret = enable_mic_route(stdev->route_hdl, false,
                                            EXTERNAL_OSCILLATOR);
                        if (ret != 0) {
                            ALOGE("Failed to disable mic route with INT OSC");
                        }
                        ret = enable_mic_route(stdev->route_hdl, true,
                                            INTERNAL_OSCILLATOR);
                        if (ret != 0) {
                            ALOGE("Failed to enable mic route with EXT OSC");
                        }
                    } else {
                        // main mic is turned by media record, close it by 48khz
                        ret = enable_amp_ref_route(stdev->route_hdl, false, STRM_48K);
                        if (ret != 0) {
                            ALOGE("Failed to disable amp-ref route");
                        }
                    }
                } else {
                    ALOGW("%s: barge-in isn't enabled", __func__);
                }
            }
        } else {
            ALOGD("%s: rx stream is still active", __func__);
        }
        break;
    case AUDIO_EVENT_PLAYBACK_STREAM_ACTIVE:
        ALOGD("%s: handle playback stream active", __func__);

        if (stdev->rx_active_count > 0) {
            if (stdev->is_mic_route_enabled != false) {
                // Atleast one keyword model is active so update the routes
                // Check if the bargein route is enabled if not enable bargein route
                // Check each model, if it is active then update it's route
                stdev->transit_case = TRANSIT_SETUP_AEC;
                pthread_cond_signal(&stdev->transition_cond);
            }
        } else {
            ALOGW("%s: unexpeted stream active event", __func__);
        }
        break;
    case AUDIO_EVENT_STOP_LAB:
        /* Close Stream Driver */
        for (i = 0; i < MAX_MODELS; i++) {
            if (stdev->models[i].is_active &&
                stdev->models[i].config &&
                (stdev->models[i].config->capture_handle ==
                 config->u.ses_info.capture_handle)) {
                index = i;
                break;
            }
        }

        /*
         * Close unused adnc if ...
         * 1. No capture handle is found
         * 2. Model is inactive
         * 3. adnc stream handle is existed
         */
        if (index == -1 && stdev->is_streaming > 0) {
            ALOGD("%s: close unused adnc handle, cap_handle:%d", __func__,
                  config->u.ses_info.capture_handle);
            for (i = 0; i < MAX_MODELS; i++) {
                if (stdev->adnc_strm_handle[i] != 0 &&
                    !stdev->models[i].is_active) {
                    stdev->adnc_strm_close(stdev->adnc_strm_handle[i]);
                    stdev->adnc_strm_handle[i] = 0;
                    stdev->is_streaming--;
                    stdev->adnc_strm_last_read[i] = reset_time;
                }
            }
            goto exit;
        }

        ALOGD("%s: close streaming %d, cap_handle:%d, index:%d",
              __func__, event, config->u.ses_info.capture_handle, index);
        if (index != -1 && stdev->adnc_strm_handle[index] != 0) {
            stdev->adnc_strm_close(stdev->adnc_strm_handle[index]);
            stdev->adnc_strm_handle[index] = 0;
            stdev->is_streaming--;
            stdev->adnc_strm_last_read[index] = reset_time;
        }

        break;

    case AUDIO_EVENT_SSR:
        /*[TODO] Do we need to handle adsp SSR event ? */
        ALOGD("%s: handle audio subsystem restart %d", __func__, event);
        break;

    case AUDIO_EVENT_READ_SAMPLES:
        /* It is possible to change session info, check config */
        if (config->u.aud_info.ses_info == NULL) {
            ALOGE("%s: Invalid config, event:%d", __func__, event);
            ret = -EINVAL;
            goto exit;
        }

        for (i = 0; i < MAX_MODELS; i++) {
            if (stdev->models[i].is_active &&
                stdev->models[i].config &&
                (stdev->models[i].config->capture_handle ==
                 config->u.aud_info.ses_info->capture_handle)) {
                index = i;
                break;
            }
        }

        /* There is not valid model to provide pcm samples. */
        if (i == MAX_MODELS) {
            ret = -EINVAL;
            goto exit;
        }

        /* Open Stream Driver */
        if (index != -1 && stdev->adnc_strm_handle[index] == 0) {
            if (stdev->adnc_strm_open == NULL) {
                ALOGE("%s: Error adnc streaming not supported", __func__);
            } else {
                bool keyword_stripping_enabled = false;
                int stream_end_point;
                if (check_uuid_equality(stdev->models[index].uuid,
                                        stdev->hotword_model_uuid)) {
                    stream_end_point = CVQ_ENDPOINT;
                } else if (check_uuid_equality(stdev->models[index].uuid,
                                        stdev->ambient_model_uuid)) {
                    stream_end_point = MUSIC_BUF_ENDPOINT;
                } else if (check_uuid_equality(stdev->models[index].uuid,
                                        stdev->entity_model_uuid)) {
                    stream_end_point = MUSIC_BUF_ENDPOINT;
                } else {
                    stream_end_point = CVQ_ENDPOINT;
                }
                stdev->adnc_strm_handle[index] = stdev->adnc_strm_open(
                                            keyword_stripping_enabled, 0,
                                            stream_end_point);
                if (stdev->adnc_strm_handle[index]) {
                    ALOGD("Opened adnc index %d handle %d endpoint 0x%x",
                          index, config->u.aud_info.ses_info->capture_handle,
                          stream_end_point);
                    stdev->is_streaming++;

                    clock_gettime(CLOCK_REALTIME, &stdev->adnc_strm_last_read[index]);
                    if (stdev->is_streaming == 1)
                        pthread_cond_signal(&stdev->tunnel_create);
                } else {
                    ALOGE("%s: DSP is currently not streaming", __func__);
                }
            }
        }

        if (index != -1 && stdev->adnc_strm_handle[index] != 0) {
            //ALOGD("%s: soundtrigger HAL adnc_strm_read", __func__);
            clock_gettime(CLOCK_REALTIME, &stdev->adnc_strm_last_read[index]);
            pthread_mutex_unlock(&stdev->lock);
            stdev->adnc_strm_read(stdev->adnc_strm_handle[index],
                                config->u.aud_info.buf,
                                config->u.aud_info.num_bytes);
            pthread_mutex_lock(&stdev->lock);
        } else {
            ALOGE("%s: soundtrigger is not streaming", __func__);
        }

        break;

    case AUDIO_EVENT_NUM_ST_SESSIONS:
    case AUDIO_EVENT_DEVICE_CONNECT:
    case AUDIO_EVENT_DEVICE_DISCONNECT:
    case AUDIO_EVENT_SVA_EXEC_MODE:
    case AUDIO_EVENT_SVA_EXEC_MODE_STATUS:
        ALOGV("%s: useless event %d", __func__, event);
        break;

    default:
        ALOGW("%s: Unknown event %d", __func__, event);
        break;
    }

exit:
    pthread_mutex_unlock(&stdev->lock);
    return ret;
}

static struct hw_module_methods_t hal_module_methods = {
    .open = stdev_open,
};

struct sound_trigger_module HAL_MODULE_INFO_SYM = {
    .common = {
        .tag = HARDWARE_MODULE_TAG,
        .module_api_version = SOUND_TRIGGER_MODULE_API_VERSION_1_0,
        .hal_api_version = HARDWARE_HAL_API_VERSION,
        .id = SOUND_TRIGGER_HARDWARE_MODULE_ID,
        .name = "Knowles Sound Trigger HAL",
        .author = "Knowles Electronics",
        .methods = &hal_module_methods,
    },
};
