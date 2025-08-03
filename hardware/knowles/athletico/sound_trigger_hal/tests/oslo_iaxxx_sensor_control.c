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

#define LOG_TAG "oslo_iaxxx_sensor_control"

#include "oslo_iaxxx_sensor_control.h"

#include <log/log.h>

#include "cvq_ioctl.h"
#include <linux/mfd/adnc/iaxxx-module.h>

#define IAXXX_DEV_NODE "/dev/iaxxx-module-celldrv"

struct ia_sensor_mgr *iaxxx_sensor_mgr_init(void) {
    struct ia_sensor_mgr *smd = NULL;

    smd = (struct ia_sensor_mgr *)malloc(sizeof(struct ia_sensor_mgr));
    if (NULL == smd) {
        ALOGE("%s: ERROR Failed to allocated memory for ia_sensor_mgr", __func__);
        return NULL;
    }

    if ((smd->dev_node = fopen(IAXXX_DEV_NODE, "rw")) == NULL) {
        ALOGE("%s: ERROR file %s open for write error: %s\n", __func__, IAXXX_DEV_NODE,
              strerror(errno));
        free(smd);
        return NULL;
    }

    return smd;
}

int iaxxx_sensor_mgr_deinit(struct ia_sensor_mgr *smd) {
    if (smd == NULL) {
        ALOGE("%s: ERROR: Invalid handle to ia_sensor_mgr", __func__);
        return -1;
    }

    if (smd->dev_node) {
        fclose(smd->dev_node);
    }

    free(smd);

    return 0;
}

void oslo_driver_set_param(struct ia_sensor_mgr *smd, int param_id, float param_val) {
    int err = 0;
    struct iaxxx_sensor_param sp;

    if (NULL == smd) {
        ALOGE("%s: NULL handle passed", __func__);
        return;
    }

    sp.inst_id = 0;
    sp.block_id = 0;

    sp.param_id = param_id;
    sp.param_val = param_val;

    ALOGD("Set sensor param 0x%X with value %f", param_id, param_val);
    fprintf(stdout, "Set sensor param 0x%X with value %f\n", param_id, param_val);

    err = ioctl(fileno(smd->dev_node), MODULE_SENSOR_SET_PARAM, (unsigned long)&sp);
    if (-1 == err) {
        ALOGE("%s: ERROR: MODULE_SENSOR_SET_PARAM IOCTL failed with error %d(%s)", __func__, errno,
              strerror(errno));
        return;
    }
}

uint32_t oslo_driver_get_param(struct ia_sensor_mgr *smd, int param_id) {
    struct iaxxx_sensor_param sp;
    int err = 0;
    ALOGD("Get param - param_id 0x%X", param_id);

    if (NULL == smd) {
        ALOGE("%s: NULL handle passed", __func__);
        return 0;
    }

    sp.inst_id = 0;
    sp.block_id = 0;

    sp.param_id = param_id;
    sp.param_val = 0;
    err = ioctl(fileno(smd->dev_node), MODULE_SENSOR_GET_PARAM, (unsigned long)&sp);
    if (-1 == err) {
        ALOGE("%s: ERROR: MODULE_SENSOR_GET_PARAM IOCTL failed with error %d(%s)", __func__, errno,
              strerror(errno));
        return 0;
    } else {
        ALOGD("Value of param 0x%X is %u", sp.param_id, sp.param_val);
        fprintf(stdout, "Value of param 0x%X is %u\n", sp.param_id, sp.param_val);
    }

    return sp.param_val;
}

size_t oslo_driver_set_param_blk(struct ia_sensor_mgr *smd, uint32_t param_blk_id,
                                 const void *blk_data, uint32_t blk_size) {
    size_t bytes_written = 0;
    struct iaxxx_sensor_param_blk spb;
    int err;

    if (NULL == smd) {
        ALOGE("%s: NULL handle passed", __func__);
        return 0;
    }

    spb.inst_id = 0;
    spb.block_id = 0;
    spb.param_blk_id = param_blk_id;

    spb.blk_data = (uintptr_t)blk_data;
    spb.blk_size = MIN(blk_size, SENSOR_MAX_PARAMBLK_SIZE);

    ALOGD("Set sensor param blk with size %d", blk_size);

    err = ioctl(fileno(smd->dev_node), MODULE_SENSOR_WRITE_PARAM_BLK, (unsigned long)&spb);
    if (-1 == err) {
        ALOGE("%s: ERROR: MODULE_SENSOR_WRITE_PARAM_BLK failed with error %d(%s)", __func__, errno,
              strerror(errno));
    } else {
        bytes_written = spb.blk_size;
    }

    return bytes_written;
}

int oslo_driver_get_stats(struct ia_sensor_mgr *smd, struct iaxxx_sensor_mode_stats stats[]) {
    int rc = 0;

    if (NULL == smd || NULL == stats) {
        ALOGE("%s: NULL argument passed", __func__);
        return -EINVAL;
    }

    rc = ioctl(fileno(smd->dev_node), IAXXX_SENSOR_MODE_STATS, (unsigned long)stats);
    if (rc != 0) {
        ALOGE("%s: ERROR: IAXXX_SENSOR_MODE_STATS failed with error %d(%s)", __func__, errno,
              strerror(errno));
    }

    return rc;
}

void oslo_plugin_set_param(int param_id, uint32_t param_val) {
    struct iaxxx_odsp_hw *ioh = NULL;
    int err = 0;

    ioh = iaxxx_odsp_init();
    if (ioh == NULL) {
        ALOGE("ERROR: Failed to init odsp HAL");
        return;
    }

    err = iaxxx_odsp_plugin_set_parameter(ioh, SENSOR_INSTANCE_ID, param_id, param_val,
                                          IAXXX_HMD_BLOCK_ID);
    if (err != 0) {
        ALOGE("Failed to set param_id %d with error %d", param_id, err);
    } else {
        ALOGD("Set param_id %d with value %" PRIu32, param_id, param_val);
    }

    if (ioh) {
        err = iaxxx_odsp_deinit(ioh);
        if (err != 0) {
            ALOGE("Failed to deinit the odsp HAL");
        }
    }
}

uint32_t oslo_plugin_get_param(int param_id) {
    struct iaxxx_odsp_hw *ioh = NULL;
    int err = 0;
    uint32_t param_val;

    ioh = iaxxx_odsp_init();
    if (ioh == NULL) {
        ALOGE("ERROR: Failed to init odsp HAL");
        return 0;
    }

    err = iaxxx_odsp_plugin_get_parameter(ioh, SENSOR_INSTANCE_ID, param_id, IAXXX_HMD_BLOCK_ID,
                                          &param_val);
    if (err != 0) {
        ALOGE("Failed to get param_id %u with error %d", param_id, err);
    } else {
        ALOGD("Value of param 0x%X is %u", param_id, param_val);
        fprintf(stdout, "Value of param 0x%X is %u\n", param_id, param_val);
    }

    if (ioh) {
        err = iaxxx_odsp_deinit(ioh);
        if (err != 0) {
            ALOGE("Failed to deinit the odsp HAL");
        }
    }

    return param_val;
}
