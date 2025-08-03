/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

#include "bta_dm_api_mock.h"

static dm::MockBtaDmInterface* dm_interface = nullptr;

void dm::SetMockBtaDmInterface(MockBtaDmInterface* mock_bta_dm_interface) {
  dm_interface = mock_bta_dm_interface;
}

void BTA_DmBleScan(bool start, uint8_t duration, bool low_latency_scan) {
  LOG_ASSERT(dm_interface) << "Mock BTA DM interface not set!";
  return dm_interface->BTA_DmBleScan(start, duration, low_latency_scan);
}

void BTA_DmBleCsisObserve(bool observe, tBTA_DM_SEARCH_CBACK* p_results_cb) {
  LOG_ASSERT(dm_interface) << "Mock BTA DM interface not set!";
  return dm_interface->BTA_DmBleCsisObserve(observe, p_results_cb);
}

void BTA_DmSirkSecCbRegister(tBTA_DM_SEC_CBACK* p_cback) {
  LOG_ASSERT(dm_interface) << "Mock BTA DM interface not set!";
  return dm_interface->BTA_DmSirkSecCbRegister(p_cback);
}

void BTA_DmSirkConfirmDeviceReply(const RawAddress& bd_addr, bool accept) {
  LOG_ASSERT(dm_interface) << "Mock BTA DM interface not set!";
  return dm_interface->BTA_DmSirkConfirmDeviceReply(bd_addr, accept);
}
