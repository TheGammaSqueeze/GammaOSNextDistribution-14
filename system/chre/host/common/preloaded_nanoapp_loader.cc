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

#include "chre_host/preloaded_nanoapp_loader.h"
#include <chre_host/host_protocol_host.h>
#include <fstream>
#include "chre_host/config_util.h"
#include "chre_host/file_stream.h"
#include "chre_host/fragmented_load_transaction.h"
#include "chre_host/log.h"
#include "hal_client_id.h"

namespace android::chre {

using android::chre::readFileContents;
using android::hardware::contexthub::common::implementation::kHalId;

namespace {

bool getNanoappHeaderFromFile(const char *headerFileName,
                              std::vector<uint8_t> &headerBuffer) {
  if (!readFileContents(headerFileName, headerBuffer)) {
    LOGE("Failed to read header file for nanoapp %s", headerFileName);
    return false;
  }
  if (headerBuffer.size() != sizeof(NanoAppBinaryHeader)) {
    LOGE("Nanoapp binary's header size is incorrect");
    return false;
  }
  return true;
}

inline bool shouldSkipNanoapp(
    std::optional<const std::vector<uint64_t>> nanoappIds, uint64_t theAppId) {
  return nanoappIds.has_value() &&
         std::find(nanoappIds->begin(), nanoappIds->end(), theAppId) !=
             nanoappIds->end();
}
}  // namespace

void PreloadedNanoappLoader::getPreloadedNanoappIds(
    std::vector<uint64_t> &out_preloadedNanoappIds) {
  std::vector<std::string> nanoappNames;
  std::string directory;
  out_preloadedNanoappIds.clear();
  if (!getPreloadedNanoappsFromConfigFile(mConfigPath, directory,
                                          nanoappNames)) {
    LOGE("Failed to parse preloaded nanoapps config file");
  }
  for (const std::string &nanoappName : nanoappNames) {
    std::string headerFileName = directory + "/" + nanoappName + ".napp_header";
    std::vector<uint8_t> headerBuffer;
    if (!getNanoappHeaderFromFile(headerFileName.c_str(), headerBuffer)) {
      LOGE("Failed to parse the nanoapp header for %s", headerFileName.c_str());
      continue;
    }
    auto header =
        reinterpret_cast<const NanoAppBinaryHeader *>(headerBuffer.data());
    out_preloadedNanoappIds.emplace_back(header->appId);
  }
}

int PreloadedNanoappLoader::loadPreloadedNanoapps(
    const std::optional<const std::vector<uint64_t>> &skippedNanoappIds) {
  std::string directory;
  std::vector<std::string> nanoapps;
  int numOfNanoappsLoaded = 0;
  if (!getPreloadedNanoappsFromConfigFile(mConfigPath, directory, nanoapps)) {
    LOGE("Failed to load any preloaded nanoapp");
    return numOfNanoappsLoaded;
  }
  if (mIsPreloadingOngoing.exchange(true)) {
    LOGE("Preloading is ongoing. A new request shouldn't happen.");
    return numOfNanoappsLoaded;
  }

  for (uint32_t i = 0; i < nanoapps.size(); ++i) {
    std::string headerFilename = directory + "/" + nanoapps[i] + ".napp_header";
    std::string nanoappFilename = directory + "/" + nanoapps[i] + ".so";
    // parse the header
    std::vector<uint8_t> headerBuffer;
    if (!getNanoappHeaderFromFile(headerFilename.c_str(), headerBuffer)) {
      LOGE("Failed to parse the nanoapp header for %s",
           nanoappFilename.c_str());
      continue;
    }
    const auto header =
        reinterpret_cast<const NanoAppBinaryHeader *>(headerBuffer.data());
    // check if the app should be skipped
    if (shouldSkipNanoapp(skippedNanoappIds, header->appId)) {
      LOGI("Loading of %s is skipped.", headerFilename.c_str());
      continue;
    }
    // load the binary
    if (loadNanoapp(header, nanoappFilename, i)) {
      numOfNanoappsLoaded++;
    }
  }
  mIsPreloadingOngoing.store(false);
  return numOfNanoappsLoaded;
}

bool PreloadedNanoappLoader::loadNanoapp(const NanoAppBinaryHeader *appHeader,
                                         const std::string &nanoappFileName,
                                         uint32_t transactionId) {
  // parse the binary
  std::vector<uint8_t> nanoappBuffer;
  if (!readFileContents(nanoappFileName.c_str(), nanoappBuffer)) {
    LOGE("Unable to read %s.", nanoappFileName.c_str());
    return false;
  }
  // Build the target API version from major and minor.
  uint32_t targetApiVersion = (appHeader->targetChreApiMajorVersion << 24) |
                              (appHeader->targetChreApiMinorVersion << 16);
  return sendFragmentedLoadAndWaitForEachResponse(
      appHeader->appId, appHeader->appVersion, appHeader->flags,
      targetApiVersion, nanoappBuffer.data(), nanoappBuffer.size(),
      transactionId);
}

bool PreloadedNanoappLoader::sendFragmentedLoadAndWaitForEachResponse(
    uint64_t appId, uint32_t appVersion, uint32_t appFlags,
    uint32_t appTargetApiVersion, const uint8_t *appBinary, size_t appSize,
    uint32_t transactionId) {
  std::vector<uint8_t> binary(appSize);
  std::copy(appBinary, appBinary + appSize, binary.begin());

  FragmentedLoadTransaction transaction(transactionId, appId, appVersion,
                                        appFlags, appTargetApiVersion, binary);
  while (!transaction.isComplete()) {
    auto nextRequest = transaction.getNextRequest();
    auto future = sendFragmentedLoadRequest(nextRequest);
    if (!waitAndVerifyFuture(future, nextRequest)) {
      return false;
    }
  }
  return true;
}

bool PreloadedNanoappLoader::waitAndVerifyFuture(
    std::future<bool> &future, const FragmentedLoadRequest &request) {
  if (!future.valid()) {
    LOGE("Failed to send out the fragmented load fragment");
    return false;
  }
  if (future.wait_for(kTimeoutInMs) != std::future_status::ready) {
    LOGE(
        "Waiting for response of fragment %zu transaction %d times out "
        "after %lld ms",
        request.fragmentId, request.transactionId, kTimeoutInMs.count());
    return false;
  }
  if (!future.get()) {
    LOGE(
        "Received a failure result for loading fragment %zu of "
        "transaction %d",
        request.fragmentId, request.transactionId);
    return false;
  }
  return true;
}

bool PreloadedNanoappLoader::verifyFragmentLoadResponse(
    const ::chre::fbs::LoadNanoappResponseT &response) const {
  if (!response.success) {
    LOGE("Loading nanoapp binary fragment %d of transaction %u failed.",
         response.fragment_id, response.transaction_id);
    // TODO(b/247124878): Report metrics.
    return false;
  }
  if (mPreloadedNanoappPendingTransaction.transactionId !=
      response.transaction_id) {
    LOGE(
        "Fragmented load response with transactionId %u but transactionId "
        "%u is expected",
        response.transaction_id,
        mPreloadedNanoappPendingTransaction.transactionId);
    return false;
  }
  if (mPreloadedNanoappPendingTransaction.fragmentId != response.fragment_id) {
    LOGE(
        "Fragmented load response with unexpected fragment id %u while "
        "%zu is expected",
        response.fragment_id, mPreloadedNanoappPendingTransaction.fragmentId);
    return false;
  }
  return true;
}

bool PreloadedNanoappLoader::onLoadNanoappResponse(
    const ::chre::fbs::LoadNanoappResponseT &response, HalClientId clientId) {
  std::unique_lock<std::mutex> lock(mPreloadedNanoappsMutex);
  if (clientId != kHalId || !mFragmentedLoadPromise.has_value()) {
    LOGE(
        "Received an unexpected preload nanoapp %s response for client %d "
        "transaction %u fragment %u",
        response.success ? "success" : "failure", clientId,
        response.transaction_id, response.fragment_id);
    return false;
  }
  // set value for the future instance
  mFragmentedLoadPromise->set_value(verifyFragmentLoadResponse(response));
  // reset the promise as the value can only be retrieved once from it
  mFragmentedLoadPromise = std::nullopt;
  return true;
}

std::future<bool> PreloadedNanoappLoader::sendFragmentedLoadRequest(
    ::android::chre::FragmentedLoadRequest &request) {
  flatbuffers::FlatBufferBuilder builder(request.binary.size() + 128);
  // TODO(b/247124878): Confirm if respondBeforeStart can be set to true on all
  //  the devices.
  HostProtocolHost::encodeFragmentedLoadNanoappRequest(
      builder, request, /* respondBeforeStart= */ true);
  HostProtocolHost::mutateHostClientId(builder.GetBufferPointer(),
                                       builder.GetSize(), kHalId);
  std::unique_lock<std::mutex> lock(mPreloadedNanoappsMutex);
  if (!mConnection->sendMessage(builder.GetBufferPointer(),
                                builder.GetSize())) {
    // Returns an invalid future to indicate the failure
    return std::future<bool>{};
  }
  mPreloadedNanoappPendingTransaction = {
      .transactionId = request.transactionId,
      .fragmentId = request.fragmentId,
  };
  mFragmentedLoadPromise = std::make_optional<std::promise<bool>>();
  return mFragmentedLoadPromise->get_future();
}
}  // namespace android::chre