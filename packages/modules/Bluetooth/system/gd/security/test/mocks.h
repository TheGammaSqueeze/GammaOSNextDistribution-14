/******************************************************************************
 *
 *  Copyright 2019 The Android Open Source Project
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

#include <gmock/gmock.h>

#include "hci/address_with_type.h"
#include "hci/le_security_interface.h"
#include "security/ui.h"

namespace bluetooth {
namespace security {

class UIMock : public UI {
 public:
  UIMock() = default;
  UIMock(const UIMock&) = delete;
  UIMock& operator=(const UIMock&) = delete;

  ~UIMock() = default;

  // Convert these to accept ConfirmationData
  MOCK_METHOD2(DisplayPairingPrompt, void(const bluetooth::hci::AddressWithType& address, std::string name));
  MOCK_METHOD1(Cancel, void(const bluetooth::hci::AddressWithType& address));
  MOCK_METHOD1(DisplayConfirmValue, void(ConfirmationData));
  MOCK_METHOD1(DisplayYesNoDialog, void(ConfirmationData));
  MOCK_METHOD1(DisplayEnterPasskeyDialog, void(ConfirmationData));
  MOCK_METHOD1(DisplayPasskey, void(ConfirmationData));
  MOCK_METHOD1(DisplayEnterPinDialog, void(ConfirmationData));
};

class LeSecurityInterfaceMock : public hci::LeSecurityInterface {
 public:
  MOCK_METHOD2(EnqueueCommand, void(std::unique_ptr<hci::LeSecurityCommandBuilder> command,
                                    common::ContextualOnceCallback<void(hci::CommandCompleteView)> on_complete));
  MOCK_METHOD2(EnqueueCommand, void(std::unique_ptr<hci::LeSecurityCommandBuilder> command,
                                    common::ContextualOnceCallback<void(hci::CommandStatusView)> on_status));
};

}  // namespace security
}  // namespace bluetooth
