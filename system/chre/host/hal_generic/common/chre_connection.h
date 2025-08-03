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
#ifndef ANDROID_HARDWARE_CONTEXTHUB_COMMON_CHRE_CONNECTION_H_
#define ANDROID_HARDWARE_CONTEXTHUB_COMMON_CHRE_CONNECTION_H_

#include <flatbuffers/flatbuffers.h>
#include <chrono>
#include <cstddef>
#include <cstdint>
#include <future>
#include <string>
#include "chre_host/fragmented_load_transaction.h"
#include "hal_client_id.h"

namespace android::hardware::contexthub::common::implementation {

/** This abstract class defines the interface between HAL and CHRE. */
class ChreConnection {
 public:
  virtual ~ChreConnection() = default;

  /**
   * Initializes the connection between HAL and CHRE.
   *
   * @return true if success, otherwise false.
   */
  virtual bool init() = 0;

  /**
   * Sends a message to CHRE.
   *
   * @return true if success, otherwise false.
   */
  virtual bool sendMessage(void *data, size_t length) = 0;

  /**
   * @return The nanoapp loading fragment size in bytes.
   */
  virtual size_t getLoadFragmentSizeBytes() const {
    static_assert(CHRE_HOST_DEFAULT_FRAGMENT_SIZE > 0);
    return CHRE_HOST_DEFAULT_FRAGMENT_SIZE;
  }

  /**
   * Sends a message encapsulated in a FlatBufferBuilder to CHRE.
   *
   * @return true if success, otherwise false.
   */
  inline bool sendMessage(const flatbuffers::FlatBufferBuilder &builder) {
    return sendMessage(builder.GetBufferPointer(), builder.GetSize());
  }

  /**
   * Gets the offset between the Context hub and Android time in nanoseconds.
   *
   * This function may be used for synchronizing timestamps between the Context
   * hub and Android.
   *
   * @param offset points to the address storing the offset in nanoseconds which
   * is defined as android time - context hub time.
   * @return true on success, otherwise false.
   */
  virtual bool getTimeOffset(int64_t * /*offset*/) {
    return false;
  }

  /**
   * Returns true if time sync is required by the platform, false otherwise.
   *
   * When this function returns false, getTimeOffset may not be implemented.
   */
  virtual bool isTimeSyncNeeded() {
    return false;
  }
};

}  // namespace android::hardware::contexthub::common::implementation
#endif  // ANDROID_HARDWARE_CONTEXTHUB_COMMON_CHRE_CONNECTION_H_
