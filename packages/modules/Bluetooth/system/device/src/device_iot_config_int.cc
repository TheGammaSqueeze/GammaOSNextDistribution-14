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

#define LOG_TAG "device_iot_config"
#include "device_iot_config_int.h"

#include <base/logging.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include <mutex>
#include <string>

#include "btcore/include/module.h"
#include "btif/include/btif_common.h"
#include "common/init_flags.h"
#include "device/include/device_iot_config.h"
#include "include/check.h"
#include "os/log.h"
#include "osi/include/alarm.h"
#include "osi/include/config.h"
#include "osi/include/future.h"
#include "osi/include/osi.h"
#include "osi/include/properties.h"
#include "types/raw_address.h"

extern enum ConfigSource device_iot_config_source;

extern int device_iot_config_devices_loaded;
extern char device_iot_config_time_created[TIME_STRING_LENGTH];

extern std::mutex config_lock;  // protects operations on |config|.
extern std::unique_ptr<config_t> config;
extern alarm_t* config_timer;

using bluetooth::common::InitFlags;

static void cleanup() {
  alarm_free(config_timer);
  config_timer = NULL;
  config.reset();
  config = NULL;
  device_iot_config_source = NOT_LOADED;
}

// Module lifecycle functions
future_t* device_iot_config_module_init(void) {
  LOG_INFO("");

  std::unique_lock<std::mutex> lock(config_lock);

  config_timer = NULL;
  config = NULL;

  if (device_iot_config_is_factory_reset()) {
    device_iot_config_delete_files();
  }

  config = config_new(IOT_CONFIG_FILE_PATH);
  device_iot_config_source = ORIGINAL;
  if (!config) {
    LOG_WARN("Unable to load config file: %s; using backup.",
             IOT_CONFIG_FILE_PATH);
    config = config_new(IOT_CONFIG_BACKUP_PATH);
    device_iot_config_source = BACKUP;
  }

  if (!config) {
    LOG_ERROR("Unable to load bak file; creating empty config.");
    config = config_new_empty();
    device_iot_config_source = NEW_FILE;
  }

  if (!config) {
    LOG_ERROR("Unable to allocate a config object.");
    cleanup();
    return future_new_immediate(FUTURE_FAIL);
  }

  int version;
  if (device_iot_config_source == NEW_FILE) {
    version = DEVICE_IOT_INFO_CURRENT_VERSION;
    config_set_int(config.get(), INFO_SECTION, VERSION_KEY, version);
  } else {
    version = config_get_int(*config, INFO_SECTION, VERSION_KEY, -1);
    if (version == -1) {
      version = DEVICE_IOT_INFO_FIRST_VERSION;
      config_set_int(config.get(), INFO_SECTION, VERSION_KEY, version);
    }
  }

  if (version != DEVICE_IOT_INFO_CURRENT_VERSION) {
    LOG_INFO("Version in file is %d, CURRENT_VERSION is %d ", version,
             DEVICE_IOT_INFO_CURRENT_VERSION);
    remove(IOT_CONFIG_FILE_PATH);
    remove(IOT_CONFIG_BACKUP_PATH);
    config.reset();
    config = config_new_empty();
    if (!config) {
      LOG_ERROR("Unable to allocate a config object.");
      cleanup();
      return future_new_immediate(FUTURE_FAIL);
    }
    config_set_int(config.get(), INFO_SECTION, VERSION_KEY,
                   DEVICE_IOT_INFO_CURRENT_VERSION);
    device_iot_config_source = NEW_FILE;
  }

  device_iot_config_devices_loaded = device_iot_config_get_device_num(*config);
  LOG_INFO("Devices loaded %d", device_iot_config_devices_loaded);

  // Read or set config file creation timestamp
  const std::string* time_str =
      config_get_string(*config, INFO_SECTION, FILE_CREATED_TIMESTAMP, NULL);
  if (time_str != NULL) {
    strncpy(device_iot_config_time_created, time_str->c_str(),
            TIME_STRING_LENGTH);
  } else {
    // Read or set config file creation timestamp
    time_t current_time = time(NULL);
    struct tm* time_created = localtime(&current_time);
    if (time_created) {
      strftime(device_iot_config_time_created, TIME_STRING_LENGTH,
               TIME_STRING_FORMAT, time_created);
      config_set_string(config.get(), INFO_SECTION, FILE_CREATED_TIMESTAMP,
                        std::string(device_iot_config_time_created));
    }
  }

  // TODO: use a non-wake alarm for this once we have
  // API support for it. There's no need to wake the system to
  // write back to disk.
  config_timer = alarm_new("btif.iot.config");
  if (!config_timer) {
    LOG_ERROR("Unable to create alarm.");
    cleanup();
    return future_new_immediate(FUTURE_FAIL);
  }

  return future_new_immediate(FUTURE_SUCCESS);
}

future_t* device_iot_config_module_start_up(void) {
  LOG_INFO("");
  return future_new_immediate(FUTURE_SUCCESS);
}

future_t* device_iot_config_module_shut_down(void) {
  LOG_INFO("");
  device_iot_config_flush();
  return future_new_immediate(FUTURE_SUCCESS);
}

future_t* device_iot_config_module_clean_up(void) {
  LOG_INFO("");
  if (config_timer != NULL && alarm_is_scheduled(config_timer))
    device_iot_config_flush();

  alarm_free(config_timer);
  config_timer = NULL;

  std::unique_lock<std::mutex> lock(config_lock);
  config.reset();
  config = NULL;
  return future_new_immediate(FUTURE_SUCCESS);
}

EXPORT_SYMBOL module_t device_iot_config_module = {
    .name = DEVICE_IOT_CONFIG_MODULE,
    .init = device_iot_config_module_init,
    .start_up = device_iot_config_module_start_up,
    .shut_down = device_iot_config_module_shut_down,
    .clean_up = device_iot_config_module_clean_up};

void device_iot_config_write(uint16_t event, UNUSED_ATTR char* p_param) {
  if (!InitFlags::IsDeviceIotConfigLoggingEnabled()) return;

  CHECK(config != NULL);
  CHECK(config_timer != NULL);

  LOG_INFO("evt=%d", event);
  std::unique_lock<std::mutex> lock(config_lock);
  if (event == IOT_CONFIG_SAVE_TIMER_FIRED_EVT) {
    device_iot_config_set_modified_time();
  }

  rename(IOT_CONFIG_FILE_PATH, IOT_CONFIG_BACKUP_PATH);
  device_iot_config_restrict_device_num(*config);
  device_iot_config_sections_sort_by_entry_key(*config,
                                               device_iot_config_compare_key);
  config_save(*config, IOT_CONFIG_FILE_PATH);
}

void device_iot_config_sections_sort_by_entry_key(config_t& config,
                                                  compare_func comp) {
  for (auto& entry : config.sections) {
    entry.entries.sort(comp);
  }
}

bool device_iot_config_has_key_value(const std::string& section,
                                     const std::string& key,
                                     const std::string& value_str) {
  CHECK(config != NULL);

  const std::string* stored_value =
      config_get_string(*config, section, key, NULL);

  if (!stored_value || value_str.compare(*stored_value) != 0) return false;

  return true;
}

void device_iot_config_save_async(void) {
  if (!InitFlags::IsDeviceIotConfigLoggingEnabled()) return;

  CHECK(config != NULL);
  CHECK(config_timer != NULL);

  LOG_VERBOSE("");
  alarm_set(config_timer, CONFIG_SETTLE_PERIOD_MS,
            device_iot_config_timer_save_cb, NULL);
}

int device_iot_config_get_device_num(const config_t& conf) {
  if (!InitFlags::IsDeviceIotConfigLoggingEnabled()) return 0;

  int devices = 0;

  for (const auto& entry : conf.sections) {
    if (RawAddress::IsValidAddress(entry.name)) {
      devices++;
    }
  }
  return devices;
}

void device_iot_config_restrict_device_num(config_t& config) {
  int curr_num = device_iot_config_get_device_num(config);
  int removed_devices = 0;
  int need_remove_devices_num;

  if (curr_num <= DEVICES_MAX_NUM_IN_IOT_INFO_FILE) {
    return;
  }

  need_remove_devices_num =
      curr_num - DEVICES_MAX_NUM_IN_IOT_INFO_FILE + DEVICES_NUM_MARGIN;
  LOG_INFO("curr_num=%d, need_remove_num=%d", curr_num,
           need_remove_devices_num);

  std::list<section_t>::iterator i = config.sections.begin();
  while (i != config.sections.end()) {
    if (!RawAddress::IsValidAddress(i->name)) {
      ++i;
      continue;
    }

    i = config.sections.erase(i);
    if (++removed_devices >= need_remove_devices_num) {
      break;
    }
  }
}

bool device_iot_config_compare_key(const entry_t& first,
                                   const entry_t& second) {
  bool first_is_profile_key = strncasecmp(first.key.c_str(), "Profile", 7) == 0;
  bool second_is_profile_key =
      strncasecmp(second.key.c_str(), "Profile", 7) == 0;
  if (!first_is_profile_key && !second_is_profile_key) {
    return true;
  } else if (first_is_profile_key && second_is_profile_key) {
    return strcasecmp(first.key.c_str(), second.key.c_str()) <= 0;
  } else {
    return !first_is_profile_key;
  }
}

void device_iot_config_timer_save_cb(UNUSED_ATTR void* data) {
  // Moving file I/O to btif context instead of timer callback because
  // it usually takes a lot of time to be completed, introducing
  // delays during A2DP playback causing blips or choppiness.
  LOG_VERBOSE("");
  btif_transfer_context(device_iot_config_write,
                        IOT_CONFIG_SAVE_TIMER_FIRED_EVT, NULL, 0, NULL);
}

void device_iot_config_set_modified_time() {
  time_t current_time = time(NULL);
  struct tm* time_modified = localtime(&current_time);
  char device_iot_config_time_modified[TIME_STRING_LENGTH];
  if (time_modified) {
    strftime(device_iot_config_time_modified, TIME_STRING_LENGTH,
             TIME_STRING_FORMAT, time_modified);
    config_set_string(config.get(), INFO_SECTION, FILE_MODIFIED_TIMESTAMP,
                      device_iot_config_time_modified);
  }
}

bool device_iot_config_is_factory_reset(void) {
  return osi_property_get_bool(PROPERTY_FACTORY_RESET, false);
}

void device_iot_config_delete_files(void) {
  remove(IOT_CONFIG_FILE_PATH);
  remove(IOT_CONFIG_BACKUP_PATH);
}
