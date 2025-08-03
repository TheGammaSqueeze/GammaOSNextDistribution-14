/******************************************************************************
 *
 *  Copyright 2018-2019,2022-2023 NXP
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
#ifdef NXP_BOOTTIME_UPDATE
#ifndef VENDOR_NXP_NXPNFC_V1_0_NXPNFC_H
#define VENDOR_NXP_NXPNFC_V1_0_NXPNFC_H

#include <android/binder_auto_utils.h>
#include <android/binder_enums.h>
#include <android/binder_ibinder.h>
#include <android/binder_interface_utils.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android/hardware/secure_element/1.0/ISecureElementHalCallback.h>
#include <android/hardware/secure_element/1.1/ISecureElement.h>
#include <android/hardware/secure_element/1.1/ISecureElementHalCallback.h>
#include <binder/IServiceManager.h>
#include <hardware/hardware.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <vendor/nxp/nxpese/1.0/INxpEse.h>

#include "hal_nxpese.h"
namespace vendor {
namespace nxp {
namespace nxpese {
namespace V1_0 {
namespace implementation {

using ::android::sp;
using ::android::hardware::hidl_array;
using android::hardware::hidl_handle;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::secure_element::V1_0::ISecureElementHalCallback;
using ::android::hidl::base::V1_0::DebugInfo;
using ::android::hidl::base::V1_0::IBase;
using ::vendor::nxp::nxpese::V1_0::INxpEse;
struct NxpEse : public INxpEse {
  // Methods from ::android::hidl::base::V1_0::IBase follow.
  Return<void> debug(const hidl_handle& handle,
                     const hidl_vec<hidl_string>& options) override;
  Return<void> ioctl(uint64_t ioctlType, const hidl_vec<uint8_t>& inOutData,
                     ioctl_cb _hidl_cb) override;
  static Return<void> setSeCallBack(
      const android::sp<ISecureElementHalCallback>& clientCallback);
  static Return<void> setSeCallBack_1_1(
      const android::sp<
          ::android::hardware::secure_element::V1_1::ISecureElementHalCallback>&
          clientCallback);
  static Return<void> setVirtualISOCallBack(
      const android::sp<ISecureElementHalCallback>& clientCallback);
  static Return<void> setVirtualISOCallBack_1_1(
      const android::sp<
          ::android::hardware::secure_element::V1_1::ISecureElementHalCallback>&
          clientCallback);
  static void initSEService();
  static void initVIrtualISOService();
 private:
  Return<void> ioctlHandler(uint64_t ioctlType,
                            ese_nxp_IoctlInOutData_t& inpOutData);
};

}  // namespace implementation
}  // namespace V1_0
}  // namespace nxpese
}  // namespace nxp
}  // namespace vendor

#endif  // VENDOR_NXP_NXPNFC_V1_0_NXPNFC_H
#endif // NXP_BOOTTIME_UPDATE