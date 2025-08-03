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

#include <hardware/bluetooth.h>

#include <array>
#include <optional>
#include <vector>

typedef std::array<uint8_t, 16> Octet16;

namespace bluetooth {
namespace csis {

/** Connection State */
enum class ConnectionState : uint8_t {
  DISCONNECTED = 0,
  CONNECTING,
  CONNECTED,
  DISCONNECTING,
};

enum class CsisGroupLockStatus {
  SUCCESS = 0,
  FAILED_INVALID_GROUP,
  FAILED_GROUP_EMPTY,
  FAILED_GROUP_NOT_CONNECTED,
  FAILED_LOCKED_BY_OTHER,
  FAILED_OTHER_REASON,
  LOCKED_GROUP_MEMBER_LOST,
};

static constexpr uint8_t CSIS_RANK_INVALID = 0x00;

class CsisClientCallbacks {
 public:
  virtual ~CsisClientCallbacks() = default;

  /** Callback for profile connection state change */
  virtual void OnConnectionState(const RawAddress& addr,
                                 ConnectionState state) = 0;

  /** Callback for the new available device */
  virtual void OnDeviceAvailable(const RawAddress& addr, int group_id,
                                 int group_size, int rank,
                                 const bluetooth::Uuid& uuid) = 0;

  /* Callback for available set member*/
  virtual void OnSetMemberAvailable(const RawAddress& address,
                                    int group_id) = 0;

  /* Callback for lock changed in the group */
  virtual void OnGroupLockChanged(int group_id, bool locked,
                                  CsisGroupLockStatus status) = 0;
};

class CsisClientInterface {
 public:
  virtual ~CsisClientInterface() = default;

  /** Register the Csis Client profile callbacks */
  virtual void Init(CsisClientCallbacks* callbacks) = 0;

  /** Connect to Csis Client */
  virtual void Connect(const RawAddress& addr) = 0;

  /** Disconnect from Csis Client */
  virtual void Disconnect(const RawAddress& addr) = 0;

  /** Lock/Unlock Csis group */
  virtual void LockGroup(int group_id, bool lock) = 0;

  /* Called when unbonded. */
  virtual void RemoveDevice(const RawAddress& address) = 0;

  /** Closes the interface */
  virtual void Cleanup(void) = 0;
};

} /* namespace csis */
} /* namespace bluetooth */
