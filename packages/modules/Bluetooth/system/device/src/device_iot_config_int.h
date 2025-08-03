/******************************************************************************
 *
 *  Copyright (C) 2014 Google, Inc.
 *  Copyright (C) 2018 The Linux Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#pragma once

#include "osi/include/config.h"
#include "osi/include/osi.h"

#define PROPERTY_ENABLE_LOGGING \
  "persist.bluetooth.device_iot_config.enablelogging"
#define PROPERTY_FACTORY_RESET "persist.bluetooth.factoryreset"

#define INFO_SECTION "Info"
#define VERSION_KEY "Version"
#define FILE_CREATED_TIMESTAMP "TimeCreated"
#define FILE_MODIFIED_TIMESTAMP "TimeModified"
#define TIME_STRING_LENGTH sizeof("YYYY-MM-DD HH:MM:SS")
static const char* TIME_STRING_FORMAT = "%Y-%m-%d %H:%M:%S";

#ifndef DEVICES_MAX_NUM_IN_IOT_INFO_FILE
#define DEVICES_MAX_NUM_IN_IOT_INFO_FILE 40
#endif
#define DEVICES_NUM_MARGIN 5

#if (DEVICES_MAX_NUM_IN_IOT_INFO_FILE < DEVICES_NUM_MARGIN)
#undef DEVICES_MAX_NUM_IN_IOT_INFO_FILE
#define DEVICES_MAX_NUM_IN_IOT_INFO_FILE DEVICES_NUM_MARGIN
#endif

#define DEVICE_IOT_INFO_CURRENT_VERSION 1
#define DEVICE_IOT_INFO_FIRST_VERSION 1

#define IOT_CONFIG_FLUSH_EVT 0
#define IOT_CONFIG_SAVE_TIMER_FIRED_EVT 1

#ifdef __ANDROID__
static const char* IOT_CONFIG_FILE_PATH =
    "/data/misc/bluedroid/bt_remote_dev_info.conf";
static const char* IOT_CONFIG_BACKUP_PATH =
    "/data/misc/bluedroid/bt_remote_dev_info.bak";
#else   // !__ANDROID__
static const char* IOT_CONFIG_FILE_PATH = "bt_remote_dev_info.conf";
static const char* IOT_CONFIG_BACKUP_PATH = "bt_remote_dev_info.bak";
#endif  // __ANDROID__
static const uint64_t CONFIG_SETTLE_PERIOD_MS = 12000;

enum ConfigSource { NOT_LOADED, ORIGINAL, BACKUP, NEW_FILE, RESET };

#define CHECK_LOGGING_ENABLED(return_value)                               \
  do {                                                                    \
    if (!bluetooth::common::InitFlags::IsDeviceIotConfigLoggingEnabled()) \
      return (return_value);                                              \
  } while (0)

struct config_t;
struct future_t;

typedef bool (*compare_func)(const entry_t& first, const entry_t& second);

// config_lock is used by the callee in the following methods
future_t* device_iot_config_module_init(void);
future_t* device_iot_config_module_start_up(void);
future_t* device_iot_config_module_shut_down(void);
future_t* device_iot_config_module_clean_up(void);
void device_iot_config_write(uint16_t event, char* p_param);

// config_lock is used by the caller of the following methods
void device_iot_config_sections_sort_by_entry_key(config_t& config,
                                                  compare_func comp);
bool device_iot_config_has_key_value(const std::string& section,
                                     const std::string& key,
                                     const std::string& value_str);
void device_iot_config_save_async(void);
int device_iot_config_get_device_num(const config_t& config);
void device_iot_config_restrict_device_num(config_t& config);
bool device_iot_config_compare_key(const entry_t& first, const entry_t& second);
void device_iot_config_timer_save_cb(UNUSED_ATTR void* data);
void device_iot_config_set_modified_time();
bool device_iot_config_is_factory_reset(void);
void device_iot_config_delete_files(void);
