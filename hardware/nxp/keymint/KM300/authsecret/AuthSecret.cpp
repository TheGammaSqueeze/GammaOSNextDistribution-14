/*
 * Copyright (C) 2018 The Android Open Source Project
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
 ** Copyright 2022-2023 NXP
 **
 *********************************************************************************/

#define LOG_TAG "AuthSecret-Hal"
#include "AuthSecret.h"
#include "AuthSecretHelper.h"

using keymint::javacard::OmapiTransport;

const std::vector<uint8_t> gAuthSecretAppletAID = {0xA0, 0x00, 0x00, 0x03, 0x96,
                                                   0x54, 0x53, 0x00, 0x00, 0x00,
                                                   0x01, 0x00, 0x52};

static OmapiTransport *gTransport = new OmapiTransport(gAuthSecretAppletAID);
static AuthSecretHelper *gAuthSecretImplInstance = AuthSecretHelper::getInstance();

namespace aidl {
namespace android {
namespace hardware {
namespace authsecret {

static void authSecretTimerExpiryFunc(union sigval arg) {
  LOG(INFO) << StringPrintf(
      "%s: Enter. Clearing AuthSecret Approved Status !!!", __func__);
  AuthSecret *obj = (AuthSecret *)arg.sival_ptr;
  if (obj != nullptr)
    obj->clearAuthApprovedStatus();
}

void AuthSecret::clearAuthApprovedStatus() {
  LOG(INFO) << StringPrintf("%s: Enter", __func__);
  std::vector<uint8_t> cmd;
  std::vector<uint8_t> timeout;
  std::vector<uint8_t> input;
  bool status = gAuthSecretImplInstance->constructApdu(
      Instruction::INS_CLEAR_APPROVED_STATUS, input, cmd, std::move(timeout));
  if (!status) {
    LOG(ERROR) << StringPrintf("%s: constructApdu failed", __func__);
    return;
  }

  std::vector<uint8_t> resp;
  uint8_t retry = 0;
  do {
    if (!gTransport->sendData(cmd, resp)) {
      LOG(ERROR) << StringPrintf("%s: Error in sending data in sendData.",
                                 __func__);
    } else {
      if ((resp.size() < 2) || (getApduStatus(resp) != APDU_RESP_STATUS_OK)) {
        LOG(ERROR) << StringPrintf("%s: failed", __func__);
      } else { break; }
    }
    usleep(1 * ONE_SEC);
  } while (++retry < MAX_RETRY_COUNT);


  LOG(INFO) << StringPrintf("%s: Exit", __func__);
}

// Methods from ::android::hardware::authsecret::IAuthSecret follow.
::ndk::ScopedAStatus
AuthSecret::setPrimaryUserCredential(const std::vector<uint8_t> &in_secret) {
  LOG(INFO) << StringPrintf("%s: Enter", __func__);
  std::vector<uint8_t> cmd;
  std::vector<uint8_t> timeout;
  bool status = gAuthSecretImplInstance->constructApdu(
      Instruction::INS_VERIFY_PIN, in_secret, cmd, std::move(timeout));
  if (!status) {
    LOG(ERROR) << StringPrintf("%s: constructApdu failed", __func__);
    return ::ndk::ScopedAStatus::ok();
  }

  mAuthClearTimer.kill();

  clearAuthApprovedStatus();

  std::vector<uint8_t> resp;
  uint8_t retry = 0;
  do {
    if (!gTransport->sendData(cmd, resp)) {
      LOG(ERROR) << StringPrintf("%s: Error in sending data in sendData.",
                                 __func__);
    } else {
      break;
    }
  } while (++retry < MAX_RETRY_COUNT);

  if ((resp.size() < 2) || (getApduStatus(resp) != APDU_RESP_STATUS_OK) ||
      !gAuthSecretImplInstance->checkVerifyStatus(resp)) {
    clearAuthApprovedStatus();
    return ::ndk::ScopedAStatus::ok();
  }

  uint64_t clearAuthTimeout =
      gAuthSecretImplInstance->extractTimeoutValue(std::move(resp));
  LOG(INFO) << StringPrintf("%s: AuthSecret Clear status Timeout = %ld secs",
                            __func__, (long)clearAuthTimeout);
  if (clearAuthTimeout) {
    if (!mAuthClearTimer.set(clearAuthTimeout * 1000, this,
                             authSecretTimerExpiryFunc)) {
      LOG(ERROR) << StringPrintf("%s: Set Timer Failed !!!", __func__);
      clearAuthApprovedStatus();
    }
  }
  gTransport->closeConnection();
  LOG(INFO) << StringPrintf("%s: Exit", __func__);
  return ::ndk::ScopedAStatus::ok();
}

} // namespace authsecret
} // namespace hardware
} // namespace android
} // aidl
