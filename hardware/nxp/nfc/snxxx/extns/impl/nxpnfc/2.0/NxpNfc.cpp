/******************************************************************************
 *
 *  Copyright 2018-2021 NXP
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

#include <log/log.h>

#include "NxpNfc.h"
#include "phNxpNciHal.h"
#include "phNxpNciHal_Adaptation.h"

extern bool nfc_debug_enabled;

namespace vendor {
namespace nxp {
namespace nxpnfc {
namespace V2_0 {
namespace implementation {

Return<void> NxpNfc::getVendorParam(const ::android::hardware::hidl_string& key,
                                    getVendorParam_cb _hidl_cb) {
  string val = phNxpNciHal_getSystemProperty(key);
  _hidl_cb(val);
  return Void();
}

Return<bool> NxpNfc::setVendorParam(
    const ::android::hardware::hidl_string& key,
    const ::android::hardware::hidl_string& value) {
  return phNxpNciHal_setSystemProperty(key, value);
}

Return<bool> NxpNfc::resetEse(uint64_t resetType) {
  NFCSTATUS status = NFCSTATUS_FAILED;
  bool ret = false;
  ALOGD("NxpNfc::resetEse Entry");

  status = phNxpNciHal_resetEse(resetType);
  if (NFCSTATUS_SUCCESS == status) {
    ret = true;
    status = NFCSTATUS_SUCCESS;
    ALOGD("Reset request (%02x) completed", (uint8_t)resetType);
  } else {
    ALOGE("Reset request (%02x) failed", (uint8_t)resetType);
  }

  ALOGD("NxpNfc::resetEse Exit");
  return ret;
}

Return<bool> NxpNfc::setEseUpdateState(NxpNfcHalEseState eSEState) {
  bool status = false;

  ALOGD("NxpNfc::setEseUpdateState Entry");

  if (eSEState == NxpNfcHalEseState::HAL_NFC_ESE_JCOP_UPDATE_COMPLETED ||
      eSEState == NxpNfcHalEseState::HAL_NFC_ESE_LS_UPDATE_COMPLETED) {
    ALOGD(
        "NxpNfc::setEseUpdateState state == HAL_NFC_ESE_JCOP_UPDATE_COMPLETED");
    seteSEClientState((uint8_t)eSEState);
    eSEClientUpdate_NFC_Thread();
  }
  if (eSEState == NxpNfcHalEseState::HAL_NFC_ESE_UPDATE_COMPLETED) {
    status = phNxpNciHal_Abort();
  }

  ALOGD("NxpNfc::setEseUpdateState Exit");
  return status;
}

Return<bool> NxpNfc::setNxpTransitConfig(
    const ::android::hardware::hidl_string& strval) {
  bool status = true;
  ALOGD("NxpNfc::setNxpTransitConfig Entry");

  status = phNxpNciHal_setNxpTransitConfig((char*)strval.c_str());

  ALOGD("NxpNfc::setNxpTransitConfig Exit");
  return status;
}

Return<bool> NxpNfc::isJcopUpdateRequired() {
  bool status = 0;
  ALOGD("NxpNfc::isJcopUpdateRequired Entry");

#ifdef NXP_BOOTTIME_UPDATE
  status = getJcopUpdateRequired();
#endif

  ALOGD("NxpNfc::isJcopUpdateRequired Exit");
  return status;
}

Return<bool> NxpNfc::isLsUpdateRequired() {
  bool status = 0;
  ALOGD("NxpNfc::isLsUpdateRequired Entry");

#ifdef NXP_BOOTTIME_UPDATE
  status = getLsUpdateRequired();
#endif

  ALOGD("NxpNfc::isLsUpdateRequired Exit");
  return status;
}

}  // namespace implementation
}  // namespace V2_0
}  // namespace nxpnfc
}  // namespace nxp
}  // namespace vendor
