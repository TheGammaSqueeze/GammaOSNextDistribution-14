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

#define LOG_TAG "bt_headless_sdp"

#include "test/headless/sdp/sdp.h"

#include <future>

#include "base/logging.h"     // LOG() stdout and android log
#include "bta/dm/bta_dm_int.h"
#include "bta/include/bta_api.h"
#include "os/log.h"
#include "osi/include/osi.h"  // UNUSED_ATTR
#include "stack/include/sdp_api.h"
#include "test/headless/get_options.h"
#include "test/headless/headless.h"
#include "test/headless/sdp/sdp_db.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

using namespace bluetooth::legacy::stack::sdp;
using namespace bluetooth::test::headless;

static void bta_jv_start_discovery_callback(
    UNUSED_ATTR const RawAddress& bd_addr, tSDP_STATUS result,
    const void* user_data) {
  auto promise =
      static_cast<std::promise<uint16_t>*>(const_cast<void*>(user_data));
  promise->set_value(result);
}

namespace {

constexpr size_t kMaxDiscoveryRecords = 1024;

int sdp_query_uuid([[maybe_unused]] unsigned int num_loops,
                   [[maybe_unused]] const RawAddress& raw_address,
                   [[maybe_unused]] const bluetooth::Uuid& uuid) {
  SdpDb sdp_discovery_db(kMaxDiscoveryRecords);

  if (!get_legacy_stack_sdp_api()->service.SDP_InitDiscoveryDb(
          sdp_discovery_db.RawPointer(), sdp_discovery_db.Length(),
          1,  // num_uuid,
          &uuid, 0, nullptr)) {
    LOG_CONSOLE("Unable to initialize sdp discovery");
    return -1;
  }
  LOG_CONSOLE("Initialized sdp discovery database");

  std::promise<tSDP_STATUS> promise;
  auto future = promise.get_future();

  sdp_discovery_db.Print(stdout);

  if (!get_legacy_stack_sdp_api()->service.SDP_ServiceSearchAttributeRequest2(
          raw_address, sdp_discovery_db.RawPointer(),
          bta_jv_start_discovery_callback, (void*)&promise)) {
    fprintf(stdout, "%s Failed to start search attribute request\n", __func__);
    return -2;
  }
  LOG_CONSOLE("Started service search for uuid:%s", uuid.ToString().c_str());

  const tSDP_STATUS result = future.get();
  if (result != SDP_SUCCESS) {
    fprintf(stdout, "Failed search discovery result:%s\n",
            sdp_status_text(result).c_str());
    return result;
  }

  LOG_CONSOLE("Found records peer:%s uuid:%s", raw_address.ToString().c_str(),
              uuid.ToString().c_str());
  for (unsigned i = 0; i < BTA_MAX_SERVICE_ID; i++) {
    uint16_t uuid_as16Bit = bta_service_id_to_uuid_lkup_tbl[i];
    tSDP_DISC_REC* rec = SDP_FindServiceInDb(sdp_discovery_db.RawPointer(),
                                             uuid_as16Bit, nullptr);
    if (rec != nullptr) {
      LOG_CONSOLE("   uuid:0x%x", uuid_as16Bit);
    }
  }

  return 0;
}

}  // namespace

int bluetooth::test::headless::Sdp::Run() {
  if (options_.loop_ < 1) {
    printf("This test requires at least a single loop\n");
    options_.Usage();
    return -1;
  }
  if (options_.device_.size() != 1) {
    printf("This test requires a single device specified\n");
    options_.Usage();
    return -1;
  }
  if (options_.uuid_.size() != 1) {
    printf("This test requires a single uuid specified\n");
    options_.Usage();
    return -1;
  }

  return RunOnHeadlessStack<int>([this]() {
    return sdp_query_uuid(options_.loop_, options_.device_.front(),
                          options_.uuid_.front());
  });
}
