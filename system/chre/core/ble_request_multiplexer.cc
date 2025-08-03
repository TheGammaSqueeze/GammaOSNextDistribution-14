/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "chre/core/ble_request_multiplexer.h"

#include "chre/core/event_loop_manager.h"

namespace chre {

DynamicVector<BleRequest> &BleRequestMultiplexer::getMutableRequests() {
  return mRequests;
}

const BleRequest *BleRequestMultiplexer::findRequest(uint16_t instanceId,
                                                     size_t *index) {
  for (size_t i = 0; i < mRequests.size(); ++i) {
    if (mRequests[i].getInstanceId() == instanceId) {
      if (index != nullptr) {
        *index = i;
      }
      return &mRequests[i];
    }
  }
  return nullptr;
}

bool BleRequestMultiplexer::hasRequests(RequestStatus status) const {
  for (const BleRequest &request : mRequests) {
    if (request.getRequestStatus() == status) {
      return true;
    }
  }
  return false;
}

void BleRequestMultiplexer::removeRequests(RequestStatus status) {
  bool requestRemoved = false;
  size_t index = 0;
  while (index < mRequests.size()) {
    if (mRequests[index].getRequestStatus() == status) {
      mRequests.erase(index);
      requestRemoved = true;
    } else {
      // Only increment index if nothing is erased since erasing moves later
      // elements down a spot so keeping the index the same is safe.
      index++;
    }
  }

  if (requestRemoved) {
    // Only update the maximal request after removing all needed requests to
    // reduce allocations performed.
    bool maximalRequestChanged = false;
    updateMaximalRequest(&maximalRequestChanged);
  }
}

void BleRequestMultiplexer::removeDisabledRequests() {
  size_t index = 0;
  while (index < mRequests.size()) {
    BleRequest &request = mRequests[index];
    if (!request.isEnabled() &&
        request.getRequestStatus() == RequestStatus::APPLIED) {
      mRequests.erase(index);
    } else {
      // Only increment index if nothing is erased since erasing moves later
      // elements down a spot so keeping the index the same is safe.
      index++;
    }
  }

  // No need to update the maximal request after removing since disabled
  // requests don't affect the maximal request.
}

bool BleRequestMultiplexer::isMaximalRequestEnabled() {
  return getCurrentMaximalRequest().isEnabled();
}

}  // namespace chre
