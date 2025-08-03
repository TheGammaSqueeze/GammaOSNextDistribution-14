/******************************************************************************
 *
 *  Copyright (C) 2016 The Linux Foundation
 *  Copyright 2015 Google, Inc.
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

#define LOG_TAG "bt_device_interop"

#include "device/include/interop.h"

#include <assert.h>
#include <base/logging.h>
#include <ctype.h>
#include <fcntl.h>
#include <hardware/bluetooth.h>
#include <pthread.h>
#include <stdio.h>
#include <string.h>  // For memcmp
#include <sys/stat.h>
#include <unistd.h>

#include <iostream>
#include <map>
#include <string>
#include <utility>

#include "btcore/include/module.h"
#include "btif/include/btif_storage.h"
#include "check.h"
#include "device/include/interop_config.h"
#include "device/include/interop_database.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/compat.h"
#include "osi/include/config.h"
#include "osi/include/list.h"
#include "osi/include/osi.h"
#include "types/raw_address.h"

#ifdef __ANDROID__
static const char* INTEROP_DYNAMIC_FILE_PATH =
    "/data/misc/bluedroid/interop_database_dynamic.conf";
static const char* INTEROP_STATIC_FILE_PATH =
    "/apex/com.android.btservices/etc/bluetooth/interop_database.conf";
#elif TARGET_FLOSS
#include <base/files/file_util.h>

#include <filesystem>

static const std::filesystem::path kDynamicConfigFileConfigFile =
    std::filesystem::temp_directory_path() / "interop_database_dynamic.conf";
static const char* INTEROP_DYNAMIC_FILE_PATH =
    kDynamicConfigFileConfigFile.c_str();

static const char* INTEROP_STATIC_FILE_PATH =
    "/var/lib/bluetooth/interop_database.conf";
#else  // !TARGET_FLOSS and !__ANDROID__
#include <base/files/file_util.h>

#include <filesystem>

static const std::filesystem::path kDynamicConfigFileConfigFile =
    std::filesystem::temp_directory_path() / "interop_database_dynamic.conf";
static const char* INTEROP_DYNAMIC_FILE_PATH =
    kDynamicConfigFileConfigFile.c_str();

static const std::filesystem::path kStaticConfigFileConfigFile =
    std::filesystem::temp_directory_path() / "interop_database.conf";

static const char* INTEROP_STATIC_FILE_PATH =
    kStaticConfigFileConfigFile.c_str();
#endif  // __ANDROID__

#define CASE_RETURN_STR(const) \
  case const:                  \
    return #const;

static list_t* interop_list = NULL;
static list_t* media_player_list = NULL;

bool interop_is_initialized = false;
// protects operations on |interop_list|
pthread_mutex_t interop_list_lock;

// protects operations on |config|
static pthread_mutex_t file_lock;
static std::unique_ptr<const config_t> config_static;
static std::unique_ptr<config_t> config_dynamic;
static const char* UNKNOWN_INTEROP_FEATURE = "UNKNOWN";
// map from feature name to feature id
static std::map<std::string, int> feature_name_id_map;

// Macro used to find the total number of feature_types
#define NO_OF_FEATURES(x) (sizeof(x) / sizeof((x)[0]))

#define SECTION_MAX_LENGTH (249)
#define KEY_MAX_LENGTH (249)
#define VALID_VNDR_PRDT_LEN (13)
#define VALID_MNFR_STR_LEN (6)
#define VALID_SSR_LAT_LEN (15)
#define VALID_VERSION_LEN (6)
#define VALID_LMP_VERSION_LEN (20)
#define VALID_ADDR_RANGE_LEN (35)
#define VENDOR_VALUE_SEPARATOR "-"

#define ADDR_BASED "Address_Based"
#define ADDR_RANGE_BASED "Address_Range_Based"
#define NAME_BASED "Name_Based"
#define MNFR_BASED "Manufacturer_Based"
#define VNDR_PRDT_BASED "Vndr_Prdt_Based"
#define SSR_MAX_LAT_BASED "SSR_Max_Lat_Based"
#define VERSION_BASED "Version_Based"
#define LMP_VERSION_BASED "LMP_Version_Based"

typedef struct {
  char* key;
  char* value;
} interop_entry_t;

typedef struct {
  char* name;
  list_t* entries;
} interop_section_t;

typedef struct {
  RawAddress addr;
  uint16_t max_lat;
  interop_feature_t feature;
} interop_hid_ssr_max_lat_t;

typedef struct {
  uint16_t version;
  interop_feature_t feature;
} interop_version_t;

typedef struct {
  RawAddress addr;
  uint8_t lmp_ver;
  uint16_t lmp_sub_ver;
  interop_feature_t feature;
} interop_lmp_version_t;

typedef enum {
  INTEROP_BL_TYPE_ADDR = 0,
  INTEROP_BL_TYPE_NAME,
  INTEROP_BL_TYPE_MANUFACTURE,
  INTEROP_BL_TYPE_VNDR_PRDT,
  INTEROP_BL_TYPE_SSR_MAX_LAT,
  INTEROP_BL_TYPE_VERSION,
  INTEROP_BL_TYPE_LMP_VERSION,
  INTEROP_BL_TYPE_ADDR_RANGE,
} interop_bl_type;

typedef enum {
  INTEROP_ENTRY_TYPE_STATIC = 1 << 0,
  INTEROP_ENTRY_TYPE_DYNAMIC = 1 << 1
} interop_entry_type;

typedef struct {
  interop_bl_type bl_type;
  interop_entry_type bl_entry_type;

  union {
    interop_addr_entry_t addr_entry;
    interop_name_entry_t name_entry;
    interop_manufacturer_t mnfr_entry;
    interop_hid_multitouch_t vnr_pdt_entry;
    interop_hid_ssr_max_lat_t ssr_max_lat_entry;
    interop_version_t version_entry;
    interop_lmp_version_t lmp_version_entry;
    interop_addr_range_entry_t addr_range_entry;
  } entry_type;

} interop_db_entry_t;

static const char* interop_feature_string_(const interop_feature_t feature);
static void interop_free_entry_(void* data);
static void interop_lazy_init_(void);

// Config related functions
static void interop_config_cleanup(void);

// This function is used to initialize the interop list and load the entries
// from file
static void load_config();
static void interop_database_save_allowlisted_media_players_list(
    const config_t* config);
static void interop_database_add_(interop_db_entry_t* db_entry, bool persist);
static bool interop_database_remove_(interop_db_entry_t* entry);
static bool interop_database_match(interop_db_entry_t* entry,
                                   interop_db_entry_t** ret_entry,
                                   interop_entry_type entry_type);
static void interop_config_flush(void);
static bool interop_config_remove(const std::string& section,
                                  const std::string& key);

// Interface functions

bool interop_match_addr(const interop_feature_t feature,
                        const RawAddress* addr) {
  CHECK(addr);
  return (interop_database_match_addr(feature, addr));
}

bool interop_match_name(const interop_feature_t feature, const char* name) {
  CHECK(name);
  return (interop_database_match_name(feature, name));
}

bool interop_match_addr_or_name(const interop_feature_t feature,
                                const RawAddress* addr,
                                bt_status_t (*get_remote_device_property)(
                                    const RawAddress*, bt_property_t*)) {
  CHECK(addr);
  CHECK(get_remote_device_property);

  bt_bdname_t bdname;
  bt_property_t prop_name;

  if (interop_match_addr(feature, addr)) return true;

  BTIF_STORAGE_FILL_PROPERTY(&prop_name, BT_PROPERTY_BDNAME,
                             sizeof(bt_bdname_t), bdname.name);

  if (get_remote_device_property(addr, &prop_name) != BT_STATUS_SUCCESS)
    return false;
  if (strlen((const char*)bdname.name) == 0) return false;

  return interop_match_name(feature, (const char*)bdname.name);
}

bool interop_match_manufacturer(const interop_feature_t feature,
                                uint16_t manufacturer) {
  return (interop_database_match_manufacturer(feature, manufacturer));
}

bool interop_match_vendor_product_ids(const interop_feature_t feature,
                                      uint16_t vendor_id, uint16_t product_id) {
  return interop_database_match_vndr_prdt(feature, vendor_id, product_id);
}

bool interop_match_addr_get_max_lat(const interop_feature_t feature,
                                    const RawAddress* addr, uint16_t* max_lat) {
  return interop_database_match_addr_get_max_lat(feature, addr, max_lat);
}

void interop_database_add(const uint16_t feature, const RawAddress* addr,
                          size_t length) {
  CHECK(addr);
  CHECK(length > 0);
  CHECK(length < sizeof(RawAddress));
  interop_database_add_addr(feature, addr, length);
}

void interop_database_clear() {
  LOG_DEBUG("interop_is_initialized: %d interop_list: %p",
            interop_is_initialized, interop_list);

  if (interop_is_initialized && interop_list) {
    for (int feature = BEGINNING_OF_INTEROP_LIST;
         feature != END_OF_INTEROP_LIST; feature++) {
      interop_database_remove_feature((interop_feature_t)feature);
    }
  }
}

static void interop_init_feature_name_id_map() {
  LOG_DEBUG("");

  feature_name_id_map.clear();

  int feature;

  for (feature = BEGINNING_OF_INTEROP_LIST; feature < END_OF_INTEROP_LIST;
       feature++) {
    const char* feature_name =
        interop_feature_string_((interop_feature_t)feature);
    if (!strcmp(UNKNOWN_INTEROP_FEATURE, feature_name)) continue;

    feature_name_id_map.insert({feature_name, feature});
  }
}

// Module life-cycle functions
static future_t* interop_init(void) {
  interop_init_feature_name_id_map();

  interop_lazy_init_();
  interop_is_initialized = true;
  return future_new_immediate(FUTURE_SUCCESS);
}

static future_t* interop_clean_up(void) {
  pthread_mutex_lock(&interop_list_lock);
  list_free(interop_list);
  interop_list = NULL;
  list_free(media_player_list);
  media_player_list = NULL;
  interop_is_initialized = false;
  pthread_mutex_unlock(&interop_list_lock);
  pthread_mutex_destroy(&interop_list_lock);
  interop_config_cleanup();

  return future_new_immediate(FUTURE_SUCCESS);
}

EXPORT_SYMBOL module_t interop_module = {
    .name = INTEROP_MODULE,
    .init = interop_init,
    .start_up = NULL,
    .shut_down = NULL,
    .clean_up = interop_clean_up,
    .dependencies = {NULL},
};

// Local functions

static const char* interop_feature_string_(const interop_feature_t feature) {
  switch (feature) {
    CASE_RETURN_STR(INTEROP_DISABLE_LE_SECURE_CONNECTIONS)
    CASE_RETURN_STR(INTEROP_AUTO_RETRY_PAIRING)
    CASE_RETURN_STR(INTEROP_DISABLE_ABSOLUTE_VOLUME)
    CASE_RETURN_STR(INTEROP_DISABLE_AUTO_PAIRING)
    CASE_RETURN_STR(INTEROP_KEYBOARD_REQUIRES_FIXED_PIN)
    CASE_RETURN_STR(INTEROP_2MBPS_LINK_ONLY)
    CASE_RETURN_STR(INTEROP_HID_PREF_CONN_SUP_TIMEOUT_3S)
    CASE_RETURN_STR(INTEROP_GATTC_NO_SERVICE_CHANGED_IND)
    CASE_RETURN_STR(INTEROP_DISABLE_SDP_AFTER_PAIRING)
    CASE_RETURN_STR(INTEROP_DISABLE_AUTH_FOR_HID_POINTING)
    CASE_RETURN_STR(INTEROP_REMOVE_HID_DIG_DESCRIPTOR)
    CASE_RETURN_STR(INTEROP_DISABLE_SNIFF_DURING_SCO)
    CASE_RETURN_STR(INTEROP_INCREASE_AG_CONN_TIMEOUT)
    CASE_RETURN_STR(INTEROP_DISABLE_LE_CONN_PREFERRED_PARAMS)
    CASE_RETURN_STR(INTEROP_DISABLE_AAC_CODEC)
    CASE_RETURN_STR(INTEROP_DISABLE_AAC_VBR_CODEC)
    CASE_RETURN_STR(INTEROP_DYNAMIC_ROLE_SWITCH)
    CASE_RETURN_STR(INTEROP_DISABLE_ROLE_SWITCH)
    CASE_RETURN_STR(INTEROP_DISABLE_ROLE_SWITCH_POLICY)
    CASE_RETURN_STR(INTEROP_HFP_1_7_DENYLIST)
    CASE_RETURN_STR(INTEROP_ADV_PBAP_VER_1_1)
    CASE_RETURN_STR(INTEROP_UPDATE_HID_SSR_MAX_LAT)
    CASE_RETURN_STR(INTEROP_DISABLE_AVDTP_RECONFIGURE)
    CASE_RETURN_STR(INTEROP_DISABLE_HF_INDICATOR)
    CASE_RETURN_STR(INTEROP_DISABLE_LE_CONN_UPDATES)
    CASE_RETURN_STR(INTEROP_DELAY_SCO_FOR_MT_CALL)
    CASE_RETURN_STR(INTEROP_DISABLE_CODEC_NEGOTIATION)
    CASE_RETURN_STR(INTEROP_DISABLE_PLAYER_APPLICATION_SETTING_CMDS)
    CASE_RETURN_STR(INTEROP_ENABLE_AAC_CODEC)
    CASE_RETURN_STR(INTEROP_DISABLE_CONNECTION_AFTER_COLLISION)
    CASE_RETURN_STR(INTEROP_AVRCP_BROWSE_OPEN_CHANNEL_COLLISION)
    CASE_RETURN_STR(INTEROP_ADV_PBAP_VER_1_2)
    CASE_RETURN_STR(INTEROP_DISABLE_PCE_SDP_AFTER_PAIRING)
    CASE_RETURN_STR(INTEROP_DISABLE_SNIFF_LINK_DURING_SCO)
    CASE_RETURN_STR(INTEROP_DISABLE_SNIFF_DURING_CALL)
    CASE_RETURN_STR(INTEROP_HID_HOST_LIMIT_SNIFF_INTERVAL)
    CASE_RETURN_STR(INTEROP_DISABLE_REFRESH_ACCEPT_SIG_TIMER)
    CASE_RETURN_STR(INTEROP_BROWSE_PLAYER_ALLOW_LIST)
    CASE_RETURN_STR(INTEROP_SKIP_INCOMING_STATE)
    CASE_RETURN_STR(INTEROP_NOT_UPDATE_AVRCP_PAUSED_TO_REMOTE)
    CASE_RETURN_STR(INTEROP_PHONE_POLICY_INCREASED_DELAY_CONNECT_OTHER_PROFILES)
    CASE_RETURN_STR(INTEROP_PHONE_POLICY_REDUCED_DELAY_CONNECT_OTHER_PROFILES)
    CASE_RETURN_STR(INTEROP_HFP_FAKE_INCOMING_CALL_INDICATOR)
    CASE_RETURN_STR(INTEROP_HFP_SEND_CALL_INDICATORS_BACK_TO_BACK)
    CASE_RETURN_STR(INTEROP_SETUP_SCO_WITH_NO_DELAY_AFTER_SLC_DURING_CALL)
    CASE_RETURN_STR(INTEROP_ENABLE_PREFERRED_CONN_PARAMETER)
    CASE_RETURN_STR(INTEROP_RETRY_SCO_AFTER_REMOTE_REJECT_SCO)
    CASE_RETURN_STR(INTEROP_DELAY_SCO_FOR_MO_CALL)
    CASE_RETURN_STR(INTEROP_CHANGE_HID_VID_PID)
    CASE_RETURN_STR(END_OF_INTEROP_LIST)
    CASE_RETURN_STR(INTEROP_HFP_1_8_DENYLIST)
    CASE_RETURN_STR(INTEROP_DISABLE_ROLE_SWITCH_DURING_CONNECTION)
    CASE_RETURN_STR(INTEROP_DISABLE_NAME_REQUEST)
    CASE_RETURN_STR(INTEROP_AVRCP_1_4_ONLY)
    CASE_RETURN_STR(INTEROP_DISABLE_SNIFF)
    CASE_RETURN_STR(INTEROP_DISABLE_AVDTP_SUSPEND)
    CASE_RETURN_STR(INTEROP_SLC_SKIP_BIND_COMMAND)
    CASE_RETURN_STR(INTEROP_AVRCP_1_3_ONLY)
    CASE_RETURN_STR(INTEROP_DISABLE_ROBUST_CACHING);
    CASE_RETURN_STR(INTEROP_HFP_1_7_ALLOWLIST);
    CASE_RETURN_STR(INTEROP_HFP_1_9_ALLOWLIST);
    CASE_RETURN_STR(INTEROP_IGNORE_DISC_BEFORE_SIGNALLING_TIMEOUT);
    CASE_RETURN_STR(INTEROP_SUSPEND_ATT_TRAFFIC_DURING_PAIRING);
    CASE_RETURN_STR(INTEROP_INSERT_CALL_WHEN_SCO_START);
    CASE_RETURN_STR(INTEROP_DELAY_AUTH);
  }
  return UNKNOWN_INTEROP_FEATURE;
}

static void interop_free_entry_(void* data) {
  interop_db_entry_t* entry = (interop_db_entry_t*)data;
  osi_free(entry);
}

static void interop_lazy_init_(void) {
  pthread_mutex_init(&interop_list_lock, NULL);
  if (interop_list == NULL) {
    interop_list = list_new(interop_free_entry_);
    load_config();
  }
}

// interop config related functions

static int interop_config_init(void) {
  struct stat sts;
  pthread_mutex_init(&file_lock, NULL);
  pthread_mutex_lock(&file_lock);

  if (!stat(INTEROP_STATIC_FILE_PATH, &sts) && sts.st_size) {
    if (!(config_static = config_new(INTEROP_STATIC_FILE_PATH))) {
      LOG_WARN("unable to load static config file for : %s",
               INTEROP_STATIC_FILE_PATH);
    }
  }
  if (!config_static && !(config_static = config_new_empty())) {
    goto error;
  }

  if (!stat(INTEROP_DYNAMIC_FILE_PATH, &sts) && sts.st_size) {
    if (!(config_dynamic = config_new(INTEROP_DYNAMIC_FILE_PATH))) {
      LOG_WARN("unable to load dynamic config file for : %s",
               INTEROP_DYNAMIC_FILE_PATH);
    }
  }
  if (!config_dynamic && !(config_dynamic = config_new_empty())) {
    goto error;
  }
  pthread_mutex_unlock(&file_lock);
  return 0;

error:
  config_static.reset();
  config_dynamic.reset();
  pthread_mutex_unlock(&file_lock);
  return -1;
}

static void interop_config_flush(void) {
  CHECK(config_dynamic.get() != NULL);

  pthread_mutex_lock(&file_lock);
  config_save(*config_dynamic, INTEROP_DYNAMIC_FILE_PATH);
  pthread_mutex_unlock(&file_lock);
}

static bool interop_config_remove(const std::string& section,
                                  const std::string& key) {
  CHECK(config_dynamic.get() != NULL);

  pthread_mutex_lock(&file_lock);
  bool ret = config_remove_key(config_dynamic.get(), section, key);
  pthread_mutex_unlock(&file_lock);

  return ret;
}

static bool interop_config_remove_section(const std::string& section) {
  CHECK(config_dynamic.get() != NULL);

  pthread_mutex_lock(&file_lock);
  bool ret = config_remove_section(config_dynamic.get(), section);
  pthread_mutex_unlock(&file_lock);

  return ret;
}

static bool interop_config_set_str(const std::string& section,
                                   const std::string& key,
                                   const std::string& value) {
  CHECK(config_dynamic.get() != NULL);

  pthread_mutex_lock(&file_lock);
  config_set_string(config_dynamic.get(), section, key, value);
  pthread_mutex_unlock(&file_lock);

  return true;
}

int interop_feature_name_to_feature_id(const char* feature_name) {
  if (feature_name == NULL) {
    return -1;
  }

  auto it = feature_name_id_map.find(std::string(feature_name));
  if (it == feature_name_id_map.end()) {
    LOG_WARN("feature does not exist: %s", feature_name);
    return -1;
  }

  return it->second;
}

static bool interop_config_add_or_remove(interop_db_entry_t* db_entry,
                                         bool add) {
  bool status = true;
  std::string key;
  std::string value;
  interop_feature_t feature;

  // add it to the config file as well
  switch (db_entry->bl_type) {
    case INTEROP_BL_TYPE_ADDR: {
      interop_addr_entry_t addr_entry = db_entry->entry_type.addr_entry;

      const std::string bdstr = addr_entry.addr.ToColonSepHexString().substr(
          0, addr_entry.length * 3 - 1);

      feature = db_entry->entry_type.addr_entry.feature;
      key.assign(bdstr);
      value.assign(ADDR_BASED);

      break;
    }
    case INTEROP_BL_TYPE_NAME: {
      feature = db_entry->entry_type.name_entry.feature;
      key.assign(db_entry->entry_type.name_entry.name);
      value.assign(NAME_BASED);

      break;
    }
    case INTEROP_BL_TYPE_MANUFACTURE: {
      char m_facturer[KEY_MAX_LENGTH] = {'\0'};
      snprintf(m_facturer, sizeof(m_facturer), "0x%04x",
               db_entry->entry_type.mnfr_entry.manufacturer);

      feature = db_entry->entry_type.mnfr_entry.feature;
      key.assign(m_facturer);
      value.assign(MNFR_BASED);

      break;
    }
    case INTEROP_BL_TYPE_VNDR_PRDT: {
      char m_vnr_pdt[KEY_MAX_LENGTH] = {'\0'};
      snprintf(m_vnr_pdt, sizeof(m_vnr_pdt), "0x%04x-0x%04x",
               db_entry->entry_type.vnr_pdt_entry.vendor_id,
               db_entry->entry_type.vnr_pdt_entry.product_id);

      feature = db_entry->entry_type.vnr_pdt_entry.feature;
      key.assign(m_vnr_pdt);
      value.assign(VNDR_PRDT_BASED);

      break;
    }
    case INTEROP_BL_TYPE_SSR_MAX_LAT: {
      interop_hid_ssr_max_lat_t ssr_entry =
          db_entry->entry_type.ssr_max_lat_entry;
      char m_ssr_max_lat[KEY_MAX_LENGTH] = {'\0'};

      const std::string bdstr =
          ssr_entry.addr.ToColonSepHexString().substr(0, 3 * 3 - 1);

      snprintf(m_ssr_max_lat, sizeof(m_ssr_max_lat), "%s-0x%04x", bdstr.c_str(),
               db_entry->entry_type.ssr_max_lat_entry.max_lat);

      feature = db_entry->entry_type.ssr_max_lat_entry.feature;
      key.assign(m_ssr_max_lat);
      value.assign(SSR_MAX_LAT_BASED);

      break;
    }
    case INTEROP_BL_TYPE_VERSION: {
      char m_version[KEY_MAX_LENGTH] = {'\0'};
      snprintf(m_version, sizeof(m_version), "0x%04x",
               db_entry->entry_type.version_entry.version);

      feature = db_entry->entry_type.version_entry.feature;
      key.assign(m_version);
      value.assign(VERSION_BASED);

      break;
    }
    case INTEROP_BL_TYPE_LMP_VERSION: {
      interop_lmp_version_t lmp_version_entry =
          db_entry->entry_type.lmp_version_entry;
      char m_lmp_version[KEY_MAX_LENGTH] = {'\0'};
      const std::string bdstr =
          lmp_version_entry.addr.ToColonSepHexString().substr(0, 3 * 3 - 1);

      snprintf(m_lmp_version, sizeof(m_lmp_version), "%s-0x%02x-0x%04x",
               bdstr.c_str(), db_entry->entry_type.lmp_version_entry.lmp_ver,
               db_entry->entry_type.lmp_version_entry.lmp_sub_ver);

      feature = db_entry->entry_type.lmp_version_entry.feature;
      key.assign(m_lmp_version);
      value.assign(LMP_VERSION_BASED);

      break;
    }
    default:
      LOG_ERROR("bl_type: %d not handled", db_entry->bl_type);
      status = false;
      break;
  }

  if (status) {
    if (add) {
      interop_config_set_str(interop_feature_string_(feature), key, value);
    } else {
      interop_config_remove(interop_feature_string_(feature), key);
    }
    interop_config_flush();
  }

  return status;
}

static void interop_database_add_(interop_db_entry_t* db_entry, bool persist) {
  interop_db_entry_t* ret_entry = NULL;
  bool match_found =
      interop_database_match(db_entry, &ret_entry,
                             (interop_entry_type)(INTEROP_ENTRY_TYPE_STATIC |
                                                  INTEROP_ENTRY_TYPE_DYNAMIC));

  if (match_found) {
    // return as the entry is already present
    LOG_DEBUG("Entry is already present in the list");
    return;
  }

  pthread_mutex_lock(&interop_list_lock);

  if (interop_list) {
    list_append(interop_list, db_entry);
  }

  pthread_mutex_unlock(&interop_list_lock);

  if (!persist) {
    // return if the persist option is not set
    return;
  }

  interop_config_add_or_remove(db_entry, true);
}

static bool interop_database_match(interop_db_entry_t* entry,
                                   interop_db_entry_t** ret_entry,
                                   interop_entry_type entry_type) {
  CHECK(entry);
  bool found = false;
  pthread_mutex_lock(&interop_list_lock);
  if (interop_list == NULL || list_length(interop_list) == 0) {
    pthread_mutex_unlock(&interop_list_lock);
    return false;
  }

  const list_node_t* node = list_begin(interop_list);

  while (node != list_end(interop_list)) {
    interop_db_entry_t* db_entry = (interop_db_entry_t*)list_node(node);
    CHECK(db_entry);

    if (entry->bl_type != db_entry->bl_type) {
      node = list_next(node);
      continue;
    }

    if ((entry_type == INTEROP_ENTRY_TYPE_STATIC) ||
        (entry_type == INTEROP_ENTRY_TYPE_DYNAMIC)) {
      if (entry->bl_entry_type != db_entry->bl_entry_type) {
        node = list_next(node);
        continue;
      }
    }

    switch (db_entry->bl_type) {
      case INTEROP_BL_TYPE_ADDR: {
        interop_addr_entry_t* src = &entry->entry_type.addr_entry;
        interop_addr_entry_t* cur = &db_entry->entry_type.addr_entry;
        if ((src->feature == cur->feature) &&
            (!memcmp(&src->addr, &cur->addr, cur->length))) {
          /* cur len is used to remove src entry from config file, when
           * interop_database_remove_addr is called. */
          src->length = cur->length;
          found = true;
        }
        break;
      }
      case INTEROP_BL_TYPE_NAME: {
        interop_name_entry_t* src = &entry->entry_type.name_entry;
        interop_name_entry_t* cur = &db_entry->entry_type.name_entry;

        if ((src->feature == cur->feature) &&
            (strcasestr(src->name, cur->name) == src->name)) {
          found = true;
        }
        break;
      }
      case INTEROP_BL_TYPE_MANUFACTURE: {
        interop_manufacturer_t* src = &entry->entry_type.mnfr_entry;
        interop_manufacturer_t* cur = &db_entry->entry_type.mnfr_entry;

        if (src->feature == cur->feature &&
            src->manufacturer == cur->manufacturer) {
          found = true;
        }
        break;
      }
      case INTEROP_BL_TYPE_VNDR_PRDT: {
        interop_hid_multitouch_t* src = &entry->entry_type.vnr_pdt_entry;
        interop_hid_multitouch_t* cur = &db_entry->entry_type.vnr_pdt_entry;

        if ((src->feature == cur->feature) &&
            (src->vendor_id == cur->vendor_id) &&
            (src->product_id == cur->product_id)) {
          found = true;
        }
        break;
      }
      case INTEROP_BL_TYPE_SSR_MAX_LAT: {
        interop_hid_ssr_max_lat_t* src = &entry->entry_type.ssr_max_lat_entry;
        interop_hid_ssr_max_lat_t* cur =
            &db_entry->entry_type.ssr_max_lat_entry;

        if ((src->feature == cur->feature) &&
            !memcmp(&src->addr, &cur->addr, 3)) {
          found = true;
        }
        break;
      }
      case INTEROP_BL_TYPE_VERSION: {
        interop_version_t* src = &entry->entry_type.version_entry;
        interop_version_t* cur = &db_entry->entry_type.version_entry;

        if ((src->feature == cur->feature) && (src->version == cur->version)) {
          found = true;
        }
        break;
      }
      case INTEROP_BL_TYPE_LMP_VERSION: {
        interop_lmp_version_t* src = &entry->entry_type.lmp_version_entry;
        interop_lmp_version_t* cur = &db_entry->entry_type.lmp_version_entry;

        if ((src->feature == cur->feature) &&
            (!memcmp(&src->addr, &cur->addr, 3))) {
          found = true;
        }
        break;
      }
      case INTEROP_BL_TYPE_ADDR_RANGE: {
        interop_addr_range_entry_t* src = &entry->entry_type.addr_range_entry;
        interop_addr_range_entry_t* cur =
            &db_entry->entry_type.addr_range_entry;

        // src->addr_start has the actual address, which need to be searched in
        // the range
        if ((src->feature == cur->feature) &&
            (src->addr_start >= cur->addr_start) &&
            (src->addr_start <= cur->addr_end)) {
          found = true;
        }
        break;
      }
      default:
        LOG_ERROR("bl_type: %d not handled", db_entry->bl_type);
        break;
    }

    if (found && ret_entry) {
      *ret_entry = db_entry;
      break;
    }
    node = list_next(node);
  }
  pthread_mutex_unlock(&interop_list_lock);
  return found;
}

static bool interop_database_remove_(interop_db_entry_t* entry) {
  interop_db_entry_t* ret_entry = NULL;

  if (!interop_database_match(
          entry, &ret_entry,
          (interop_entry_type)(INTEROP_ENTRY_TYPE_DYNAMIC))) {
    LOG_ERROR("Entry not found in the list");
    return false;
  }

  // first remove it from linked list
  pthread_mutex_lock(&interop_list_lock);
  list_remove(interop_list, (void*)ret_entry);
  pthread_mutex_unlock(&interop_list_lock);

  return interop_config_add_or_remove(entry, false);
}

static char* trim(char* str) {
  while (isspace(*str)) ++str;

  if (!*str) return str;

  char* end_str = str + strlen(str) - 1;
  while (end_str > str && isspace(*end_str)) --end_str;

  end_str[1] = '\0';
  return str;
}

bool token_to_ul(char* token, uint16_t* ul) {
  char* e;
  bool ret_value = false;

  token = trim(token);
  errno = 0;
  *ul = (uint16_t)strtoul(token, &e, 16);
  if ((e != NULL) && errno != EINVAL && errno != ERANGE) ret_value = true;
  return ret_value;
}

static bool get_vendor_product_id(char* vendorstr, uint16_t* vendor,
                                  uint16_t* product) {
  char* token;
  char* saveptr = NULL;
  bool ret_value = false;

  if ((token = strtok_r(vendorstr, VENDOR_VALUE_SEPARATOR, &saveptr)) != NULL) {
    ret_value = token_to_ul(token, vendor);
  }

  if (ret_value &&
      (token = strtok_r(NULL, VENDOR_VALUE_SEPARATOR, &saveptr)) != NULL) {
    ret_value = token_to_ul(token, product);
  }
  return ret_value;
}

static bool get_addr_maxlat(char* str, char* bdaddrstr, uint16_t* max_lat) {
  char* token;
  char* saveptr = NULL;
  bool ret_value = false;

  if ((token = strtok_r(str, VENDOR_VALUE_SEPARATOR, &saveptr)) != NULL) {
    trim(token);
    strlcpy(bdaddrstr, token, KEY_MAX_LENGTH);
  } else {
    return false;
  }

  if ((token = strtok_r(NULL, VENDOR_VALUE_SEPARATOR, &saveptr)) != NULL) {
    ret_value = token_to_ul(token, max_lat);
  }
  return ret_value;
}

static bool get_addr_range(char* str, RawAddress* addr_start,
                           RawAddress* addr_end) {
  char* token;
  char* saveptr = NULL;
  bool ret_value = false;
  char addr_start_str[18] = {'\0'};
  char addr_end_str[18] = {'\0'};

  if ((token = strtok_r(str, VENDOR_VALUE_SEPARATOR, &saveptr)) != NULL) {
    trim(token);
    strlcpy(addr_start_str, token, 18);
    if (!RawAddress::FromString(addr_start_str, *addr_start)) return false;
  } else {
    return false;
  }

  if ((token = strtok_r(NULL, VENDOR_VALUE_SEPARATOR, &saveptr)) != NULL) {
    trim(token);
    strlcpy(addr_end_str, token, 18);
    if (RawAddress::FromString(addr_end_str, *addr_end)) ret_value = true;
  }
  return ret_value;
}

static bool get_addr_lmp_ver(char* str, char* bdaddrstr, uint8_t* lmp_ver,
                             uint16_t* lmp_sub_ver) {
  char* token;
  char* saveptr = NULL;
  char* e;

  if ((token = strtok_r(str, VENDOR_VALUE_SEPARATOR, &saveptr)) != NULL) {
    trim(token);
    strlcpy(bdaddrstr, token, KEY_MAX_LENGTH);
  } else {
    return false;
  }

  if ((token = strtok_r(NULL, VENDOR_VALUE_SEPARATOR, &saveptr)) != NULL) {
    trim(token);
    errno = 0;
    *lmp_ver = (uint8_t)strtoul(token, &e, 16);
    if (errno == EINVAL || errno == ERANGE) return false;
  } else {
    return false;
  }

  if ((token = strtok_r(NULL, VENDOR_VALUE_SEPARATOR, &saveptr)) != NULL) {
    return token_to_ul(token, lmp_sub_ver);
  }
  return false;
}

static bool load_to_database(int feature, const char* key, const char* value,
                             interop_entry_type entry_type) {
  if (!strncasecmp(value, ADDR_BASED, strlen(ADDR_BASED))) {
    RawAddress addr;
    int len = 0;

    len = (strlen(key) + 1) / 3;
    if (len < 3 || len > 4) {
      LOG_WARN("Ignoring as invalid entry for Address %s", key);
      return false;
    }

    std::string bdstr(key);
    std::string append_str(":00");
    for (int i = 6; i > len; i--) bdstr.append(append_str);

    if (!RawAddress::FromString(bdstr, addr)) {
      LOG_WARN(
          "key %s or Bluetooth Address %s is invalid, not added to interop "
          "list",
          key, ADDRESS_TO_LOGGABLE_CSTR(addr));
      return false;
    }

    interop_db_entry_t* entry =
        (interop_db_entry_t*)osi_calloc(sizeof(interop_db_entry_t));
    entry->bl_type = INTEROP_BL_TYPE_ADDR;
    entry->bl_entry_type = entry_type;
    entry->entry_type.addr_entry.addr = addr;
    entry->entry_type.addr_entry.feature = (interop_feature_t)feature;
    entry->entry_type.addr_entry.length = len;
    interop_database_add_(entry, false);

  } else if (!strncasecmp(value, NAME_BASED, strlen(NAME_BASED))) {
    if (strlen(key) > KEY_MAX_LENGTH - 1) {
      LOG_WARN("ignoring %s due to invalid length", key);
      return false;
    }
    interop_db_entry_t* entry =
        (interop_db_entry_t*)osi_calloc(sizeof(interop_db_entry_t));
    entry->bl_type = INTEROP_BL_TYPE_NAME;
    entry->bl_entry_type = entry_type;
    strlcpy(entry->entry_type.name_entry.name, key,
            sizeof(entry->entry_type.name_entry.name));
    entry->entry_type.name_entry.feature = (interop_feature_t)feature;
    entry->entry_type.name_entry.length = strlen(key);
    interop_database_add_(entry, false);

  } else if (!strncasecmp(value, MNFR_BASED, strlen(MNFR_BASED))) {
    uint16_t manufacturer;

    if (strlen(key) != VALID_MNFR_STR_LEN) {
      LOG_WARN("ignoring %s due to invalid Manufacturer id in config file",
               key);
      return false;
    }

    if (token_to_ul((char*)key, &manufacturer) == false) return false;

    interop_db_entry_t* entry =
        (interop_db_entry_t*)osi_calloc(sizeof(interop_db_entry_t));
    entry->bl_type = INTEROP_BL_TYPE_MANUFACTURE;
    entry->bl_entry_type = entry_type;
    entry->entry_type.mnfr_entry.feature = (interop_feature_t)feature;
    entry->entry_type.mnfr_entry.manufacturer = manufacturer;
    interop_database_add_(entry, false);

  } else if (!strncasecmp(value, VNDR_PRDT_BASED, strlen(VNDR_PRDT_BASED))) {
    uint16_t vendor_id;
    uint16_t product_id = 0;
    char tmp_key[VALID_VNDR_PRDT_LEN + 1] = {'\0'};

    if (strlen(key) != VALID_VNDR_PRDT_LEN) {
      LOG_WARN("ignoring %s due to invalid vendor/product id in config file",
               key);
      return false;
    }

    strlcpy(tmp_key, key, VALID_VNDR_PRDT_LEN + 1);
    if (!get_vendor_product_id(tmp_key, &vendor_id, &product_id)) {
      LOG_WARN("Error in parsing vendor/product id %s", key);
      return false;
    }

    interop_db_entry_t* entry =
        (interop_db_entry_t*)osi_calloc(sizeof(interop_db_entry_t));
    entry->bl_type = INTEROP_BL_TYPE_VNDR_PRDT;
    entry->bl_entry_type = entry_type;
    entry->entry_type.vnr_pdt_entry.feature = (interop_feature_t)feature;
    entry->entry_type.vnr_pdt_entry.vendor_id = vendor_id;
    entry->entry_type.vnr_pdt_entry.product_id = product_id;
    interop_database_add_(entry, false);
  } else if (!strncasecmp(value, SSR_MAX_LAT_BASED,
                          strlen(SSR_MAX_LAT_BASED))) {
    uint16_t max_lat;
    char tmp_key[KEY_MAX_LENGTH] = {'\0'};
    char bdaddr_str[KEY_MAX_LENGTH] = {'\0'};

    if (strlen(key) != VALID_SSR_LAT_LEN) {
      LOG_WARN("ignoring %s due to invalid key for ssr max lat in config file",
               key);
      return false;
    }

    strlcpy(tmp_key, key, KEY_MAX_LENGTH);
    if (!get_addr_maxlat(tmp_key, bdaddr_str, &max_lat)) {
      LOG_WARN("Error in parsing address and max_lat %s", key);
      return false;
    }

    int len = 0;

    len = (strlen(bdaddr_str) + 1) / 3;
    if (len != 3) {
      LOG_WARN("Ignoring as invalid entry for Address %s", bdaddr_str);
      return false;
    }

    std::string bdstr(bdaddr_str);
    std::string append_str(":00:00:00");
    RawAddress addr;

    bdstr.append(append_str);

    if (!RawAddress::FromString(bdstr, addr)) {
      LOG_WARN(
          "key %s or Bluetooth Address %s is invalid, not added to interop "
          "list",
          key, ADDRESS_TO_LOGGABLE_CSTR(addr));
      return false;
    }

    interop_db_entry_t* entry =
        (interop_db_entry_t*)osi_calloc(sizeof(interop_db_entry_t));
    entry->bl_type = INTEROP_BL_TYPE_SSR_MAX_LAT;
    entry->bl_entry_type = entry_type;
    entry->entry_type.ssr_max_lat_entry.feature = (interop_feature_t)feature;
    entry->entry_type.ssr_max_lat_entry.addr = addr;
    entry->entry_type.ssr_max_lat_entry.max_lat = max_lat;
    interop_database_add_(entry, false);
  } else if (!strncasecmp(value, VERSION_BASED, strlen(VERSION_BASED))) {
    uint16_t version;

    if (strlen(key) != VALID_VERSION_LEN) {
      LOG_WARN("ignoring %s due to invalid version in config file", key);
      return false;
    }

    if (token_to_ul((char*)key, &version) == false) return false;

    interop_db_entry_t* entry =
        (interop_db_entry_t*)osi_calloc(sizeof(interop_db_entry_t));
    entry->bl_type = INTEROP_BL_TYPE_VERSION;
    entry->bl_entry_type = entry_type;
    entry->entry_type.version_entry.feature = (interop_feature_t)feature;
    entry->entry_type.version_entry.version = version;
    interop_database_add_(entry, false);
  } else if (!strncasecmp(value, LMP_VERSION_BASED,
                          strlen(LMP_VERSION_BASED))) {
    uint8_t lmp_ver;
    uint16_t lmp_sub_ver;
    char tmp_key[KEY_MAX_LENGTH] = {'\0'};
    char bdaddr_str[KEY_MAX_LENGTH] = {'\0'};

    if (strlen(key) != VALID_LMP_VERSION_LEN) {
      LOG_WARN("ignoring %s due to invalid key for lmp ver in config file",
               key);
      return false;
    }

    strlcpy(tmp_key, key, KEY_MAX_LENGTH);
    if (!get_addr_lmp_ver(tmp_key, bdaddr_str, &lmp_ver, &lmp_sub_ver)) {
      LOG_WARN("Error in parsing address and lmp_ver %s", key);
      return false;
    }

    int len = 0;

    len = (strlen(bdaddr_str) + 1) / 3;
    if (len != 3) {
      LOG_WARN("Ignoring as invalid entry for Address %s", bdaddr_str);
      return false;
    }

    std::string bdstr(key);
    std::string append_str(":00:00:00");
    RawAddress addr;

    bdstr.append(append_str);

    if (!RawAddress::FromString(bdstr, addr)) {
      LOG_WARN(
          "key %s or Bluetooth Address %s is invalid, not added to interop "
          "list",
          key, ADDRESS_TO_LOGGABLE_CSTR(addr));
      return false;
    }

    interop_db_entry_t* entry =
        (interop_db_entry_t*)osi_calloc(sizeof(interop_db_entry_t));
    entry->bl_type = INTEROP_BL_TYPE_LMP_VERSION;
    entry->bl_entry_type = entry_type;
    entry->entry_type.lmp_version_entry.feature = (interop_feature_t)feature;
    entry->entry_type.lmp_version_entry.addr = addr;
    entry->entry_type.lmp_version_entry.lmp_ver = lmp_ver;
    entry->entry_type.lmp_version_entry.lmp_sub_ver = lmp_sub_ver;
    interop_database_add_(entry, false);
  } else if (!strncasecmp(value, ADDR_RANGE_BASED, strlen(ADDR_RANGE_BASED))) {
    RawAddress addr_start;
    RawAddress addr_end;
    char tmp_key[KEY_MAX_LENGTH] = {'\0'};

    if (strlen(key) != VALID_ADDR_RANGE_LEN) {
      LOG_WARN("Ignoring as invalid entry for Address range %s", key);
      return false;
    }

    strlcpy(tmp_key, key, VALID_ADDR_RANGE_LEN + 1);
    if (!get_addr_range(tmp_key, &addr_start, &addr_end)) {
      LOG_WARN("key: %s addr_start %s or addr end  %s is added to interop list",
               key, ADDRESS_TO_LOGGABLE_CSTR(addr_start),
               ADDRESS_TO_LOGGABLE_CSTR(addr_end));

      return false;
    }

    interop_db_entry_t* entry =
        (interop_db_entry_t*)osi_calloc(sizeof(interop_db_entry_t));
    entry->bl_type = INTEROP_BL_TYPE_ADDR_RANGE;
    entry->bl_entry_type = entry_type;
    entry->entry_type.addr_range_entry.addr_start = addr_start;
    entry->entry_type.addr_range_entry.addr_end = addr_end;
    entry->entry_type.addr_range_entry.feature = (interop_feature_t)feature;
    interop_database_add_(entry, false);
  }

  LOG_VERBOSE("feature:: %d, key :: %s, value :: %s", feature, key, value);
  return true;
}

static void load_config() {
  int init_status = interop_config_init();

  if (init_status == -1) {
    LOG_ERROR("Error in initializing interop static config file");
    return;
  }

  pthread_mutex_lock(&file_lock);
  for (const section_t& sec : config_static.get()->sections) {
    int feature = -1;
    if ((feature = interop_feature_name_to_feature_id(sec.name.c_str())) !=
        -1) {
      for (const entry_t& entry : sec.entries) {
        load_to_database(feature, entry.key.c_str(), entry.value.c_str(),
                         INTEROP_ENTRY_TYPE_STATIC);
      }
    }
  }
  interop_database_save_allowlisted_media_players_list(config_static.get());
  // We no longer need the static config file
  config_static.reset();

  for (const section_t& sec : config_dynamic.get()->sections) {
    int feature = -1;
    if ((feature = interop_feature_name_to_feature_id(sec.name.c_str())) !=
        -1) {
      for (const entry_t& entry : sec.entries) {
        load_to_database(feature, entry.key.c_str(), entry.value.c_str(),
                         INTEROP_ENTRY_TYPE_DYNAMIC);
      }
    }
  }
  pthread_mutex_unlock(&file_lock);
}

static void interop_config_cleanup(void) {
  interop_config_flush();

  pthread_mutex_lock(&file_lock);
  config_static.reset();
  config_dynamic.reset();
  pthread_mutex_unlock(&file_lock);
  pthread_mutex_destroy(&file_lock);
}

void interop_database_add_addr(const uint16_t feature, const RawAddress* addr,
                               size_t length) {
  CHECK(addr);
  CHECK(length > 0);
  CHECK(length < sizeof(RawAddress));

  interop_db_entry_t* entry =
      (interop_db_entry_t*)osi_calloc(sizeof(interop_db_entry_t));
  entry->bl_type = INTEROP_BL_TYPE_ADDR;
  entry->bl_entry_type = INTEROP_ENTRY_TYPE_DYNAMIC;
  memcpy(&entry->entry_type.addr_entry.addr, addr, length);
  entry->entry_type.addr_entry.feature = (interop_feature_t)feature;
  entry->entry_type.addr_entry.length = length;
  interop_database_add_(entry, true);
}

void interop_database_add_name(const uint16_t feature, const char* name) {
  CHECK(name);
  const size_t name_length = strlen(name);
  CHECK(name_length < KEY_MAX_LENGTH);

  interop_db_entry_t* entry =
      (interop_db_entry_t*)osi_calloc(sizeof(interop_db_entry_t));
  entry->bl_type = INTEROP_BL_TYPE_NAME;
  entry->bl_entry_type = INTEROP_ENTRY_TYPE_DYNAMIC;
  strlcpy(entry->entry_type.name_entry.name, name,
          sizeof(entry->entry_type.name_entry.name));
  entry->entry_type.name_entry.feature = (interop_feature_t)feature;
  entry->entry_type.name_entry.length = name_length;
  interop_database_add_(entry, true);
}

void interop_database_add_manufacturer(const interop_feature_t feature,
                                       uint16_t manufacturer) {
  interop_db_entry_t* entry =
      (interop_db_entry_t*)osi_calloc(sizeof(interop_db_entry_t));
  entry->bl_type = INTEROP_BL_TYPE_MANUFACTURE;
  entry->bl_entry_type = INTEROP_ENTRY_TYPE_DYNAMIC;
  entry->entry_type.mnfr_entry.feature = feature;
  entry->entry_type.mnfr_entry.manufacturer = manufacturer;
  interop_database_add_(entry, true);
}

void interop_database_add_vndr_prdt(const interop_feature_t feature,
                                    uint16_t vendor_id, uint16_t product_id) {
  interop_db_entry_t* entry =
      (interop_db_entry_t*)osi_calloc(sizeof(interop_db_entry_t));
  entry->bl_type = INTEROP_BL_TYPE_VNDR_PRDT;
  entry->bl_entry_type = INTEROP_ENTRY_TYPE_DYNAMIC;
  entry->entry_type.vnr_pdt_entry.feature = (interop_feature_t)feature;
  entry->entry_type.vnr_pdt_entry.vendor_id = vendor_id;
  entry->entry_type.vnr_pdt_entry.product_id = product_id;
  interop_database_add_(entry, true);
}

void interop_database_add_addr_max_lat(const interop_feature_t feature,
                                       const RawAddress* addr,
                                       uint16_t max_lat) {
  CHECK(addr);

  interop_db_entry_t* entry =
      (interop_db_entry_t*)osi_calloc(sizeof(interop_db_entry_t));
  entry->bl_type = INTEROP_BL_TYPE_SSR_MAX_LAT;
  entry->bl_entry_type = INTEROP_ENTRY_TYPE_DYNAMIC;
  entry->entry_type.ssr_max_lat_entry.addr = *addr;
  entry->entry_type.ssr_max_lat_entry.feature = feature;
  entry->entry_type.ssr_max_lat_entry.max_lat = max_lat;
  interop_database_add_(entry, true);
}

void interop_database_add_version(const interop_feature_t feature,
                                  uint16_t version) {
  interop_db_entry_t* entry =
      (interop_db_entry_t*)osi_calloc(sizeof(interop_db_entry_t));
  entry->bl_type = INTEROP_BL_TYPE_VERSION;
  entry->bl_entry_type = INTEROP_ENTRY_TYPE_DYNAMIC;
  entry->entry_type.version_entry.feature = (interop_feature_t)feature;
  entry->entry_type.version_entry.version = version;
  interop_database_add_(entry, true);
}

void interop_database_add_addr_lmp_version(const interop_feature_t feature,
                                           const RawAddress* addr,
                                           uint8_t lmp_ver,
                                           uint16_t lmp_sub_ver) {
  CHECK(addr);

  interop_db_entry_t* entry =
      (interop_db_entry_t*)osi_calloc(sizeof(interop_db_entry_t));
  entry->bl_type = INTEROP_BL_TYPE_LMP_VERSION;
  entry->bl_entry_type = INTEROP_ENTRY_TYPE_DYNAMIC;
  entry->entry_type.lmp_version_entry.addr = *addr;
  entry->entry_type.lmp_version_entry.feature = feature;
  entry->entry_type.lmp_version_entry.lmp_ver = lmp_ver;
  entry->entry_type.lmp_version_entry.lmp_sub_ver = lmp_sub_ver;
  interop_database_add_(entry, true);
}

bool interop_database_match_manufacturer(const interop_feature_t feature,
                                         uint16_t manufacturer) {
  interop_db_entry_t entry;

  entry.bl_type = INTEROP_BL_TYPE_MANUFACTURE;
  entry.entry_type.mnfr_entry.feature = feature;
  entry.entry_type.mnfr_entry.manufacturer = manufacturer;

  if (interop_database_match(
          &entry, NULL,
          (interop_entry_type)(INTEROP_ENTRY_TYPE_STATIC |
                               INTEROP_ENTRY_TYPE_DYNAMIC))) {
    LOG_WARN(
        "Device with manufacturer id: %d is a match for interop workaround %s",
        manufacturer, interop_feature_string_(feature));
    return true;
  }

  return false;
}

bool interop_database_match_name(const interop_feature_t feature,
                                 const char* name) {
  char trim_name[KEY_MAX_LENGTH] = {'\0'};
  CHECK(name);

  strlcpy(trim_name, name, KEY_MAX_LENGTH);
  interop_db_entry_t entry;

  entry.bl_type = INTEROP_BL_TYPE_NAME;
  strlcpy(entry.entry_type.name_entry.name, trim(trim_name), KEY_MAX_LENGTH);
  entry.entry_type.name_entry.feature = (interop_feature_t)feature;
  entry.entry_type.name_entry.length = strlen(entry.entry_type.name_entry.name);

  if (interop_database_match(
          &entry, NULL,
          (interop_entry_type)(INTEROP_ENTRY_TYPE_STATIC |
                               INTEROP_ENTRY_TYPE_DYNAMIC))) {
    LOG_WARN("Device with name: %s is a match for interop workaround %s", name,
             interop_feature_string_(feature));
    return true;
  }

  return false;
}

bool interop_database_match_addr(const interop_feature_t feature,
                                 const RawAddress* addr) {
  CHECK(addr);

  interop_db_entry_t entry;

  entry.bl_type = INTEROP_BL_TYPE_ADDR;
  entry.entry_type.addr_entry.addr = *addr;
  entry.entry_type.addr_entry.feature = (interop_feature_t)feature;
  entry.entry_type.addr_entry.length = sizeof(RawAddress);

  if (interop_database_match(
          &entry, NULL,
          (interop_entry_type)(INTEROP_ENTRY_TYPE_STATIC |
                               INTEROP_ENTRY_TYPE_DYNAMIC))) {
    LOG_WARN("Device %s is a match for interop workaround %s.",
             ADDRESS_TO_LOGGABLE_CSTR(*addr), interop_feature_string_(feature));
    return true;
  }

  entry.bl_type = INTEROP_BL_TYPE_ADDR_RANGE;
  entry.bl_entry_type = INTEROP_ENTRY_TYPE_STATIC;
  entry.entry_type.addr_range_entry.addr_start = *addr;
  entry.entry_type.addr_range_entry.feature = (interop_feature_t)feature;

  if (interop_database_match(&entry, NULL,
                             (interop_entry_type)(INTEROP_ENTRY_TYPE_STATIC))) {
    LOG_WARN("Device %s is a match for interop workaround %s.",
             ADDRESS_TO_LOGGABLE_CSTR(*addr), interop_feature_string_(feature));
    return true;
  }

  return false;
}

bool interop_database_match_vndr_prdt(const interop_feature_t feature,
                                      uint16_t vendor_id, uint16_t product_id) {
  interop_db_entry_t entry;

  entry.bl_type = INTEROP_BL_TYPE_VNDR_PRDT;

  entry.entry_type.vnr_pdt_entry.feature = (interop_feature_t)feature;
  entry.entry_type.vnr_pdt_entry.vendor_id = vendor_id;
  entry.entry_type.vnr_pdt_entry.product_id = product_id;
  if (interop_database_match(
          &entry, NULL,
          (interop_entry_type)(INTEROP_ENTRY_TYPE_STATIC |
                               INTEROP_ENTRY_TYPE_DYNAMIC))) {
    LOG_WARN(
        "Device with vendor_id: %d product_id: %d is a match for interop "
        "workaround %s",
        vendor_id, product_id, interop_feature_string_(feature));
    return true;
  }

  return false;
}

bool interop_database_match_addr_get_max_lat(const interop_feature_t feature,
                                             const RawAddress* addr,
                                             uint16_t* max_lat) {
  interop_db_entry_t entry;
  interop_db_entry_t* ret_entry = NULL;

  entry.bl_type = INTEROP_BL_TYPE_SSR_MAX_LAT;

  entry.entry_type.ssr_max_lat_entry.feature = feature;
  entry.entry_type.ssr_max_lat_entry.addr = *addr;
  entry.entry_type.ssr_max_lat_entry.feature = feature;
  if (interop_database_match(
          &entry, &ret_entry,
          (interop_entry_type)(INTEROP_ENTRY_TYPE_STATIC |
                               INTEROP_ENTRY_TYPE_DYNAMIC))) {
    LOG_WARN("Device %s is a match for interop workaround %s.",
             ADDRESS_TO_LOGGABLE_CSTR(*addr), interop_feature_string_(feature));
    *max_lat = ret_entry->entry_type.ssr_max_lat_entry.max_lat;
    return true;
  }

  return false;
}

bool interop_database_match_version(const interop_feature_t feature,
                                    uint16_t version) {
  interop_db_entry_t entry;

  entry.bl_type = INTEROP_BL_TYPE_VERSION;

  entry.entry_type.version_entry.feature = (interop_feature_t)feature;
  entry.entry_type.version_entry.version = version;
  if (interop_database_match(
          &entry, NULL,
          (interop_entry_type)(INTEROP_ENTRY_TYPE_STATIC |
                               INTEROP_ENTRY_TYPE_DYNAMIC))) {
    LOG_WARN("Device with version: 0x%04x is a match for interop workaround %s",
             version, interop_feature_string_(feature));
    return true;
  }

  return false;
}

bool interop_database_match_addr_get_lmp_ver(const interop_feature_t feature,
                                             const RawAddress* addr,
                                             uint8_t* lmp_ver,
                                             uint16_t* lmp_sub_ver) {
  interop_db_entry_t entry;
  interop_db_entry_t* ret_entry = NULL;

  entry.bl_type = INTEROP_BL_TYPE_LMP_VERSION;

  entry.entry_type.lmp_version_entry.feature = feature;
  entry.entry_type.lmp_version_entry.addr = *addr;
  entry.entry_type.lmp_version_entry.feature = feature;
  if (interop_database_match(
          &entry, &ret_entry,
          (interop_entry_type)(INTEROP_ENTRY_TYPE_STATIC |
                               INTEROP_ENTRY_TYPE_DYNAMIC))) {
    LOG_WARN("Device %s is a match for interop workaround %s.",
             ADDRESS_TO_LOGGABLE_CSTR(*addr), interop_feature_string_(feature));
    *lmp_ver = ret_entry->entry_type.lmp_version_entry.lmp_ver;
    *lmp_sub_ver = ret_entry->entry_type.lmp_version_entry.lmp_sub_ver;
    return true;
  }

  return false;
}

bool interop_database_remove_name(const interop_feature_t feature,
                                  const char* name) {
  CHECK(name);

  interop_db_entry_t entry;

  entry.bl_type = INTEROP_BL_TYPE_NAME;
  entry.bl_entry_type = INTEROP_ENTRY_TYPE_DYNAMIC;
  strlcpy(entry.entry_type.name_entry.name, name, 20);
  entry.entry_type.name_entry.feature = (interop_feature_t)feature;
  entry.entry_type.name_entry.length = strlen(entry.entry_type.name_entry.name);
  if (interop_database_remove_(&entry)) {
    LOG_WARN("Device with name: %s is removed from interop workaround %s", name,
             interop_feature_string_(feature));
    return true;
  }

  return false;
}

bool interop_database_remove_manufacturer(const interop_feature_t feature,
                                          uint16_t manufacturer) {
  interop_db_entry_t entry;

  entry.bl_type = INTEROP_BL_TYPE_MANUFACTURE;
  entry.bl_entry_type = INTEROP_ENTRY_TYPE_DYNAMIC;
  entry.entry_type.mnfr_entry.feature = feature;
  entry.entry_type.mnfr_entry.manufacturer = manufacturer;
  if (interop_database_remove_(&entry)) {
    LOG_WARN(
        "Device with manufacturer id: %d is removed from interop workaround %s",
        manufacturer, interop_feature_string_(feature));
    return true;
  }

  return false;
}

bool interop_database_remove_addr(const interop_feature_t feature,
                                  const RawAddress* addr) {
  CHECK(addr);

  interop_db_entry_t entry;

  entry.bl_type = INTEROP_BL_TYPE_ADDR;
  entry.bl_entry_type = INTEROP_ENTRY_TYPE_DYNAMIC;
  entry.entry_type.addr_entry.addr = *addr;
  entry.entry_type.addr_entry.feature = (interop_feature_t)feature;
  entry.entry_type.addr_entry.length = sizeof(RawAddress);
  if (interop_database_remove_(&entry)) {
    LOG_WARN("Device %s is a removed from interop workaround %s.",
             ADDRESS_TO_LOGGABLE_CSTR(*addr), interop_feature_string_(feature));
    return true;
  }

  return false;
}

bool interop_database_remove_feature(const interop_feature_t feature) {
  if (interop_list == NULL || list_length(interop_list) == 0) return false;

  list_node_t* node = list_begin(interop_list);
  while (node != list_end(interop_list)) {
    interop_db_entry_t* entry =
        static_cast<interop_db_entry_t*>(list_node(node));
    CHECK(entry);

    bool entry_match = false;
    if (entry->bl_entry_type == INTEROP_ENTRY_TYPE_DYNAMIC) {
      switch (entry->bl_type) {
        case INTEROP_BL_TYPE_ADDR:
          if (entry->entry_type.addr_entry.feature == feature) {
            entry_match = true;
          }
          break;
        case INTEROP_BL_TYPE_NAME:
          if (entry->entry_type.name_entry.feature == feature) {
            entry_match = true;
          }
          break;
        case INTEROP_BL_TYPE_MANUFACTURE:
          if (entry->entry_type.mnfr_entry.feature == feature) {
            entry_match = true;
          }
          break;
        case INTEROP_BL_TYPE_VNDR_PRDT:
          if (entry->entry_type.vnr_pdt_entry.feature == feature) {
            entry_match = true;
          }
          break;
        case INTEROP_BL_TYPE_SSR_MAX_LAT:
          if (entry->entry_type.ssr_max_lat_entry.feature == feature) {
            entry_match = true;
          }
          break;
        case INTEROP_BL_TYPE_VERSION:
          if (entry->entry_type.version_entry.feature == feature) {
            entry_match = true;
          }
          break;
        case INTEROP_BL_TYPE_LMP_VERSION:
          if (entry->entry_type.lmp_version_entry.feature == feature) {
            entry_match = true;
          }
          break;
        default:
          break;
      }
    }

    node = list_next(node);

    if (entry_match) {
      pthread_mutex_lock(&interop_list_lock);
      list_remove(interop_list, (void*)entry);
      pthread_mutex_unlock(&interop_list_lock);
    }
  }

  for (const section_t& sec : config_dynamic.get()->sections) {
    if (feature == interop_feature_name_to_feature_id(sec.name.c_str())) {
      LOG_WARN("found feature - %s", interop_feature_string_(feature));
      interop_config_remove_section(sec.name);
      return true;
    }
  }

  return false;
}

bool interop_database_remove_vndr_prdt(const interop_feature_t feature,
                                       uint16_t vendor_id,
                                       uint16_t product_id) {
  interop_db_entry_t entry;

  entry.bl_type = INTEROP_BL_TYPE_VNDR_PRDT;
  entry.bl_entry_type = INTEROP_ENTRY_TYPE_DYNAMIC;

  entry.entry_type.vnr_pdt_entry.feature = (interop_feature_t)feature;
  entry.entry_type.vnr_pdt_entry.vendor_id = vendor_id;
  entry.entry_type.vnr_pdt_entry.product_id = product_id;

  if (interop_database_remove_(&entry)) {
    LOG_WARN(
        "Device with vendor_id: %d product_id: %d is removed from interop "
        "workaround %s",
        vendor_id, product_id, interop_feature_string_(feature));
    return true;
  }
  return false;
}

bool interop_database_remove_addr_max_lat(const interop_feature_t feature,
                                          const RawAddress* addr,
                                          uint16_t max_lat) {
  interop_db_entry_t entry;

  entry.bl_type = INTEROP_BL_TYPE_SSR_MAX_LAT;
  entry.bl_entry_type = INTEROP_ENTRY_TYPE_DYNAMIC;

  entry.entry_type.ssr_max_lat_entry.addr = *addr;
  entry.entry_type.ssr_max_lat_entry.feature = feature;
  entry.entry_type.ssr_max_lat_entry.max_lat = max_lat;

  if (interop_database_remove_(&entry)) {
    LOG_WARN("Device %s is a removed from interop workaround %s.",
             ADDRESS_TO_LOGGABLE_CSTR(*addr), interop_feature_string_(feature));
    return true;
  }
  return false;
}

bool interop_database_remove_version(const interop_feature_t feature,
                                     uint16_t version) {
  interop_db_entry_t entry;

  entry.bl_type = INTEROP_BL_TYPE_VERSION;
  entry.bl_entry_type = INTEROP_ENTRY_TYPE_DYNAMIC;

  entry.entry_type.version_entry.feature = (interop_feature_t)feature;
  entry.entry_type.version_entry.version = version;

  if (interop_database_remove_(&entry)) {
    LOG_WARN(
        "Device with version: 0x%04x is removed from interop workaround %s",
        version, interop_feature_string_(feature));
    return true;
  }
  return false;
}

bool interop_database_remove_addr_lmp_version(const interop_feature_t feature,
                                              const RawAddress* addr,
                                              uint8_t lmp_ver,
                                              uint16_t lmp_sub_ver) {
  interop_db_entry_t entry;

  entry.bl_type = INTEROP_BL_TYPE_LMP_VERSION;
  entry.bl_entry_type = INTEROP_ENTRY_TYPE_DYNAMIC;

  entry.entry_type.lmp_version_entry.addr = *addr;
  entry.entry_type.lmp_version_entry.feature = feature;
  entry.entry_type.lmp_version_entry.lmp_ver = lmp_ver;
  entry.entry_type.lmp_version_entry.lmp_sub_ver = lmp_sub_ver;

  if (interop_database_remove_(&entry)) {
    LOG_WARN("Device %s is a removed from interop workaround %s.",
             ADDRESS_TO_LOGGABLE_CSTR(*addr), interop_feature_string_(feature));
    return true;
  }
  return false;
}

static void delete_media_player_node(void* data) {
  std::string* key = static_cast<std::string*>(data);
  delete key;
}

static void interop_database_save_allowlisted_media_players_list(
    const config_t* config) {
  media_player_list = list_new(delete_media_player_node);
  for (const section_t& sec : config->sections) {
    if (INTEROP_BROWSE_PLAYER_ALLOW_LIST ==
        interop_feature_name_to_feature_id(sec.name.c_str())) {
      LOG_WARN("found feature - %s", sec.name.c_str());
      for (const entry_t& entry : sec.entries) {
        list_append(media_player_list, (void*)(new std::string(entry.key)));
      }
      break;
    }
  }
}

bool interop_get_allowlisted_media_players_list(list_t* p_bl_devices) {
  if (media_player_list == nullptr) return false;

  const list_node_t* node = list_begin(media_player_list);
  bool found = false;

  while (node != list_end(media_player_list)) {
    found = true;
    std::string* key = (std::string*)list_node(node);
    list_append(p_bl_devices, (void*)key->c_str());
    node = list_next(node);
  }
  return found;
}
