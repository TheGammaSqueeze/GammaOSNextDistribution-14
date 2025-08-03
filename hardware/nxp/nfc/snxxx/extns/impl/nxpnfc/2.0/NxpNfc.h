/******************************************************************************
 *
 *  Copyright 2018-2020 NXP
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

#ifndef VENDOR_NXP_NXPNFC_V2_0_NXPNFC_H
#define VENDOR_NXP_NXPNFC_V2_0_NXPNFC_H

#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <vendor/nxp/nxpnfc/2.0/INxpNfc.h>

enum Constants : uint16_t {
  HAL_NFC_ESE_HARD_RESET = 5,
};

namespace vendor {
namespace nxp {
namespace nxpnfc {
namespace V2_0 {
namespace implementation {

using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hidl::base::V1_0::IBase;
using ::vendor::nxp::nxpnfc::V2_0::INxpNfc;

struct NxpNfc : public INxpNfc {
  Return<void> getVendorParam(const ::android::hardware::hidl_string& key,
                              getVendorParam_cb _hidl_cb) override;
  Return<bool> setVendorParam(
      const ::android::hardware::hidl_string& key,
      const ::android::hardware::hidl_string& value) override;
  Return<bool> resetEse(uint64_t resetType) override;
  Return<bool> setEseUpdateState(NxpNfcHalEseState state) override;
  Return<bool> setNxpTransitConfig(
      const ::android::hardware::hidl_string& strval) override;
  Return<bool> isJcopUpdateRequired() override;
  Return<bool> isLsUpdateRequired() override;
};

}  // namespace implementation
}  // namespace V2_0
}  // namespace nxpnfc
}  // namespace nxp
}  // namespace vendor

#endif  // VENDOR_NXP_NXPNFC_V2_0_NXPNFC_H
