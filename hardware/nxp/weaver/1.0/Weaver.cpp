/******************************************************************************
 *
 *  Copyright 2020, 2022 NXP
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
#define LOG_TAG "Weaver@1.0-service"

#include "Weaver.h"
#include <log/log.h>
#include <string.h>
#include <hidl/LegacySupport.h>
#include <weaver_interface.h>
#include <weaver-impl.h>

/* Mutex to synchronize multiple transceive */

namespace android {
namespace hardware {
namespace weaver {
namespace V1_0 {
namespace implementation {

  WeaverInterface *pInterface = nullptr;
  Weaver::Weaver() {
    ALOGI("INITIALIZING WEAVER");
    pInterface = WeaverImpl::getInstance();
    if(pInterface != NULL) {
      pInterface->Init();
    }
  }

  Return<void> Weaver::getConfig(getConfig_cb _hidl_cb) {
  ALOGI("GETCONFIG API ENTRY");
    if(_hidl_cb == NULL) {
      return Void();
    }
    WeaverConfig configResp;
    if(pInterface == NULL) {
      ALOGI("Weaver Interface not defined");
      _hidl_cb(WeaverStatus::FAILED, configResp);
      return Void();
    }
    SlotInfo slotInfo;
    Status_Weaver status = pInterface->GetSlots(slotInfo);
    if(status == WEAVER_STATUS_OK) {
      configResp.slots =  slotInfo.slots;
      configResp.keySize = slotInfo.keySize;
      configResp.valueSize = slotInfo.valueSize;
      ALOGI("Weaver Success for getSlots Slots :(%d)", configResp.slots);
      _hidl_cb(WeaverStatus::OK, configResp);
    } else {
      _hidl_cb(WeaverStatus::FAILED, configResp);
    }
    return Void();
  }

  Return<::android::hardware::weaver::V1_0::WeaverStatus>
    Weaver::write(uint32_t slotId, const hidl_vec<uint8_t>& key, const hidl_vec<uint8_t>& value) {
      ALOGI("Write API ENTRY");
      WeaverStatus status = WeaverStatus::FAILED;
      if(key != NULL && value != NULL && pInterface != NULL
          && (pInterface->Write(slotId, key, value) == WEAVER_STATUS_OK)) {
        status = WeaverStatus::OK;
      }
      return status;
    }

  Return<void>
    Weaver::read(uint32_t slotId, const hidl_vec<uint8_t>& key, read_cb _hidl_cb) {
      ALOGI("Read API ENTRY");
      if (_hidl_cb == NULL) {
        return Void();
      }
      WeaverReadResponse readResp;
      if (key == NULL || pInterface == NULL) {
        _hidl_cb(WeaverReadStatus::FAILED, readResp);
      } else {
        ReadRespInfo readInfo;
        Status_Weaver status = pInterface->Read(slotId, key, readInfo);
        switch (status) {
          case WEAVER_STATUS_OK:
            ALOGI("Read OK");
            readResp.value = readInfo.value;
            _hidl_cb(WeaverReadStatus::OK, readResp);
            break;
          case WEAVER_STATUS_INCORRECT_KEY:
            ALOGI("Read Incorrect Key");
            readResp.value.resize(0);
            readResp.timeout = readInfo.timeout;
            _hidl_cb(WeaverReadStatus::INCORRECT_KEY, readResp);
            break;
          case WEAVER_STATUS_THROTTLE:
            ALOGI("Read WEAVER_THROTTLE");
            readResp.value.resize(0);
            readResp.timeout = readInfo.timeout;
            _hidl_cb(WeaverReadStatus::THROTTLE, readResp);
            break;
          default:
            readResp.timeout = 0;
            readResp.value.resize(0);
            _hidl_cb(WeaverReadStatus::FAILED, readResp);
        }
      }
      return Void();
    }

  void Weaver::serviceDied(uint64_t /*cookie*/, const wp<IBase>& /*who*/) {
    if(pInterface != NULL) {
      pInterface->DeInit();
    }
  }
}
}
}
}
}
