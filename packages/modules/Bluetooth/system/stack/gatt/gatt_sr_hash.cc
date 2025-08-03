/******************************************************************************
 *
 *  Copyright 2020 The Android Open Source Project
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

#include <base/logging.h>
#include <base/strings/string_number_conversions.h>
#include <bluetooth/log.h>

#include <list>

#include "crypto_toolbox/crypto_toolbox.h"
#include "gatt_int.h"
#include "stack/include/bt_types.h"
#include "types/bluetooth/uuid.h"

using bluetooth::Uuid;
using namespace bluetooth;

static size_t calculate_database_info_size(std::list<tGATT_SRV_LIST_ELEM>* lst_ptr) {
  size_t len = 0;
  auto srv_it = lst_ptr->begin();
  for (; srv_it != lst_ptr->end(); srv_it++) {
    auto attr_list = &srv_it->p_db->attr_list;
    auto attr_it = attr_list->begin();
    for (; attr_it != attr_list->end(); attr_it++) {
      if (attr_it->uuid == Uuid::From16Bit(GATT_UUID_PRI_SERVICE) ||
          attr_it->uuid == Uuid::From16Bit(GATT_UUID_SEC_SERVICE)) {
        // Service declaration (Handle + Type + Value)
        len += 4 + gatt_build_uuid_to_stream_len(attr_it->p_value->uuid);
      } else if (attr_it->uuid == Uuid::From16Bit(GATT_UUID_INCLUDE_SERVICE)){
        // Included service declaration (Handle + Type + Value)
        len += 8 + gatt_build_uuid_to_stream_len(attr_it->p_value->incl_handle.service_type);
      } else if (attr_it->uuid == Uuid::From16Bit(GATT_UUID_CHAR_DECLARE)) {
        // Characteristic declaration (Handle + Type + Value)
        len += 7 + gatt_build_uuid_to_stream_len((++attr_it)->uuid);
      } else if (attr_it->uuid == Uuid::From16Bit(GATT_UUID_CHAR_DESCRIPTION) ||
                 attr_it->uuid == Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG) ||
                 attr_it->uuid == Uuid::From16Bit(GATT_UUID_CHAR_SRVR_CONFIG) ||
                 attr_it->uuid == Uuid::From16Bit(GATT_UUID_CHAR_PRESENT_FORMAT) ||
                 attr_it->uuid == Uuid::From16Bit(GATT_UUID_CHAR_AGG_FORMAT)) {
        // Descriptor (Handle + Type)
        len += 4;
      } else if (attr_it->uuid == Uuid::From16Bit(GATT_UUID_CHAR_EXT_PROP)) {
        // Descriptor for ext property (Handle + Type + Value)
        len += 6;
      }
    }
  }
  return len;
}

static void fill_database_info(std::list<tGATT_SRV_LIST_ELEM>* lst_ptr, uint8_t* p_data) {
  auto srv_it = lst_ptr->begin();
  for (; srv_it != lst_ptr->end(); srv_it++) {
    auto attr_list = &srv_it->p_db->attr_list;
    auto attr_it = attr_list->begin();
    for (; attr_it != attr_list->end(); attr_it++) {
      if (attr_it->uuid == Uuid::From16Bit(GATT_UUID_PRI_SERVICE) ||
          attr_it->uuid == Uuid::From16Bit(GATT_UUID_SEC_SERVICE)) {
        // Service declaration
        UINT16_TO_STREAM(p_data, attr_it->handle);

        if (srv_it->is_primary) {
          UINT16_TO_STREAM(p_data, GATT_UUID_PRI_SERVICE);
        } else {
          UINT16_TO_STREAM(p_data, GATT_UUID_SEC_SERVICE);
        }

        gatt_build_uuid_to_stream(&p_data, attr_it->p_value->uuid);
      } else if (attr_it->uuid == Uuid::From16Bit(GATT_UUID_INCLUDE_SERVICE)){
        // Included service declaration
        UINT16_TO_STREAM(p_data, attr_it->handle);
        UINT16_TO_STREAM(p_data, GATT_UUID_INCLUDE_SERVICE);
        UINT16_TO_STREAM(p_data, attr_it->p_value->incl_handle.s_handle);
        UINT16_TO_STREAM(p_data, attr_it->p_value->incl_handle.e_handle);

        gatt_build_uuid_to_stream(&p_data, attr_it->p_value->incl_handle.service_type);
      } else if (attr_it->uuid == Uuid::From16Bit(GATT_UUID_CHAR_DECLARE)) {
        // Characteristic declaration
        UINT16_TO_STREAM(p_data, attr_it->handle);
        UINT16_TO_STREAM(p_data, GATT_UUID_CHAR_DECLARE);
        UINT8_TO_STREAM(p_data, attr_it->p_value->char_decl.property);
        UINT16_TO_STREAM(p_data, attr_it->p_value->char_decl.char_val_handle);

        // Increment 1 to fetch characteristic uuid from value declaration attribute
        gatt_build_uuid_to_stream(&p_data, (++attr_it)->uuid);
      } else if (attr_it->uuid == Uuid::From16Bit(GATT_UUID_CHAR_DESCRIPTION) ||
                 attr_it->uuid == Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG) ||
                 attr_it->uuid == Uuid::From16Bit(GATT_UUID_CHAR_SRVR_CONFIG) ||
                 attr_it->uuid == Uuid::From16Bit(GATT_UUID_CHAR_PRESENT_FORMAT) ||
                 attr_it->uuid == Uuid::From16Bit(GATT_UUID_CHAR_AGG_FORMAT)) {
        // Descriptor
        UINT16_TO_STREAM(p_data, attr_it->handle);
        UINT16_TO_STREAM(p_data, attr_it->uuid.As16Bit());
      } else if (attr_it->uuid == Uuid::From16Bit(GATT_UUID_CHAR_EXT_PROP)) {
        // Descriptor
        UINT16_TO_STREAM(p_data, attr_it->handle);
        UINT16_TO_STREAM(p_data, attr_it->uuid.As16Bit());
        UINT16_TO_STREAM(p_data, attr_it->p_value
                                     ? attr_it->p_value->char_ext_prop
                                     : 0x0000);
      }
    }
  }
}

Octet16 gatts_calculate_database_hash(std::list<tGATT_SRV_LIST_ELEM>* lst_ptr) {
  int len = calculate_database_info_size(lst_ptr);

  std::vector<uint8_t> serialized(len);
  fill_database_info(lst_ptr, serialized.data());

  std::reverse(serialized.begin(), serialized.end());
  Octet16 db_hash = crypto_toolbox::aes_cmac(Octet16{0}, serialized.data(),
                                  serialized.size());
  log::info("hash={}", base::HexEncode(db_hash.data(), db_hash.size()));

  return db_hash;
}
