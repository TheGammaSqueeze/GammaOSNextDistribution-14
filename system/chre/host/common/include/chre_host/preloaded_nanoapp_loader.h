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

#ifndef CHRE_HOST_PRELOADED_NANOAPP_LOADER_H_
#define CHRE_HOST_PRELOADED_NANOAPP_LOADER_H_

#include <android/binder_to_string.h>
#include <atomic>
#include <cstdint>
#include <mutex>
#include <optional>
#include <string>
#include <utility>

#include "chre_connection.h"
#include "chre_host/generated/host_messages_generated.h"
#include "chre_host/napp_header.h"
#include "fragmented_load_transaction.h"
#include "hal_client_id.h"

namespace android::chre {

using namespace ::android::hardware::contexthub::common::implementation;

/**
 * A class loads preloaded nanoapps.
 *
 * A context hub can include a set of nanoapps that are included in the device
 * image and are loaded when CHRE starts. These are known as preloaded nanoapps.
 * A HAL implementation should use this class to load preloaded nanoapps before
 * exposing API to HAL clients.
 */
class PreloadedNanoappLoader {
 public:
  explicit PreloadedNanoappLoader(ChreConnection *connection,
                                  std::string configPath)
      : mConnection(connection), mConfigPath(std::move(configPath)) {}

  ~PreloadedNanoappLoader() = default;
  /**
   * Attempts to load all preloaded nanoapps from a config file.
   *
   * The config file is expected to be valid JSON with the following structure:
   *
   * { "nanoapps": [
   *     "/path/to/nanoapp_1",
   *     "/path/to/nanoapp_2"
   * ]}
   *
   * The napp_header and so files will both be used.
   *
   * @param skippedNanoappIds nanoapp ids identifying which nanoapps will NOT be
   * loaded.
   *
   * @return the number of nanoapps loaded
   */
  int loadPreloadedNanoapps(const std::optional<const std::vector<uint64_t>>
                                &skippedNanoappIds = std::nullopt);

  /** Callback function to handle the response from CHRE. */
  bool onLoadNanoappResponse(const ::chre::fbs::LoadNanoappResponseT &response,
                             HalClientId clientId);

  void getPreloadedNanoappIds(std::vector<uint64_t> &out_preloadedNanoappIds);

  /** Returns true if the loading is ongoing. */
  [[nodiscard]] bool isPreloadOngoing() const {
    return mIsPreloadingOngoing;
  }

 private:
  /** Tracks the transaction state of the ongoing nanoapp loading */
  struct Transaction {
    uint32_t transactionId;
    size_t fragmentId;
  };

  /** Timeout value of waiting for the response of a fragmented load */
  static constexpr auto kTimeoutInMs = std::chrono::milliseconds(2000);

  /**
   * Loads a preloaded nanoapp.
   *
   * @param appHeader The nanoapp header binary blob.
   * @param nanoappFileName The nanoapp binary file name.
   * @param transactionId The transaction ID identifying this load transaction.
   * @return true if successful, false otherwise.
   */
  bool loadNanoapp(const NanoAppBinaryHeader *appHeader,
                   const std::string &nanoappFileName, uint32_t transactionId);

  /**
   * Chunks the nanoapp binary into fragments and load each fragment
   * sequentially.
   */
  bool sendFragmentedLoadAndWaitForEachResponse(
      uint64_t appId, uint32_t appVersion, uint32_t appFlags,
      uint32_t appTargetApiVersion, const uint8_t *appBinary, size_t appSize,
      uint32_t transactionId);

  /** Sends the FragmentedLoadRequest to CHRE. */
  std::future<bool> sendFragmentedLoadRequest(
      ::android::chre::FragmentedLoadRequest &request);

  /** Verifies the future returned by sendFragmentedLoadRequest(). */
  [[nodiscard]] static bool waitAndVerifyFuture(
      std::future<bool> &future, const FragmentedLoadRequest &request);

  /** Verifies the response of a loading request. */
  [[nodiscard]] bool verifyFragmentLoadResponse(
      const ::chre::fbs::LoadNanoappResponseT &response) const;

  Transaction mPreloadedNanoappPendingTransaction{0, 0};

  /** The value of this promise carries the result in the load response. */
  std::optional<std::promise<bool>> mFragmentedLoadPromise = std::nullopt;

  /** The mutex used to guard states change for preloading. */
  std::mutex mPreloadedNanoappsMutex;

  std::atomic_bool mIsPreloadingOngoing = false;

  ChreConnection *mConnection;
  std::string mConfigPath;
};

}  // namespace android::chre

#endif  // CHRE_HOST_PRELOADED_NANOAPP_LOADER_H_
