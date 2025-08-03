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
#ifndef ANDROID_HARDWARE_CONTEXTHUB_COMMON_CHRE_CONNECTION_CALLBACK_H_
#define ANDROID_HARDWARE_CONTEXTHUB_COMMON_CHRE_CONNECTION_CALLBACK_H_

namespace android::hardware::contexthub::common::implementation {

/**
 * The callback interface for ChreConnection.
 *
 * A Context Hub HAL should implement this interface so that the ChreConnection
 * has an API to pass message to.
 */
class ChreConnectionCallback {
 public:
  virtual ~ChreConnectionCallback() = default;
  virtual void handleMessageFromChre(const unsigned char *message,
                                     size_t messageLen) = 0;

  /** This method should be called when CHRE is reconnected to HAL and ready to
   * accept new messages. */
  virtual void onChreRestarted(){};
};
}  // namespace android::hardware::contexthub::common::implementation
#endif  // ANDROID_HARDWARE_CONTEXTHUB_COMMON_CHRE_CONNECTION_CALLBACK_H_