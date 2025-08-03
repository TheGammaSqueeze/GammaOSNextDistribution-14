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

#include <gtest/gtest.h>

#include "stack/gatt/gatt_int.h"
#include "types/bluetooth/uuid.h"

using bluetooth::Uuid;

tGATT_CB gatt_cb;

static void add_item_to_list(std::list<tGATT_SRV_LIST_ELEM>& srv_list_info,
                      tGATT_SVC_DB* db, bool is_primary) {
  srv_list_info.emplace_back();
  tGATT_SRV_LIST_ELEM& elem = srv_list_info.back();
  elem.p_db = db;
  elem.is_primary = is_primary;
}

// BT Spec 5.2, Vol 3, Part G, Appendix B
TEST(GattDatabaseTest, matchExampleInBtSpecV52) {
  tGATT_SVC_DB local_db[4];
  for (int i=0; i<4; i++) local_db[i] = tGATT_SVC_DB();
  std::list<tGATT_SRV_LIST_ELEM> srv_list_info;

  // 0x1800
  add_item_to_list(srv_list_info, &local_db[0], true);
  gatts_init_service_db(local_db[0], Uuid::From16Bit(0x1800), true, 0x0001, 5);
  gatts_add_characteristic(local_db[0],
    GATT_PERM_READ | GATT_PERM_WRITE,
    GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_WRITE,
    Uuid::From16Bit(0x2A00));
  gatts_add_characteristic(local_db[0], GATT_PERM_READ, GATT_CHAR_PROP_BIT_READ,
    Uuid::From16Bit(0x2A01));
  // 0x1801
  add_item_to_list(srv_list_info, &local_db[1], true);
  gatts_init_service_db(local_db[1], Uuid::From16Bit(0x1801), true, 0x0006, 8);
  gatts_add_characteristic(local_db[1], 0, GATT_CHAR_PROP_BIT_INDICATE,
    Uuid::From16Bit(0x2A05));
  gatts_add_char_descr(local_db[1], GATT_CHAR_PROP_BIT_READ, Uuid::From16Bit(0x2902));
  gatts_add_characteristic(local_db[1],
    GATT_PERM_READ | GATT_PERM_WRITE,
    GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_WRITE,
    Uuid::From16Bit(0x2B29));
  gatts_add_characteristic(local_db[1], GATT_PERM_READ, GATT_CHAR_PROP_BIT_READ,
    Uuid::From16Bit(0x2B2A));
  // 0x1808
  add_item_to_list(srv_list_info, &local_db[2], true);
  gatts_init_service_db(local_db[2], Uuid::From16Bit(0x1808), true, 0x000E, 6);
  gatts_add_included_service(local_db[2], 0x0014, 0x0016, Uuid::From16Bit(0x180F));
  gatts_add_characteristic(local_db[2], GATT_PERM_READ,
    GATT_CHAR_PROP_BIT_READ | GATT_CHAR_PROP_BIT_INDICATE | GATT_CHAR_PROP_BIT_EXT_PROP,
    Uuid::From16Bit(0x2A18));
  gatts_add_char_descr(local_db[2], 0x0000, Uuid::From16Bit(0x2902));
  gatts_add_char_ext_prop_descr(local_db[2], 0x0000);
  // 0x180F
  add_item_to_list(srv_list_info, &local_db[3], false);
  gatts_init_service_db(local_db[3], Uuid::From16Bit(0x180F), false, 0x0014, 3);
  gatts_add_characteristic(local_db[3], GATT_PERM_READ,  GATT_CHAR_PROP_BIT_READ,
    Uuid::From16Bit(0x2A19));

  Octet16 expected_hash{0xF1, 0xCA, 0x2D, 0x48, 0xEC, 0xF5, 0x8B, 0xAC,
                        0x8A, 0x88, 0x30, 0xBB, 0xB9, 0xFB, 0xA9, 0x90};
  std::reverse(expected_hash.begin(), expected_hash.end());

  Octet16 result_hash = gatts_calculate_database_hash(&srv_list_info);

  ASSERT_EQ(result_hash, expected_hash);
}
