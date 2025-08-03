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

#ifndef _IAXXX_ODSP_HW_H_
#define _IAXXX_ODSP_HW_H_

#if __cplusplus
extern "C"
{
#endif

#include <linux/mfd/adnc/iaxxx-odsp.h>
#include <linux/mfd/adnc/iaxxx-system-identifiers.h>

#define NAME_MAX_SIZE 256
struct iaxxx_odsp_hw;

struct iaxxx_config_file {
    const char *filename;
};

struct iaxxx_config_value {
    uint64_t config_val;
    uint32_t config_val_sz;
};

union iaxxx_config_data {
    struct iaxxx_config_file fdata;
    struct iaxxx_config_value vdata;
};

enum iaxxx_config_type {
    CONFIG_FILE,
    CONFIG_VALUE
};

struct iaxxx_create_config_data {
    enum iaxxx_config_type type;
    union iaxxx_config_data data;
};

struct iaxxx_plugin_status_data {
    uint32_t block_id;
    uint8_t  create_status;
    uint8_t  enable_status;
    uint16_t process_count;
    uint16_t process_err_count;
    uint32_t in_frames_consumed;
    uint32_t out_frames_produced;
    uint32_t private_memsize;
    uint8_t  frame_notification_mode;
    uint8_t  state_management_mode;
};

struct iaxxx_plugin_endpoint_status_data {
    uint8_t  status;
    uint8_t  frame_status;
    uint8_t  endpoint_status;
    uint8_t  usage;
    uint8_t  mandatory;
    uint16_t counter;
    uint8_t  op_encoding;
    uint8_t  op_sample_rate;
    uint16_t op_frame_length;
};

struct iaxxx_get_event_info {
  uint16_t event_id;
  uint32_t data;
};

enum clock_source {
    SYSCLK,
    INT_OSC,
    EXT_OSC,
};

/**
 * Initialize the ODSP HAL
 *
 * Input  - NA
 * Output - Handle to iaxxx_odsp_hw on success, NULL on failure
 */
struct iaxxx_odsp_hw* iaxxx_odsp_init();

/**
 * De-Initialize the ODSP HAL
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_deinit(struct iaxxx_odsp_hw *odsp_hw_hdl);

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
                            const char *pkg_name,
                            const uint32_t pkg_id);

/**
 * Unload a package
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          pkg_id - Package ID
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_package_unload(struct iaxxx_odsp_hw *odsp_hw_hdl,
			                const uint32_t pkg_id);

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
                            uint8_t inst_id, char *version, uint32_t len);

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
                            uint8_t inst_id, char *version, uint32_t len);

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
                            const uint32_t config_id);

/**
 * Set the creation configuration on a plugin
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          inst_id - Instance ID
 *          block_id - Block ID
 *          cdata - Creation configuration data
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_set_creation_config(
                                        struct iaxxx_odsp_hw *odsp_hw_hdl,
                                        const uint32_t inst_id,
                                        const uint32_t block_id,
                                        struct iaxxx_create_config_data cdata);

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
                            const uint32_t block_id);

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
                            const uint32_t block_id);

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
                            const uint32_t block_id);

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
                            const uint32_t block_id);

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
                                    const uint32_t block_id);

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
                                    uint32_t *param_val);

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
                                        const uint32_t param_buf_sz);

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
                                        const char *file_name);

/**
 * Set custom configuration for plugin
 *
 * Input  - odsp_hw_hdl 	    - Handle to odsp hw structure
 *          inst_id  		    - Instance ID
 *          block_id 		    - Block ID
 *          param_blk_id  	    - Parameter block id
 *          custom_config_id    - Id for what type of custom configuration
 *          filename		    - Name of file with custom config data
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_set_custom_cfg(struct iaxxx_odsp_hw *odsp_hw_hdl,
                                    const uint32_t inst_id,
                                    const uint32_t block_id,
                                    const uint32_t param_blk_id,
                                    const uint32_t custom_config_id,
                                    const char *filename);

/**
 * Subscribe to an event
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          src_id  	- System Id of event source
 *          event_id 	- Event Id
 *          dst_id	- System Id of event destination
 *          dst_opaque	- Info sought by destination task when even occurs
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_evt_subscribe(struct iaxxx_odsp_hw *odsp_hw_hdl,
                            const uint16_t src_id,
                            const uint16_t event_id,
                            const uint16_t dst_id,
                            const uint32_t dst_opaque);

/**
 * Unsubscribe an event
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          src_id  	- System Id of event source
 *          event_id 	- Event Id
 *          dst_id	- System Id of event destination
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_evt_unsubscribe(struct iaxxx_odsp_hw *odsp_hw_hdl,
                            const uint16_t src_id,
                            const uint16_t event_id,
                            const uint16_t dst_id);

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
                        uint32_t src_opaque);

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
                                    uint32_t *dst_opaque);

/**
 * Reset index for retrieving subscription entries
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_evt_reset_read_index(struct iaxxx_odsp_hw *odsp_hw_hdl);

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
                                        uint32_t *dst_opaque);

/**
 * Retrieve an event
 *
 * Input  - odsp_hw_hdl - Handle to odsp hw structure
 *          event_info  - Struct to return event info
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_evt_getevent(struct iaxxx_odsp_hw *odsp_hw_hdl,
                            struct iaxxx_get_event_info *event_info);

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
                                            const uint32_t config_id);

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
                                        const uint32_t param_buf_sz);

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
                            const uint32_t block_id);

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
                                uint8_t *error_instance);

/* Read the timestamps of all output endpoint
 *
 * Input  - odsp_hw_hdl     - Handle to odsp hw structure
 *          proc_id         - Proc ID
 *          timestamps      - The timestamps array(with 16 elements count)
 *                            to be filled.
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_get_ep_timestamps(struct iaxxx_odsp_hw *odsp_hw_hdl,
                                    uint64_t *timestamps, uint8_t proc_id);

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
                                        const uint32_t max_no_retries);

/**
 * Get Plugin status information.
 *
 * Input  - odsp_hw_hdl         - Handle to odsp hw structure
 *          inst_id             - Instance ID
 *          plugin_status_data  - Pointer to struct to return plugin status.
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_plugin_get_status_info(
                        struct iaxxx_odsp_hw *odsp_hw_hdl,
                        const uint32_t inst_id,
                        struct iaxxx_plugin_status_data *plugin_status_data);

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
int iaxxx_odsp_plugin_get_endpoint_status(
            struct iaxxx_odsp_hw *odsp_hw_hdl,
            const uint32_t inst_id,
            const uint8_t ep_index,
            const uint8_t direction,
            struct iaxxx_plugin_endpoint_status_data *plugin_ep_status_data);

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
                                    uint8_t proc_id, uint32_t *status);

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
                                uint32_t app_ver_str_len);

/**
 * Returns Device ID
 *
 * Input  - odsp_hw_hdl     - Handle to odsp hw structure
 *          device_id       - Returned Device ID
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_get_device_id(struct iaxxx_odsp_hw *odsp_hw_hdl,
                            uint32_t *device_id);

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
                                       uint32_t *status);

/**
 * Resets the firmware by redownloading the firmware
 *
 * Input  - odsp_hw_hdl     - Handle to odsp hw structure
 *
 * Output - 0 on success, on failure < 0
 */
int iaxxx_odsp_reset_fw(struct iaxxx_odsp_hw *odsp_hw_hdl);

#if __cplusplus
} // extern "C"
#endif

#endif // #ifndef _IAXXX_ODSP_HW_H_
