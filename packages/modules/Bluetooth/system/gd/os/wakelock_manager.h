/******************************************************************************
 *
 *  Copyright 2021 Google, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#pragma once

#include <memory>
#include <mutex>
#include <string>

#include <flatbuffers/flatbuffers.h>

#include "handler.h"
#include "wakelock_manager_generated.h"

namespace bluetooth {
namespace os {

class WakelockManager {
 public:
  static const std::string kBtWakelockId;

  static WakelockManager& Get() {
    static WakelockManager instance;
    return instance;
  }

  // The set of functions required by GD to grab wake locks. A caller with a custom wakelock implementation should
  // implement this class and passed into the stack through SetCallouts()
  class OsCallouts {
   public:
    virtual ~OsCallouts() = default;
    virtual void AcquireCallout(const std::string& lock_name) = 0;
    virtual void ReleaseCallout(const std::string& lock_name) = 0;
  };

  // Set the Bluetooth OS callouts to |callouts|.
  //
  // This function should be called when native kernel wakelock are not used directly.
  // If this function is not called, or |callouts| is nullptr, then native kernel wakelock will be used.
  // When |callouts| are used, the callbacks are going to be invoked asynchronously to avoid being blocked by upper
  // layer delays. Therefore, a handler is needed and the callout result will be ignored.
  //
  // This method must be called before calling Acquire() or Release()
  void SetOsCallouts(OsCallouts* callouts, Handler* handler);

  // Acquire the Bluetooth wakelock.
  // Return true on success, otherwise false.
  // The function is thread safe.
  bool Acquire();

  // Release the Bluetooth wakelock.
  // Return true on success, otherwise false.
  // The function is thread safe.
  bool Release();

  // Cleanup the wakelock internal runtime state.
  // This will NOT clean up the callouts
  void CleanUp();

  // Dump wakelock-related debug info to a flat buffer defined in wakelock_manager.fbs
  flatbuffers::Offset<WakelockManagerData> GetDumpsysData(flatbuffers::FlatBufferBuilder* fb_builder);

  ~WakelockManager();

 private:
  WakelockManager();

  std::recursive_mutex mutex_;
  bool initialized_ = false;
  OsCallouts* os_callouts_ = nullptr;
  Handler* os_callouts_handler_ = nullptr;
  bool is_native_ = true;

  struct Stats;
  std::unique_ptr<Stats> pstats_;
};

}  // namespace os
}  // namespace bluetooth
