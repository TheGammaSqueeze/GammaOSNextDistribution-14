/*
 * Copyright 2023 The Android Open Source Project
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
/*
 * Generated mock file from original source file
 *   Functions generated:34
 *
 *  mockcify.pl ver 0.6.2
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_device_interop.h"

#include <cstdint>

#include "device/include/interop.h"
#include "test/common/mock_functions.h"

// Original usings

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace device_interop {

// Function state capture and return values, if needed
struct interop_database_add interop_database_add;
struct interop_database_add_addr interop_database_add_addr;
struct interop_database_add_addr_lmp_version
    interop_database_add_addr_lmp_version;
struct interop_database_add_addr_max_lat interop_database_add_addr_max_lat;
struct interop_database_add_manufacturer interop_database_add_manufacturer;
struct interop_database_add_name interop_database_add_name;
struct interop_database_add_version interop_database_add_version;
struct interop_database_add_vndr_prdt interop_database_add_vndr_prdt;
struct interop_database_clear interop_database_clear;
struct interop_database_match_addr interop_database_match_addr;
struct interop_database_match_addr_get_lmp_ver
    interop_database_match_addr_get_lmp_ver;
struct interop_database_match_addr_get_max_lat
    interop_database_match_addr_get_max_lat;
struct interop_database_match_manufacturer interop_database_match_manufacturer;
struct interop_database_match_name interop_database_match_name;
struct interop_database_match_version interop_database_match_version;
struct interop_database_match_vndr_prdt interop_database_match_vndr_prdt;
struct interop_database_remove_addr interop_database_remove_addr;
struct interop_database_remove_addr_lmp_version
    interop_database_remove_addr_lmp_version;
struct interop_database_remove_addr_max_lat
    interop_database_remove_addr_max_lat;
struct interop_database_remove_feature interop_database_remove_feature;
struct interop_database_remove_manufacturer
    interop_database_remove_manufacturer;
struct interop_database_remove_name interop_database_remove_name;
struct interop_database_remove_version interop_database_remove_version;
struct interop_database_remove_vndr_prdt interop_database_remove_vndr_prdt;
struct interop_feature_name_to_feature_id interop_feature_name_to_feature_id;
struct interop_get_allowlisted_media_players_list
    interop_get_allowlisted_media_players_list;
struct interop_match_addr interop_match_addr;
struct interop_match_addr_get_max_lat interop_match_addr_get_max_lat;
struct interop_match_addr_or_name interop_match_addr_or_name;
struct interop_match_manufacturer interop_match_manufacturer;
struct interop_match_name interop_match_name;
struct interop_match_vendor_product_ids interop_match_vendor_product_ids;
struct token_to_ul token_to_ul;

}  // namespace device_interop
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace device_interop {

bool interop_database_match_addr::return_value = false;
bool interop_database_match_addr_get_lmp_ver::return_value = false;
bool interop_database_match_addr_get_max_lat::return_value = false;
bool interop_database_match_manufacturer::return_value = false;
bool interop_database_match_name::return_value = false;
bool interop_database_match_version::return_value = false;
bool interop_database_match_vndr_prdt::return_value = false;
bool interop_database_remove_addr::return_value = false;
bool interop_database_remove_addr_lmp_version::return_value = false;
bool interop_database_remove_addr_max_lat::return_value = false;
bool interop_database_remove_feature::return_value = false;
bool interop_database_remove_manufacturer::return_value = false;
bool interop_database_remove_name::return_value = false;
bool interop_database_remove_version::return_value = false;
bool interop_database_remove_vndr_prdt::return_value = false;
int interop_feature_name_to_feature_id::return_value = 0;
bool interop_get_allowlisted_media_players_list::return_value = false;
bool interop_match_addr::return_value = false;
bool interop_match_addr_get_max_lat::return_value = false;
bool interop_match_addr_or_name::return_value = false;
bool interop_match_manufacturer::return_value = false;
bool interop_match_name::return_value = false;
bool interop_match_vendor_product_ids::return_value = false;
bool token_to_ul::return_value = false;

}  // namespace device_interop
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void interop_database_add(const uint16_t feature, const RawAddress* addr,
                          size_t length) {
  inc_func_call_count(__func__);
  test::mock::device_interop::interop_database_add(feature, addr, length);
}
void interop_database_add_addr(const uint16_t feature, const RawAddress* addr,
                               size_t length) {
  inc_func_call_count(__func__);
  test::mock::device_interop::interop_database_add_addr(feature, addr, length);
}
void interop_database_add_addr_lmp_version(const interop_feature_t feature,
                                           const RawAddress* addr,
                                           uint8_t lmp_ver,
                                           uint16_t lmp_sub_ver) {
  inc_func_call_count(__func__);
  test::mock::device_interop::interop_database_add_addr_lmp_version(
      feature, addr, lmp_ver, lmp_sub_ver);
}
void interop_database_add_addr_max_lat(const interop_feature_t feature,
                                       const RawAddress* addr,
                                       uint16_t max_lat) {
  inc_func_call_count(__func__);
  test::mock::device_interop::interop_database_add_addr_max_lat(feature, addr,
                                                                max_lat);
}
void interop_database_add_manufacturer(const interop_feature_t feature,
                                       uint16_t manufacturer) {
  inc_func_call_count(__func__);
  test::mock::device_interop::interop_database_add_manufacturer(feature,
                                                                manufacturer);
}
void interop_database_add_name(const uint16_t feature, const char* name) {
  inc_func_call_count(__func__);
  test::mock::device_interop::interop_database_add_name(feature, name);
}
void interop_database_add_version(const interop_feature_t feature,
                                  uint16_t version) {
  inc_func_call_count(__func__);
  test::mock::device_interop::interop_database_add_version(feature, version);
}
void interop_database_add_vndr_prdt(const interop_feature_t feature,
                                    uint16_t vendor_id, uint16_t product_id) {
  inc_func_call_count(__func__);
  test::mock::device_interop::interop_database_add_vndr_prdt(feature, vendor_id,
                                                             product_id);
}
void interop_database_clear() {
  inc_func_call_count(__func__);
  test::mock::device_interop::interop_database_clear();
}
bool interop_database_match_addr(const interop_feature_t feature,
                                 const RawAddress* addr) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_database_match_addr(feature, addr);
}
bool interop_database_match_addr_get_lmp_ver(const interop_feature_t feature,
                                             const RawAddress* addr,
                                             uint8_t* lmp_ver,
                                             uint16_t* lmp_sub_ver) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_database_match_addr_get_lmp_ver(
      feature, addr, lmp_ver, lmp_sub_ver);
}
bool interop_database_match_addr_get_max_lat(const interop_feature_t feature,
                                             const RawAddress* addr,
                                             uint16_t* max_lat) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_database_match_addr_get_max_lat(
      feature, addr, max_lat);
}
bool interop_database_match_manufacturer(const interop_feature_t feature,
                                         uint16_t manufacturer) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_database_match_manufacturer(
      feature, manufacturer);
}
bool interop_database_match_name(const interop_feature_t feature,
                                 const char* name) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_database_match_name(feature, name);
}
bool interop_database_match_version(const interop_feature_t feature,
                                    uint16_t version) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_database_match_version(feature,
                                                                    version);
}
bool interop_database_match_vndr_prdt(const interop_feature_t feature,
                                      uint16_t vendor_id, uint16_t product_id) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_database_match_vndr_prdt(
      feature, vendor_id, product_id);
}
bool interop_database_remove_addr(const interop_feature_t feature,
                                  const RawAddress* addr) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_database_remove_addr(feature,
                                                                  addr);
}
bool interop_database_remove_addr_lmp_version(const interop_feature_t feature,
                                              const RawAddress* addr,
                                              uint8_t lmp_ver,
                                              uint16_t lmp_sub_ver) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_database_remove_addr_lmp_version(
      feature, addr, lmp_ver, lmp_sub_ver);
}
bool interop_database_remove_addr_max_lat(const interop_feature_t feature,
                                          const RawAddress* addr,
                                          uint16_t max_lat) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_database_remove_addr_max_lat(
      feature, addr, max_lat);
}
bool interop_database_remove_feature(const interop_feature_t feature) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_database_remove_feature(feature);
}
bool interop_database_remove_manufacturer(const interop_feature_t feature,
                                          uint16_t manufacturer) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_database_remove_manufacturer(
      feature, manufacturer);
}
bool interop_database_remove_name(const interop_feature_t feature,
                                  const char* name) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_database_remove_name(feature,
                                                                  name);
}
bool interop_database_remove_version(const interop_feature_t feature,
                                     uint16_t version) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_database_remove_version(feature,
                                                                     version);
}
bool interop_database_remove_vndr_prdt(const interop_feature_t feature,
                                       uint16_t vendor_id,
                                       uint16_t product_id) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_database_remove_vndr_prdt(
      feature, vendor_id, product_id);
}
int interop_feature_name_to_feature_id(const char* feature_name) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_feature_name_to_feature_id(
      feature_name);
}
bool interop_get_allowlisted_media_players_list(list_t* p_bl_devices) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_get_allowlisted_media_players_list(
      p_bl_devices);
}
bool interop_match_addr(const interop_feature_t feature,
                        const RawAddress* addr) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_match_addr(feature, addr);
}
bool interop_match_addr_get_max_lat(const interop_feature_t feature,
                                    const RawAddress* addr, uint16_t* max_lat) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_match_addr_get_max_lat(
      feature, addr, max_lat);
}
bool interop_match_addr_or_name(const interop_feature_t feature,
                                const RawAddress* addr,
                                bt_status_t (*get_remote_device_property)(
                                    const RawAddress*, bt_property_t*)) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_match_addr_or_name(
      feature, addr, get_remote_device_property);
}
bool interop_match_manufacturer(const interop_feature_t feature,
                                uint16_t manufacturer) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_match_manufacturer(feature,
                                                                manufacturer);
}
bool interop_match_name(const interop_feature_t feature, const char* name) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_match_name(feature, name);
}
bool interop_match_vendor_product_ids(const interop_feature_t feature,
                                      uint16_t vendor_id, uint16_t product_id) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::interop_match_vendor_product_ids(
      feature, vendor_id, product_id);
}
bool token_to_ul(char* token, uint16_t* ul) {
  inc_func_call_count(__func__);
  return test::mock::device_interop::token_to_ul(token, ul);
}
// Mocked functions complete
// END mockcify generation
