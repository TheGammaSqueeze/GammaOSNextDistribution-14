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
#include "hal_client_manager.h"

#include <cstdio>
#include <fstream>

#include <aidl/android/hardware/contexthub/AsyncEventType.h>
#include <android-base/strings.h>
#include <android_chre_flags.h>
#include <json/json.h>
#include <utils/SystemClock.h>

namespace android::hardware::contexthub::common::implementation {

using ::aidl::android::hardware::contexthub::AsyncEventType;
using ::aidl::android::hardware::contexthub::ContextHubMessage;
using ::aidl::android::hardware::contexthub::HostEndpointInfo;
using ::aidl::android::hardware::contexthub::IContextHubCallback;
using ::android::chre::flags::context_hub_callback_uuid_enabled;

namespace {
using Client = HalClientManager::Client;
bool getClientMappingsFromFile(const std::string &filePath,
                               Json::Value &mappings) {
  std::fstream file(filePath);
  Json::CharReaderBuilder builder;
  return file.good() &&
         Json::parseFromStream(builder, file, &mappings, /* errs= */ nullptr);
}

bool isCallbackV3Enabled(const std::shared_ptr<IContextHubCallback> &callback) {
  int32_t callbackVersion;
  callback->getInterfaceVersion(&callbackVersion);
  return callbackVersion >= 3 && context_hub_callback_uuid_enabled();
}

std::string getName(const std::shared_ptr<IContextHubCallback> &callback) {
  if (!isCallbackV3Enabled(callback)) {
    return std::string{Client::kNameUnset};
  }
  std::string name;
  callback->getName(&name);
  return name;
}
}  // namespace

std::string HalClientManager::getUuid(
    const std::shared_ptr<IContextHubCallback> &callback) {
  if (!isCallbackV3Enabled(callback)) {
    return isSystemServerConnected() ? kVendorClientUuid : kSystemServerUuid;
  }
  std::array<uint8_t, 16> uuidBytes{};
  callback->getUuid(&uuidBytes);
  std::ostringstream oStringStream;
  char buffer[3]{};
  for (const uint8_t &byte : uuidBytes) {
    snprintf(buffer, sizeof(buffer), "%02x", static_cast<int>(byte));
    oStringStream << buffer;
  }
  return oStringStream.str();
}

Client *HalClientManager::getClientByField(
    const std::function<bool(const Client &client)> &fieldMatcher) {
  for (Client &client : mClients) {
    if (fieldMatcher(client)) {
      return &client;
    }
  }
  return nullptr;
}

Client *HalClientManager::getClientByClientId(HalClientId clientId) {
  return getClientByField([&clientId](const Client &client) {
    return client.clientId == clientId;
  });
}

Client *HalClientManager::getClientByUuid(const std::string &uuid) {
  return getClientByField(
      [&uuid](const Client &client) { return client.uuid == uuid; });
}

Client *HalClientManager::getClientByProcessId(pid_t pid) {
  return getClientByField(
      [&pid](const Client &client) { return client.pid == pid; });
}

bool HalClientManager::updateNextClientId() {
  std::unordered_set<HalClientId> usedClientIds{};
  for (const Client &client : mClients) {
    usedClientIds.insert(client.clientId);
  }
  for (int i = 0; i < kMaxNumOfHalClients; i++) {
    mNextClientId = (mNextClientId + 1) % kMaxHalClientId;
    if (mNextClientId != ::chre::kHostClientIdUnspecified &&
        mReservedClientIds.find(mNextClientId) == mReservedClientIds.end() &&
        usedClientIds.find(mNextClientId) == usedClientIds.end()) {
      // Found a client id that is not reserved nor used.
      return true;
    }
  }
  LOGE("Unable to find the next available client id");
  mNextClientId = ::chre::kHostClientIdUnspecified;
  return false;
}

bool HalClientManager::createClient(
    const std::string &uuid, pid_t pid,
    const std::shared_ptr<IContextHubCallback> &callback,
    void *deathRecipientCookie) {
  if (mClients.size() > kMaxNumOfHalClients ||
      mNextClientId == ::chre::kHostClientIdUnspecified) {
    LOGE("Too many HAL clients (%zu) registered which should never happen.",
         mClients.size());
    return false;
  }
  mClients.emplace_back(uuid, getName(callback), mNextClientId, pid, callback,
                        deathRecipientCookie);
  updateClientIdMappingFile();
  updateNextClientId();
  return true;
}

HalClientId HalClientManager::getClientId(pid_t pid) {
  const std::lock_guard<std::mutex> lock(mLock);
  const Client *client = getClientByProcessId(pid);
  if (client == nullptr) {
    LOGE("Failed to find the client id for pid %d", pid);
    return ::chre::kHostClientIdUnspecified;
  }
  return client->clientId;
}

std::shared_ptr<IContextHubCallback> HalClientManager::getCallback(
    HalClientId clientId) {
  const std::lock_guard<std::mutex> lock(mLock);
  const Client *client = getClientByClientId(clientId);
  if (client == nullptr) {
    LOGE("Failed to find the callback for the client id %" PRIu16, clientId);
    return nullptr;
  }
  return client->callback;
}

bool HalClientManager::registerCallback(
    pid_t pid, const std::shared_ptr<IContextHubCallback> &callback,
    void *deathRecipientCookie) {
  const std::lock_guard<std::mutex> lock(mLock);
  Client *client = getClientByProcessId(pid);
  if (client != nullptr) {
    LOGW("The pid %d has already registered. Overriding its callback.", pid);
    if (!mDeadClientUnlinker(client->callback, client->deathRecipientCookie)) {
      LOGE("Unable to unlink the old callback for pid %d", pid);
      return false;
    }
    client->callback.reset();
    client->callback = callback;
    client->deathRecipientCookie = deathRecipientCookie;
    return true;
  }

  std::string uuid = getUuid(callback);
  client = getClientByUuid(uuid);
  if (client != nullptr) {
    if (client->pid != Client::kPidUnset) {
      // A client is trying to connect to HAL from a different process. But the
      // previous connection is still active because otherwise the pid will be
      // cleared in handleClientDeath().
      LOGE("Client (uuid=%s, name=%s) already has a connection to HAL.",
           uuid.c_str(), client->name.c_str());
      return false;
    }

    // For a known client the previous assigned clientId will be reused.
    client->reset(/* processId= */ pid,
                  /* contextHubCallback= */ callback,
                  /* cookie= */ deathRecipientCookie);

    // Updates a client's name only if it is changed from Client::NAME_UNSET.
    std::string name = getName(callback);
    if (client->name == Client::kNameUnset && name != Client::kNameUnset) {
      client->name = name;
      updateClientIdMappingFile();
    }
    return true;
  }
  return createClient(uuid, pid, callback, deathRecipientCookie);
}

void HalClientManager::handleClientDeath(pid_t pid) {
  const std::lock_guard<std::mutex> lock(mLock);
  Client *client = getClientByProcessId(pid);
  if (client == nullptr) {
    LOGE("Failed to locate the dead pid %d", pid);
    return;
  }

  if (!mDeadClientUnlinker(client->callback, client->deathRecipientCookie)) {
    LOGE("Unable to unlink the old callback for pid %d in death handler", pid);
  }
  client->reset(/* processId= */ Client::kPidUnset,
                /* contextHubCallback= */ nullptr, /* cookie= */ nullptr);

  if (mPendingLoadTransaction.has_value() &&
      mPendingLoadTransaction->clientId == client->clientId) {
    mPendingLoadTransaction.reset();
  }
  if (mPendingUnloadTransaction.has_value() &&
      mPendingUnloadTransaction->clientId == client->clientId) {
    mPendingLoadTransaction.reset();
  }
  LOGI("Process %" PRIu32 " is disconnected from HAL.", pid);
}

bool HalClientManager::registerPendingLoadTransaction(
    pid_t pid, std::unique_ptr<chre::FragmentedLoadTransaction> transaction) {
  if (transaction->isComplete()) {
    LOGW("No need to register a completed load transaction.");
    return false;
  }

  const std::lock_guard<std::mutex> lock(mLock);
  const Client *client = getClientByProcessId(pid);
  if (client == nullptr) {
    LOGE("Unknown HAL client when registering its pending load transaction.");
    return false;
  }
  if (!isNewTransactionAllowed(client->clientId)) {
    return false;
  }
  mPendingLoadTransaction.emplace(
      client->clientId, /* registeredTimeMs= */ android::elapsedRealtime(),
      /* currentFragmentId= */ 0, std::move(transaction));
  return true;
}

std::optional<chre::FragmentedLoadRequest>
HalClientManager::getNextFragmentedLoadRequest() {
  const std::lock_guard<std::mutex> lock(mLock);
  if (mPendingLoadTransaction->transaction->isComplete()) {
    LOGI("Pending load transaction %" PRIu32
         " is finished with client %" PRIu16,
         mPendingLoadTransaction->transaction->getTransactionId(),
         mPendingLoadTransaction->clientId);
    mPendingLoadTransaction.reset();
    return std::nullopt;
  }
  auto request = mPendingLoadTransaction->transaction->getNextRequest();
  mPendingLoadTransaction->currentFragmentId = request.fragmentId;
  LOGV("Client %" PRIu16 " has fragment #%zu ready",
       mPendingLoadTransaction->clientId, request.fragmentId);
  return request;
}

bool HalClientManager::registerPendingUnloadTransaction(
    pid_t pid, uint32_t transactionId) {
  const std::lock_guard<std::mutex> lock(mLock);
  const Client *client = getClientByProcessId(pid);
  if (client == nullptr) {
    LOGE("Unknown HAL client when registering its pending unload transaction.");
    return false;
  }
  if (!isNewTransactionAllowed(client->clientId)) {
    return false;
  }
  mPendingUnloadTransaction.emplace(
      client->clientId, transactionId,
      /* registeredTimeMs= */ android::elapsedRealtime());
  return true;
}

bool HalClientManager::isNewTransactionAllowed(HalClientId clientId) {
  if (mPendingLoadTransaction.has_value()) {
    auto timeElapsedMs =
        android::elapsedRealtime() - mPendingLoadTransaction->registeredTimeMs;
    if (timeElapsedMs < kTransactionTimeoutThresholdMs) {
      LOGE("Rejects client %" PRIu16
           "'s transaction because an active load "
           "transaction %" PRIu32 " with current fragment id %" PRIu32
           " from client %" PRIu16 " exists. Try again later.",
           clientId, mPendingLoadTransaction->transaction->getTransactionId(),
           mPendingLoadTransaction->currentFragmentId,
           mPendingLoadTransaction->clientId);
      return false;
    }
    LOGE("Client %" PRIu16 "'s pending load transaction %" PRIu32
         " with current fragment id %" PRIu32
         " is overridden by client %" PRIu16
         " after holding the slot for %" PRIu64 " ms",
         mPendingLoadTransaction->clientId,
         mPendingLoadTransaction->transaction->getTransactionId(),
         mPendingLoadTransaction->currentFragmentId, clientId, timeElapsedMs);
    mPendingLoadTransaction.reset();
    return true;
  }
  if (mPendingUnloadTransaction.has_value()) {
    auto timeElapsedMs = android::elapsedRealtime() -
                         mPendingUnloadTransaction->registeredTimeMs;
    if (timeElapsedMs < kTransactionTimeoutThresholdMs) {
      LOGE("Rejects client %" PRIu16
           "'s transaction because an active unload "
           "transaction %" PRIu32 " from client %" PRIu16
           " exists. Try again later.",
           clientId, mPendingUnloadTransaction->transactionId,
           mPendingUnloadTransaction->clientId);
      return false;
    }
    LOGE("A pending unload transaction %" PRIu32
         " registered by client %" PRIu16
         " is overridden by a new transaction from client %" PRIu16
         " after holding the slot for %" PRIu64 "ms",
         mPendingUnloadTransaction->transactionId,
         mPendingUnloadTransaction->clientId, clientId, timeElapsedMs);
    mPendingUnloadTransaction.reset();
    return true;
  }
  return true;
}

bool HalClientManager::registerEndpointId(pid_t pid,
                                          const HostEndpointId &endpointId) {
  const std::lock_guard<std::mutex> lock(mLock);
  Client *client = getClientByProcessId(pid);
  if (client == nullptr) {
    LOGE(
        "Unknown HAL client (pid %d). Register the callback before registering "
        "an endpoint.",
        pid);
    return false;
  }
  if (!isValidEndpointId(client, endpointId)) {
    LOGE("Endpoint id %" PRIu16 " from process %d is out of range.", endpointId,
         pid);
    return false;
  }
  if (client->endpointIds.find(endpointId) != client->endpointIds.end()) {
    LOGW("The endpoint %" PRIu16 " is already connected.", endpointId);
    return false;
  }
  client->endpointIds.insert(endpointId);
  LOGI("Endpoint id %" PRIu16 " is connected to client %" PRIu16, endpointId,
       client->clientId);
  return true;
}

bool HalClientManager::removeEndpointId(pid_t pid,
                                        const HostEndpointId &endpointId) {
  const std::lock_guard<std::mutex> lock(mLock);
  Client *client = getClientByProcessId(pid);
  if (client == nullptr) {
    LOGE(
        "Unknown HAL client (pid %d). A callback should have been registered "
        "before removing an endpoint.",
        pid);
    return false;
  }
  if (!isValidEndpointId(client, endpointId)) {
    LOGE("Endpoint id %" PRIu16 " from process %d is out of range.", endpointId,
         pid);
    return false;
  }
  if (client->endpointIds.find(endpointId) == client->endpointIds.end()) {
    LOGW("The endpoint %" PRIu16 " is not connected.", endpointId);
    return false;
  }
  client->endpointIds.erase(endpointId);
  LOGI("Endpoint id %" PRIu16 " is removed from client %" PRIu16, endpointId,
       client->clientId);
  return true;
}

std::shared_ptr<IContextHubCallback> HalClientManager::getCallbackForEndpoint(
    const HostEndpointId mutatedEndpointId) {
  const std::lock_guard<std::mutex> lock(mLock);
  Client *client;
  if (mutatedEndpointId & kVendorEndpointIdBitMask) {
    HalClientId clientId =
        mutatedEndpointId >> kNumOfBitsForEndpointId & kMaxHalClientId;
    client = getClientByClientId(clientId);
  } else {
    client = getClientByUuid(kSystemServerUuid);
  }

  HostEndpointId originalEndpointId =
      convertToOriginalEndpointId(mutatedEndpointId);
  if (client == nullptr) {
    LOGE("Unknown endpoint id %" PRIu16 ". Please register the callback first.",
         originalEndpointId);
    return nullptr;
  }
  if (client->endpointIds.find(originalEndpointId) ==
      client->endpointIds.end()) {
    LOGW(
        "Received a message from CHRE for an unknown or disconnected endpoint "
        "id %" PRIu16,
        originalEndpointId);
  }
  return client->callback;
}

void HalClientManager::sendMessageForAllCallbacks(
    const ContextHubMessage &message,
    const std::vector<std::string> &messageParams) {
  const std::lock_guard<std::mutex> lock(mLock);
  for (const auto &client : mClients) {
    if (client.callback != nullptr) {
      client.callback->handleContextHubMessage(message, messageParams);
    }
  }
}

const std::unordered_set<HostEndpointId>
    *HalClientManager::getAllConnectedEndpoints(pid_t pid) {
  const std::lock_guard<std::mutex> lock(mLock);
  const Client *client = getClientByProcessId(pid);
  if (client == nullptr) {
    LOGE("Unknown HAL client with pid %d", pid);
    return nullptr;
  }
  return &(client->endpointIds);
}

bool HalClientManager::mutateEndpointIdFromHostIfNeeded(
    pid_t pid, HostEndpointId &endpointId) {
  const std::lock_guard<std::mutex> lock(mLock);
  const Client *client = getClientByProcessId(pid);
  if (client == nullptr) {
    LOGE("Unknown HAL client with pid %d", pid);
    return false;
  }
  // no need to mutate client id for framework service
  if (client->uuid != kSystemServerUuid) {
    endpointId = kVendorEndpointIdBitMask |
                 client->clientId << kNumOfBitsForEndpointId | endpointId;
  }
  return true;
}

HostEndpointId HalClientManager::convertToOriginalEndpointId(
    const HostEndpointId &endpointId) {
  if (endpointId & kVendorEndpointIdBitMask) {
    return endpointId & kMaxVendorEndpointId;
  }
  return endpointId;
}

HalClientManager::HalClientManager(
    DeadClientUnlinker deadClientUnlinker,
    const std::string &clientIdMappingFilePath,
    const std::unordered_set<HalClientId> &reservedClientIds) {
  mDeadClientUnlinker = std::move(deadClientUnlinker);
  mClientMappingFilePath = clientIdMappingFilePath;
  mReservedClientIds = reservedClientIds;
  // Parses the file to construct a mapping from process names to client ids.
  Json::Value mappings;
  if (!getClientMappingsFromFile(mClientMappingFilePath, mappings)) {
    // TODO(b/247124878): When the device was firstly booted up the file doesn't
    //   exist which is expected. Consider to create a default file to avoid
    //   confusions.
    LOGW("Unable to find and read %s.", mClientMappingFilePath.c_str());
  } else {
    for (int i = 0; i < mappings.size(); i++) {
      Json::Value mapping = mappings[i];
      if (!mapping.isMember(kJsonClientId) || !mapping.isMember(kJsonUuid) ||
          !mapping.isMember(kJsonName)) {
        LOGE("Unable to find expected key name for the entry %d", i);
        continue;
      }
      std::string uuid = mapping[kJsonUuid].asString();
      std::string name = mapping[kJsonName].asString();
      auto clientId = static_cast<HalClientId>(mapping[kJsonClientId].asUInt());
      mClients.emplace_back(uuid, name, clientId);
    }
  }
  std::lock_guard<std::mutex> lock{mLock};
  updateNextClientId();
}

bool HalClientManager::isPendingLoadTransactionMatched(
    HalClientId clientId, uint32_t transactionId, uint32_t currentFragmentId) {
  bool success =
      isPendingTransactionMatched(clientId, transactionId,
                                  mPendingLoadTransaction) &&
      mPendingLoadTransaction->currentFragmentId == currentFragmentId;
  if (!success) {
    if (mPendingLoadTransaction.has_value()) {
      LOGE("Transaction of client %" PRIu16 " transaction %" PRIu32
           " fragment %" PRIu32
           " doesn't match the current pending transaction (client %" PRIu16
           " transaction %" PRIu32 " fragment %" PRIu32 ").",
           clientId, transactionId, currentFragmentId,
           mPendingLoadTransaction->clientId,
           mPendingLoadTransaction->transactionId,
           mPendingLoadTransaction->currentFragmentId);
    } else {
      LOGE("Transaction of client %" PRIu16 " transaction %" PRIu32
           " fragment %" PRIu32 " doesn't match any pending transaction.",
           clientId, transactionId, currentFragmentId);
    }
  }
  return success;
}

void HalClientManager::resetPendingLoadTransaction() {
  const std::lock_guard<std::mutex> lock(mLock);
  mPendingLoadTransaction.reset();
}

bool HalClientManager::resetPendingUnloadTransaction(HalClientId clientId,
                                                     uint32_t transactionId) {
  const std::lock_guard<std::mutex> lock(mLock);
  // Only clear a pending transaction when the client id and the transaction id
  // are both matched
  if (isPendingTransactionMatched(clientId, transactionId,
                                  mPendingUnloadTransaction)) {
    LOGI("Clears out the pending unload transaction: client id %" PRIu16
         ", transaction id %" PRIu32,
         clientId, transactionId);
    mPendingUnloadTransaction.reset();
    return true;
  }
  LOGW("Client %" PRIu16 " doesn't have a pending unload transaction %" PRIu32
       ". Skip resetting",
       clientId, transactionId);
  return false;
}

void HalClientManager::handleChreRestart() {
  std::vector<std::shared_ptr<IContextHubCallback>> callbacks;
  {
    const std::lock_guard<std::mutex> lock(mLock);
    mPendingLoadTransaction.reset();
    mPendingUnloadTransaction.reset();
    for (Client &client : mClients) {
      client.endpointIds.clear();
      if (client.callback != nullptr) {
        // Create a copy of the callback and call it later without holding the
        // lock to avoid deadlocks.
        callbacks.push_back(client.callback);
      }
    }
  }

  for (const auto &callback : callbacks) {
    callback->handleContextHubAsyncEvent(AsyncEventType::RESTARTED);
  }
}

void HalClientManager::updateClientIdMappingFile() {
  Json::Value mappings;
  for (const auto &client : mClients) {
    Json::Value mapping;
    mapping[kJsonUuid] = client.uuid;
    mapping[kJsonName] = client.name;
    mapping[kJsonClientId] = client.clientId;
    mappings.append(mapping);
  }
  // write to the file; Create the file if it doesn't exist
  Json::StreamWriterBuilder factory;
  std::unique_ptr<Json::StreamWriter> const writer(factory.newStreamWriter());
  std::ofstream fileStream(mClientMappingFilePath);
  writer->write(mappings, &fileStream);
  fileStream << std::endl;
}

std::string HalClientManager::debugDump() {
  std::ostringstream result;
  result << "\n-- HAL Client Manager Debug Info --\n"
         << "\nKnown clients, in the format of [isConnected] (uuid : name) : "
            "Pid, ClientId, {Connected endpoint Ids}\n\n";

  // Dump states of each client.

  const std::lock_guard<std::mutex> lock(mLock);

  std::string endpointIds;
  for (const auto &client : mClients) {
    endpointIds.clear();
    for (const HostEndpointId &endpointId : client.endpointIds) {
      endpointIds.append(std::to_string(endpointId)).append(", ");
    }
    bool isConnected = client.callback != nullptr;
    result << (isConnected ? "[ x ]" : "[   ]") << " (" << std::setw(32)
           << client.uuid << " : " << std::setw(17) << client.name
           << ") : " << std::setw(5) << client.pid << ", " << std::setw(2)
           << client.clientId << ", {" << endpointIds << "}\n";
  }

  // Dump active transactions, if any.
  result << "\nActive pending transaction:\n\n";
  if (mPendingLoadTransaction.has_value()) {
    result << "Load transaction from client "
           << mPendingLoadTransaction->clientId << ": Transaction "
           << mPendingLoadTransaction->transactionId
           << " with current fragment id "
           << mPendingLoadTransaction->currentFragmentId << "\n";
  }
  if (mPendingUnloadTransaction.has_value()) {
    result << "Unload transaction from client "
           << mPendingUnloadTransaction->clientId << ": Transaction "
           << mPendingUnloadTransaction->transactionId << "\n";
  }

  result << "\n-- End Of HAL Client Manager Debug Info --\n";
  return result.str();
}
}  // namespace android::hardware::contexthub::common::implementation
