/*
 * Copyright 2021 The Android Open Source Project
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
 *   Functions generated:12
 *
 *  mockcify.pl ver 0.2.1
 */

#include <cstdint>
#include <functional>
#include <string>

// Original included files, if any
#include "stack/include/pan_api.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any
namespace test {
namespace mock {
namespace stack_pan_api {

// Shared state between mocked functions and tests
// Name: PAN_Connect
// Params: const RawAddress& rem_bda, tPAN_ROLE src_role, tPAN_ROLE dst_role,
// uint16_t* handle Returns: tPAN_RESULT
struct PAN_Connect {
  std::function<tPAN_RESULT(const RawAddress& rem_bda, tPAN_ROLE src_role,
                            tPAN_ROLE dst_role, uint16_t* handle)>
      body{[](const RawAddress& /* rem_bda */, tPAN_ROLE /* src_role */,
              tPAN_ROLE /* dst_role */,
              uint16_t* /* handle */) { return PAN_SUCCESS; }};
  tPAN_RESULT operator()(const RawAddress& rem_bda, tPAN_ROLE src_role,
                         tPAN_ROLE dst_role, uint16_t* handle) {
    return body(rem_bda, src_role, dst_role, handle);
  };
};
extern struct PAN_Connect PAN_Connect;
// Name: PAN_Disconnect
// Params: uint16_t handle
// Returns: tPAN_RESULT
struct PAN_Disconnect {
  std::function<tPAN_RESULT(uint16_t handle)> body{
      [](uint16_t /* handle */) { return PAN_SUCCESS; }};
  tPAN_RESULT operator()(uint16_t handle) { return body(handle); };
};
extern struct PAN_Disconnect PAN_Disconnect;
// Name: PAN_SetMulticastFilters
// Params: uint16_t handle, uint16_t num_mcast_filters, uint8_t* p_start_array,
// uint8_t* p_end_array Returns: tPAN_RESULT
struct PAN_SetMulticastFilters {
  std::function<tPAN_RESULT(uint16_t handle, uint16_t num_mcast_filters,
                            uint8_t* p_start_array, uint8_t* p_end_array)>
      body{[](uint16_t /* handle */, uint16_t /* num_mcast_filters */,
              uint8_t* /* p_start_array */,
              uint8_t* /* p_end_array */) { return PAN_SUCCESS; }};
  tPAN_RESULT operator()(uint16_t handle, uint16_t num_mcast_filters,
                         uint8_t* p_start_array, uint8_t* p_end_array) {
    return body(handle, num_mcast_filters, p_start_array, p_end_array);
  };
};
extern struct PAN_SetMulticastFilters PAN_SetMulticastFilters;
// Name: PAN_SetProtocolFilters
// Params: uint16_t handle, uint16_t num_filters, uint16_t* p_start_array,
// uint16_t* p_end_array Returns: tPAN_RESULT
struct PAN_SetProtocolFilters {
  std::function<tPAN_RESULT(uint16_t handle, uint16_t num_filters,
                            uint16_t* p_start_array, uint16_t* p_end_array)>
      body{[](uint16_t /* handle */, uint16_t /* num_filters */,
              uint16_t* /* p_start_array */,
              uint16_t* /* p_end_array */) { return PAN_SUCCESS; }};
  tPAN_RESULT operator()(uint16_t handle, uint16_t num_filters,
                         uint16_t* p_start_array, uint16_t* p_end_array) {
    return body(handle, num_filters, p_start_array, p_end_array);
  };
};
extern struct PAN_SetProtocolFilters PAN_SetProtocolFilters;
// Name: PAN_SetRole
// Params: uint8_t role, const std::string p_user_name, const std::string
// p_nap_name Returns: tPAN_RESULT
struct PAN_SetRole {
  std::function<tPAN_RESULT(uint8_t role, std::string p_user_name,
                            std::string p_nap_name)>
      body{[](uint8_t /* role */, std::string /* p_user_name */,
              std::string /* p_nap_name */) { return PAN_SUCCESS; }};
  tPAN_RESULT operator()(uint8_t role, std::string p_user_name,
                         std::string p_nap_name) {
    return body(role, p_user_name, p_nap_name);
  };
};
extern struct PAN_SetRole PAN_SetRole;
// Name: PAN_Write
// Params: uint16_t handle, const RawAddress& dst, const RawAddress& src,
// uint16_t protocol, uint8_t* p_data, uint16_t len, bool ext Returns:
// tPAN_RESULT
struct PAN_Write {
  std::function<tPAN_RESULT(uint16_t handle, const RawAddress& dst,
                            const RawAddress& src, uint16_t protocol,
                            uint8_t* p_data, uint16_t len, bool ext)>
      body{[](uint16_t /* handle */, const RawAddress& /* dst */,
              const RawAddress& /* src */, uint16_t /* protocol */,
              uint8_t* /* p_data */, uint16_t /* len */,
              bool /* ext */) { return PAN_SUCCESS; }};
  tPAN_RESULT operator()(uint16_t handle, const RawAddress& dst,
                         const RawAddress& src, uint16_t protocol,
                         uint8_t* p_data, uint16_t len, bool ext) {
    return body(handle, dst, src, protocol, p_data, len, ext);
  };
};
extern struct PAN_Write PAN_Write;
// Name: PAN_WriteBuf
// Params: uint16_t handle, const RawAddress& dst, const RawAddress& src,
// uint16_t protocol, BT_HDR* p_buf, bool ext Returns: tPAN_RESULT
struct PAN_WriteBuf {
  std::function<tPAN_RESULT(uint16_t handle, const RawAddress& dst,
                            const RawAddress& src, uint16_t protocol,
                            BT_HDR* p_buf, bool ext)>
      body{[](uint16_t /* handle */, const RawAddress& /* dst */,
              const RawAddress& /* src */, uint16_t /* protocol */,
              BT_HDR* /* p_buf */, bool /* ext */) { return PAN_SUCCESS; }};
  tPAN_RESULT operator()(uint16_t handle, const RawAddress& dst,
                         const RawAddress& src, uint16_t protocol,
                         BT_HDR* p_buf, bool ext) {
    return body(handle, dst, src, protocol, p_buf, ext);
  };
};
extern struct PAN_WriteBuf PAN_WriteBuf;
// Name: PAN_Deregister
// Params: void
// Returns: void
struct PAN_Deregister {
  std::function<void(void)> body{[](void) { ; }};
  void operator()(void) { body(); };
};
extern struct PAN_Deregister PAN_Deregister;
// Name: PAN_Dumpsys
// Params: int fd
// Returns: void
struct PAN_Dumpsys {
  std::function<void(int fd)> body{[](int /* fd */) { ; }};
  void operator()(int fd) { body(fd); };
};
extern struct PAN_Dumpsys PAN_Dumpsys;
// Name: PAN_Init
// Params: void
// Returns: void
struct PAN_Init {
  std::function<void(void)> body{[](void) { ; }};
  void operator()(void) { body(); };
};
extern struct PAN_Init PAN_Init;
// Name: PAN_Register
// Params: tPAN_REGISTER* p_register
// Returns: void
struct PAN_Register {
  std::function<void(tPAN_REGISTER* p_register)> body{
      [](tPAN_REGISTER* /* p_register */) { ; }};
  void operator()(tPAN_REGISTER* p_register) { body(p_register); };
};
extern struct PAN_Register PAN_Register;

}  // namespace stack_pan_api
}  // namespace mock
}  // namespace test

// END mockcify generation
