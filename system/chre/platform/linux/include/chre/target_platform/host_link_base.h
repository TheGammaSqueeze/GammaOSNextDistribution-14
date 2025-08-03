/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef CHRE_PLATFORM_LINUX_HOST_LINK_BASE_H_
#define CHRE_PLATFORM_LINUX_HOST_LINK_BASE_H_

namespace chre {

class HostLinkBase {
 public:
  /**
   * Enqueues a NAN configuration request to be sent to the host.
   * For Linux, the request is simply echoed back via a NAN configuration
   * update event since there's no actual host to send the request to.
   *
   * @param enable Requests that NAN be enabled or disabled based on the
   *        boolean's value.
   */
  void sendNanConfiguration(bool enable);
};

}  // namespace chre

#endif  // CHRE_PLATFORM_LINUX_HOST_LINK_BASE_H_
