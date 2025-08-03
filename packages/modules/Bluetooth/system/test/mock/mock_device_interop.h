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
#pragma once

/*
 * Generated mock file from original source file
 *   Functions generated:34
 *
 *  mockcify.pl ver 0.6.2
 */

#include <cstdint>
#include <functional>

// Original included files, if any

#include "device/include/interop.h"
#include "osi/include/list.h"
#include "types/raw_address.h"

// Original usings

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace device_interop {

// Shared state between mocked functions and tests
// Name: interop_database_add
// Params: const uint16_t feature, const RawAddress* addr, size_t length
// Return: void
struct interop_database_add {
  std::function<void(const uint16_t feature, const RawAddress* addr,
                     size_t length)>
      body{[](const uint16_t /* feature */, const RawAddress* /* addr */,
              size_t /* length */) {}};
  void operator()(const uint16_t feature, const RawAddress* addr,
                  size_t length) {
    body(feature, addr, length);
  };
};
extern struct interop_database_add interop_database_add;

// Name: interop_database_add_addr
// Params: const uint16_t feature, const RawAddress* addr, size_t length
// Return: void
struct interop_database_add_addr {
  std::function<void(const uint16_t feature, const RawAddress* addr,
                     size_t length)>
      body{[](const uint16_t /* feature */, const RawAddress* /* addr */,
              size_t /* length */) {}};
  void operator()(const uint16_t feature, const RawAddress* addr,
                  size_t length) {
    body(feature, addr, length);
  };
};
extern struct interop_database_add_addr interop_database_add_addr;

// Name: interop_database_add_addr_lmp_version
// Params: const interop_feature_t feature, const RawAddress* addr, uint8_t
// lmp_ver, uint16_t lmp_sub_ver Return: void
struct interop_database_add_addr_lmp_version {
  std::function<void(const interop_feature_t feature, const RawAddress* addr,
                     uint8_t lmp_ver, uint16_t lmp_sub_ver)>
      body{[](const interop_feature_t /* feature */,
              const RawAddress* /* addr */, uint8_t /* lmp_ver */,
              uint16_t /* lmp_sub_ver */) {}};
  void operator()(const interop_feature_t feature, const RawAddress* addr,
                  uint8_t lmp_ver, uint16_t lmp_sub_ver) {
    body(feature, addr, lmp_ver, lmp_sub_ver);
  };
};
extern struct interop_database_add_addr_lmp_version
    interop_database_add_addr_lmp_version;

// Name: interop_database_add_addr_max_lat
// Params: const interop_feature_t feature, const RawAddress* addr, uint16_t
// max_lat Return: void
struct interop_database_add_addr_max_lat {
  std::function<void(const interop_feature_t feature, const RawAddress* addr,
                     uint16_t max_lat)>
      body{[](const interop_feature_t /* feature */,
              const RawAddress* /* addr */, uint16_t /* max_lat */) {}};
  void operator()(const interop_feature_t feature, const RawAddress* addr,
                  uint16_t max_lat) {
    body(feature, addr, max_lat);
  };
};
extern struct interop_database_add_addr_max_lat
    interop_database_add_addr_max_lat;

// Name: interop_database_add_manufacturer
// Params: const interop_feature_t feature, uint16_t manufacturer
// Return: void
struct interop_database_add_manufacturer {
  std::function<void(const interop_feature_t feature, uint16_t manufacturer)>
      body{[](const interop_feature_t /* feature */,
              uint16_t /* manufacturer */) {}};
  void operator()(const interop_feature_t feature, uint16_t manufacturer) {
    body(feature, manufacturer);
  };
};
extern struct interop_database_add_manufacturer
    interop_database_add_manufacturer;

// Name: interop_database_add_name
// Params: const uint16_t feature, const char* name
// Return: void
struct interop_database_add_name {
  std::function<void(const uint16_t feature, const char* name)> body{
      [](const uint16_t /* feature */, const char* /* name */) {}};
  void operator()(const uint16_t feature, const char* name) {
    body(feature, name);
  };
};
extern struct interop_database_add_name interop_database_add_name;

// Name: interop_database_add_version
// Params: const interop_feature_t feature, uint16_t version
// Return: void
struct interop_database_add_version {
  std::function<void(const interop_feature_t feature, uint16_t version)> body{
      [](const interop_feature_t /* feature */, uint16_t /* version */) {}};
  void operator()(const interop_feature_t feature, uint16_t version) {
    body(feature, version);
  };
};
extern struct interop_database_add_version interop_database_add_version;

// Name: interop_database_add_vndr_prdt
// Params: const interop_feature_t feature, uint16_t vendor_id, uint16_t
// product_id Return: void
struct interop_database_add_vndr_prdt {
  std::function<void(const interop_feature_t feature, uint16_t vendor_id,
                     uint16_t product_id)>
      body{[](const interop_feature_t /* feature */, uint16_t /* vendor_id */,
              uint16_t /* product_id */) {}};
  void operator()(const interop_feature_t feature, uint16_t vendor_id,
                  uint16_t product_id) {
    body(feature, vendor_id, product_id);
  };
};
extern struct interop_database_add_vndr_prdt interop_database_add_vndr_prdt;

// Name: interop_database_clear
// Params:
// Return: void
struct interop_database_clear {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct interop_database_clear interop_database_clear;

// Name: interop_database_match_addr
// Params: const interop_feature_t feature, const RawAddress* addr
// Return: bool
struct interop_database_match_addr {
  static bool return_value;
  std::function<bool(const interop_feature_t feature, const RawAddress* addr)>
      body{[](const interop_feature_t /* feature */,
              const RawAddress* /* addr */) { return return_value; }};
  bool operator()(const interop_feature_t feature, const RawAddress* addr) {
    return body(feature, addr);
  };
};
extern struct interop_database_match_addr interop_database_match_addr;

// Name: interop_database_match_addr_get_lmp_ver
// Params: const interop_feature_t feature, const RawAddress* addr, uint8_t*
// lmp_ver, uint16_t* lmp_sub_ver Return: bool
struct interop_database_match_addr_get_lmp_ver {
  static bool return_value;
  std::function<bool(const interop_feature_t feature, const RawAddress* addr,
                     uint8_t* lmp_ver, uint16_t* lmp_sub_ver)>
      body{[](const interop_feature_t /* feature */,
              const RawAddress* /* addr */, uint8_t* /* lmp_ver */,
              uint16_t* /* lmp_sub_ver */) { return return_value; }};
  bool operator()(const interop_feature_t feature, const RawAddress* addr,
                  uint8_t* lmp_ver, uint16_t* lmp_sub_ver) {
    return body(feature, addr, lmp_ver, lmp_sub_ver);
  };
};
extern struct interop_database_match_addr_get_lmp_ver
    interop_database_match_addr_get_lmp_ver;

// Name: interop_database_match_addr_get_max_lat
// Params: const interop_feature_t feature, const RawAddress* addr, uint16_t*
// max_lat Return: bool
struct interop_database_match_addr_get_max_lat {
  static bool return_value;
  std::function<bool(const interop_feature_t feature, const RawAddress* addr,
                     uint16_t* max_lat)>
      body{[](const interop_feature_t /* feature */,
              const RawAddress* /* addr */,
              uint16_t* /* max_lat */) { return return_value; }};
  bool operator()(const interop_feature_t feature, const RawAddress* addr,
                  uint16_t* max_lat) {
    return body(feature, addr, max_lat);
  };
};
extern struct interop_database_match_addr_get_max_lat
    interop_database_match_addr_get_max_lat;

// Name: interop_database_match_manufacturer
// Params: const interop_feature_t feature, uint16_t manufacturer
// Return: bool
struct interop_database_match_manufacturer {
  static bool return_value;
  std::function<bool(const interop_feature_t feature, uint16_t manufacturer)>
      body{[](const interop_feature_t /* feature */,
              uint16_t /* manufacturer */) { return return_value; }};
  bool operator()(const interop_feature_t feature, uint16_t manufacturer) {
    return body(feature, manufacturer);
  };
};
extern struct interop_database_match_manufacturer
    interop_database_match_manufacturer;

// Name: interop_database_match_name
// Params: const interop_feature_t feature, const char* name
// Return: bool
struct interop_database_match_name {
  static bool return_value;
  std::function<bool(const interop_feature_t feature, const char* name)> body{
      [](const interop_feature_t /* feature */, const char* /* name */) {
        return return_value;
      }};
  bool operator()(const interop_feature_t feature, const char* name) {
    return body(feature, name);
  };
};
extern struct interop_database_match_name interop_database_match_name;

// Name: interop_database_match_version
// Params: const interop_feature_t feature, uint16_t version
// Return: bool
struct interop_database_match_version {
  static bool return_value;
  std::function<bool(const interop_feature_t feature, uint16_t version)> body{
      [](const interop_feature_t /* feature */, uint16_t /* version */) {
        return return_value;
      }};
  bool operator()(const interop_feature_t feature, uint16_t version) {
    return body(feature, version);
  };
};
extern struct interop_database_match_version interop_database_match_version;

// Name: interop_database_match_vndr_prdt
// Params: const interop_feature_t feature, uint16_t vendor_id, uint16_t
// product_id Return: bool
struct interop_database_match_vndr_prdt {
  static bool return_value;
  std::function<bool(const interop_feature_t feature, uint16_t vendor_id,
                     uint16_t product_id)>
      body{[](const interop_feature_t /* feature */, uint16_t /* vendor_id */,
              uint16_t /* product_id */) { return return_value; }};
  bool operator()(const interop_feature_t feature, uint16_t vendor_id,
                  uint16_t product_id) {
    return body(feature, vendor_id, product_id);
  };
};
extern struct interop_database_match_vndr_prdt interop_database_match_vndr_prdt;

// Name: interop_database_remove_addr
// Params: const interop_feature_t feature, const RawAddress* addr
// Return: bool
struct interop_database_remove_addr {
  static bool return_value;
  std::function<bool(const interop_feature_t feature, const RawAddress* addr)>
      body{[](const interop_feature_t /* feature */,
              const RawAddress* /* addr */) { return return_value; }};
  bool operator()(const interop_feature_t feature, const RawAddress* addr) {
    return body(feature, addr);
  };
};
extern struct interop_database_remove_addr interop_database_remove_addr;

// Name: interop_database_remove_addr_lmp_version
// Params: const interop_feature_t feature, const RawAddress* addr, uint8_t
// lmp_ver, uint16_t lmp_sub_ver Return: bool
struct interop_database_remove_addr_lmp_version {
  static bool return_value;
  std::function<bool(const interop_feature_t feature, const RawAddress* addr,
                     uint8_t lmp_ver, uint16_t lmp_sub_ver)>
      body{[](const interop_feature_t /* feature */,
              const RawAddress* /* addr */, uint8_t /* lmp_ver */,
              uint16_t /* lmp_sub_ver */) { return return_value; }};
  bool operator()(const interop_feature_t feature, const RawAddress* addr,
                  uint8_t lmp_ver, uint16_t lmp_sub_ver) {
    return body(feature, addr, lmp_ver, lmp_sub_ver);
  };
};
extern struct interop_database_remove_addr_lmp_version
    interop_database_remove_addr_lmp_version;

// Name: interop_database_remove_addr_max_lat
// Params: const interop_feature_t feature, const RawAddress* addr, uint16_t
// max_lat Return: bool
struct interop_database_remove_addr_max_lat {
  static bool return_value;
  std::function<bool(const interop_feature_t feature, const RawAddress* addr,
                     uint16_t max_lat)>
      body{[](const interop_feature_t /* feature */,
              const RawAddress* /* addr */,
              uint16_t /* max_lat */) { return return_value; }};
  bool operator()(const interop_feature_t feature, const RawAddress* addr,
                  uint16_t max_lat) {
    return body(feature, addr, max_lat);
  };
};
extern struct interop_database_remove_addr_max_lat
    interop_database_remove_addr_max_lat;

// Name: interop_database_remove_feature
// Params: const interop_feature_t feature
// Return: bool
struct interop_database_remove_feature {
  static bool return_value;
  std::function<bool(const interop_feature_t feature)> body{
      [](const interop_feature_t /* feature */) { return return_value; }};
  bool operator()(const interop_feature_t feature) { return body(feature); };
};
extern struct interop_database_remove_feature interop_database_remove_feature;

// Name: interop_database_remove_manufacturer
// Params: const interop_feature_t feature, uint16_t manufacturer
// Return: bool
struct interop_database_remove_manufacturer {
  static bool return_value;
  std::function<bool(const interop_feature_t feature, uint16_t manufacturer)>
      body{[](const interop_feature_t /* feature */,
              uint16_t /* manufacturer */) { return return_value; }};
  bool operator()(const interop_feature_t feature, uint16_t manufacturer) {
    return body(feature, manufacturer);
  };
};
extern struct interop_database_remove_manufacturer
    interop_database_remove_manufacturer;

// Name: interop_database_remove_name
// Params: const interop_feature_t feature, const char* name
// Return: bool
struct interop_database_remove_name {
  static bool return_value;
  std::function<bool(const interop_feature_t feature, const char* name)> body{
      [](const interop_feature_t /* feature */, const char* /* name */) {
        return return_value;
      }};
  bool operator()(const interop_feature_t feature, const char* name) {
    return body(feature, name);
  };
};
extern struct interop_database_remove_name interop_database_remove_name;

// Name: interop_database_remove_version
// Params: const interop_feature_t feature, uint16_t version
// Return: bool
struct interop_database_remove_version {
  static bool return_value;
  std::function<bool(const interop_feature_t feature, uint16_t version)> body{
      [](const interop_feature_t /* feature */, uint16_t /* version */) {
        return return_value;
      }};
  bool operator()(const interop_feature_t feature, uint16_t version) {
    return body(feature, version);
  };
};
extern struct interop_database_remove_version interop_database_remove_version;

// Name: interop_database_remove_vndr_prdt
// Params: const interop_feature_t feature, uint16_t vendor_id, uint16_t
// product_id Return: bool
struct interop_database_remove_vndr_prdt {
  static bool return_value;
  std::function<bool(const interop_feature_t feature, uint16_t vendor_id,
                     uint16_t product_id)>
      body{[](const interop_feature_t /* feature */, uint16_t /* vendor_id */,
              uint16_t /* product_id */) { return return_value; }};
  bool operator()(const interop_feature_t feature, uint16_t vendor_id,
                  uint16_t product_id) {
    return body(feature, vendor_id, product_id);
  };
};
extern struct interop_database_remove_vndr_prdt
    interop_database_remove_vndr_prdt;

// Name: interop_feature_name_to_feature_id
// Params: const char* feature_name
// Return: int
struct interop_feature_name_to_feature_id {
  static int return_value;
  std::function<int(const char* feature_name)> body{
      [](const char* /* feature_name */) { return return_value; }};
  int operator()(const char* feature_name) { return body(feature_name); };
};
extern struct interop_feature_name_to_feature_id
    interop_feature_name_to_feature_id;

// Name: interop_get_allowlisted_media_players_list
// Params: list_t* p_bl_devices
// Return: bool
struct interop_get_allowlisted_media_players_list {
  static bool return_value;
  std::function<bool(list_t* p_bl_devices)> body{
      [](list_t* /* p_bl_devices */) { return return_value; }};
  bool operator()(list_t* p_bl_devices) { return body(p_bl_devices); };
};
extern struct interop_get_allowlisted_media_players_list
    interop_get_allowlisted_media_players_list;

// Name: interop_match_addr
// Params: const interop_feature_t feature, const RawAddress* addr
// Return: bool
struct interop_match_addr {
  static bool return_value;
  std::function<bool(const interop_feature_t feature, const RawAddress* addr)>
      body{[](const interop_feature_t /* feature */,
              const RawAddress* /* addr */) { return return_value; }};
  bool operator()(const interop_feature_t feature, const RawAddress* addr) {
    return body(feature, addr);
  };
};
extern struct interop_match_addr interop_match_addr;

// Name: interop_match_addr_get_max_lat
// Params: const interop_feature_t feature, const RawAddress* addr, uint16_t*
// max_lat Return: bool
struct interop_match_addr_get_max_lat {
  static bool return_value;
  std::function<bool(const interop_feature_t feature, const RawAddress* addr,
                     uint16_t* max_lat)>
      body{[](const interop_feature_t /* feature */,
              const RawAddress* /* addr */,
              uint16_t* /* max_lat */) { return return_value; }};
  bool operator()(const interop_feature_t feature, const RawAddress* addr,
                  uint16_t* max_lat) {
    return body(feature, addr, max_lat);
  };
};
extern struct interop_match_addr_get_max_lat interop_match_addr_get_max_lat;

// Name: interop_match_addr_or_name
// Params: const interop_feature_t feature, const RawAddress* addr, bt_status_t
// (*get_remote_device_property Return: bool
struct interop_match_addr_or_name {
  static bool return_value;
  std::function<bool(const interop_feature_t feature, const RawAddress* addr,
                     bt_status_t (*get_remote_device_property)(
                         const RawAddress*, bt_property_t*))>
      body{[](const interop_feature_t /* feature */,
              const RawAddress* /* addr */,
              bt_status_t (* /* get_remote_device_property */)(
                  const RawAddress*, bt_property_t*)) { return return_value; }};
  bool operator()(const interop_feature_t feature, const RawAddress* addr,
                  bt_status_t (*get_remote_device_property)(const RawAddress*,
                                                            bt_property_t*)) {
    return body(feature, addr, get_remote_device_property);
  };
};
extern struct interop_match_addr_or_name interop_match_addr_or_name;

// Name: interop_match_manufacturer
// Params: const interop_feature_t feature, uint16_t manufacturer
// Return: bool
struct interop_match_manufacturer {
  static bool return_value;
  std::function<bool(const interop_feature_t feature, uint16_t manufacturer)>
      body{[](const interop_feature_t /* feature */,
              uint16_t /* manufacturer */) { return return_value; }};
  bool operator()(const interop_feature_t feature, uint16_t manufacturer) {
    return body(feature, manufacturer);
  };
};
extern struct interop_match_manufacturer interop_match_manufacturer;

// Name: interop_match_name
// Params: const interop_feature_t feature, const char* name
// Return: bool
struct interop_match_name {
  static bool return_value;
  std::function<bool(const interop_feature_t feature, const char* name)> body{
      [](const interop_feature_t /* feature */, const char* /* name */) {
        return return_value;
      }};
  bool operator()(const interop_feature_t feature, const char* name) {
    return body(feature, name);
  };
};
extern struct interop_match_name interop_match_name;

// Name: interop_match_vendor_product_ids
// Params: const interop_feature_t feature, uint16_t vendor_id, uint16_t
// product_id Return: bool
struct interop_match_vendor_product_ids {
  static bool return_value;
  std::function<bool(const interop_feature_t feature, uint16_t vendor_id,
                     uint16_t product_id)>
      body{[](const interop_feature_t /* feature */, uint16_t /* vendor_id */,
              uint16_t /* product_id */) { return return_value; }};
  bool operator()(const interop_feature_t feature, uint16_t vendor_id,
                  uint16_t product_id) {
    return body(feature, vendor_id, product_id);
  };
};
extern struct interop_match_vendor_product_ids interop_match_vendor_product_ids;

// Name: token_to_ul
// Params: char* token, uint16_t* ul
// Return: bool
struct token_to_ul {
  static bool return_value;
  std::function<bool(char* token, uint16_t* ul)> body{
      [](char* /* token */, uint16_t* /* ul */) { return return_value; }};
  bool operator()(char* token, uint16_t* ul) { return body(token, ul); };
};
extern struct token_to_ul token_to_ul;

}  // namespace device_interop
}  // namespace mock
}  // namespace test

// END mockcify generation
