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

#include <base/functional/bind.h>
#include <hardware/bt_csis.h>

#include "bta/include/bta_csis_api.h"
#include "test/common/mock_functions.h"

using bluetooth::csis::CsisClient;
using bluetooth::csis::CsisClientCallbacks;

void CsisClient::AddFromStorage(const RawAddress& addr,
                                const std::vector<uint8_t>& in) {
  inc_func_call_count(__func__);
}
bool CsisClient::GetForStorage(const RawAddress& addr,
                               std::vector<uint8_t>& out) {
  inc_func_call_count(__func__);
  return false;
}
void CsisClient::CleanUp() { inc_func_call_count(__func__); }
bluetooth::csis::CsisClient* CsisClient::Get(void) {
  inc_func_call_count(__func__);
  return nullptr;
}
bool CsisClient::IsCsisClientRunning() {
  inc_func_call_count(__func__);
  return false;
}
void CsisClient::Initialize(CsisClientCallbacks* callbacks,
                            base::Closure initCb) {
  inc_func_call_count(__func__);
}
void CsisClient::DebugDump(int fd) { inc_func_call_count(__func__); }
