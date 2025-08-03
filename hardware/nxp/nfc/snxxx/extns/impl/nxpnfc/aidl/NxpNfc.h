/******************************************************************************
 *
 *  Copyright 2022 NXP
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
#pragma once

#ifndef VENDOR_NXP_NXPNFC_AIDL_NXPNFC_H
#define VENDOR_NXP_NXPNFC_AIDL_NXPNFC_H

#include <aidl/vendor/nxp/nxpnfc_aidl/BnNxpNfc.h>
#include <android-base/logging.h>

enum Constants : uint16_t {
  HAL_NFC_ESE_HARD_RESET = 5,
};

namespace aidl {
namespace vendor {
namespace nxp {
namespace nxpnfc_aidl {

using ::aidl::vendor::nxp::nxpnfc_aidl::INxpNfc;

class NxpNfc : public BnNxpNfc {
 public:
  ::ndk::ScopedAStatus getVendorParam(const std::string& key,
                                      std::string* _aidl_return) override;
  ::ndk::ScopedAStatus setVendorParam(const std::string& key,
                                      const std::string& value,
                                      bool* _aidl_return) override;
  ::ndk::ScopedAStatus resetEse(int64_t resetType, bool* _aidl_return) override;
  ::ndk::ScopedAStatus setNxpTransitConfig(const std::string& strVal,
                                           bool* _aidl_return) override;
};

}  // namespace nxpnfc_aidl
}  // namespace nxp
}  // namespace vendor
}  // namespace aidl
#endif  // VENDOR_NXP_NXPNFC_AIDL_NXPNFC_H
