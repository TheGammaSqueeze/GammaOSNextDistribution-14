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
#pragma once

#include <vector>

#include "base/functional/callback.h"
#include "bind_helpers.h"
#include "bta/include/bta_groups.h"
#include "hardware/bt_csis.h"

namespace bluetooth {
namespace csis {

using CsisLockCb = base::OnceCallback<void(int group_id, bool locked,
                                           CsisGroupLockStatus status)>;

class CsisClient {
 public:
  virtual ~CsisClient() = default;
  static void Initialize(bluetooth::csis::CsisClientCallbacks* callbacks,
                         base::Closure initCb);
  static void AddFromStorage(const RawAddress& addr,
                             const std::vector<uint8_t>& in);
  static bool GetForStorage(const RawAddress& addr, std::vector<uint8_t>& out);
  static void CleanUp();
  static CsisClient* Get();
  static void DebugDump(int fd);
  static bool IsCsisClientRunning();
  virtual void Connect(const RawAddress& addr) = 0;
  virtual void Disconnect(const RawAddress& addr) = 0;
  virtual void RemoveDevice(const RawAddress& address) = 0;
  virtual int GetGroupId(
      const RawAddress& addr,
      bluetooth::Uuid uuid = bluetooth::groups::kGenericContextUuid) = 0;
  virtual void LockGroup(int group_id, bool lock, CsisLockCb cb) = 0;
  virtual std::vector<RawAddress> GetDeviceList(int group_id) = 0;
  virtual int GetDesiredSize(int group_id) = 0;
};
}  // namespace csis
}  // namespace bluetooth
