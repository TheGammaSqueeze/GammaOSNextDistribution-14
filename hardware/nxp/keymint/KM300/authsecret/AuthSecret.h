/*
 * Copyright (C) 2020 The Android Open Source Project
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
/******************************************************************************
 **
 ** The original Work has been changed by NXP.
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 ** http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 **
 ** Copyright 2023 NXP
 **
 *********************************************************************************/

#pragma once

#include "EseTransportUtils.h"
#include "IntervalTimer.h"
#include "OmapiTransport.h"
#include <aidl/android/hardware/authsecret/BnAuthSecret.h>

namespace aidl {
namespace android {
namespace hardware {
namespace authsecret {

class AuthSecret : public BnAuthSecret {
public:
  /**
   * \brief Constructor. Invoked during service start.
   */
  explicit AuthSecret() { clearAuthApprovedStatus(); }

  /**
   * \brief Function to clear the Auth Approved status in IAR applet
   *
   * \retval None
   *
   */
  void clearAuthApprovedStatus();

  // Methods from ::android::hardware::authsecret::IAuthSecret follow.

  /**
   * \brief Sends the secret blob to IAR applet
   *
   * \retval None
   *
   * \param[in_secret]  Secret Blob.
   */
  ::ndk::ScopedAStatus
  setPrimaryUserCredential(const std::vector<uint8_t> &in_secret) override;

private:
  IntervalTimer mAuthClearTimer;

  /**
   * \brief Function to convert SW byte array to integer
   *
   * \retval SW status in integer format
   *
   * \param[inputData] Response APDU data.
   */
  inline uint16_t getApduStatus(std::vector<uint8_t> &inputData) {
    // Last two bytes are the status SW0SW1
    uint8_t SW0 = inputData.at(inputData.size() - 2);
    uint8_t SW1 = inputData.at(inputData.size() - 1);
    return (SW0 << 8 | SW1);
  }
};

} // namespace authsecret
} // namespace hardware
} // namespace android
} // aidl
