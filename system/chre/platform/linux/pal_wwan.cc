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

#include "chre/pal/wwan.h"

#include "chre/platform/linux/task_util/task_manager.h"

#include "chre/util/memory.h"
#include "chre/util/unique_ptr.h"

#include <chrono>
#include <cinttypes>

/**
 * A simulated implementation of the WWAN PAL for the linux platform.
 */
namespace {

using ::chre::TaskManagerSingleton;

const struct chrePalSystemApi *gSystemApi = nullptr;
const struct chrePalWwanCallbacks *gCallbacks = nullptr;

//! Task to deliver asynchronous WWAN cell info results after a CHRE request.
std::optional<uint32_t> gCellInfosTaskId;

void sendCellInfoResult() {
  auto result = chre::MakeUniqueZeroFill<struct chreWwanCellInfoResult>();
  auto cell = chre::MakeUniqueZeroFill<struct chreWwanCellInfo>();
  cell->timeStamp = gSystemApi->getCurrentTime();
  cell->cellInfoType = CHRE_WWAN_CELL_INFO_TYPE_GSM;
  // INT*_MAX == unknown
  cell->CellInfo.gsm.cellIdentityGsm.mcc = INT32_MAX;
  cell->CellInfo.gsm.cellIdentityGsm.mnc = INT32_MAX;
  cell->CellInfo.gsm.cellIdentityGsm.lac = INT32_MAX;
  cell->CellInfo.gsm.cellIdentityGsm.cid = INT32_MAX;
  cell->CellInfo.gsm.cellIdentityGsm.arfcn = INT32_MAX;
  cell->CellInfo.gsm.cellIdentityGsm.bsic = INT8_MAX;
  cell->CellInfo.gsm.signalStrengthGsm.signalStrength = INT32_MAX;
  cell->CellInfo.gsm.signalStrengthGsm.signalStrength = INT32_MAX;
  cell->CellInfo.gsm.signalStrengthGsm.signalStrength = INT32_MAX;

  result->cellInfoCount = 1;
  result->errorCode = CHRE_ERROR_NONE;
  result->cells = cell.release();

  gCallbacks->cellInfoResultCallback(result.release());
}

void stopCellInfoTask() {
  if (gCellInfosTaskId.has_value()) {
    TaskManagerSingleton::get()->cancelTask(*gCellInfosTaskId);
    gCellInfosTaskId.reset();
  }
}

uint32_t chrePalWwanGetCapabilities() {
  return CHRE_WWAN_GET_CELL_INFO;
}

bool chrePalWwanRequestCellInfo() {
  stopCellInfoTask();
  gCellInfosTaskId = TaskManagerSingleton::get()->addTask(sendCellInfoResult);
  return gCellInfosTaskId.has_value();
}

void chrePalWwanReleaseCellInfoResult(struct chreWwanCellInfoResult *result) {
  for (uint8_t i = 0; i < result->cellInfoCount; i++) {
    chre::memoryFree(const_cast<struct chreWwanCellInfo *>(&result->cells[i]));
  }
  chre::memoryFree(result);
}

void chrePalWwanApiClose() {
  stopCellInfoTask();
}

bool chrePalWwanApiOpen(const struct chrePalSystemApi *systemApi,
                        const struct chrePalWwanCallbacks *callbacks) {
  chrePalWwanApiClose();

  bool success = false;
  if (systemApi != nullptr && callbacks != nullptr) {
    gSystemApi = systemApi;
    gCallbacks = callbacks;
    success = true;
  }

  return success;
}

}  // anonymous namespace

const struct chrePalWwanApi *chrePalWwanGetApi(uint32_t requestedApiVersion) {
  static const struct chrePalWwanApi kApi = {
      .moduleVersion = CHRE_PAL_WWAN_API_CURRENT_VERSION,
      .open = chrePalWwanApiOpen,
      .close = chrePalWwanApiClose,
      .getCapabilities = chrePalWwanGetCapabilities,
      .requestCellInfo = chrePalWwanRequestCellInfo,
      .releaseCellInfoResult = chrePalWwanReleaseCellInfoResult,
  };

  if (!CHRE_PAL_VERSIONS_ARE_COMPATIBLE(kApi.moduleVersion,
                                        requestedApiVersion)) {
    return nullptr;
  } else {
    return &kApi;
  }
}
