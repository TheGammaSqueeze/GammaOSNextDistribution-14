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

#define LOG_TAG "iaxxx_odsp_hw"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <log/log.h>
#include <sys/ioctl.h>
#include <inttypes.h>

#include "iaxxx_odsp_hw.h"

#define DEV_NODE "/dev/iaxxx-odsp-celldrv"
#define FUNCTION_ENTRY_LOG ALOGV("Entering %s", __func__);
#define FUNCTION_EXIT_LOG ALOGV("Exiting %s", __func__);

#define IAXXX_DEBUG_BLOCK_0_EXEC_STATUS_IDENTIFIER_MASK 0x0000ffff
#define IAXXX_DEBUG_BLOCK_0_EXEC_STATUS_IDENTIFIER_POS 0
#define IAXXX_DEBUG_BLOCK_0_EXEC_STATUS_TYPE_MASK 0x00070000
#define IAXXX_DEBUG_BLOCK_0_EXEC_STATUS_TYPE_POS 16

struct iaxxx_odsp_hw {
    FILE *dev_node;
};

/**
 * Initialize the ODSP HAL
 *
 * Input  - NA
 * Output - Handle to iaxxx_odsp_hw on success, NULL on failure
 */
struct iaxxx_odsp_hw* iaxxx_odsp_init()
{
    struct iaxxx_odsp_hw *ioh = NULL;

    FUNCTION_ENTRY_LOG;

    ioh = (struct iaxxx_odsp_hw *)malloc(sizeof(struct iaxxx_odsp_hw));
    if (ioh == NULL) {
        ALOGE("%s: ERROR: Failed to allocate memory for iaxxx_odsp_hw",
                __func__);
        goto func_exit;
    }

    ioh->dev_node = fopen(DEV_NODE, "rw");
    if (ioh->dev_node == NULL) {
        ALOGE("%s: ERROR: Failed to open %s", __func__, DEV_NODE);
        free(ioh);
        ioh = NULL;
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return ioh;
}

/**
 * De-Initialize the ODSP HAL
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_deinit(struct iaxxx_odsp_hw *odsp_hw_hdl)
{

    int err = 0;

    FUNCTION_ENTRY_LOG;
    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    if (odsp_hw_hdl->dev_node) {
        fclose(odsp_hw_hdl->dev_node);
    }

    free(odsp_hw_hdl);
func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Load a package
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          pkg_name - Relative path to the Package binary (Should be placed in
 *                     the firmware location)
 *          pkg_id - Package ID
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_package_load(struct iaxxx_odsp_hw *odsp_hw_hdl,
                            const char *pkg_name, const uint32_t pkg_id)
{
    int err = 0;
    struct iaxxx_pkg_mgmt_info pkg_info;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    if (pkg_name == NULL) {
        ALOGE("%s: ERROR: Package name cannot be null", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: Package name %s, package id %u",
        __func__, pkg_name, pkg_id);

    strlcpy(pkg_info.pkg_name, pkg_name, NAME_MAX_SIZE);
    pkg_info.pkg_id = pkg_id;
    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_LOAD_PACKAGE, (unsigned long)&pkg_info);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Unload a package
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          pkg_name - Relative path to the Package binary (Should be placed in
 *                     the firmware location)
 *          pkg_id - Package ID
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_package_unload(struct iaxxx_odsp_hw *odsp_hw_hdl,
                            const uint32_t pkg_id)
{
    int err = 0;
    struct iaxxx_pkg_mgmt_info pkg_info;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: package id %u", __func__, pkg_id);

    pkg_info.pkg_id = pkg_id;
    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_UNLOAD_PACKAGE, (unsigned long)&pkg_info);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Get package version
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          inst_id - Instance ID
 *          version - Package version string buffer
 *          len - String buffer size
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_get_package_version(struct iaxxx_odsp_hw *odsp_hw_hdl,
                                        uint8_t inst_id, char *version,
                                        uint32_t len)
{
    int err = 0;
    struct iaxxx_plugin_get_package_version v;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    if (len > sizeof(v.version)) {
        ALOGW("%s: WARNING: Too large len %u, limit it to %zu", __func__, len,
            sizeof(v.version));
        len = sizeof(v.version);
    }

    ALOGV("%s: inst_id %u", __func__, inst_id);

    v.inst_id = inst_id;
    v.len = len;
    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_PLG_GET_PACKAGE_VERSION, (unsigned long)&v);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    } else {
        memcpy(version, v.version, len);
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Get plugin version
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          inst_id - Instance ID
 *          version - Plugin version string buffer
 *          len - String buffer size
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_get_plugin_version(struct iaxxx_odsp_hw *odsp_hw_hdl,
                                        uint8_t inst_id, char *version,
                                        uint32_t len)
{
    int err = 0;
    struct iaxxx_plugin_get_plugin_version v;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    if (len > sizeof(v.version)) {
        ALOGW("%s: WARNING: Too large len %u, limit it to %zu", __func__, len,
            sizeof(v.version));
        len = sizeof(v.version);
    }

    ALOGV("%s: inst_id %u", __func__, inst_id);

    v.inst_id = inst_id;
    v.len = len;
    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_PLG_GET_PLUGIN_VERSION, (unsigned long)&v);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    } else {
        memcpy(version, v.version, len);
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Create a plugin
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          inst_id - Instance ID
 *          priority - Priority of the plugin
 *          pkg_id - Package ID
 *          plg_idx - Plugin Index*
 *          block_id - Block ID
 *          config_id - Config ID
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_create(struct iaxxx_odsp_hw *odsp_hw_hdl,
                            const uint32_t inst_id,
                            const uint32_t priority,
                            const uint32_t pkg_id,
                            const uint32_t plg_idx,
                            const uint32_t block_id,
                            const uint32_t config_id)
{
    int err = 0;
    struct iaxxx_plugin_info pi;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: Plugin index %u, package id %u, block id %u, instance id %u"
        " priority %u", __func__, plg_idx, pkg_id, block_id, inst_id, priority);

    pi.plg_idx = plg_idx;
    pi.pkg_id = pkg_id;
    pi.block_id = block_id;
    pi.inst_id = inst_id;
    pi.priority = priority;
    pi.config_id = config_id;
    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_PLG_CREATE, (unsigned long)&pi);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Set the creation configuration on a plugin
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          inst_id - Instance ID
 *          block_id - Block ID
 *          cdata - Creation configuration data
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_set_creation_config(struct iaxxx_odsp_hw *odsp_hw_hdl,
                                        const uint32_t inst_id,
                                        const uint32_t block_id,
                                        struct iaxxx_create_config_data cdata)
{
    int err = 0;
    struct iaxxx_plugin_create_cfg pcc;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    pcc.inst_id = inst_id;
    pcc.block_id = block_id;
    pcc.cfg_size = 0;
    switch (cdata.type) {
    case CONFIG_FILE:
        strlcpy(pcc.file_name, cdata.data.fdata.filename, NAME_MAX_SIZE);
        ALOGV("%s: Configuration file name %s", __func__, pcc.file_name);
        break;
    case CONFIG_VALUE:
        pcc.cfg_size = cdata.data.vdata.config_val_sz;
        pcc.cfg_val = cdata.data.vdata.config_val;
        ALOGV("%s: Configuration value %"PRId64, __func__, pcc.cfg_val);
        break;
    default:
        ALOGE("%s: ERROR: Invalid type of configuration type", __func__);
        err = -1;
        goto func_exit;
        break;
    }

    ALOGV("%s: Instance id %u, block id %u", __func__, inst_id, block_id);

    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_PLG_SET_CREATE_CFG, (unsigned long)&pcc);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Destroy the plugin
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          inst_id - Instance ID
 *          block_id - Block ID
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_destroy(struct iaxxx_odsp_hw *odsp_hw_hdl,
                            const uint32_t inst_id,
                            const uint32_t block_id)
{
    int err = 0;
    struct iaxxx_plugin_info pi;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: instance id %u, block id %u", __func__, inst_id, block_id);

    pi.block_id = block_id;
    pi.inst_id = inst_id;
    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_PLG_DESTROY, (unsigned long) &pi);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Enable the plugin
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          inst_id - Instance ID
 *          block_id - Block ID
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_enable(struct iaxxx_odsp_hw *odsp_hw_hdl,
                            const uint32_t inst_id,
                            const uint32_t block_id)
{
    int err = 0;
    struct iaxxx_plugin_info pi;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: instance id %u, block id %u", __func__, inst_id, block_id);

    pi.block_id = block_id;
    pi.inst_id = inst_id;
    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_PLG_ENABLE, (unsigned long)&pi);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Disable the plugin
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          inst_id - Instance ID
 *          block_id - Block ID
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_disable(struct iaxxx_odsp_hw *odsp_hw_hdl,
                            const uint32_t inst_id,
                            const uint32_t block_id)
{
    int err = 0;
    struct iaxxx_plugin_info pi;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: instance id %u, block id %u", __func__, inst_id, block_id);

    pi.block_id = block_id;
    pi.inst_id = inst_id;
    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_PLG_DISABLE, (unsigned long)&pi);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Reset the plugin
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          inst_id - Instance ID
 *          block_id - Block ID
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_reset(struct iaxxx_odsp_hw *odsp_hw_hdl,
                            const uint32_t inst_id,
                            const uint32_t block_id)
{
    int err = 0;
    struct iaxxx_plugin_info pi;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: instance id %u, block id %u", __func__, inst_id, block_id);

    pi.block_id = block_id;
    pi.inst_id = inst_id;
    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_PLG_RESET, (unsigned long)&pi);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Set a parameter on a plugin
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          inst_id - Instance ID
 *          param_id - Parameter ID
 *          param_val - Parameter Value*
 *          block_id  - Block ID
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_set_parameter(struct iaxxx_odsp_hw *odsp_hw_hdl,
                                    const uint32_t inst_id,
                                    const uint32_t param_id,
                                    const uint32_t param_val,
                                    const uint32_t block_id)
{
    int err = 0;
    struct iaxxx_plugin_param pp;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: Instance id %u, block id %u param_id %u param_val %u",
        __func__, inst_id, block_id, param_id, param_val);

    pp.inst_id = inst_id;
    pp.block_id = block_id;
    pp.param_id = param_id;
    pp.param_val = param_val;
    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_PLG_SET_PARAM, (unsigned long)&pp);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Get the value of parameter on a plugin
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          inst_id - Instance ID
 *          param_id - Parameter ID
 *          block_id - Block ID*
 *          param_val - Parameter Value
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_get_parameter(struct iaxxx_odsp_hw *odsp_hw_hdl,
                                    const uint32_t inst_id,
                                    const uint32_t param_id,
                                    const uint32_t block_id,
                                    uint32_t *param_val)
{
    int err = 0;
    struct iaxxx_plugin_param pp;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    pp.inst_id = inst_id;
    pp.block_id = block_id;
    pp.param_id = param_id;
    pp.param_val = 0;
    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_PLG_GET_PARAM, (unsigned long)&pp);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
        goto func_exit;
    }

    *param_val = pp.param_val;

    ALOGV("%s: Instance id %u, block id %u param_id %u param_val %u",
        __func__, inst_id, block_id, param_id, *param_val);

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Set a parameter block on a plugin
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          inst_id - Instance ID
 *          param_blk_id - Parameter block id
 *          block_id - Block ID
 *          param_buf - Pointer to the parameter block
 *          param_buf_sz - Parameter block size
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_set_parameter_blk(struct iaxxx_odsp_hw *odsp_hw_hdl,
                                        const uint32_t inst_id,
                                        const uint32_t param_blk_id,
                                        const uint32_t block_id,
                                        const void *param_buf,
                                        const uint32_t param_buf_sz)
{
    int err = 0;
    struct iaxxx_plugin_param_blk ppb;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: Instance id %u, block id %u, param_buf_sz %u, parm_blk_id %u",
        __func__, inst_id, block_id, param_buf_sz, param_blk_id);

    ppb.inst_id = inst_id;
    ppb.block_id = block_id;
    ppb.param_size = param_buf_sz;
    ppb.param_blk = (uintptr_t)param_buf;
    ppb.id = param_blk_id;
    ppb.file_name[0] = '\0';
    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_PLG_SET_PARAM_BLK, (unsigned long)&ppb);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Set a parameter block on a plugin
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          inst_id - Instance ID
 *          param_blk_id - Parameter block id
 *          block_id - Block ID
 *          file_name - Relative path to the Parameter File(Should be placed in
 *                     the firmware location)
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_set_parameter_blk_from_file(
                                        struct iaxxx_odsp_hw *odsp_hw_hdl,
                                        const uint32_t inst_id,
                                        const uint32_t param_blk_id,
                                        const uint32_t block_id,
                                        const char *file_name)
{
    int err = 0;
    struct iaxxx_plugin_param_blk ppb;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: Instance id %u, block id %u, file_name %s, parm_blk_id %u",
        __func__, inst_id, block_id, file_name, param_blk_id);

    ppb.param_size = 0;
    ppb.param_blk = (uintptr_t) NULL;
    ppb.inst_id = inst_id;
    ppb.block_id = block_id;
    ppb.id = param_blk_id;
    strlcpy(ppb.file_name, file_name, NAME_MAX_SIZE);
    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_PLG_SET_PARAM_BLK, (unsigned long)&ppb);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}



/**
 * Set custom configuration for plugin
 *
 * Input  - odsp_hw_hdl         - Handle to odsp hw structure
 *          inst_id             - Instance ID
 *          block_id            - Block ID
 *          param_blk_id        - Parameter block id
 *          custom_config_id    - Id for what type of custom configuration
 *          filename            - Name of file with custom config data
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_set_custom_cfg(struct iaxxx_odsp_hw *odsp_hw_hdl,
                                    const uint32_t inst_id,
                                    const uint32_t block_id,
                                    const uint32_t param_blk_id,
                                    const uint32_t custom_config_id,
                                    const char *filename)
{
    int err = 0;
    struct iaxxx_plugin_custom_cfg pcc;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: Instance id %u, block id %u, param blk id %u"
        " custom-config id %u filename %s",
        __func__, inst_id, block_id, param_blk_id,custom_config_id, filename);

    strlcpy(pcc.file_name, filename, NAME_MAX_SIZE);
    pcc.inst_id = inst_id;
    pcc.block_id = block_id;
    pcc.param_blk_id = param_blk_id;
    pcc.custom_config_id = custom_config_id;

    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_PLG_SET_CUSTOM_CFG, (unsigned long)&pcc);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Subscribe to an event
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          src_id      - System Id of event source
 *          event_id    - Event Id
 *          dst_id  - System Id of event destination
 *          dst_opaque  - Info sought by destination task when even occurs
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_evt_subscribe(struct iaxxx_odsp_hw *odsp_hw_hdl,
                            const uint16_t src_id,
                            const uint16_t event_id,
                            const uint16_t dst_id,
                            const uint32_t dst_opaque)
{
    int err = 0;
    struct iaxxx_evt_info ei;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: src id %u, event id %u, dst_id %u dst_opq %u",
        __func__, src_id, event_id, dst_id, dst_opaque);

    ei.src_id = src_id;
    ei.event_id = event_id;
    ei.dst_id = dst_id;
    ei.dst_opaque = dst_opaque;

    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_EVENT_SUBSCRIBE, (unsigned long)&ei);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Unsubscribe an event
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          src_id      - System Id of event source
 *          event_id    - Event Id
 *          dst_id  - System Id of event destination
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_evt_unsubscribe(struct iaxxx_odsp_hw *odsp_hw_hdl,
                            const uint16_t src_id,
                            const uint16_t event_id,
                            const uint16_t dst_id)
{
    int err = 0;
    struct iaxxx_evt_info ei;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: src id %u, event id %u, dst_id %u",
        __func__, src_id, event_id, dst_id);

    ei.src_id = src_id;
    ei.event_id = event_id;
    ei.dst_id = dst_id;

    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_EVENT_UNSUBSCRIBE, (unsigned long)&ei);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Retrieve an event
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          event_info  - Struct to return event info
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_evt_getevent(struct iaxxx_odsp_hw *odsp_hw_hdl,
                            struct iaxxx_get_event_info *event_info)
{
    int err = 0;
    struct iaxxx_get_event ei;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_GET_EVENT, (unsigned long) &ei);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

    ALOGV("%s: event id %u, data %u",
            __func__, ei.event_id, ei.data);
    event_info->event_id = ei.event_id;
    event_info->data = ei.data;

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}


/**
 * Create a plugin for a statically loaded package
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          inst_id - Instance ID
 *          priority - Priority of the plugin
 *          pkg_id - Package ID
 *          plg_idx - Plugin Index*
 *          block_id - Block ID
 *          config_id - Config ID
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_create_static_package(struct iaxxx_odsp_hw *odsp_hw_hdl,
                                        const uint32_t inst_id,
                                        const uint32_t priority,
                                        const uint32_t pkg_id,
                                        const uint32_t plg_idx,
                                        const uint32_t block_id,
                                        const uint32_t config_id)
{
    int err = 0;
    struct iaxxx_plugin_info pi;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: Plugin index %u, package id %u, block id %u, instance id %u"
         " priority %u", __func__, plg_idx, pkg_id, block_id, inst_id, priority);

    pi.plg_idx = plg_idx;
    pi.pkg_id = pkg_id;
    pi.block_id = block_id;
    pi.inst_id = inst_id;
    pi.priority = priority;
    pi.config_id = config_id;
    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_PLG_CREATE_STATIC_PACKAGE, (unsigned long)&pi);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Get a parameter block from a plugin
 *
 * Input  - odsp_hw_hdl     - Handle to odsp hw structure
 *          inst_id         - Instance ID
 *          block_id        - Block ID
 *          param_blk_id    - Parameter block id
 *          param_buf       - Pointer to the parameter block
 *          param_buf_sz    - Parameter block size in words
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_get_parameter_blk(struct iaxxx_odsp_hw *odsp_hw_hdl,
                                        const uint32_t inst_id,
                                        const uint32_t block_id,
                                        const uint32_t param_blk_id,
                                        uint32_t *param_buf,
                                        const uint32_t param_buf_sz)
{
    int err = 0;
    struct iaxxx_plugin_param_blk ppb;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: Instance id %u, block id %u, param_buf_sz %u, id %u",
        __func__, inst_id, block_id, param_buf_sz, param_blk_id);

    ppb.inst_id = inst_id;
    ppb.block_id = block_id;
    ppb.id = param_blk_id;
    ppb.param_size = param_buf_sz;
    ppb.param_blk = (uintptr_t)param_buf;

    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_PLG_GET_PARAM_BLK, (unsigned long)&ppb);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Set Event for the plugin
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          inst_id - Instance ID
 *          eventEnableMask - event Mask
 *          block_id - Block ID
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_setevent(struct iaxxx_odsp_hw *odsp_hw_hdl,
                              const uint32_t inst_id,
                              const uint32_t eventEnableMask,
                              const uint32_t block_id)
{
    int err = 0;
    struct iaxxx_set_event se;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: instance id %u, eventEnableMask %x, block id %u",
        __func__, inst_id, eventEnableMask, block_id);

    se.block_id = block_id;
    se.event_enable_mask = eventEnableMask;
    se.inst_id = inst_id;
    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_PLG_SET_EVENT, (unsigned long)&se);
    if (err == -1) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
        return err;
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Trigger an event. This may be most useful when debugging the system,
 * but can also be used to trigger simultaneous behavior in entities which
 * have subscribed, or to simply provide notifications regarding host status:
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          src_id      - SystemId of event source
 *          evt_id      - Id of event
 *          src_opaque  - Source opaque to pass with event notification
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_evt_trigger(struct iaxxx_odsp_hw *odsp_hw_hdl,
                        uint16_t src_id,
                        uint16_t evt_id,
                        uint32_t src_opaque)
{
    int err = 0;
    struct iaxxx_evt_trigger et;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: src_id=%u, evt_id=%u, src_opaque=%u", __func__, src_id, evt_id,
        src_opaque);

    et.src_id = src_id;
    et.evt_id = evt_id;
    et.src_opaque = src_opaque;
    err = ioctl(fileno(odsp_hw_hdl->dev_node), ODSP_EVENT_TRIGGER,
            (unsigned long)&et);
    if (err == -1) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Fetches next event subscription entry from the last read position
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          src_id      - System Id of event source
 *          evt_id      - Event Id
 *          dst_id      - System Id of event destination
 *          dst_opaque  - Destination opaque data
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_evt_read_subscription(struct iaxxx_odsp_hw *odsp_hw_hdl,
                                    uint16_t *src_id,
                                    uint16_t *evt_id,
                                    uint16_t *dst_id,
                                    uint32_t *dst_opaque)
{
    int err = 0;
    struct iaxxx_evt_read_subscription ers;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    err = ioctl(fileno(odsp_hw_hdl->dev_node), ODSP_EVENT_READ_SUBSCRIPTION,
                (unsigned long) &ers);
    if (err == -1) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    } else {
        ALOGV("%s: src_id=%u, evt_id=%u, dst_id=%u, dst_opaque=%u", __func__,
            ers.src_id, ers.evt_id, ers.dst_id, ers.dst_opaque);

        *src_id = ers.src_id;
        *evt_id = ers.evt_id;
        *dst_id = ers.dst_id;
        *dst_opaque = ers.dst_opaque;
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Reset index for retrieving subscription entries
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_evt_reset_read_index(struct iaxxx_odsp_hw *odsp_hw_hdl)
{
    int err = 0;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    err = ioctl(fileno(odsp_hw_hdl->dev_node), ODSP_EVENT_RESET_READ_INDEX,
                NULL);
    if (err == -1) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Retrieve an event notification
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          src_id      - pointer to uint16_t for reporting SystemId of
 *                        event source
 *          evt_dd      - pointer to uint16_t for reporting Id of event
 *          src_opaque  - pointer to the first parameter of event
 *          dst_opaque  - pointer to the second parameter of event
 *                        This will be destOpaque in case if event is
 *                        subscribed with valid destOpaque otherwise
 *                        it will be used as second parameter.
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_evt_retrieve_notification(struct iaxxx_odsp_hw *odsp_hw_hdl,
                                        uint16_t *src_id,
                                        uint16_t *evt_id,
                                        uint32_t *src_opaque,
                                        uint32_t *dst_opaque)
{
    int err = 0;
    struct iaxxx_evt_retrieve_notification ern;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_EVENT_RETRIEVE_NOTIFICATION, (unsigned long)&ern);
    if (err == -1) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    } else {
        ALOGV("%s: src_id=%u, evt_id=%u, src_opaque=%u, dst_opaque=%u",
            __func__, ern.src_id, ern.evt_id, ern.src_opaque, ern.dst_opaque);

        *src_id = ern.src_id;
        *evt_id = ern.evt_id;
        *src_opaque = ern.src_opaque;
        *dst_opaque = ern.dst_opaque;
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/* Read Plugin Error Info
 *
 * Input  - odsp_hw_hdl     - Handle to odsp hw structure
 *          error_code      - Pointer to uint32 to return error code
 *          error_instance  - Pointer to uint8 to return plugin instance
 *                            where error occurred.
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_read_error(struct iaxxx_odsp_hw *odsp_hw_hdl,
                                const uint32_t block_id,
                                uint32_t *error_code,
                                uint8_t *error_instance)
{
    int err = 0;
    struct iaxxx_plugin_error_info pei;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: Block id %u", __func__, block_id);

    pei.block_id = block_id;

    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_PLG_READ_PLUGIN_ERROR, (unsigned long)&pei);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    } else {
        *error_code = pei.error_code;
        *error_instance = pei.error_instance;
        ALOGV("%s: Plugin error code:%x instance=%d", __func__,
            pei.error_code, pei.error_instance);
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/* Read the timestamps of all output endpoint
 *
 * Input  - odsp_hw_hdl     - Handle to odsp hw structure
 *          proc_id         - Proc id
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_get_ep_timestamps(struct iaxxx_odsp_hw *odsp_hw_hdl,
                                    uint64_t *timestamps,
                                    uint8_t proc_id)
{
    int err = 0;
    struct iaxxx_plugin_endpoint_timestamps pet = {
        .proc_id = proc_id
    };

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: Proc id %u", __func__, proc_id);

    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_PLG_GET_ENDPOINT_TIMESTAMPS, (unsigned long)&pet);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    } else {
        memcpy(timestamps, pet.timestamps, sizeof(pet.timestamps));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Set a parameter block on a plugin and get ack to
 * ensure the data has been sent and retry if not.
 *
 * Input  - odsp_hw_hdl       - Handle to odsp hw structure
 *          inst_id           - Instance ID
 *          param_blk_id      - Parameter block id
 *          block_id          - Block ID
 *          set_param_buf     - Pointer to the parameter block
 *          set_param_buf_sz  - Parameter block size in bytes
 *          response_data_buf - Buffer for response data from plugin
 *          response_data_sz  - Size of Buffer in uint32 words for
 *                              response data from plugin
 *          max_no_retries    - Max number of retries in case of busy
 *                              response from plugin.
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_set_parameter_blk_with_ack(
                                        struct iaxxx_odsp_hw *odsp_hw_hdl,
                                        const uint32_t inst_id,
                                        const uint32_t param_blk_id,
                                        const uint32_t block_id,
                                        const void *set_param_buf,
                                        const uint32_t set_param_buf_sz,
                                        uint32_t* response_data_buf,
                                        const uint32_t response_data_sz,
                                        const uint32_t max_no_retries)
{

    int err = 0;
    struct iaxxx_plugin_set_param_blk_with_ack_info pspbwa;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: Instance id %u, block id %u, param blk id %u max-retries:%u",
        __func__, inst_id, block_id, param_blk_id, max_no_retries);

    pspbwa.inst_id = inst_id;
    pspbwa.block_id = block_id;
    pspbwa.param_blk_id = param_blk_id;
    pspbwa.set_param_blk_buffer = (uintptr_t)set_param_buf;
    pspbwa.set_param_blk_size = set_param_buf_sz;
    pspbwa.response_buffer = (uintptr_t)response_data_buf;
    pspbwa.response_buf_size = response_data_sz;
    pspbwa.max_retries = max_no_retries;

    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_PLG_SET_PARAM_BLK_WITH_ACK, (unsigned long)&pspbwa);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }


func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Get Plugin status information.
 *
 * Input  - odsp_hw_hdl         - Handle to odsp hw structure
 *          inst_id             - Instance ID
 *          plugin_status_data  - Pointer to struct to return plugin status.
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_get_status_info(struct iaxxx_odsp_hw *odsp_hw_hdl,
                            const uint32_t inst_id,
                            struct iaxxx_plugin_status_data *plugin_status_data)
{
    int err = 0;
    struct iaxxx_plugin_status_info psi;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: Instance id %u", __func__, inst_id);

    psi.inst_id = inst_id;

    err = ioctl(fileno(odsp_hw_hdl->dev_node),
            ODSP_PLG_GET_STATUS_INFO, (unsigned long) &psi);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
        goto func_exit;
    }

    plugin_status_data->block_id = psi.block_id;
    plugin_status_data->create_status = psi.create_status;
    plugin_status_data->enable_status = psi.enable_status;
    plugin_status_data->process_count = psi.process_count;
    plugin_status_data->process_err_count = psi.process_err_count;
    plugin_status_data->in_frames_consumed = psi.in_frames_consumed;
    plugin_status_data->out_frames_produced = psi.out_frames_produced;
    plugin_status_data->private_memsize = psi.private_memsize;
    plugin_status_data->frame_notification_mode = psi.frame_notification_mode;
    plugin_status_data->state_management_mode = psi.state_management_mode;

    ALOGV("%s: block_id:%u create_status:%d enable_status:%d",
        __func__, psi.block_id, psi.create_status, psi.enable_status);
    ALOGV("%s: in_frames_consumed:%u out_frames_produced:%u",
        __func__, psi.in_frames_consumed, psi.out_frames_produced);
    ALOGV("%s: process_count:%u process_err_count=%u private_memsize=%u",
        __func__, psi.process_count, psi.process_err_count,
        psi.private_memsize);
    ALOGV("%s: frame_notification_mode:%u state_management_mode=%u",
        __func__, psi.frame_notification_mode, psi.state_management_mode);

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Get Plugin endpoint status information.
 *
 * Input  - odsp_hw_hdl            - Handle to odsp hw structure
 *          inst_id                - Instance ID
 *          ep_index               - Index of Endpoint
 *          direction              - If Input endpoint or Output endpoint
 *                                   0 => input, 1 => output
 *          plugin_ep_status_data  - Pointer to struct to return plugin
 *                                   endpoint status.
 *
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_get_endpoint_status(struct iaxxx_odsp_hw *odsp_hw_hdl,
            const uint32_t inst_id,
            const uint8_t ep_index,
            const uint8_t direction,
            struct iaxxx_plugin_endpoint_status_data *plugin_ep_status_data)
{
    int err = 0;
    struct iaxxx_plugin_endpoint_status_info plugin_ep_status_info;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: Instance id %u", __func__, inst_id);

    plugin_ep_status_info.inst_id = inst_id;
    plugin_ep_status_info.ep_index = ep_index;
    plugin_ep_status_info.direction = direction;

    err = ioctl(fileno(odsp_hw_hdl->dev_node),
            ODSP_PLG_GET_ENDPOINT_STATUS,
            (unsigned long) &plugin_ep_status_info);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
        goto func_exit;
    }

    plugin_ep_status_data->status
                            = plugin_ep_status_info.status;
    plugin_ep_status_data->frame_status
                            = plugin_ep_status_info.frame_status;
    plugin_ep_status_data->endpoint_status
                            = plugin_ep_status_info.endpoint_status;
    plugin_ep_status_data->usage
                            = plugin_ep_status_info.usage;
    plugin_ep_status_data->mandatory
                            = plugin_ep_status_info.mandatory;
    plugin_ep_status_data->counter
                            = plugin_ep_status_info.counter;

    if(direction) {
        plugin_ep_status_data->op_encoding
                                = plugin_ep_status_info.op_encoding;
        plugin_ep_status_data->op_sample_rate
                                = plugin_ep_status_info.op_sample_rate;
        plugin_ep_status_data->op_frame_length
                                = plugin_ep_status_info.op_frame_length;
    }

    ALOGV("%s: status:%u frame_status:%d endpoint_status:%d",
        __func__, plugin_ep_status_data->status,
        plugin_ep_status_info.frame_status,
        plugin_ep_status_info.endpoint_status);

    ALOGV("%s: usage:%d mandatory:%d counter:%u",
        __func__, plugin_ep_status_data->usage,
        plugin_ep_status_info.mandatory,
        plugin_ep_status_info.counter);

    if(direction)
        ALOGV("%s: op_encoding:%d op_sample_rate:%d op_frame_length:%u",
            __func__, plugin_ep_status_data->op_encoding,
            plugin_ep_status_info.op_sample_rate,
            plugin_ep_status_info.op_frame_length);

func_exit:
    FUNCTION_EXIT_LOG;
    return err;

}

/**
 * Returns the execution status of given processor
 *
 * Input  - odsp_hw_hdl     - Handle to odsp hw structure
 *          proc_id         - Proc id
 *          status          - Execution status of the processor
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_get_proc_execution_status(struct iaxxx_odsp_hw *odsp_hw_hdl,
                                        uint8_t proc_id,
                                        uint32_t *status)
{
    int err = 0;
    struct iaxxx_proc_execution_status s = {
        .proc_id = proc_id
    };

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    ALOGV("%s: Proc id %u", __func__, proc_id);

    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_GET_PROC_EXECUTION_STATUS, (unsigned long)&s);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    } else {
        uint32_t id, type;

        *status = s.status;

        id = ((*status & IAXXX_DEBUG_BLOCK_0_EXEC_STATUS_IDENTIFIER_MASK)
            >> IAXXX_DEBUG_BLOCK_0_EXEC_STATUS_IDENTIFIER_POS);
        type = ((*status & IAXXX_DEBUG_BLOCK_0_EXEC_STATUS_TYPE_MASK)
            >> IAXXX_DEBUG_BLOCK_0_EXEC_STATUS_TYPE_POS);
        switch (type) {
        case 0:
            ALOGI("%s() Proc %d is running Firmware\n",
                __func__, s.proc_id);
            break;
        case 1:
            ALOGI("%s() Proc %d is running ISR %d\n",
                __func__, s.proc_id, id);
            break;
        case 2:
            ALOGI("%s() Proc %d is running Package ID %d\n",
                __func__, s.proc_id, id);
            break;
        case 3:
            ALOGI("%s() Proc %d is running Plugin inst ID %d\n",
                __func__, s.proc_id, id);
            break;
        default:
            break;
        }
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Returns Rom version number, Rom version string
 *         Application version number, Application version string
 *
 * Input  - odsp_hw_hdl     - Handle to odsp hw structure
 *          rom_ver_num     - Rom version number
 *          rom_ver_str     - Rom version string
 *          rom_ver_str_len - Rom version string length
 *          app_ver_num     - App version number
 *          app_ver_str     - App version string
 *          app_ver_str_len - App version string length
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_get_sys_versions(struct iaxxx_odsp_hw *odsp_hw_hdl,
                                uint32_t *rom_ver_num,
                                char *rom_ver_str,
                                uint32_t rom_ver_str_len,
                                uint32_t *app_ver_num,
                                char *app_ver_str,
                                uint32_t app_ver_str_len)
{
    int err = 0;
    struct iaxxx_sys_versions v = {
        .app_ver_str_len =
                    (app_ver_str ? app_ver_str_len : IAXXX_MAX_VER_STR_SIZE),
        .rom_ver_str_len =
                    (rom_ver_str ? rom_ver_str_len : IAXXX_MAX_VER_STR_SIZE)
    };

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                ODSP_GET_SYS_VERSIONS, (unsigned long)&v);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    } else {
        if (rom_ver_num)
            *rom_ver_num = v.rom_ver_num;
        if (rom_ver_str)
            memcpy(rom_ver_str, v.rom_ver_str, v.rom_ver_str_len);
        if (app_ver_num)
            *app_ver_num = v.app_ver_num;
        if (app_ver_str)
            memcpy(app_ver_str, v.app_ver_str, v.app_ver_str_len);
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Returns Device ID
 *
 * Input  - odsp_hw_hdl     - Handle to odsp hw structure
 *          device_id       - Returned Device ID
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_get_device_id(struct iaxxx_odsp_hw *odsp_hw_hdl,
                            uint32_t *device_id)
{
    int err = 0;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                    ODSP_GET_SYS_DEVICE_ID, (unsigned long)device_id);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Returns System mode
 *
 * Input  - odsp_hw_hdl     - Handle to odsp hw structure
 *          mode            - Returned system mode
 *              0: System is in reset mode.
 *              1: System is currently in bootloader mode.
 *              2: System has entered application mode.
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_get_sys_mode(struct iaxxx_odsp_hw *odsp_hw_hdl,
                            uint32_t *mode)
{
    int err = 0;

    FUNCTION_ENTRY_LOG;

    if (odsp_hw_hdl == NULL) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                    ODSP_GET_SYS_MODE, (unsigned long)mode);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Returns Firmware status
 *
 * Input  - odsp_hw_hdl     - Handle to odsp hw structure
 *          mode            - Returned firmware status
 *              0: Firmware has crashed
 *              1: Firmware is idle
 *              2: Firmware is active
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_get_fw_status(struct iaxxx_odsp_hw *odsp_hw_hdl,
                                       uint32_t *status)
{
    int err = 0;

    FUNCTION_ENTRY_LOG;

    if (NULL == odsp_hw_hdl) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    err = ioctl(fileno(odsp_hw_hdl->dev_node),
                    ODSP_GET_FW_STATUS, (unsigned long)status);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

/**
 * Resets the firmware by redownloading the firmware
 *
 * Input  - odsp_hw_hdl     - Handle to odsp hw structure
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_reset_fw(struct iaxxx_odsp_hw *odsp_hw_hdl)
{
    int err = 0;

    FUNCTION_ENTRY_LOG;

    if (NULL == odsp_hw_hdl) {
        ALOGE("%s: ERROR: Invalid handle to iaxxx_odsp_hw", __func__);
        err = -1;
        goto func_exit;
    }

    err = ioctl(fileno(odsp_hw_hdl->dev_node), ODSP_RESET_FW);
    if (err < 0) {
        ALOGE("%s: ERROR: Failed with error %s", __func__, strerror(errno));
    }

func_exit:
    FUNCTION_EXIT_LOG;
    return err;
}

