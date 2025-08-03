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
 *   Functions generated:14
 */

#include <base/functional/bind.h>
#include <base/location.h>

#include <cstdint>
#include <string>
#include <vector>

#include "bta/include/bta_ag_api.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

tBTA_STATUS BTA_AgEnable(tBTA_AG_CBACK* p_cback) {
  inc_func_call_count(__func__);
  return BTA_SUCCESS;
}
void BTA_AgAudioClose(uint16_t handle) { inc_func_call_count(__func__); }
void BTA_AgAudioOpen(uint16_t handle, tBTA_AG_PEER_CODEC disabled_codecs) {
  inc_func_call_count(__func__);
}
void BTA_AgClose(uint16_t handle) { inc_func_call_count(__func__); }
void BTA_AgDeregister(uint16_t handle) { inc_func_call_count(__func__); }
void BTA_AgDisable() { inc_func_call_count(__func__); }
void BTA_AgOpen(uint16_t handle, const RawAddress& bd_addr) {
  inc_func_call_count(__func__);
}
void BTA_AgRegister(tBTA_SERVICE_MASK services, tBTA_AG_FEAT features,
                    const std::vector<std::string>& service_names,
                    uint8_t app_id) {
  inc_func_call_count(__func__);
}
void BTA_AgResult(uint16_t handle, tBTA_AG_RES result,
                  const tBTA_AG_RES_DATA& data) {
  inc_func_call_count(__func__);
}
void BTA_AgSetActiveDevice(const RawAddress& active_device_addr) {
  inc_func_call_count(__func__);
}
void BTA_AgSetCodec(uint16_t handle, tBTA_AG_PEER_CODEC codec) {
  inc_func_call_count(__func__);
}
void BTA_AgSetScoOffloadEnabled(bool value) { inc_func_call_count(__func__); }
void BTA_AgSetScoAllowed(bool value) { inc_func_call_count(__func__); }
bool is_hfp_aptx_voice_enabled() {
  inc_func_call_count(__func__);
  return false;
};