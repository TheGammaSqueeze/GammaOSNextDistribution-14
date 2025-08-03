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
#include "internal_include/bt_target.h"

#define LOG_TAG "device_iot_config"
#include <base/logging.h>
#include <ctype.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include <mutex>
#include <string>

#include "common/init_flags.h"
#include "device/include/device_iot_config.h"
#include "device_iot_config_int.h"
#include "include/check.h"
#include "os/log.h"
#include "osi/include/alarm.h"
#include "osi/include/allocator.h"
#include "osi/include/compat.h"
#include "osi/include/config.h"

enum ConfigSource device_iot_config_source = NOT_LOADED;

int device_iot_config_devices_loaded = -1;
char device_iot_config_time_created[TIME_STRING_LENGTH];

std::mutex config_lock;  // protects operations on |config|.
std::unique_ptr<config_t> config;
alarm_t* config_timer;

using bluetooth::common::InitFlags;

bool device_iot_config_has_section(const std::string& section) {
  if (!InitFlags::IsDeviceIotConfigLoggingEnabled()) return false;

  CHECK(config != NULL);

  std::unique_lock<std::mutex> lock(config_lock);
  return config_has_section(*config, section);
}

bool device_iot_config_exist(const std::string& section,
                             const std::string& key) {
  if (!InitFlags::IsDeviceIotConfigLoggingEnabled()) return false;

  CHECK(config != NULL);

  std::unique_lock<std::mutex> lock(config_lock);
  return config_has_key(*config, section, key);
}

bool device_iot_config_get_int(const std::string& section,
                               const std::string& key, int& value) {
  if (!InitFlags::IsDeviceIotConfigLoggingEnabled()) return false;

  CHECK(config != NULL);

  std::unique_lock<std::mutex> lock(config_lock);
  bool ret = config_has_key(*config, section, key);
  if (ret) value = config_get_int(*config, section, key, value);

  return ret;
}

bool device_iot_config_set_int(const std::string& section,
                               const std::string& key, int value) {
  if (!InitFlags::IsDeviceIotConfigLoggingEnabled()) return false;

  CHECK(config != NULL);

  std::unique_lock<std::mutex> lock(config_lock);
  char value_str[32] = {0};
  snprintf(value_str, sizeof(value_str), "%d", value);
  if (device_iot_config_has_key_value(section, key, value_str)) return true;

  config_set_string(config.get(), section, key, value_str);
  device_iot_config_save_async();

  return true;
}

bool device_iot_config_int_add_one(const std::string& section,
                                   const std::string& key) {
  if (!InitFlags::IsDeviceIotConfigLoggingEnabled()) return false;

  CHECK(config != NULL);

  int result = 0;
  std::unique_lock<std::mutex> lock(config_lock);
  result = config_get_int(*config, section, key, result);
  if (result >= 0) {
    result += 1;
  } else {
    result = 0;
  }
  config_set_int(config.get(), section, key, result);
  device_iot_config_save_async();

  return true;
}

bool device_iot_config_get_hex(const std::string& section,
                               const std::string& key, int& value) {
  if (!InitFlags::IsDeviceIotConfigLoggingEnabled()) return false;

  CHECK(config != NULL);

  std::unique_lock<std::mutex> lock(config_lock);
  const std::string* stored_value =
      config_get_string(*config, section, key, NULL);
  if (!stored_value) return false;

  errno = 0;
  char* endptr = nullptr;
  int result = strtoul(stored_value->c_str(), &endptr, 16);
  if (stored_value->c_str() == endptr) return false;
  if (endptr == nullptr || endptr[0] != '\0') return false;
  if (errno) return false;

  value = result;
  return true;
}

bool device_iot_config_set_hex(const std::string& section,
                               const std::string& key, int value,
                               int byte_num) {
  if (!InitFlags::IsDeviceIotConfigLoggingEnabled()) return false;

  CHECK(config != NULL);

  char value_str[32] = {0};
  if (byte_num == 1)
    snprintf(value_str, sizeof(value_str), "%02x", value);
  else if (byte_num == 2)
    snprintf(value_str, sizeof(value_str), "%04x", value);
  else if (byte_num == 3)
    snprintf(value_str, sizeof(value_str), "%06x", value);
  else if (byte_num == 4)
    snprintf(value_str, sizeof(value_str), "%08x", value);

  std::unique_lock<std::mutex> lock(config_lock);
  if (device_iot_config_has_key_value(section, key, value_str)) return true;

  config_set_string(config.get(), section, key, value_str);
  device_iot_config_save_async();

  return true;
}

bool device_iot_config_set_hex_if_greater(const std::string& section,
                                          const std::string& key, int value,
                                          int byte_num) {
  if (!InitFlags::IsDeviceIotConfigLoggingEnabled()) return false;

  int stored_value = 0;
  bool ret = device_iot_config_get_hex(section, key, stored_value);
  if (ret && stored_value >= value) return true;

  return device_iot_config_set_hex(section, key, value, byte_num);
}

bool device_iot_config_get_str(const std::string& section,
                               const std::string& key, char* value,
                               int* size_bytes) {
  if (!InitFlags::IsDeviceIotConfigLoggingEnabled()) return false;

  CHECK(config != NULL);
  CHECK(value != NULL);
  CHECK(size_bytes != NULL);

  std::unique_lock<std::mutex> lock(config_lock);
  const std::string* stored_value =
      config_get_string(*config, section, key, NULL);

  if (!stored_value) return false;

  strlcpy(value, stored_value->c_str(), *size_bytes);
  *size_bytes = strlen(value) + 1;

  return true;
}

bool device_iot_config_set_str(const std::string& section,
                               const std::string& key,
                               const std::string& value) {
  if (!InitFlags::IsDeviceIotConfigLoggingEnabled()) return false;

  CHECK(config != NULL);

  std::unique_lock<std::mutex> lock(config_lock);
  if (device_iot_config_has_key_value(section, key, value)) return true;

  config_set_string(config.get(), section, key, value);
  device_iot_config_save_async();

  return true;
}

bool device_iot_config_get_bin(const std::string& section,
                               const std::string& key, uint8_t* value,
                               size_t* length) {
  if (!InitFlags::IsDeviceIotConfigLoggingEnabled()) return false;

  CHECK(config != NULL);
  CHECK(value != NULL);
  CHECK(length != NULL);

  std::unique_lock<std::mutex> lock(config_lock);
  const std::string* value_string =
      config_get_string(*config, section, key, NULL);

  if (!value_string) return false;

  const char* value_str = value_string->c_str();

  size_t value_len = strlen(value_str);
  if ((value_len % 2) != 0 || *length < (value_len / 2)) return false;

  for (size_t i = 0; i < value_len; ++i)
    if (!isxdigit(value_str[i])) return false;

  for (*length = 0; *value_str; value_str += 2, *length += 1) {
    errno = 0;
    char* endptr = nullptr;
    value[*length] = strtoul(value_str, &endptr, 16);
    if (value_str == endptr) return false;
    if (*endptr) return false;
    if (errno) return false;
  }

  return true;
}

size_t device_iot_config_get_bin_length(const std::string& section,
                                        const std::string& key) {
  if (!InitFlags::IsDeviceIotConfigLoggingEnabled()) return 0;

  CHECK(config != NULL);

  std::unique_lock<std::mutex> lock(config_lock);
  const std::string* value_str = config_get_string(*config, section, key, NULL);

  if (!value_str) return 0;

  size_t value_len = strlen(value_str->c_str());
  return ((value_len % 2) != 0) ? 0 : (value_len / 2);
}

bool device_iot_config_set_bin(const std::string& section,
                               const std::string& key, const uint8_t* value,
                               size_t length) {
  if (!InitFlags::IsDeviceIotConfigLoggingEnabled()) return false;

  const char* lookup = "0123456789abcdef";

  CHECK(config != NULL);

  LOG_VERBOSE("Key = %s", key.c_str());
  if (length > 0) CHECK(value != NULL);

  char* str = (char*)osi_calloc(length * 2 + 1);
  if (str == NULL) {
    LOG_ERROR("Unable to allocate a str.");
    return false;
  }

  for (size_t i = 0; i < length; ++i) {
    str[(i * 2) + 0] = lookup[(value[i] >> 4) & 0x0F];
    str[(i * 2) + 1] = lookup[value[i] & 0x0F];
  }

  std::unique_lock<std::mutex> lock(config_lock);
  if (device_iot_config_has_key_value(section, key, str)) {
    osi_free(str);
    return true;
  }

  config_set_string(config.get(), section, key, str);
  device_iot_config_save_async();

  osi_free(str);
  return true;
}

bool device_iot_config_remove(const std::string& section,
                              const std::string& key) {
  if (!InitFlags::IsDeviceIotConfigLoggingEnabled()) return false;

  CHECK(config != NULL);

  std::unique_lock<std::mutex> lock(config_lock);
  return config_remove_key(config.get(), section, key);
}

void device_iot_config_flush(void) {
  if (!InitFlags::IsDeviceIotConfigLoggingEnabled()) return;

  CHECK(config != NULL);
  CHECK(config_timer != NULL);

  int event = alarm_is_scheduled(config_timer) ? IOT_CONFIG_SAVE_TIMER_FIRED_EVT
                                               : IOT_CONFIG_FLUSH_EVT;
  LOG_VERBOSE("evt=%d", event);
  alarm_cancel(config_timer);
  device_iot_config_write(event, NULL);
}

bool device_iot_config_clear(void) {
  if (!InitFlags::IsDeviceIotConfigLoggingEnabled()) return true;

  CHECK(config != NULL);
  CHECK(config_timer != NULL);

  LOG_INFO("");
  alarm_cancel(config_timer);

  std::unique_lock<std::mutex> lock(config_lock);
  config.reset();

  config = config_new_empty();
  if (config == NULL) {
    return false;
  }

  bool ret = config_save(*config, IOT_CONFIG_FILE_PATH);
  device_iot_config_source = RESET;
  return ret;
}

void device_debug_iot_config_dump(int fd) {
  if (!InitFlags::IsDeviceIotConfigLoggingEnabled()) return;

  dprintf(fd, "\nBluetooth Iot Config:\n");

  dprintf(fd, "  Config Source: ");
  switch (device_iot_config_source) {
    case NOT_LOADED:
      dprintf(fd, "Not loaded\n");
      break;
    case ORIGINAL:
      dprintf(fd, "Original file\n");
      break;
    case BACKUP:
      dprintf(fd, "Backup file\n");
      break;
    case NEW_FILE:
      dprintf(fd, "New file\n");
      break;
    case RESET:
      dprintf(fd, "Reset file\n");
      break;
  }

  dprintf(fd, "  Devices loaded: %d\n", device_iot_config_devices_loaded);
  dprintf(fd, "  File created/tagged: %s\n", device_iot_config_time_created);
}
