/*
 * Copyright 2022 The Android Open Source Project
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

#include <memory>
#include <string>
#include <utility>

#include "common/contextual_callback.h"
#include "hci/hci_packets.h"
#include "module.h"

namespace bluetooth {
namespace hci {

// The RemoteNameRequestModule handles Remote Name Requests, which produce both Remote Name Request
// Completed events, and Remote Host Supported Features Notification events.

using CompletionCallback = common::ContextualOnceCallback<void(ErrorCode)>;
using RemoteHostSupportedFeaturesCallback = common::ContextualOnceCallback<void(uint64_t)>;
using RemoteNameCallback =
    common::ContextualOnceCallback<void(ErrorCode, std::array<uint8_t, 248>)>;

// Historical note: This class is intended to provide a shim at the *HCI* layer, so legacy Remote
// Name Requests can interoperate with the GD ACL scheduler. Thus, we intentionally do not merge
// identical requests, cache responses, or handle request timeouts - we leave this to our callers.
// When GD clients start to use this module, richer functionality should be added.
class RemoteNameRequestModule : public bluetooth::Module {
 public:
  // Dispatch a Remote Name Request
  void StartRemoteNameRequest(
      Address address,
      std::unique_ptr<RemoteNameRequestBuilder> request,
      CompletionCallback on_completion,
      RemoteHostSupportedFeaturesCallback on_remote_host_supported_features_notification,
      RemoteNameCallback on_remote_name_complete);

  // Cancel a Remote Name Request
  void CancelRemoteNameRequest(Address address);

  // Due to controller bugs (b/184239841), an ACL connection completion is sometimes reported in
  // place of an RNR completion This method lets the ACL manager inform the RNR module if this
  // happens, since we don't get the appropriate HCI event.
  void ReportRemoteNameRequestCancellation(Address address);

 private:
  struct impl;
  std::unique_ptr<impl> pimpl_;

 protected:
  void ListDependencies(ModuleList* list) const override;
  void Start() override;
  void Stop() override;
  std::string ToString() const override {
    return std::string("RemoteNameRequestModule");
  }

 public:
  static const ModuleFactory Factory;
  RemoteNameRequestModule();
  ~RemoteNameRequestModule();
};

}  // namespace hci
}  // namespace bluetooth