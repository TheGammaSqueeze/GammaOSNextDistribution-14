/*
 * Copyright 2020 The Android Open Source Project
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

#ifndef FUZZER_SDP_HELPERS_H_
#define FUZZER_SDP_HELPERS_H_

// NOTE: This file should not be included directly.
//       It is included by the corresponding "...Functions.h" file.

#include <fuzzer/FuzzedDataProvider.h>

#include <algorithm>
#include <vector>

#include "fuzzers/common/commonFuzzHelpers.h"
#include "stack/include/sdp_api.h"
#include "stack/include/sdpdefs.h"
#include "stack/sdp/sdpint.h"
#include "types/raw_address.h"

using namespace bluetooth::legacy::stack::sdp;

#define SDP_MAX_NUM_ELEMS 128
#define SDP_MAX_ELEM_LEN 1024
#define SDP_MAX_ATTRS 1024

constexpr int32_t kMaxVectorSize = 1000;

struct SDP_Sequence_Helper {
  uint8_t num_elem;
  std::shared_ptr<uint8_t> type;
  std::shared_ptr<uint8_t> len;
  std::shared_ptr<uint8_t*> p_val;
  std::vector<std::shared_ptr<uint8_t>> p_val_buffers;
};

// Keep a vector of our initialized db objects
// It will be up to the caller to free this array at the end of a fuzz loop
std::vector<std::shared_ptr<tSDP_DISCOVERY_DB>> sdp_db_vect;
std::vector<uint32_t> sdp_record_handles;
std::vector<SDP_Sequence_Helper> sdp_sequence_vect;
std::vector<std::shared_ptr<tSDP_DISC_REC>> sdp_disc_rec_vect;
std::vector<std::shared_ptr<tSDP_DISC_ATTR>> sdp_disc_attr_vect;
std::vector<std::shared_ptr<tSDP_PROTO_LIST_ELEM>> sdp_protolist_elem_vect;

std::shared_ptr<tSDP_DISC_ATTR> generateArbitrarySdpDiscAttr(
    FuzzedDataProvider*, bool);

static bool initialized = false;
void setupSdpFuzz() {
  if (!initialized) {
    sdp_init();
    initialized = true;
  }
}

// Function to clean up and clear any allocated objects
void cleanupSdpFuzz() {
  // Delete sdp_sequence_vect, sdp_disc_rec_vect, sdp_disc_attr_vect
  sdp_sequence_vect.clear();

  sdp_disc_rec_vect.clear();

  // Delete attributes & protolist elements
  sdp_disc_attr_vect.clear();
  sdp_protolist_elem_vect.clear();

  // Delete all records
  get_legacy_stack_sdp_api()->handle.SDP_DeleteRecord(0);
  sdp_record_handles.clear();

  // Delete Databases
  sdp_db_vect.clear();
}

std::vector<uint16_t> generateArbitraryAttrList(FuzzedDataProvider* fdp) {
  // build out attr_list
  uint16_t num_attrs = fdp->ConsumeIntegralInRange<uint16_t>(0, SDP_MAX_ATTRS);

  std::vector<uint16_t> attr_list;
  for (uint16_t i = 0; i < num_attrs; i++) {
    attr_list.push_back(fdp->ConsumeIntegral<uint16_t>());
  }

  return attr_list;
}

tSDP_DISC_ATVAL generateArbitrarySdpDiscAttrVal(FuzzedDataProvider* fdp) {
  tSDP_DISC_ATVAL new_attrval;

  new_attrval.v.u8 = fdp->ConsumeIntegral<uint8_t>();
  new_attrval.v.u16 = fdp->ConsumeIntegral<uint16_t>();
  new_attrval.v.u32 = fdp->ConsumeIntegral<uint32_t>();
  for (int i = 0; i < 4; i++) {
    new_attrval.v.array[i] = fdp->ConsumeIntegral<uint8_t>();
  }
  new_attrval.v.p_sub_attr = generateArbitrarySdpDiscAttr(fdp, true).get();

  return new_attrval;
}

std::shared_ptr<tSDP_DISC_ATTR> generateArbitrarySdpDiscAttr(
    FuzzedDataProvider* fdp, bool allow_null) {
  // Give it a chance to return a nullptr
  if ((allow_null && !fdp->ConsumeBool()) ||
      sdp_disc_attr_vect.size() > kMaxVectorSize) {
    return nullptr;
  }

  std::shared_ptr<tSDP_DISC_ATTR> new_attr(new tSDP_DISC_ATTR);
  sdp_disc_attr_vect.push_back(new_attr);

  new_attr->p_next_attr = generateArbitrarySdpDiscAttr(fdp, true).get();
  new_attr->attr_id = fdp->ConsumeBool() ? ATTR_ID_BT_PROFILE_DESC_LIST
                                         : fdp->ConsumeIntegral<uint16_t>();
  new_attr->attr_len_type =
      fdp->ConsumeBool() ? 16 : fdp->ConsumeIntegral<uint16_t>();
  new_attr->attr_value = generateArbitrarySdpDiscAttrVal(fdp);

  return new_attr;
}

std::shared_ptr<tSDP_DISC_REC> generateArbitrarySdpDiscRecord(
    FuzzedDataProvider* fdp, bool allow_null) {
  // Give it a chance to return a nullptr
  if (allow_null && !fdp->ConsumeBool()) {
    return nullptr;
  }

  std::shared_ptr<tSDP_DISC_REC> new_rec(new tSDP_DISC_REC);
  sdp_disc_rec_vect.push_back(new_rec);

  new_rec->p_first_attr = generateArbitrarySdpDiscAttr(fdp, true).get();
  new_rec->p_next_rec = generateArbitrarySdpDiscRecord(fdp, true).get();
  new_rec->time_read = fdp->ConsumeIntegral<uint32_t>();
  new_rec->remote_bd_addr = generateRawAddress(fdp);

  return new_rec;
}

tSDP_PROTOCOL_ELEM generateArbitrarySdpProtocolElements(
    FuzzedDataProvider* fdp) {
  tSDP_PROTOCOL_ELEM p_elem;

  // Set our protocol element values
  p_elem.protocol_uuid = fdp->ConsumeIntegral<uint16_t>();
  p_elem.num_params =
      fdp->ConsumeIntegralInRange<uint16_t>(0, SDP_MAX_PROTOCOL_PARAMS);
  uint16_t num_loops = std::min(
      p_elem.num_params, static_cast<unsigned short>(SDP_MAX_PROTOCOL_PARAMS));
  // Regardless of number set above, fill out the entire allocated array
  for (uint16_t i = 0; i < num_loops; i++) {
    p_elem.params[i] = fdp->ConsumeIntegral<uint16_t>();
  }

  return p_elem;
}

std::shared_ptr<tSDP_PROTO_LIST_ELEM> generateArbitrarySdpProtocolElementList(
    FuzzedDataProvider* fdp) {
  std::shared_ptr<tSDP_PROTO_LIST_ELEM> p_elem_list(new tSDP_PROTO_LIST_ELEM);
  sdp_protolist_elem_vect.push_back(p_elem_list);

  // Populate our element list
  p_elem_list->num_elems =
      fdp->ConsumeIntegralInRange<uint16_t>(0, SDP_MAX_LIST_ELEMS);
  uint16_t num_loops = std::min(
      p_elem_list->num_elems, static_cast<unsigned short>(SDP_MAX_LIST_ELEMS));
  for (uint16_t i = 0; i < num_loops; i++) {
    p_elem_list->list_elem[i] = generateArbitrarySdpProtocolElements(fdp);
  }

  return p_elem_list;
}

tSDP_PROTO_LIST_ELEM** generateArbitrarySdpProtocolElementListArray(
    FuzzedDataProvider* fdp, uint16_t* array_size) {
  *array_size = fdp->ConsumeIntegralInRange<uint16_t>(0, SDP_MAX_ATTR_LEN);
  if (*array_size == 0) {
    return nullptr;
  }
  tSDP_PROTO_LIST_ELEM** p_list_array = static_cast<tSDP_PROTO_LIST_ELEM**>(
      calloc(*array_size, sizeof(tSDP_PROTO_LIST_ELEM*)));
  if (p_list_array == nullptr) {
    return nullptr;
  }

  tSDP_PROTO_LIST_ELEM* p = p_list_array[0];
  for (uint16_t i = 0; i < *array_size; i++, p++) {
    p = generateArbitrarySdpProtocolElementList(fdp).get();
  }

  return p_list_array;
}

tSDP_DI_RECORD generateArbitrarySdpDiRecord(FuzzedDataProvider* fdp) {
  tSDP_DI_RECORD record;

  record.vendor = fdp->ConsumeIntegral<uint16_t>();
  record.vendor_id_source = fdp->ConsumeIntegral<uint16_t>();
  record.product = fdp->ConsumeIntegral<uint16_t>();
  record.version = fdp->ConsumeIntegral<uint16_t>();
  record.primary_record = fdp->ConsumeBool();
  size_t num_executable_urls =
      fdp->ConsumeIntegralInRange<size_t>(0, SDP_MAX_ATTR_LEN);
  for (size_t i = 0; i < num_executable_urls; i++) {
    record.client_executable_url[i] = fdp->ConsumeIntegral<char>();
  }
  size_t num_descriptions =
      fdp->ConsumeIntegralInRange<size_t>(0, SDP_MAX_ATTR_LEN);
  for (size_t i = 0; i < num_descriptions; i++) {
    record.service_description[i] = fdp->ConsumeIntegral<char>();
  }
  size_t num_documentation_urls =
      fdp->ConsumeIntegralInRange<size_t>(0, SDP_MAX_ATTR_LEN);
  for (size_t i = 0; i < num_documentation_urls; i++) {
    record.documentation_url[i] = fdp->ConsumeIntegral<char>();
  }

  return record;
}

tSDP_DI_GET_RECORD generateArbitrarySdpDiGetRecord(FuzzedDataProvider* fdp) {
  tSDP_DI_GET_RECORD get_record;
  get_record.spec_id = fdp->ConsumeIntegral<uint16_t>();
  get_record.rec = generateArbitrarySdpDiRecord(fdp);

  return get_record;
}

SDP_Sequence_Helper generateArbitrarySdpElemSequence(FuzzedDataProvider* fdp) {
  SDP_Sequence_Helper ret;

  // Get the number of our elements
  ret.num_elem = fdp->ConsumeIntegralInRange<uint16_t>(1, SDP_MAX_NUM_ELEMS);
  ret.type.reset(new uint8_t[ret.num_elem]);
  ret.len.reset(new uint8_t[ret.num_elem]);
  ret.p_val.reset(new uint8_t*[ret.num_elem]);
  for (uint16_t i = 0; i < ret.num_elem; i++) {
    (ret.type.get())[i] = fdp->ConsumeIntegral<uint8_t>();
    if ((ret.len.get())[i] == 0) {
      (ret.p_val.get())[i] = nullptr;
      (ret.len.get())[i] = 0;
    } else {
      uint8_t buf_size = fdp->ConsumeIntegral<uint8_t>();
      // Link the size to the size of the buffer we're creating
      (ret.len.get())[i] = buf_size;
      std::shared_ptr<uint8_t> p_val_sp(
          reinterpret_cast<uint8_t*>(calloc(buf_size, sizeof(uint8_t))), free);
      ret.p_val_buffers.push_back(p_val_sp);
      (ret.p_val.get())[i] = p_val_sp.get();
      std::vector<uint8_t> bytes = fdp->ConsumeBytes<uint8_t>(buf_size);
      memcpy((ret.p_val.get())[i], bytes.data(), bytes.size());
    }
  }

  // Push this struct to our array so we can delete later
  sdp_sequence_vect.push_back(ret);

  return ret;
}

// Define our callback functions we'll be using within our functions
void sdp_disc_cmpl_cb(const RawAddress& bd_addr, tSDP_STATUS result) {}
void sdp_disc_cmpl_cb2(const RawAddress& bd_addr, tSDP_STATUS result,
                       const void* user_data) {}

#endif  // FUZZER_SDP_HELPERS_H_
