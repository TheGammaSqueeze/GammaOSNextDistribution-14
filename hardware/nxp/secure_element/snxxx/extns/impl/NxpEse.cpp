/******************************************************************************
 *
 *  Copyright 2018-2023 NXP
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
#include "NxpEse.h"
#include "eSEClient.h"
#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <memunreachable/memunreachable.h>

#include "phNxpEse_Api.h"

namespace vendor {
namespace nxp {
namespace nxpese {
namespace V1_0 {
namespace implementation {
using android::base::StringPrintf;
// using android::hardware::secure_element::V1_0::implementation::SecureElement;
static android::sp<ISecureElementHalCallback> seCallback;
static android::sp<
    ::android::hardware::secure_element::V1_1::ISecureElementHalCallback>
    seCallback_1_1;
static android::sp<ISecureElementHalCallback> virtualISOCallback;
static android::sp<
    ::android::hardware::secure_element::V1_1::ISecureElementHalCallback>
    virtualISOCallback_1_1;
bool isSeHalV1_1 = false;
// Methods from ::vendor::nxp::nxpese::V1_0::INxpEse follow.
Return<void> NxpEse::setSeCallBack(
    const android::sp<ISecureElementHalCallback>& clientCallback) {
  seCallback = clientCallback;
  return Void();
}

Return<void> NxpEse::setSeCallBack_1_1(
    const sp<
        ::android::hardware::secure_element::V1_1::ISecureElementHalCallback>&
        clientCallback) {
  seCallback_1_1 = clientCallback;
  isSeHalV1_1 = true;
  return Void();
}

Return<void> NxpEse::setVirtualISOCallBack(
    const android::sp<ISecureElementHalCallback>& clientCallback) {
  virtualISOCallback = clientCallback;
  return Void();
}

Return<void> NxpEse::setVirtualISOCallBack_1_1(
    const android::sp<
        ::android::hardware::secure_element::V1_1::ISecureElementHalCallback>&
        clientCallback) {
  virtualISOCallback_1_1 = clientCallback;
  isSeHalV1_1 = true;
  return Void();
}
void NxpEse::initSEService() {
  ESESTATUS status = ESESTATUS_SUCCESS;
  ESESTATUS deInitStatus = ESESTATUS_SUCCESS;
  phNxpEse_initParams initParams;
  memset(&initParams, 0x00, sizeof(phNxpEse_initParams));
  initParams.initMode = ESE_MODE_NORMAL;
  initParams.mediaType = ESE_PROTOCOL_MEDIA_SPI_APDU_GATE;

  if (!seCallback && !isSeHalV1_1) return;

  if (!seCallback_1_1 && isSeHalV1_1) return;

  status = phNxpEse_open(initParams);
  if (status != ESESTATUS_SUCCESS) {
    goto exit;
  }

  status = phNxpEse_SetEndPoint_Cntxt(0);
  if (status != ESESTATUS_SUCCESS) {
    goto exit1;
  }
  status = phNxpEse_init(initParams);
  if (status != ESESTATUS_SUCCESS) {
    goto exit1;
  }
  status = phNxpEse_ResetEndPoint_Cntxt(0);
  if (status != ESESTATUS_SUCCESS) {
    goto exit2;
  }

  LOG(INFO) << "ESE SPI init complete !!!";
exit2:
  deInitStatus = phNxpEse_deInit();
exit1:
  status = phNxpEse_close(deInitStatus);
exit:
  if (status == ESESTATUS_SUCCESS) {
    if (isSeHalV1_1)
      seCallback_1_1->onStateChange_1_1(true, "NXP SE HAL init ok");
    else
      seCallback->onStateChange(true);
  } else {
    LOG(ERROR) << "eSE-Hal Init failed";
    if (isSeHalV1_1)
      seCallback_1_1->onStateChange_1_1(false, "NXP SE HAL init not ok");
    else
      seCallback->onStateChange(false);
  }
}

void NxpEse::initVIrtualISOService() {
  ESESTATUS status = ESESTATUS_SUCCESS;
  phNxpEse_initParams initParams;
  ESESTATUS deInitStatus = ESESTATUS_SUCCESS;
  memset(&initParams, 0x00, sizeof(phNxpEse_initParams));
  initParams.initMode = ESE_MODE_NORMAL;
  initParams.mediaType = ESE_PROTOCOL_MEDIA_SPI_APDU_GATE;

  if (!virtualISOCallback && !isSeHalV1_1) return;

  if (!virtualISOCallback_1_1 && isSeHalV1_1) return;

  status = phNxpEse_SetEndPoint_Cntxt(1);
  if (status != ESESTATUS_SUCCESS) {
    goto exit1;
  }
  status = phNxpEse_init(initParams);
  if (status != ESESTATUS_SUCCESS) {
    goto exit1;
  }
  status = phNxpEse_ResetEndPoint_Cntxt(1);
  if (status != ESESTATUS_SUCCESS) {
    goto exit2;
  }

  LOG(INFO) << "ESE SPI init complete !!!";
exit2:
  deInitStatus = phNxpEse_deInit();
exit1:
  status = phNxpEse_close(deInitStatus);

  if (status == ESESTATUS_SUCCESS) {
    if (isSeHalV1_1)
      virtualISOCallback_1_1->onStateChange_1_1(true, "NXP SE HAL init ok");
    else
      virtualISOCallback->onStateChange(true);
  } else {
    LOG(ERROR) << "eSE-Hal Init failed";
    if (isSeHalV1_1)
      virtualISOCallback_1_1->onStateChange_1_1(false,
                                                "NXP SE HAL init not ok");
    else
      virtualISOCallback->onStateChange(false);
  }
}
Return<void> NxpEse::ioctlHandler(uint64_t ioctlType,
                                  ese_nxp_IoctlInOutData_t& inpOutData) {
  switch (ioctlType) {
    case HAL_ESE_IOCTL_NFC_JCOP_DWNLD: {
      // nfc_nci_IoctlInOutData_t* inpOutData =
      // (nfc_nci_IoctlInOutData_t*)inpOutData;
      int update_state = inpOutData.inp.data.nxpCmd.p_cmd[0];
      if (update_state == ESE_JCOP_UPDATE_COMPLETED ||
          update_state == ESE_LS_UPDATE_COMPLETED) {
        seteSEClientState(update_state);
        eSEClientUpdate_SE_Thread();
      }
    } break;
  }
  return Void();
}

Return<void> NxpEse::ioctl(uint64_t ioctlType,
                           const hidl_vec<uint8_t>& inOutData,
                           ioctl_cb _hidl_cb) {
  ese_nxp_IoctlInOutData_t inpOutData;
  ese_nxp_IoctlInOutData_t* pInOutData =
      (ese_nxp_IoctlInOutData_t*)&inOutData[0];

  /*data from proxy->stub is copied to local data which can be updated by
   * underlying HAL implementation since it's an inout argument*/
  memcpy(&inpOutData, pInOutData, sizeof(ese_nxp_IoctlInOutData_t));
  ESESTATUS status = phNxpEse_spiIoctl(ioctlType, &inpOutData);
  ioctlHandler(ioctlType, inpOutData);
  /*copy data and additional fields indicating status of ioctl operation
   * and context of the caller. Then invoke the corresponding proxy callback*/
  inpOutData.out.ioctlType = ioctlType;
  inpOutData.out.result = status;
  if (ioctlType == HAL_ESE_IOCTL_GET_ESE_UPDATE_STATE) {
    inpOutData.out.data.status =
        (getJcopUpdateRequired() | (getLsUpdateRequired() << 8));
  }
  EseData outputData;
  outputData.setToExternal((uint8_t*)&inpOutData.out,
                           sizeof(ese_nxp_ExtnOutputData_t));
  LOG(ERROR) << "GET ESE update state2 = " << inpOutData.out.data.status;
  _hidl_cb(outputData);
  return Void();
}

// Methods from ::android::hidl::base::V1_0::IBase follow.
Return<void> NxpEse::debug(const hidl_handle& /* fd */,
                           const hidl_vec<hidl_string>& /* options */) {
  LOG(INFO) << "\n SecureElement-NxpEse HAL MemoryLeak Info = \n"
            << ::android::GetUnreachableMemoryString(true, 10000).c_str();
  return Void();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace nxpese
}  // namespace nxp
}  // namespace vendor
#endif //NXP_BOOTTIME_UPDATE