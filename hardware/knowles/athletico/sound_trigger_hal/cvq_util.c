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

#define LOG_TAG "SoundTriggerHALUtil"
#define LOG_NDEBUG 0

#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdbool.h>
#include <string.h>
#include <signal.h>
#include <sys/stat.h>
#include <log/log.h>

#include <errno.h>
#include <linux/errno.h>
#include <sys/ioctl.h>

#include "cvq_ioctl.h"

/* Array for ST route */
static const char * const route_table[ST_ROUTE_MAX] = {

/* Input Port routing definition */
    [ST_MIC_ROUTE_INT_CLK] = "mic-route-internal-clock",
    [ST_MIC_ROUTE_EXT_CLK] = "mic-route-external-clock",
    [ST_BARGEIN_AMP_REF] = "bargein-amp-ref",
    [ST_BARGEIN_AMP_REF_48K] = "bargein-amp-ref-48k",

/* Plugin routing definition */
    [ST_HOTWORD_WITHOUT_BARGEIN] = "hotword-route-without-bargein",
    [ST_HOTWORD_WITH_BARGEIN] = "hotword-route-with-bargein",
    [ST_HOTWORD_BUFFER_WITHOUT_BARGEIN] = "buffer-route-without-bargein",
    [ST_HOTWORD_BUFFER_WITH_BARGEIN] = "buffer-route-with-bargein",
    [ST_AMBIENT_WITHOUT_BARGEIN] = "ambient-route-without-bargein",
    [ST_AMBIENT_WITH_BARGEIN] = "ambient-route-with-bargein",
    [ST_AMBIENT_BUFFER_WITHOUT_BARGEIN] = "downlink-audio-route",
    [ST_AMBIENT_BUFFER_WITH_BARGEIN] = "music-audio-route",
    [ST_BARGEIN_ROUTE] = "bargein-route",
    [ST_CHRE_WITHOUT_BARGEIN] = "chre-route-without-bargein",
    [ST_CHRE_WITH_BARGEIN] = "chre-route-with-bargein",
    [ST_SRC_ROUTE_MIC] = "src-route-mic",
    [ST_SRC_ROUTE_AMP_REF] = "src-route-amp-ref",
    [ST_SENSOR_ROUTE] = "oslo-route",
};

int write_model(struct iaxxx_odsp_hw *odsp_hdl, unsigned char *data,
                int length, int kw_type)
 {
    int err = 0;

    switch(kw_type)
    {
        case 0: //HOTWORD
            ALOGV("+%s+ OK_GOOGLE_KW_ID", __func__);

            err = iaxxx_odsp_plugin_set_parameter_blk(odsp_hdl,
                                        HOTWORD_INSTANCE_ID, HOTWORD_SLOT_ID,
                                        IAXXX_HMD_BLOCK_ID, data, length);
            break;
        case 1: //AMBIENT
            ALOGV("+%s+ AMBIENT_KW_ID", __func__);

            err = iaxxx_odsp_plugin_set_parameter_blk(odsp_hdl,
                                    AMBIENT_INSTANCE_ID, AMBIENT_SLOT_ID,
                                    IAXXX_HMD_BLOCK_ID, data, length);
            break;
        case 2: //ENTITY
            ALOGV("+%s+ Entity_KW_ID", __func__);

            err = iaxxx_odsp_plugin_set_parameter_blk(odsp_hdl,
                                    AMBIENT_INSTANCE_ID, ENTITY_SLOT_ID,
                                    IAXXX_HMD_BLOCK_ID, data, length);
            break;
        case 3: //WAKEUP
            ALOGV("+%s+ WAKEUP_KW_ID", __func__);

            err = iaxxx_odsp_plugin_set_parameter_blk(odsp_hdl,
                                    HOTWORD_INSTANCE_ID, WAKEUP_SLOT_ID,
                                    IAXXX_HMD_BLOCK_ID, data, length);
            break;
        default:
            ALOGE("%s: Unknown KW_ID\n", __func__);
            err = -EINVAL;
            goto exit;
    }

    if (err < 0) {
        ALOGE("%s: Failed to load the keyword with error %s\n",
            __func__, strerror(errno));
        goto exit;
    }

    ALOGV("-%s-", __func__);
exit:
    return err;
}

int get_model_state(struct iaxxx_odsp_hw *odsp_hdl, const uint32_t inst_id,
                    const uint32_t param_val)
{
    int err = 0;
    const uint32_t param_id = AMBIENT_GET_MODEL_STATE_PARAM_ID;
    const uint32_t block_id = IAXXX_HMD_BLOCK_ID;

    err = iaxxx_odsp_plugin_set_parameter(odsp_hdl, inst_id, param_id,
                                          param_val, block_id);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to get the model state", __func__);
    }

    return err;
}

int get_event(struct iaxxx_odsp_hw *odsp_hdl, struct iaxxx_get_event_info *ge)
{
    int err = 0;

    ALOGV("+%s+", __func__);
    err = iaxxx_odsp_evt_getevent(odsp_hdl, ge);
    if (err != 0) {
        ALOGE("%s: ERROR Failed to get event with error %d(%s)",
            __func__, errno, strerror(errno));
    }

    ALOGV("-%s-", __func__);
    return err;
}

int reset_ambient_plugin(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;

    ALOGV("+%s+", __func__);
    err = iaxxx_odsp_plugin_set_parameter(odsp_hdl,
                                        AMBIENT_INSTANCE_ID,
                                        AMBIENT_RESET_PARAM_ID,
                                        AMBIENT_SLOT_ID,
                                        IAXXX_HMD_BLOCK_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Set param for ambient lib reset failed %d(%s)",
            __func__, errno, strerror(errno));
    }

    ALOGV("-%s-", __func__);
    return err;
}

int set_sensor_route(struct audio_route *route_hdl, bool enable)
{
    int err = 0;

    ALOGV("+%s+", __func__);
    if (enable)
        err = audio_route_apply_and_update_path(route_hdl, route_table[ST_SENSOR_ROUTE]);
    else
        err = audio_route_reset_and_update_path(route_hdl, route_table[ST_SENSOR_ROUTE]);
    if (err)
        ALOGE("%s: route fail %d", __func__, err);

    ALOGV("-%s-", __func__);
    return err;
}

int set_ambient_state(struct iaxxx_odsp_hw *odsp_hdl,
                    unsigned int current)
{
    int err = 0;
    ALOGV("+%s+ enable models %x", __func__, current & PLUGIN2_MASK);

    err = iaxxx_odsp_plugin_setevent(odsp_hdl, AMBIENT_INSTANCE_ID,
                                    current & PLUGIN2_MASK, IAXXX_HMD_BLOCK_ID);
    if (err < 0) {
        ALOGE("%s: ERROR: ambient set event failed with error %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }
    if (current & AMBIENT_MASK) {
        err = iaxxx_odsp_evt_subscribe(odsp_hdl, AMBIENT_EVT_SRC_ID,
                                    AMBIENT_DETECTION, IAXXX_SYSID_HOST, 0);
        if (err < 0) {
            ALOGE("%s: ERROR: Ambient subscribe event failed"
                " with error %d(%s)", __func__,
                errno, strerror(errno));
            goto exit;
        }

    }
    if (current & ENTITY_MASK) {
        err = iaxxx_odsp_evt_subscribe(odsp_hdl, AMBIENT_EVT_SRC_ID,
                                    ENTITY_DETECTION, IAXXX_SYSID_HOST, 0);
        if (err < 0) {
            ALOGE("%s: ERROR: Entity subscribe event failed"
                " with error %d(%s)", __func__,
                errno, strerror(errno));
            goto exit;
        }
    }

exit:
    ALOGV("-%s-", __func__);
    return err;
}

int tear_ambient_state(struct iaxxx_odsp_hw *odsp_hdl,
                    unsigned int current)
{
    int err = 0;
    ALOGV("+%s+ current %x", __func__, current & PLUGIN2_MASK);
    if (current & AMBIENT_MASK) {
        err = iaxxx_odsp_evt_unsubscribe(odsp_hdl, AMBIENT_EVT_SRC_ID,
                                        AMBIENT_DETECTION, IAXXX_SYSID_HOST);
        if (err < 0) {
            ALOGE("%s: ERROR: Ambient unsubscribe event failed"
                " with error %d(%s)", __func__,
                errno, strerror(errno));
            goto exit;
        }
        err = iaxxx_odsp_plugin_set_parameter(odsp_hdl,
                                            AMBIENT_INSTANCE_ID,
                                            AMBIENT_UNLOAD_PARAM_ID,
                                            AMBIENT_SLOT_ID,
                                            IAXXX_HMD_BLOCK_ID);
        if (err < 0) {
            ALOGE("%s: ERROR: Ambient model unload failed with error %d(%s)",
                __func__, errno, strerror(errno));
            goto exit;
        }
    }
    if (current & ENTITY_MASK) {
        err = iaxxx_odsp_evt_unsubscribe(odsp_hdl, AMBIENT_EVT_SRC_ID,
                                        ENTITY_DETECTION, IAXXX_SYSID_HOST);
        if (err < 0) {
            ALOGE("%s: ERROR: Entity unsubscribe event failed"
                " with error %d(%s)", __func__,
                errno, strerror(errno));
            goto exit;
        }
        err = iaxxx_odsp_plugin_set_parameter(odsp_hdl,
                                            AMBIENT_INSTANCE_ID,
                                            AMBIENT_UNLOAD_PARAM_ID,
                                            ENTITY_SLOT_ID,
                                            IAXXX_HMD_BLOCK_ID);
        if (err < 0) {
            ALOGE("%s: ERROR: Entity model unload failed with error %d(%s)",
                __func__, errno, strerror(errno));
            goto exit;
        }
    }

exit:
    ALOGV("-%s-", __func__);
    return err;
}

int set_ambient_route(struct audio_route *route_hdl, bool bargein)
{
    int err = 0;

    ALOGV("+%s bargein %d+", __func__, bargein);

    if (bargein == true)
        err = audio_route_apply_and_update_path(route_hdl,
                                        route_table[ST_AMBIENT_WITH_BARGEIN]);
    else
        err = audio_route_apply_and_update_path(route_hdl,
                                        route_table[ST_AMBIENT_WITHOUT_BARGEIN]);
    if (err)
        ALOGE("%s: route apply fail %d", __func__, err);

    ALOGV("-%s-", __func__);
    return err;
}

int tear_ambient_route(struct audio_route *route_hdl, bool bargein)
{
    int err = 0;

    ALOGV("+%s bargein %d+", __func__, bargein);
    /* check cvq node to send ioctl */
    if (bargein == true)
        err = audio_route_reset_and_update_path(route_hdl,
                                        route_table[ST_AMBIENT_WITH_BARGEIN]);
    else
        err = audio_route_reset_and_update_path(route_hdl,
                                        route_table[ST_AMBIENT_WITHOUT_BARGEIN]);
    if (err)
        ALOGE("%s: route reset fail %d", __func__, err);

    ALOGV("-%s-", __func__);
    return err;
}

int set_hotword_state(struct iaxxx_odsp_hw *odsp_hdl, unsigned int current)
{
    int err = 0;

    ALOGV("+%s+ current %x", __func__, current & PLUGIN1_MASK);
    // Set the events and params
    err = iaxxx_odsp_plugin_setevent(odsp_hdl, HOTWORD_INSTANCE_ID,
                                    current & PLUGIN1_MASK, IAXXX_HMD_BLOCK_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Hotword set event failed with error %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    if (current & HOTWORD_MASK) {
        ALOGD("Registering for Hotword event\n");
        err = iaxxx_odsp_evt_subscribe(odsp_hdl, HOTWORD_EVT_SRC_ID,
                                    HOTWORD_DETECTION, IAXXX_SYSID_HOST, 0);
        if (err < 0) {
            ALOGE("%s: ERROR: HOTWORD subscribe event failed"
                " with error %d(%s)", __func__,
                errno, strerror(errno));
            goto exit;
        }

    }
    if (current & WAKEUP_MASK) {
        ALOGD("Registering for Wakeup event\n");
        err = iaxxx_odsp_evt_subscribe(odsp_hdl, HOTWORD_EVT_SRC_ID,
                                    WAKEUP_DETECTION, IAXXX_SYSID_HOST, 0);
        if (err < 0) {
            ALOGE("%s: ERROR: WAKEUP subscribe event failed"
                " with error %d(%s)", __func__,
                errno, strerror(errno));
            goto exit;
        }
    }

    ALOGV("-%s-", __func__);
exit:
    return err;
}

int tear_hotword_state(struct iaxxx_odsp_hw *odsp_hdl, unsigned int current)
{
    int err = 0;

    ALOGV("+%s+ current %x", __func__, current & PLUGIN1_MASK);
    if (current & HOTWORD_MASK) {
        err = iaxxx_odsp_evt_unsubscribe(odsp_hdl, HOTWORD_EVT_SRC_ID,
                                        HOTWORD_DETECTION, IAXXX_SYSID_HOST);
        if (err < 0) {
            ALOGE("%s: ERROR: Hotword unsubscribe event failed"
                " with error %d(%s)", __func__,
                errno, strerror(errno));
            goto exit;
        }
        err = iaxxx_odsp_plugin_set_parameter(odsp_hdl,
                                            HOTWORD_INSTANCE_ID,
                                            HOTWORD_UNLOAD_PARAM_ID,
                                            HOTWORD_SLOT_ID,
                                            IAXXX_HMD_BLOCK_ID);

        if (err < 0) {
            ALOGE("%s: ERROR: Ambient model unload failed with error %d(%s)",
                __func__, errno, strerror(errno));
            goto exit;
        }
    }
    if (current & WAKEUP_MASK) {
        err = iaxxx_odsp_evt_unsubscribe(odsp_hdl, HOTWORD_EVT_SRC_ID,
                                        WAKEUP_DETECTION, IAXXX_SYSID_HOST);
        if (err < 0) {
            ALOGE("%s: ERROR: WAKEUP unsubscribe event failed"
                " with error %d(%s)", __func__,
                errno, strerror(errno));
            goto exit;
        }
        err = iaxxx_odsp_plugin_set_parameter(odsp_hdl,
                                            HOTWORD_INSTANCE_ID,
                                            HOTWORD_UNLOAD_PARAM_ID,
                                            WAKEUP_SLOT_ID,
                                            IAXXX_HMD_BLOCK_ID);
        if (err < 0) {
            ALOGE("%s: ERROR: WAKEUP model unload failed with error %d(%s)",
                __func__, errno, strerror(errno));
            goto exit;
        }
    }

    ALOGV("-%s-", __func__);
exit:
    return err;
}

int set_hotword_route(struct audio_route *route_hdl, bool bargein)
{
    int err = 0;

    ALOGV("+%s bargein %d+", __func__, bargein);

    if (bargein == true)
        err = audio_route_apply_and_update_path(route_hdl,
                                            route_table[ST_HOTWORD_WITH_BARGEIN]);
    else
        err = audio_route_apply_and_update_path(route_hdl,
                                            route_table[ST_HOTWORD_WITHOUT_BARGEIN]);
    if (err)
        ALOGE("%s: route apply fail %d", __func__, err);

    ALOGV("-%s-", __func__);
    return err;
}

int tear_hotword_route(struct audio_route *route_hdl, bool bargein)
{
    int err = 0;

    ALOGV("+%s bargein %d+", __func__, bargein);
    /* check cvq node to send ioctl */
    if (bargein == true)
        err = audio_route_reset_and_update_path(route_hdl,
                                            route_table[ST_HOTWORD_WITH_BARGEIN]);
    else
        err = audio_route_reset_and_update_path(route_hdl,
                                            route_table[ST_HOTWORD_WITHOUT_BARGEIN]);
    if (err)
        ALOGE("%s: route reset fail %d", __func__, err);

    ALOGV("-%s-", __func__);
    return err;
}

int set_chre_audio_route(struct audio_route *route_hdl, bool bargein)
{
    int err = 0;

    ALOGV("+%s+", __func__);
    if (bargein)
        err = audio_route_apply_and_update_path(route_hdl,
                                        route_table[ST_CHRE_WITH_BARGEIN]);
    else
        err = audio_route_apply_and_update_path(route_hdl,
                                        route_table[ST_CHRE_WITHOUT_BARGEIN]);
    if (err)
        ALOGE("%s: route apply fail %d", __func__, err);

    ALOGV("-%s-", __func__);
    return err;
}

int tear_chre_audio_route(struct audio_route *route_hdl, bool bargein)
{
    int err = 0;

    ALOGV("+%s+", __func__);
    if (bargein == true)
        err = audio_route_reset_and_update_path(route_hdl,
                                        route_table[ST_CHRE_WITH_BARGEIN]);
    else
        err = audio_route_reset_and_update_path(route_hdl,
                                        route_table[ST_CHRE_WITHOUT_BARGEIN]);
    if (err)
        ALOGE("%s: route reset fail %d", __func__, err);

    ALOGV("-%s-", __func__);
    return err;
}

int sensor_event_init_params(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;

    ALOGV("+%s+", __func__);
    // Set the events and params
    err = iaxxx_odsp_plugin_setevent(odsp_hdl, SENSOR_INSTANCE_ID, 0x1F,
                                    IAXXX_HMD_BLOCK_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Sensor set event with error %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("Registering for 3 sensor mode switch events\n");

    // Subscribe for events
    err = iaxxx_odsp_evt_subscribe(odsp_hdl, OSLO_EVT_SRC_ID,
                                SENSOR_PRESENCE_MODE, IAXXX_SYSID_SCRIPT_MGR,
                                0x1201);
    if (err != 0) {
        ALOGE("%s: ERROR: Sensor subscribe (presence mode) failed %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    // Subscribe for events
    err = iaxxx_odsp_evt_subscribe(odsp_hdl, OSLO_EVT_SRC_ID,
                                SENSOR_DETECTED_MODE, IAXXX_SYSID_SCRIPT_MGR,
                                0x1202);
    if (err != 0) {
        ALOGE("%s: ERROR: Sensor subscribe (detection mode) failed %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    // Subscribe for events
    err = iaxxx_odsp_evt_subscribe(odsp_hdl, OSLO_EVT_SRC_ID,
                                SENSOR_MAX_MODE, IAXXX_SYSID_HOST, 0);
    if (err != 0) {
        ALOGE("%s: ERROR: Sensor subscribe (max mode) failed %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_evt_subscribe(odsp_hdl, OSLO_EVT_SRC_ID,
                                OSLO_DATA_EVENT_ID, IAXXX_SYSID_HOST_1, 0);
    if (err != 0) {
        ALOGE("%s: ERROR: Sensor subscribe (oslo data event) failed %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_evt_subscribe(odsp_hdl, OSLO_EVT_SRC_ID,
                                OSLO_CONFIGURED, IAXXX_SYSID_HOST_1, 0);
    if (err != 0) {
        ALOGE("%s: ERROR: Sensor subscribe (oslo configured) failed %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_evt_subscribe(odsp_hdl, OSLO_EVT_SRC_ID,
                                OSLO_DESTROYED, IAXXX_SYSID_HOST_1, 0);
    if (err != 0) {
        ALOGE("%s: ERROR: Sensor subscribe (oslo destroyed) %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_evt_subscribe(odsp_hdl, IAXXX_SYSID_HOST_1,
                                   OSLO_EP_DISCONNECT, IAXXX_SYSID_HOST_0, 0);
    if (err == -1) {
        ALOGE("%s: ERROR: oslo event subscription (oslo ep disconnect) failed with"
              " error %d(%s)", __func__, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_evt_trigger(odsp_hdl, OSLO_EVT_SRC_ID, OSLO_CONFIGURED, 0);
    if (err != 0) {
        ALOGE("%s: ERROR: olso event trigger (oslo configured) failed %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGV("-%s-", __func__);

exit:
    return err;
}

static int sensor_event_deinit_params(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;

    ALOGD("+%s+", __func__);

    err = iaxxx_odsp_evt_unsubscribe(odsp_hdl, OSLO_EVT_SRC_ID, SENSOR_MAX_MODE,
                                    IAXXX_SYSID_HOST);
    if (err != 0) {
        ALOGE("%s: Failed to unsubscribe sensor event (src id %d event id %d)"
            " error %d(%s)", __func__, OSLO_EVT_SRC_ID,
            SENSOR_MAX_MODE, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_evt_unsubscribe(odsp_hdl, OSLO_EVT_SRC_ID,
                                SENSOR_DETECTED_MODE, IAXXX_SYSID_SCRIPT_MGR);
    if (err != 0) {
        ALOGE("%s: Failed to unsubscribe sensor event (src id %d event id %d)"
              " error %d(%s)", __func__, OSLO_EVT_SRC_ID,
              SENSOR_DETECTED_MODE, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_evt_unsubscribe(odsp_hdl, OSLO_EVT_SRC_ID,
                                SENSOR_PRESENCE_MODE, IAXXX_SYSID_SCRIPT_MGR);
    if (err != 0) {
        ALOGE("%s: Failed to unsubscribe sensor event (src id %d event id %d)"
              " error %d(%s)", __func__, OSLO_EVT_SRC_ID,
              SENSOR_PRESENCE_MODE, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_evt_unsubscribe(odsp_hdl, OSLO_EVT_SRC_ID,
                                OSLO_DATA_EVENT_ID, IAXXX_SYSID_HOST_1);
    if (err != 0) {
        ALOGE("%s: Failed to unsubscribe sensor event (src id %d event id %d)"
              " from host %d error %d(%s)", __func__, OSLO_EVT_SRC_ID,
              OSLO_DATA_EVENT_ID, IAXXX_SYSID_HOST_1, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_evt_unsubscribe(odsp_hdl, OSLO_EVT_SRC_ID,
                                OSLO_CONFIGURED, IAXXX_SYSID_HOST_1);
    if (err != 0) {
        ALOGE("%s: Failed to unsubscribe sensor event (src id %d event id %d)"
              " from host %d error %d(%s)", __func__, OSLO_EVT_SRC_ID,
              OSLO_CONFIGURED, IAXXX_SYSID_HOST_1, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_evt_unsubscribe(odsp_hdl, OSLO_EVT_SRC_ID,
                                OSLO_DESTROYED, IAXXX_SYSID_HOST_1);
    if (err != 0) {
        ALOGE("%s: Failed to unsubscribe sensor event (src id %d event id %d)"
              " from host %d error %d(%s)", __func__, OSLO_EVT_SRC_ID,
              OSLO_DESTROYED, IAXXX_SYSID_HOST_1, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_evt_unsubscribe(odsp_hdl, IAXXX_SYSID_HOST_1,
                                OSLO_EP_DISCONNECT, IAXXX_SYSID_HOST_0);
    if (err != 0) {
        ALOGE("%s: Failed to unsubscribe sensor event (src id %d event id %d)"
              " from host %d with the error %d(%s)", __func__, IAXXX_SYSID_HOST_1,
              OSLO_EP_DISCONNECT, IAXXX_SYSID_HOST_0, errno, strerror(errno));
         goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;
}

int flush_model(struct iaxxx_odsp_hw *odsp_hdl, int kw_type)
{
    int err = 0;

    ALOGV("+%s+", __func__);

    switch(kw_type)
    {
        case 0: //HOTWORD
            err = iaxxx_odsp_plugin_set_parameter(odsp_hdl,
                                                HOTWORD_INSTANCE_ID,
                                                HOTWORD_UNLOAD_PARAM_ID,
                                                HOTWORD_SLOT_ID,
                                                IAXXX_HMD_BLOCK_ID);
            break;
        case 1: //AMBIENT
            err = iaxxx_odsp_plugin_set_parameter(odsp_hdl,
                                                AMBIENT_INSTANCE_ID,
                                                AMBIENT_UNLOAD_PARAM_ID,
                                                AMBIENT_SLOT_ID,
                                                IAXXX_HMD_BLOCK_ID);
            break;
        case 2: //ENTITY
            err = iaxxx_odsp_plugin_set_parameter(odsp_hdl,
                                                AMBIENT_INSTANCE_ID,
                                                AMBIENT_UNLOAD_PARAM_ID,
                                                ENTITY_SLOT_ID,
                                                IAXXX_HMD_BLOCK_ID);
            break;
        case 3: //WAKEUP
            err = iaxxx_odsp_plugin_set_parameter(odsp_hdl,
                                                HOTWORD_INSTANCE_ID,
                                                HOTWORD_UNLOAD_PARAM_ID,
                                                WAKEUP_SLOT_ID,
                                                IAXXX_HMD_BLOCK_ID);
            break;
        default:
            ALOGE("%s: Unknown KW_ID\n", __func__);
            err = -1;
            errno = -EINVAL;
            break;
    }

    if (err < 0) {
        ALOGE("%s: ERROR: model unload set param failed with error %d(%s)",
            __func__, errno, strerror(errno));
    }

    ALOGV("-%s-", __func__);
    return err;
}

int setup_buffer_package(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;

    ALOGD("+%s+", __func__);

    err = iaxxx_odsp_package_load(odsp_hdl, BUFFER_PACKAGE, BUF_PKG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to load Buffer package %d(%s)",
                __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;
}

int destroy_buffer_package(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;

    ALOGD("+%s+", __func__);

    err = iaxxx_odsp_package_unload(odsp_hdl, BUF_PKG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to unload Buffer package %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;
}

int setup_hotword_package(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;

    ALOGD("+%s+", __func__);

    // Download packages for ok google
    err = iaxxx_odsp_package_load(odsp_hdl, AMBIENT_EC_PACKAGE,
                                HOTWORD_PKG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to load Hotword package %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    // Create Hotword plugin
    err = iaxxx_odsp_plugin_create(odsp_hdl, HOTWORD_INSTANCE_ID, HOTWORD_PRIORITY,
                                HOTWORD_PKG_ID, HOTWORD_PLUGIN_IDX,
                                IAXXX_HMD_BLOCK_ID, PLUGIN_DEF_CONFIG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to create Hotword plugin %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_plugin_set_parameter(odsp_hdl, HOTWORD_INSTANCE_ID, 0,
                                          0, IAXXX_HMD_BLOCK_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Hotword init frontend failed %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;
}

int destroy_hotword_package(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;

    ALOGD("+%s+", __func__);

    err = iaxxx_odsp_plugin_destroy(odsp_hdl, HOTWORD_INSTANCE_ID,
                                    IAXXX_HMD_BLOCK_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to destroy Hotword plugin %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    // Unload hotword package
    err = iaxxx_odsp_package_unload(odsp_hdl, HOTWORD_PKG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to unload Hotword package %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;
}

int get_hotword_info(struct iaxxx_odsp_hw *odsp_hdl,
                    unsigned int *hotword_version, void *supported_model_arch)
{
    int err = 0;
    const uint32_t inst_id = HOTWORD_INSTANCE_ID;
    const uint32_t param_id = HOTWORD_GET_VERSION_PARAM_ID;
    const uint32_t block_id = IAXXX_HMD_BLOCK_ID;
    unsigned int param_val = HOTWORD_DEFAULT_VER;

    err = setup_hotword_package(odsp_hdl);
    if (err != 0) {
        if (errno == EEXIST) {
            ALOGW("%s: WARN: Hotword package existed already", __func__);
            err = iaxxx_odsp_plugin_get_parameter(odsp_hdl, inst_id, param_id,
                                                  block_id, &param_val);
            if (err != 0) {
                ALOGE("%s: Failed to get parameter for id %u with error %d: %s",
                      __func__, param_id, err, strerror(errno));
            } else {
                ALOGD("%s: Value of parameter id %u is %u", __func__, param_id,
                      param_val);
            }
            *hotword_version = param_val;
            err = iaxxx_odsp_plugin_get_parameter_blk(odsp_hdl, inst_id, block_id,
                                                    PARAM_BLOCK_GET_DSP_IDENTIFIER,
                                                    supported_model_arch,
                                                    PAYLOAD_MAX_STRING_LEN);
            if (err != 0) {
                ALOGE("%s: Failed to get dsp identifier with error %d: %s",
                      __func__, err, strerror(errno));
            }
        } else {
            ALOGE("%s: ERROR: setup hotword package failed", __func__);
        }
    } else {
        err = iaxxx_odsp_plugin_get_parameter(odsp_hdl, inst_id, param_id,
                                              block_id, &param_val);
        if (err != 0) {
            ALOGE("%s: Failed to get parameter for id %u with error %d: %s",
                  __func__, param_id, err, strerror(errno));
        } else {
            ALOGD("%s: Value of parameter id %u is %u", __func__, param_id,
                  param_val);
        }
        *hotword_version = param_val;
        err = iaxxx_odsp_plugin_get_parameter_blk(odsp_hdl, inst_id, block_id,
                                                PARAM_BLOCK_GET_DSP_IDENTIFIER,
                                                supported_model_arch,
                                                PAYLOAD_MAX_STRING_LEN);
        if (err != 0) {
            ALOGE("%s: Failed to get dsp identifier with error %d: %s",
                  __func__, err, strerror(errno));
        }
        err = destroy_hotword_package(odsp_hdl);
        if (err != 0)
            ALOGE("%s: ERROR: destroy hotword package failed", __func__);
    }

    return err;
}

int setup_ambient_package(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;

    ALOGD("+%s+", __func__);

    // Download packages for ambient
    err = iaxxx_odsp_package_load(odsp_hdl, AMBIENT_DA_PACKAGE,
                                AMBIENT_PKG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to load Ambient package %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    // Create Ambient plugin
    err = iaxxx_odsp_plugin_create(odsp_hdl, AMBIENT_INSTANCE_ID,
                                AMBIENT_PRIORITY, AMBIENT_PKG_ID,
                                AMBIENT_PLUGIN_IDX, IAXXX_HMD_BLOCK_ID,
                                PLUGIN_DEF_CONFIG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to create Ambient plugin %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_plugin_set_parameter(odsp_hdl, AMBIENT_INSTANCE_ID,
                                        0, 0, IAXXX_HMD_BLOCK_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Ambient init frontend failed %d(%s)",
              __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;
}

int destroy_ambient_package(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;

    ALOGD("+%s+", __func__);

    err = iaxxx_odsp_plugin_destroy(odsp_hdl, AMBIENT_INSTANCE_ID,
                                    IAXXX_HMD_BLOCK_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to destroy Ambient plugin %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_package_unload(odsp_hdl, AMBIENT_PKG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to unload Ambient package %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;
}

int get_ambient_info(struct iaxxx_odsp_hw *odsp_hdl,
                    unsigned int *ambient_version, void *supported_model_arch)
{
    int err = 0;
    const uint32_t inst_id = AMBIENT_INSTANCE_ID;
    const uint32_t param_id = AMBIENT_GET_VERSION_PARAM_ID;
    const uint32_t block_id = IAXXX_HMD_BLOCK_ID;
    unsigned int param_val = AMBIENT_DEFAULT_VER;

    err = setup_ambient_package(odsp_hdl);
    if (err != 0) {
        if (errno == EEXIST) {
            ALOGW("%s: WARN: Ambient package existed already", __func__);
            err = iaxxx_odsp_plugin_get_parameter(odsp_hdl, inst_id, param_id,
                                              block_id, &param_val);
            if (err != 0) {
                ALOGE("%s: Failed to get parameter for id %u with error %d: %s",
                      __func__, param_id, err, strerror(errno));
            } else {
                ALOGD("%s: Value of parameter id %u is %u", __func__, param_id,
                      param_val);
            }
            *ambient_version = param_val;
            err = iaxxx_odsp_plugin_get_parameter_blk(odsp_hdl, inst_id, block_id,
                                                    PARAM_BLOCK_GET_DSP_IDENTIFIER,
                                                    supported_model_arch,
                                                    PAYLOAD_MAX_STRING_LEN);
            if (err != 0) {
                ALOGE("%s: Failed to get dsp identifier with error %d: %s",
                      __func__, err, strerror(errno));
            }
        } else {
            ALOGE("%s: ERROR: setup Ambient package failed", __func__);
        }
    } else {
        err = iaxxx_odsp_plugin_get_parameter(odsp_hdl, inst_id, param_id,
                                              block_id, &param_val);
        if (err != 0) {
            ALOGE("%s: Failed to get parameter for id %u with error %d: %s",
                  __func__, param_id, err, strerror(errno));
        } else {
            ALOGD("%s: Value of parameter id %u is %u", __func__, param_id,
                  param_val);
        }
        *ambient_version = param_val;
        err = iaxxx_odsp_plugin_get_parameter_blk(odsp_hdl, inst_id, block_id,
                                                PARAM_BLOCK_GET_DSP_IDENTIFIER,
                                                supported_model_arch,
                                                PAYLOAD_MAX_STRING_LEN);
        if (err != 0) {
            ALOGE("%s: Failed to get dsp identifier with error %d: %s",
                  __func__, err, strerror(errno));
        }
        err = destroy_ambient_package(odsp_hdl);
        if (err != 0)
            ALOGE("%s: ERROR: destroy Ambient package failed", __func__);
    }

    return err;
}

int setup_aec_package(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;

    ALOGD("+%s+", __func__);

    err = iaxxx_odsp_package_load(odsp_hdl, ECHOCANCELLER_PACKAGE,
                                AEC_PKG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to load AEC passthrough package %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    // AEC PT Plugin Create
    err = iaxxx_odsp_plugin_create(odsp_hdl, AEC_INSTANCE_ID, AEC_PRIORITY,
                                AEC_PKG_ID, AEC_PLUGIN_IDX, IAXXX_HMD_BLOCK_ID,
                                PLUGIN_DEF_CONFIG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to create AEC plugin %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;
}

int destroy_aec_package(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;

    ALOGD("+%s+", __func__);

    err = iaxxx_odsp_plugin_destroy(odsp_hdl, AEC_INSTANCE_ID,
                                    IAXXX_HMD_BLOCK_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to destroy AEC plugin %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_package_unload(odsp_hdl, AEC_PKG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to unload AEC package %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;
}

int setup_chre_package(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;
    struct iaxxx_create_config_data cdata;

    ALOGD("+%s+", __func__);

    /* Create CHRE plugins */
    cdata.type = CONFIG_FILE;
    cdata.data.fdata.filename = BUFFER_CONFIG_VAL_CHRE;
    err = iaxxx_odsp_plugin_set_creation_config(odsp_hdl,
                                                CHRE_INSTANCE_ID,
                                                IAXXX_HMD_BLOCK_ID,
                                                cdata);
    if (err != 0) {
        ALOGE("%s: ERROR: CHRE Buffer configuration failed %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    // Create CHRE Buffer plugin
    err = iaxxx_odsp_plugin_create(odsp_hdl, CHRE_INSTANCE_ID, BUF_PRIORITY,
                                   BUF_PKG_ID, CHRE_PLUGIN_IDX,
                                   IAXXX_HMD_BLOCK_ID, PLUGIN_DEF_CONFIG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to create CHRE buffer %d(%s)",
           __func__, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_plugin_set_parameter(odsp_hdl, CHRE_INSTANCE_ID,
                                CHRE_EVT_PARAM_ID, CHRE_BUF_SIZE,
                                IAXXX_HMD_BLOCK_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: CHRE buffer set param failed %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_plugin_setevent(odsp_hdl, CHRE_INSTANCE_ID,
                                CHRE_EVT_MASK, IAXXX_HMD_BLOCK_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: CHRE set event failed %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    // Subscribe for events
    err = iaxxx_odsp_evt_subscribe(odsp_hdl, CHRE_EVT_SRC_ID,
                                CHRE_EVT_ID, IAXXX_SYSID_HOST_1, 0);
    if (err != 0) {
        ALOGE("%s: ERROR: ODSP_EVENT_SUBSCRIBE (for event_id %d, src_id %d)"
            " IOCTL failed %d(%s)", __func__, CHRE_EVT_ID, CHRE_EVT_SRC_ID,
            errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_evt_subscribe(odsp_hdl, CHRE_EVT_SRC_ID,
                                CHRE_CONFIGURED, IAXXX_SYSID_HOST_1, 0);
    if (err != 0) {
        ALOGE("%s: ERROR: ODSP_EVENT_SUBSCRIBE (for event_id %d, src_id %d)"
            " IOCTL failed %d(%s)", __func__, CHRE_CONFIGURED, CHRE_EVT_SRC_ID,
            errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_evt_subscribe(odsp_hdl, CHRE_EVT_SRC_ID,
                                CHRE_DESTROYED, IAXXX_SYSID_HOST_1, 0);
    if (err != 0) {
        ALOGE("%s: ERROR: ODSP_EVENT_SUBSCRIBE (for event_id %d, src_id %d)"
            " IOCTL failed %d(%s)", __func__, CHRE_DESTROYED, CHRE_EVT_SRC_ID,
            errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_evt_subscribe(odsp_hdl, IAXXX_SYSID_HOST_1,
                                   CHRE_EP_DISCONNECT, IAXXX_SYSID_HOST_0, 0);
    if (err == -1) {
        ALOGE("%s: ERROR: CHRE event subscription (CHRE EP disconnect) failed "
              " with error %d(%s)", __func__, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_evt_trigger(odsp_hdl, CHRE_EVT_SRC_ID, CHRE_CONFIGURED, 0);
    if (err != 0) {
        ALOGE("%s: ERROR: CHRE event trigger (chre configured) failed %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;
}

int destroy_chre_package(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;

    ALOGD("+%s+", __func__);

    err = iaxxx_odsp_evt_unsubscribe(odsp_hdl, CHRE_EVT_SRC_ID, CHRE_EVT_ID,
                                    IAXXX_SYSID_HOST_1);
    if (err != 0) {
        ALOGE("%s: ERROR: ODSP_EVENT_UNSUBSCRIBE (for event_id %d, src_id %d)"
            " IOCTL failed %d(%s)", __func__, CHRE_EVT_ID, CHRE_EVT_SRC_ID,
            errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_evt_unsubscribe(odsp_hdl, CHRE_EVT_SRC_ID, CHRE_CONFIGURED,
                                    IAXXX_SYSID_HOST_1);
    if (err != 0) {
        ALOGE("%s: ERROR: ODSP_EVENT_UNSUBSCRIBE (for event_id %d, src_id %d)"
            " IOCTL failed %d(%s)", __func__, CHRE_CONFIGURED, CHRE_EVT_SRC_ID,
            errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_evt_unsubscribe(odsp_hdl, CHRE_EVT_SRC_ID, CHRE_DESTROYED,
                                    IAXXX_SYSID_HOST_1);
    if (err != 0) {
        ALOGE("%s: ERROR: ODSP_EVENT_UNSUBSCRIBE (for event_id %d, src_id %d)"
            " IOCTL failed %d(%s)", __func__, CHRE_DESTROYED, CHRE_EVT_SRC_ID,
            errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_evt_unsubscribe(odsp_hdl, IAXXX_SYSID_HOST_1,
                                     CHRE_EP_DISCONNECT, IAXXX_SYSID_HOST_0);
    if (err == -1) {
        ALOGE("%s: ERROR: ODSP_EVENT_UNSUBSCRIBE (for event_id %d, src_id %d)"
              " IOCTL failed with error %d(%s)", __func__, CHRE_EP_DISCONNECT,
              IAXXX_SYSID_HOST_1, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_plugin_destroy(odsp_hdl, CHRE_INSTANCE_ID,
                                    IAXXX_HMD_BLOCK_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to destroy buffer plugin for CHRE %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;
}

int trigger_chre_destroy_event(struct iaxxx_odsp_hw *odsp_hdl) {
    int err = 0;

    ALOGD("+%s+", __func__);

    err = iaxxx_odsp_evt_trigger(odsp_hdl, CHRE_EVT_SRC_ID, CHRE_DESTROYED, 0);
    if (err == -1) {
        ALOGE("%s: ERROR: CHRE event trigger (chre destroyed) failed with "
              "error %d(%s)", __func__, errno, strerror(errno));
        goto exit;
    }

exit:
    ALOGD("-%s-", __func__);
    return err;
}

int setup_sensor_package(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;
    struct iaxxx_create_config_data cdata;

    ALOGD("+%s+", __func__);

    // Download sensor packages
    err = iaxxx_odsp_package_load(odsp_hdl, SENSOR_PACKAGE, SENSOR_PKG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to load Sensor package %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    /* Create plugins */
    cdata.type = CONFIG_FILE;
    cdata.data.fdata.filename = BUFFER_CONFIG_OSLO_VAL;
    err = iaxxx_odsp_plugin_set_creation_config(odsp_hdl,
                                                OSLO_BUF_INSTANCE_ID,
                                                IAXXX_HMD_BLOCK_ID,
                                                cdata);
    if (err != 0) {
        ALOGE("%s: ERROR: Sensor buffer configuration failed %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    // Create Buffer plugin
    err = iaxxx_odsp_plugin_create(odsp_hdl, OSLO_BUF_INSTANCE_ID,
                                   OSLO_BUF_PRIORITY, BUF_PKG_ID,
                                   BUF_PLUGIN_IDX, IAXXX_HMD_BLOCK_ID,
                                   PLUGIN_DEF_CONFIG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to create Sensor Buffer %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    cdata.type = CONFIG_FILE;
    cdata.data.fdata.filename = SENSOR_CONFIG_VAL;
    err = iaxxx_odsp_plugin_set_creation_config(odsp_hdl,
                                                SENSOR_INSTANCE_ID,
                                                IAXXX_HMD_BLOCK_ID,
                                                cdata);
    if (err == -1) {
        ALOGE("%s: ERROR: Sensor configuration %d(%s)",
            __func__, errno, strerror(errno));
        return err;
    }

    // Create placeholder sensor plugin
    err = iaxxx_odsp_plugin_create(odsp_hdl, SENSOR_INSTANCE_ID,
                                   SENSOR_PRIORITY, SENSOR_PKG_ID,
                                   SENSOR_PLUGIN_IDX, IAXXX_HMD_BLOCK_ID,
                                   PLUGIN_DEF_CONFIG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to create Sensor plugin %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    err = sensor_event_init_params(odsp_hdl);
    if (err) {
        ALOGE("%s: ERROR: Sensor event init failed %d", __func__, err);
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;
}

int destroy_sensor_package(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;

    ALOGD("+%s+", __func__);

    err = sensor_event_deinit_params(odsp_hdl);
    if (err != 0) {
        ALOGE("%s: ERROR: Sensor event uninit failed %d", __func__, err);
        goto exit;
    }

    err = iaxxx_odsp_plugin_destroy(odsp_hdl, SENSOR_INSTANCE_ID,
                                    IAXXX_HMD_BLOCK_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to destroy sensor plugin %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_plugin_destroy(odsp_hdl, OSLO_BUF_INSTANCE_ID,
                                    IAXXX_HMD_BLOCK_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to destroy sensor buffer plugin %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_package_unload(odsp_hdl, SENSOR_PKG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to unload sensor package %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;
}

int trigger_sensor_destroy_event(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;

    ALOGD("+%s+", __func__);

    err = iaxxx_odsp_evt_trigger(odsp_hdl, OSLO_EVT_SRC_ID, OSLO_DESTROYED, 0);
    if (err == -1)
        ALOGE("%s: ERROR: oslo event trigger (oslo destroyed) failed with "
              "error %d(%s)", __func__, errno, strerror(errno));

    ALOGD("-%s-", __func__);
    return err;
}

int setup_mixer_package(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;

    ALOGD("+%s+", __func__);

    // Load package for Mixer
    err = iaxxx_odsp_package_load(odsp_hdl, MIXER_PACKAGE, MIXER_PKG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to load Mixer package %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    // Create Mixer Plugin
    err = iaxxx_odsp_plugin_create(odsp_hdl, MIXER_INSTANCE_ID,
                                MIXER_PRIORITY, MIXER_PKG_ID,
                                MIXER_PLUGIN_IDX, IAXXX_HMD_BLOCK_ID,
                                PLUGIN_DEF_CONFIG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to create Mixer plugin %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;

}

int destroy_mixer_package(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;

    ALOGD("+%s+", __func__);

    // Destroy Mixer Plugin
    err = iaxxx_odsp_plugin_destroy(odsp_hdl, MIXER_INSTANCE_ID,
                                    IAXXX_HMD_BLOCK_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to destroy Mixer buffer plugin %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    // Unload package for Mixer
    err = iaxxx_odsp_package_unload(odsp_hdl, MIXER_PKG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to unload sensor package error %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;
}

int setup_src_package(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;

    ALOGD("+%s+", __func__);

    err = iaxxx_odsp_package_load(odsp_hdl, SRC_PACKAGE, SRC_PKG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to load SRC package %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;

}

int destroy_src_package(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;

    ALOGD("+%s+", __func__);

    err = iaxxx_odsp_package_unload(odsp_hdl, SRC_PKG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to unload SRC package error %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;
}

int setup_music_buffer(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;
    struct iaxxx_create_config_data cdata;

    ALOGD("+%s+", __func__);

    // Create the 8 seconds Buffer plugin for Downlink Audio
    cdata.type = CONFIG_FILE;
    cdata.data.fdata.filename = BUFFER_CONFIG_VAL_MULTI_SEC;
    err = iaxxx_odsp_plugin_set_creation_config(odsp_hdl,
                                DA_BUF_INSTANCE_ID,
                                IAXXX_HMD_BLOCK_ID,
                                cdata);
    if (err != 0) {
        ALOGE("%s: ERROR: DA buffer configuration failed %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    // Create Buffer plugin
    err = iaxxx_odsp_plugin_create(odsp_hdl,
                              DA_BUF_INSTANCE_ID,
                              BUF_PRIORITY, BUF_PKG_ID,
                              BUF_PLUGIN_IDX,
                              IAXXX_HMD_BLOCK_ID,
                              PLUGIN_DEF_CONFIG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to create DA Buffer error %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }
    ALOGD("-%s-", __func__);

exit:
    return err;
}

int destroy_music_buffer(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;

    ALOGD("+%s+", __func__);
    err = iaxxx_odsp_plugin_destroy(odsp_hdl,
                                DA_BUF_INSTANCE_ID,
                                IAXXX_HMD_BLOCK_ID);
    if (err == -1) {
        ALOGE("%s: ERROR: Failed to destroy DA buffer plugin %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;
}

int setup_howord_buffer(struct iaxxx_odsp_hw *odsp_hdl)
{
    struct iaxxx_create_config_data cdata;
    int err = 0;

    ALOGD("+%s+", __func__);

    cdata.type = CONFIG_FILE;
    cdata.data.fdata.filename = BUFFER_CONFIG_VAL_2_SEC;
    err = iaxxx_odsp_plugin_set_creation_config(odsp_hdl,
                                                BUF_INSTANCE_ID,
                                                IAXXX_HMD_BLOCK_ID,
                                                cdata);
    if (err != 0) {
        ALOGE("%s: ERROR: 8 sec buffer configuration failed %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    // Create Buffer plugin
    err = iaxxx_odsp_plugin_create(odsp_hdl, BUF_INSTANCE_ID,
                                   BUF_PRIORITY, BUF_PKG_ID, BUF_PLUGIN_IDX,
                                   IAXXX_HMD_BLOCK_ID, PLUGIN_DEF_CONFIG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to create Buffer Plugin %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;
}

int destroy_howord_buffer(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err = 0;

    ALOGD("+%s+", __func__);

    // Destroy Buffer plugin
    err = iaxxx_odsp_plugin_destroy(odsp_hdl, BUF_INSTANCE_ID,
                                    IAXXX_HMD_BLOCK_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to destroy 8 sec buffer %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;
}

int setup_src_plugin(struct iaxxx_odsp_hw *odsp_hdl, enum src_type st)
{
    int err = 0;
    int plugin_instant_id = 0;
    struct iaxxx_create_config_data cdata;

    if (st == SRC_MIC) {
        plugin_instant_id = SRC_MIC_INSTANCE_ID;
    } else if (st == SRC_AMP_REF) {
        plugin_instant_id = SRC_AMP_INSTANCE_ID;
    } else {
        ALOGE("%s: Invalid src type %d", __func__, st);
        err = -EINVAL;
        goto exit;
    }

    ALOGD("+%s+ src type %d", __func__, st);

    // set src config
    cdata.type = CONFIG_FILE;
    cdata.data.fdata.filename = SRC_CONFIG;
    err = iaxxx_odsp_plugin_set_creation_config(odsp_hdl,
                                plugin_instant_id,
                                IAXXX_HMD_BLOCK_ID,
                                cdata);
    if (err != 0) {
        ALOGE("%s: ERROR: SRC-%d configuration failed %d(%s)",
            __func__, st, errno, strerror(errno));
        goto exit;
    }

    err = iaxxx_odsp_plugin_create(odsp_hdl, plugin_instant_id,
                                SRC_PRIORITY, SRC_PKG_ID,
                                SRC_PLUGIN_IDX, IAXXX_HMD_BLOCK_ID,
                                PLUGIN_DEF_CONFIG_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to create SRC-%d plugin %d(%s)",
            __func__, st, errno, strerror(errno));
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;
}

int destroy_src_plugin(struct iaxxx_odsp_hw *odsp_hdl, enum src_type st)
{
    int err = 0;
    int plugin_instant_id = 0;

    if (st == SRC_MIC) {
        plugin_instant_id = SRC_MIC_INSTANCE_ID;
    } else if (st == SRC_AMP_REF) {
        plugin_instant_id = SRC_AMP_INSTANCE_ID;
    } else {
        ALOGE("%s: Invalid src type %d", __func__, st);
        err = -EINVAL;
        goto exit;
    }
    ALOGD("+%s+ src type %d", __func__, st);

    err = iaxxx_odsp_plugin_destroy(odsp_hdl, plugin_instant_id,
                                    IAXXX_HMD_BLOCK_ID);
    if (err != 0) {
        ALOGE("%s: ERROR: Failed to destroy SRC plugin %d(%s)",
            __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGD("-%s-", __func__);

exit:
    return err;
}

int set_hotword_buffer_route(struct audio_route *route_hdl, bool bargein)
{
    int err = 0;

    ALOGD("+%s %d+", __func__, bargein);

    if (bargein == true)
        err = audio_route_apply_and_update_path(route_hdl,
                                                route_table[ST_HOTWORD_BUFFER_WITH_BARGEIN]);
    else
        err = audio_route_apply_and_update_path(route_hdl,
                                                route_table[ST_HOTWORD_BUFFER_WITHOUT_BARGEIN]);
    if (err)
        ALOGE("%s: route fail %d", __func__, err);

    ALOGD("-%s-", __func__);
    return err;
}

int tear_hotword_buffer_route(struct audio_route *route_hdl, bool bargein)
{
    int err = 0;

    ALOGD("+%s %d+", __func__, bargein);

    if (bargein == true)
        err = audio_route_reset_and_update_path(route_hdl,
                                                route_table[ST_HOTWORD_BUFFER_WITH_BARGEIN]);
    else
        err = audio_route_reset_and_update_path(route_hdl,
                                                route_table[ST_HOTWORD_BUFFER_WITHOUT_BARGEIN]);
    if (err)
        ALOGE("%s: route fail %d", __func__, err);

    ALOGD("-%s-", __func__);
    return err;
}

int enable_bargein_route(struct audio_route *route_hdl, bool enable)
{
    int err = 0;

    ALOGV("+%s+ %d", __func__, enable);
    if (enable)
        err = audio_route_apply_and_update_path(route_hdl, route_table[ST_BARGEIN_ROUTE]);
    else
        err = audio_route_reset_and_update_path(route_hdl, route_table[ST_BARGEIN_ROUTE]);
    if (err)
        ALOGE("%s: route fail %d", __func__, err);

    ALOGD("-%s-", __func__);
    return err;
}

int enable_amp_ref_route(struct audio_route *route_hdl, bool enable,
                         enum strm_type strmt)
{
    int err = 0;

    ALOGV("+%s+ %d strm type %d", __func__, enable, strmt);
    if (strmt == STRM_16K) {
        if (enable)
            err = audio_route_apply_and_update_path(route_hdl, route_table[ST_BARGEIN_AMP_REF]);
        else
            err = audio_route_reset_and_update_path(route_hdl, route_table[ST_BARGEIN_AMP_REF]);
    } else if (strmt == STRM_48K) {
        if (enable)
            err = audio_route_apply_and_update_path(route_hdl, route_table[ST_BARGEIN_AMP_REF_48K]);
        else
            err = audio_route_reset_and_update_path(route_hdl, route_table[ST_BARGEIN_AMP_REF_48K]);
    } else {
        ALOGE("%s: ERROR: Invalid strm type", __func__);
        err = -EINVAL;
    }

    if (err)
        ALOGE("%s: route fail %d", __func__, err);

    ALOGD("-%s-", __func__);
    return err;
}

int set_music_buffer_route(struct audio_route *route_hdl, bool downlink)
{
    int err = 0;

    ALOGD("+%s+ %d", __func__, downlink);
    if (downlink)
        err = audio_route_apply_and_update_path(route_hdl,
                                                route_table[ST_AMBIENT_BUFFER_WITHOUT_BARGEIN]);
    else
        err = audio_route_apply_and_update_path(route_hdl,
                                                route_table[ST_AMBIENT_BUFFER_WITH_BARGEIN]);
    if (err)
        ALOGE("%s: route fail %d", __func__, err);

    ALOGD("-%s-", __func__);
    return err;
}

int tear_music_buffer_route(struct audio_route *route_hdl, bool downlink)
{
    int err = 0;

    ALOGD("+%s+ %d", __func__, downlink);
    if (downlink)
        err = audio_route_reset_and_update_path(route_hdl,
                                                route_table[ST_AMBIENT_BUFFER_WITHOUT_BARGEIN]);
    else
        err = audio_route_reset_and_update_path(route_hdl,
                                                route_table[ST_AMBIENT_BUFFER_WITH_BARGEIN]);
    if (err)
        ALOGE("%s: route fail %d", __func__, err);

    ALOGD("-%s-", __func__);
    return err;
}

int enable_src_route(struct audio_route *route_hdl, bool enable, enum src_type st)
{
    int err = 0;

    ALOGV("+%s+ %d src type %d", __func__, enable, st);

    if (st == SRC_MIC) {
        if (enable)
            err = audio_route_apply_and_update_path(route_hdl, route_table[ST_SRC_ROUTE_MIC]);
        else
            err = audio_route_reset_and_update_path(route_hdl, route_table[ST_SRC_ROUTE_MIC]);
    } else if (st == SRC_AMP_REF) {
        if (enable)
            err = audio_route_apply_and_update_path(route_hdl, route_table[ST_SRC_ROUTE_AMP_REF]);
        else
            err = audio_route_reset_and_update_path(route_hdl, route_table[ST_SRC_ROUTE_AMP_REF]);

    } else {
        ALOGE("%s: ERROR: Invalid src type", __func__);
        err = -EINVAL;
    }

    if (err)
        ALOGE("%s: route fail %d", __func__, err);

    ALOGD("-%s-", __func__);
    return err;
}

int enable_mic_route(struct audio_route *route_hdl, bool enable,
                    enum clock_type ct)
{
    int err = 0;

    ALOGD("+%s+ %d clock type %d", __func__, enable, ct);

    if (ct == EXTERNAL_OSCILLATOR) {
        if (enable) {
            err = audio_route_apply_and_update_path(route_hdl,
                                                    route_table[ST_MIC_ROUTE_EXT_CLK]);
        } else {
            err = audio_route_reset_and_update_path(route_hdl,
                                                    route_table[ST_MIC_ROUTE_EXT_CLK]);
        }
    } else if (ct == INTERNAL_OSCILLATOR) {
        if (enable) {
            err = audio_route_apply_and_update_path(route_hdl,
                                                    route_table[ST_MIC_ROUTE_INT_CLK]);
        } else {
            err = audio_route_reset_and_update_path(route_hdl,
                                                    route_table[ST_MIC_ROUTE_INT_CLK]);
        }
    } else {
        ALOGE("%s: ERROR: Invalid clock type", __func__);
        err = -EINVAL;
    }

    if (err)
        ALOGE("%s: route fail %d", __func__, err);

    ALOGD("-%s-", __func__);
    return err;
}

int get_entity_param_blk(struct iaxxx_odsp_hw *odsp_hdl, void *payload,
                unsigned int payload_size)
{
    int err = 0;
    err = iaxxx_odsp_plugin_get_parameter_blk(odsp_hdl,
                                            AMBIENT_INSTANCE_ID,
                                            IAXXX_HMD_BLOCK_ID,
                                            PARAM_BLOCK_GET_RECOGNITION_PAYLOAD,
                                            payload, payload_size);

    if (err < 0) {
        ALOGE("%s: Failed to get param blk error %s\n",
            __func__, strerror(errno));
    }
    return err;
}

int get_wakeup_param_blk(struct iaxxx_odsp_hw *odsp_hdl, void *payload,
                unsigned int payload_size)
{
    int err = 0;
    err = iaxxx_odsp_plugin_get_parameter_blk(odsp_hdl,
                                            HOTWORD_INSTANCE_ID,
                                            IAXXX_HMD_BLOCK_ID,
                                            PARAM_BLOCK_GET_RECOGNITION_PAYLOAD,
                                            payload, payload_size);

    if (err < 0) {
        ALOGE("%s: Failed to get param blk error %s\n",
            __func__, strerror(errno));
    }
    return err;
}

int set_default_apll_clk(struct mixer *mixer) {

    int ret = 0;
    struct mixer_ctl* ctl;

    ALOGD("+Entering %s+", __func__);

    if (!mixer) {
        ALOGE("%s mixer is NULL", __func__);
        return -EINVAL;
    }

    ctl = mixer_get_ctl_by_name(mixer, "Port ApllCLK");
    if (ctl) {
       ret = mixer_ctl_set_enum_by_string(ctl, "IAXXX_ACLK_FREQ_24576");
       if (ret)
          ALOGE("%s: update ApllCLK fail! ret = %d", __func__, ret);
    } else {
          ALOGE("%s: get Port ApllCL control fail", __func__);
          ret = -ENODEV;
    }

    ALOGD("-Exiting %s-", __func__);
    return ret;
}

int get_fw_status(struct iaxxx_odsp_hw *odsp_hdl, unsigned int *status)
{
    int err;

    ALOGD("+%s+", __func__);

    err = iaxxx_odsp_get_fw_status(odsp_hdl,
                                   status);
    if (err == -1) {
        ALOGE("%s: ERROR: Failed to get fw status with error %d(%s)",
                __func__, errno, strerror(errno));
        goto exit;
    }

    ALOGE("Firmware status is %d", *status);

exit:
    ALOGD("-%s-", __func__);
    return err;
}

int reset_fw(struct iaxxx_odsp_hw *odsp_hdl)
{
    int err;

    ALOGD("+%s+", __func__);
    err = iaxxx_odsp_reset_fw(odsp_hdl);
    if (err == -1) {
        ALOGE("%s: ERROR: Failed to reset fw with error %d(%s)",
                __func__, errno, strerror(errno));
        goto exit;
    }

exit:
    ALOGD("-%s-", __func__);
    return err;
}

int reset_all_route(struct audio_route *route_hdl)
{
    int err = 0;

    ALOGD("+%s+", __func__);
    for (int i = ST_ROUTE_MIN; i < ST_ROUTE_MAX; i++) {
        audio_route_force_reset_and_update_path(route_hdl, route_table[i]);
    }
    ALOGD("-%s-", __func__);
    return err;
}

int setup_slpi_wakeup_event(struct iaxxx_odsp_hw *odsp_hdl, bool enabled)
{
    int err;

    ALOGD("+%s+", __func__);

    if (enabled) {
        err = iaxxx_odsp_evt_subscribe(odsp_hdl, IAXXX_SYSID_CTRL_MGR_CM4,
                                    IAXXX_HOST1_WAKEUP_EVENT_ID,
                                    IAXXX_SYSID_HOST_1, 0);
        if (err != 0) {
            ALOGE("%s: ERROR: ODSP_EVENT_SUBSCRIBE (for event_id %d, src_id %d)"
                " IOCTL failed %d(%s)", __func__, IAXXX_HOST1_WAKEUP_EVENT_ID,
                IAXXX_SYSID_CTRL_MGR_CM4, errno, strerror(errno));
            goto exit;
        }
    } else {
        err = iaxxx_odsp_evt_unsubscribe(odsp_hdl, IAXXX_SYSID_CTRL_MGR_CM4,
                                        IAXXX_HOST1_WAKEUP_EVENT_ID,
                                        IAXXX_SYSID_HOST_1);
        if (err != 0) {
            ALOGE("%s: ERROR: ODSP_EVENT_UNSUBSCRIBE (for event_id %d, src_id %d)"
                " IOCTL failed %d(%s)", __func__, IAXXX_HOST1_WAKEUP_EVENT_ID,
                IAXXX_SYSID_CTRL_MGR_CM4, errno, strerror(errno));
            goto exit;
        }
    }
exit:
    ALOGD("-%s-", __func__);
    return err;
}
