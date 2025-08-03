/******************************************************************************
 *
 *  Copyright 2022-2023 NXP
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
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

#include "NxpNfc.h"

#include <log/log.h>

#include "phNxpNciHal.h"
#include "phNxpNciHal_Adaptation.h"

extern bool nfc_debug_enabled;

namespace aidl {
namespace vendor {
namespace nxp {
namespace nxpnfc_aidl {

::ndk::ScopedAStatus NxpNfc::getVendorParam(const std::string& key,
                                            std::string* _aidl_return) {
  LOG(INFO) << "NxpNfc AIDL - getVendorParam";
  *_aidl_return = phNxpNciHal_getSystemProperty(key);
  return ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus NxpNfc::setVendorParam(const std::string& key,
                                            const std::string& value,
                                            bool* _aidl_return) {
  LOG(INFO) << "NxpNfc AIDL - setVendorParam";
  *_aidl_return = phNxpNciHal_setSystemProperty(key, value);
  return ndk::ScopedAStatus::ok();
}

::ndk::ScopedAStatus NxpNfc::resetEse(int64_t resetType, bool* _aidl_return) {
  LOG(INFO) << "NxpNfc AIDL - resetEse";
  NFCSTATUS status = NFCSTATUS_FAILED;
  *_aidl_return = false;
  ALOGD("NxpNfc::resetEse Entry");

  status = phNxpNciHal_resetEse(resetType);
  if (NFCSTATUS_SUCCESS == status) {
    *_aidl_return = true;
    status = NFCSTATUS_SUCCESS;
    ALOGD("Reset request (%02x) completed", (uint8_t)resetType);
  } else {
    ALOGE("Reset request (%02x) failed", (uint8_t)resetType);
  }

  ALOGD("NxpNfc::resetEse Exit");
  return status == NFCSTATUS_SUCCESS
             ? ndk::ScopedAStatus::ok()
             : ndk::ScopedAStatus::fromServiceSpecificError(
                   static_cast<int64_t>(status));
}

::ndk::ScopedAStatus NxpNfc::setNxpTransitConfig(const std::string& strVal,
                                                 bool* _aidl_return) {
  *_aidl_return = false;
  ALOGD("NxpNfc::setNxpTransitConfig Entry");

  *_aidl_return = phNxpNciHal_setNxpTransitConfig((char*)strVal.c_str());

  ALOGD("NxpNfc::setNxpTransitConfig Exit");
  return *_aidl_return == true ? ndk::ScopedAStatus::ok()
                               : ndk::ScopedAStatus::fromServiceSpecificError(
                                     static_cast<bool>(*_aidl_return));
}

}  // namespace nxpnfc_aidl
}  // namespace nxp
}  // namespace vendor
}  // namespace aidl
