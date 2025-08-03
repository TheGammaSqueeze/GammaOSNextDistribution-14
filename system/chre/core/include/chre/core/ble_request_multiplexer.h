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

#ifndef CHRE_CORE_BLE_REQUEST_MULTIPLEXER_H_
#define CHRE_CORE_BLE_REQUEST_MULTIPLEXER_H_

#include "chre/core/ble_request.h"
#include "chre/core/request_multiplexer.h"

namespace chre {

/**
 * Synchronous callback used in forEachRequest.
 */
typedef void(RequestCallbackFunction)(BleRequest &req, void *data);

/**
 * Provides methods on top of the RequestMultiplexer class specific for working
 * with BleRequest objects.
 */
class BleRequestMultiplexer : public RequestMultiplexer<BleRequest> {
 public:
  /**
   * Returns the list of current requests in the multiplexer.
   *
   * NOTE: Mutating these requests in a way that would change the underlying
   * maximal request isn't supported and will cause problems.
   */
  DynamicVector<BleRequest> &getMutableRequests();

  /**
   * Searches through the list of BLE requests for a request owned by the
   * given nanoapp. The provided index pointer is populated with the
   * index of the request if it is found and the pointer is not null.
   *
   * @param instanceId The instance ID of the nanoapp whose request is being
   *        searched for.
   * @param index A pointer to an index that is populated if a
   *        request for this nanoapp is found.
   * @return A pointer to a BleRequest that is owned by the provided
   *         nanoapp if one is found otherwise nullptr.
   */
  const BleRequest *findRequest(uint16_t instanceId, size_t *index);

  /**
   * @param status Status type to check if any requests have
   * @return True if any requests with the provided status are in the
   *         multiplexer.
   */
  bool hasRequests(RequestStatus status) const;

  /**
   * Removes all requests of a particular status type from the multiplexer.
   *
   * @param status Status type that should be removed from the request queue.
   */
  void removeRequests(RequestStatus status);

  /**
   * Removes all disabled requests from the multiplexer.
   */
  void removeDisabledRequests();

  /**
   * @return true if current maximal request is enabled.
   */
  bool isMaximalRequestEnabled();
};

}  // namespace chre

#endif  // CHRE_CORE_BLE_REQUEST_MULTIPLEXER_H_
