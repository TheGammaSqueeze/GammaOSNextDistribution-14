/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef CHRE_CORE_HOST_ENDPOINT_MANAGER_H_
#define CHRE_CORE_HOST_ENDPOINT_MANAGER_H_

#include <cinttypes>

#include "chre/util/system/debug_dump.h"
#include "chre_api/chre/event.h"

namespace chre {

/**
 * Connected host endpoint metadata, which should only be accessed by the
 * main CHRE event loop.
 */
class HostEndpointManager : public NonCopyable {
 public:
  /**
   * Updates host endpoint connection to CHRE.
   *
   * @param info Metadata about the host endpoint that connected.
   */
  void postHostEndpointConnected(const struct chreHostEndpointInfo &info);

  /**
   * Updates host endpoint disconnection to CHRE.
   *
   * @param hostEndpointId The host endpoint ID.
   */
  void postHostEndpointDisconnected(uint16_t hostEndpointId);

  /**
   * Gets the Host endpoint information if it has been connected.
   *
   * @param hostEndpointId The host endpoint ID.
   * @param info Where the retrieved info will be stored.
   * @return true if the id is connected.
   * @return false if the id is not connected.
   */
  bool getHostEndpointInfo(uint16_t hostEndpointId,
                           struct chreHostEndpointInfo *info);

 private:
  /**
   * Stores host endpoint information if it is connected.
   */
  chre::DynamicVector<struct chreHostEndpointInfo> mHostEndpoints =
      chre::DynamicVector<struct chreHostEndpointInfo>();

  /**
   * Returns the index of where the endpoint id is stored
   *
   * @param hostEndpointId The host endpoint ID.
   * @param index Where the retrieved index will be returned.
   * @return true if the id is connected.
   * @return false if the id is not connected.
   */
  bool isHostEndpointConnected(uint16_t hostEndpointId, size_t *index);

  /**
   * Callback function used in event loop to connect or disconnect the host
   * endpoint.
   *
   * @param type Type if system callback type, needs to be
   * HostEndpointDisconnected or HostEndpointConnected
   * @param data Arbitrary data to provide to the callback
   * @param extraData Additional arbitrary data to provide to the callback
   */
  void hostNotificationCallback(uint16_t type, void *data, void *extraData);

  /**
   * Get the hostNotificationCallback of the HostEndpointManager in
   * EventLoopManager
   */
  auto getHostNotificationCallback();
};

}  // namespace chre

#endif  // CHRE_CORE_HOST_ENDPOINT_MANAGER_H_
