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

#include <stdbool.h>
#include <stddef.h>

#include <string>

#include "device_iot_conf_defs.h"
#include "internal_include/bt_target.h"

static const char DEVICE_IOT_CONFIG_MODULE[] = "device_iot_config_module";

bool device_iot_config_get_int(const std::string& section,
                               const std::string& key, int& value);
bool device_iot_config_set_int(const std::string& section,
                               const std::string& key, int value);
bool device_iot_config_int_add_one(const std::string& section,
                                   const std::string& key);
bool device_iot_config_get_hex(const std::string& section,
                               const std::string& key, int& value);
bool device_iot_config_set_hex(const std::string& section,
                               const std::string& key, int value, int byte_num);
bool device_iot_config_set_hex_if_greater(const std::string& section,
                                          const std::string& key, int value,
                                          int byte_num);
bool device_iot_config_get_str(const std::string& section,
                               const std::string& key, char* value,
                               int* size_bytes);
bool device_iot_config_set_str(const std::string& section,
                               const std::string& key,
                               const std::string& value);
bool device_iot_config_get_bin(const std::string& section,
                               const std::string& key, uint8_t* value,
                               size_t* length);
bool device_iot_config_set_bin(const std::string& section,
                               const std::string& key, const uint8_t* value,
                               size_t length);
size_t device_iot_config_get_bin_length(const std::string& section,
                                        const std::string& key);

#define DEVICE_IOT_CONFIG_ADDR(method, addr, ...) \
  device_iot_config_##method((addr).ToString(), ##__VA_ARGS__)

#define DEVICE_IOT_CONFIG_ADDR_GET_INT(addr, ...) \
  DEVICE_IOT_CONFIG_ADDR(get_int, addr, ##__VA_ARGS__)

#define DEVICE_IOT_CONFIG_ADDR_SET_INT(addr, ...) \
  DEVICE_IOT_CONFIG_ADDR(set_int, addr, ##__VA_ARGS__)

#define DEVICE_IOT_CONFIG_ADDR_INT_ADD_ONE(addr, ...) \
  DEVICE_IOT_CONFIG_ADDR(int_add_one, addr, ##__VA_ARGS__)

#define DEVICE_IOT_CONFIG_ADDR_GET_HEX(addr, ...) \
  DEVICE_IOT_CONFIG_ADDR(get_hex, addr, ##__VA_ARGS__)

#define DEVICE_IOT_CONFIG_ADDR_SET_HEX(addr, ...) \
  DEVICE_IOT_CONFIG_ADDR(set_hex, addr, ##__VA_ARGS__)

#define DEVICE_IOT_CONFIG_ADDR_SET_HEX_IF_GREATER(addr, ...) \
  DEVICE_IOT_CONFIG_ADDR(set_hex_if_greater, addr, ##__VA_ARGS__)

#define DEVICE_IOT_CONFIG_ADDR_GET_STR(addr, ...) \
  DEVICE_IOT_CONFIG_ADDR(set_gtr, addr, ##__VA_ARGS__)

#define DEVICE_IOT_CONFIG_ADDR_SET_STR(addr, ...) \
  DEVICE_IOT_CONFIG_ADDR(set_str, addr, ##__VA_ARGS__)

#define DEVICE_IOT_CONFIG_ADDR_GET_BIN(addr, ...) \
  DEVICE_IOT_CONFIG_ADDR(get_bin, addr, ##__VA_ARGS__)

#define DEVICE_IOT_CONFIG_ADDR_SET_BIN(addr, ...) \
  DEVICE_IOT_CONFIG_ADDR(set_bin, addr, ##__VA_ARGS__)

#define DEVICE_IOT_CONFIG_ADDR_GET_BIN_LENGTH(addr, ...) \
  DEVICE_IOT_CONFIG_ADDR(get_bin, addr, ##__VA_ARGS__)

bool device_iot_config_has_section(const std::string& section);
bool device_iot_config_exist(const std::string& section,
                             const std::string& key);
bool device_iot_config_remove(const std::string& section,
                              const std::string& key);

void device_iot_config_flush(void);
bool device_iot_config_clear(void);

void device_debug_iot_config_dump(int fd);
