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
#ifndef ANDROID_HARDWARE_CONTEXTHUB_COMMON_HAL_CLIENT_MANAGER_H_
#define ANDROID_HARDWARE_CONTEXTHUB_COMMON_HAL_CLIENT_MANAGER_H_

#include "chre/platform/shared/host_protocol_common.h"
#include "chre_host/fragmented_load_transaction.h"
#include "chre_host/log.h"
#include "chre_host/preloaded_nanoapp_loader.h"
#include "hal_client_id.h"

#include <sys/types.h>
#include <cstddef>
#include <unordered_map>
#include <unordered_set>
#include <utility>

#include <aidl/android/hardware/contexthub/ContextHubMessage.h>
#include <aidl/android/hardware/contexthub/IContextHub.h>
#include <aidl/android/hardware/contexthub/IContextHubCallback.h>
#include <android-base/thread_annotations.h>

using aidl::android::hardware::contexthub::ContextHubMessage;
using aidl::android::hardware::contexthub::HostEndpointInfo;
using aidl::android::hardware::contexthub::IContextHubCallback;
using android::chre::FragmentedLoadTransaction;
using HostEndpointId = uint16_t;

namespace android::hardware::contexthub::common::implementation {

/**
 * A class managing clients for Context Hub HAL.
 *
 * A HAL client is defined as a user calling the IContextHub API. The main
 * purpose of this class are:
 *   - to assign a unique HalClientId identifying each client;
 *   - to maintain a mapping between a HAL client and its states defined in
 *     HalClient;
 *   - to track the ongoing load/unload transactions
 *
 * There are 3 types of ids HalClientManager will track: client uuid, HAL client
 * id and host endpoint id.
 *   - A uuid uniquely identifies a client when it registers its callback.
 *     After a callback is registered, a HAL client id is created and will be
 *     used to identify the client in the following API calls from/to it
 *   - A client id identifies a HAL client, which is the layer beneath the host
 *     apps, such as ContextHubService. Multiple apps with different host
 *     endpoint IDs can have the same client ID.
 *   - A host endpoint id, which is defined at
 *     hardware/interfaces/contexthub/aidl/android/hardware/contexthub/ContextHubMessage.aidl,
 *     identifies a host app that communicates with a HAL client.
 *
 * For a host endpoint connected to ContextHubService, its endpoint id is kept
 *in the form below during the communication with CHRE.
 *
 *  0                   1
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |0|      endpoint_id            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 * For vendor host endpoints,  the client id is embedded into the endpoint id
 * before sending a message to CHRE. When that happens, the highest bit is set
 * to 1 and the endpoint id is mutated to the format below:
 *
 *  0                   1
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |1|   client_id     |endpoint_id|
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 * Note that HalClientManager is not responsible for generating endpoint ids,
 * which should be managed by HAL clients themselves.
 */
class HalClientManager {
 public:
  struct Client {
    static constexpr pid_t kPidUnset = 0;
    static constexpr char kNameUnset[]{"undefined"};

    explicit Client(const std::string &uuid, const std::string &name,
                    const HalClientId clientId)
        : Client(uuid, name, clientId, /* pid= */ kPidUnset,
                 /* callback= */ nullptr,
                 /* deathRecipientCookie= */ nullptr) {}

    explicit Client(std::string uuid, std::string name,
                    const HalClientId clientId, pid_t pid,
                    const std::shared_ptr<IContextHubCallback> &callback,
                    void *deathRecipientCookie)
        : uuid{std::move(uuid)},
          name{std::move(name)},
          clientId{clientId},
          pid{pid},
          callback{callback},
          deathRecipientCookie{deathRecipientCookie} {}

    /** Resets the client's fields except uuid and clientId. */
    void reset(pid_t processId,
               const std::shared_ptr<IContextHubCallback> &contextHubCallback,
               void *cookie) {
      pid = processId;
      callback = contextHubCallback;
      deathRecipientCookie = cookie;
      endpointIds.clear();
    }

    const std::string uuid;
    std::string name;
    const HalClientId clientId;
    pid_t pid{};
    std::shared_ptr<IContextHubCallback> callback{};
    // cookie is used by the death recipient's linked callback
    void *deathRecipientCookie{};
    std::unordered_set<HostEndpointId> endpointIds{};
  };

  // The endpoint id is from a vendor client if the highest bit is set to 1.
  static constexpr HostEndpointId kVendorEndpointIdBitMask = 0x8000;
  static constexpr uint8_t kNumOfBitsForEndpointId = 6;

  using DeadClientUnlinker = std::function<bool(
      const std::shared_ptr<IContextHubCallback> &callback, void *cookie)>;

  explicit HalClientManager(
      DeadClientUnlinker deadClientUnlinker,
      const std::string &clientIdMappingFilePath,
      const std::unordered_set<HalClientId> &reservedClientIds = {});
  virtual ~HalClientManager() = default;

  /** Disable copy constructor and copy assignment to avoid duplicates. */
  HalClientManager(HalClientManager &) = delete;
  void operator=(const HalClientManager &) = delete;

  /**
   * Gets the client id allocated to the current HAL client.
   *
   * The current HAL client is identified by its process id. If the process
   * doesn't have any client id assigned, HalClientManager will create one
   * mapped to its process id.
   *
   * @param pid process id of the current client
   *
   * @return client id assigned to the calling process, or
   * ::chre::kHostClientIdUnspecified if the process id is not found.
   */
  HalClientId getClientId(pid_t pid);

  /**
   * Gets the callback for the current HAL client identified by the clientId.
   *
   * @return callback previously registered. nullptr is returned if the clientId
   * is not found.
   */
  std::shared_ptr<IContextHubCallback> getCallback(HalClientId clientId);

  /**
   * Registers a IContextHubCallback function mapped to the current client's
   * client id. @p deathRecipient and @p deathRecipientCookie are used to unlink
   * the previous registered callback for the same client, if any.
   *
   * @param pid process id of the current client
   * @param callback a function incurred to handle the client death event.
   * @param deathRecipientCookie the data used by the callback.
   *
   * @return true if success, otherwise false.
   */
  bool registerCallback(pid_t pid,
                        const std::shared_ptr<IContextHubCallback> &callback,
                        void *deathRecipientCookie);

  /**
   * Registers a FragmentedLoadTransaction for the current HAL client.
   *
   * At this moment only one active transaction, either load or unload, is
   * supported.
   *
   * @param pid process id of the current client
   * @param transaction the transaction being registered
   *
   * @return true if success, otherwise false.
   */
  bool registerPendingLoadTransaction(
      pid_t pid, std::unique_ptr<chre::FragmentedLoadTransaction> transaction);

  /**
   * Returns true if the load transaction matches the arguments provided.
   */
  bool isPendingLoadTransactionExpected(HalClientId clientId,
                                        uint32_t transactionId,
                                        uint32_t currentFragmentId) {
    const std::lock_guard<std::mutex> lock(mLock);
    return isPendingLoadTransactionMatched(clientId, transactionId,
                                           currentFragmentId);
  }

  /**
   * Clears the pending load transaction.
   *
   * This function is called to proactively clear out a pending load transaction
   * that is not timed out yet.
   *
   */
  void resetPendingLoadTransaction();

  /**
   * Gets the next FragmentedLoadRequest from PendingLoadTransaction if it's
   * available.
   *
   * @return an optional FragmentedLoadRequest, std::nullopt if unavailable.
   */

  std::optional<chre::FragmentedLoadRequest> getNextFragmentedLoadRequest();

  /**
   * Registers the current HAL client as having a pending unload transaction.
   *
   * At this moment only one active transaction, either load or unload, is
   * supported.
   *
   * @param pid process id of the current client
   * @param transaction the transaction being registered
   *
   * @return true if success, otherwise false.
   */
  bool registerPendingUnloadTransaction(pid_t pid, uint32_t transactionId);

  /**
   * Clears the pending unload transaction.
   *
   * This function is called to proactively clear out a pending unload
   * transaction that is not timed out yet. @p clientId and @p
   * transactionId must match the existing pending transaction.
   *
   * @param clientId the client id of the caller.
   * @param transactionId unique id of the transaction.
   *
   * @return true if the pending transaction is cleared, otherwise false.
   */
  bool resetPendingUnloadTransaction(HalClientId clientId,
                                     uint32_t transactionId);

  /**
   * Registers an endpoint id when it is connected to HAL.
   *
   * @param pid process id of the current HAL client
   * @param endpointId the endpointId being registered
   *
   * @return true if success, otherwise false.
   */
  bool registerEndpointId(pid_t pid, const HostEndpointId &endpointId);

  /**
   * Removes an endpoint id when it is disconnected to HAL.
   *
   * @param pid process id of the current HAL client
   * @param endpointId the endpointId being registered
   *
   * @return true if success, otherwise false.
   */
  bool removeEndpointId(pid_t pid, const HostEndpointId &endpointId);

  /**
   * Mutates the endpoint id if the hal client is not the framework service.
   *
   * @param pid process id of the current HAL client
   * @param endpointId the endpointId being registered
   *
   * @return true if success, otherwise false.
   */
  bool mutateEndpointIdFromHostIfNeeded(pid_t pid, HostEndpointId &endpointId);

  /** Returns the original endpoint id sent by the host client. */
  static HostEndpointId convertToOriginalEndpointId(
      const HostEndpointId &endpointId);

  /**
   * Gets all the connected endpoints for the client identified by the @p pid.
   *
   * @return the pointer to the endpoint id set if the client is identifiable,
   * otherwise nullptr.
   */
  const std::unordered_set<HostEndpointId> *getAllConnectedEndpoints(pid_t pid);

  /** Sends a message to every connected endpoints. */
  void sendMessageForAllCallbacks(
      const ContextHubMessage &message,
      const std::vector<std::string> &messageParams);

  std::shared_ptr<IContextHubCallback> getCallbackForEndpoint(
      HostEndpointId mutatedEndpointId);

  /**
   * Handles the client death event.
   *
   * @param pid of the client that loses the binder connection to the HAL.
   */
  void handleClientDeath(pid_t pid);

  /** Handles CHRE restart event. */
  void handleChreRestart();

  /** Dumps various states maintained for debugging purpose. */
  std::string debugDump();

 protected:
  /** Pseudo names used before uuid is enabled. */
  static constexpr char kSystemServerUuid[] =
      "9a17008d6bf1445a90116d21bd985b6c";
  static constexpr char kVendorClientUuid[] = "vendor-client";

  /** Keys used in chre_hal_clients.json. */
  static constexpr char kJsonClientId[] = "ClientId";
  static constexpr char kJsonUuid[] = "uuid";
  static constexpr char kJsonName[] = "name";

  /** Max time allowed for a load/unload transaction to take. */
  static constexpr int64_t kTransactionTimeoutThresholdMs = 5000;  // 5 seconds

  static constexpr HostEndpointId kMaxVendorEndpointId =
      (1 << kNumOfBitsForEndpointId) - 1;

  struct PendingTransaction {
    PendingTransaction(HalClientId clientId, uint32_t transactionId,
                       int64_t registeredTimeMs) {
      this->clientId = clientId;
      this->transactionId = transactionId;
      this->registeredTimeMs = registeredTimeMs;
    }
    HalClientId clientId;
    uint32_t transactionId;
    int64_t registeredTimeMs;
  };

  /**
   * PendingLoadTransaction tracks ongoing load transactions.
   */
  struct PendingLoadTransaction : public PendingTransaction {
    PendingLoadTransaction(
        HalClientId clientId, int64_t registeredTimeMs,
        uint32_t currentFragmentId,
        std::unique_ptr<chre::FragmentedLoadTransaction> transaction)
        : PendingTransaction(clientId, transaction->getTransactionId(),
                             registeredTimeMs) {
      this->currentFragmentId = currentFragmentId;
      this->transaction = std::move(transaction);
    }
    uint32_t currentFragmentId;  // the fragment id being sent out.
    std::unique_ptr<chre::FragmentedLoadTransaction> transaction;

    [[nodiscard]] std::string toString() const {
      using android::internal::ToString;
      return "[Load transaction: client id " + ToString(clientId) +
             ", Transaction id " + ToString(transaction->getTransactionId()) +
             ", fragment id " + ToString(currentFragmentId) + "]";
    }
  };

  /**
   * Creates a client id to uniquely identify a HAL client.
   *
   * A file is maintained on the device for the mappings between client names
   * and client ids so that if a client has connected to HAL before the same
   * client id is always assigned to it.
   */
  bool createClient(const std::string &uuid, pid_t pid,
                    const std::shared_ptr<IContextHubCallback> &callback,
                    void *deathRecipientCookie) REQUIRES(mLock);

  /**
   * Update @p mNextClientId to be the next available one.
   *
   * @return true if success, otherwise false.
   */
  bool updateNextClientId() REQUIRES(mLock);

  /**
   * Returns true if @p clientId and @p transactionId match the
   * corresponding values in @p transaction.
   */
  static bool isPendingTransactionMatched(
      HalClientId clientId, uint32_t transactionId,
      const std::optional<PendingTransaction> &transaction) {
    return transaction.has_value() && transaction->clientId == clientId &&
           transaction->transactionId == transactionId;
  }

  /**
   * Returns true if the load transaction is expected.
   */
  bool isPendingLoadTransactionMatched(HalClientId clientId,
                                       uint32_t transactionId,
                                       uint32_t currentFragmentId)
      REQUIRES(mLock);

  /**
   * Checks if the transaction registration is allowed and clears out any stale
   * pending transaction if possible.
   *
   * This function is called when registering a new transaction. The reason that
   * we still proceed when there is already a pending transaction is because we
   * don't want a stale one, for whatever reason, to block future transactions.
   * However, every transaction is guaranteed to have up to
   * kTransactionTimeoutThresholdMs to finish.
   *
   * @param clientId id of the client trying to register the transaction
   *
   * @return true if registration is allowed, otherwise false.
   */
  bool isNewTransactionAllowed(HalClientId clientId) REQUIRES(mLock);

  /** Returns true if the endpoint id is within the accepted range. */
  [[nodiscard]] static inline bool isValidEndpointId(
      const Client *client, const HostEndpointId &endpointId) {
    return client->uuid == kSystemServerUuid ||
           endpointId <= kMaxVendorEndpointId;
  }

  /** Updates the mapping file. */
  void updateClientIdMappingFile() REQUIRES(mLock);

  // TODO(b/290375569): isSystemServerConnected) is a temporary solution
  //  to get a pseudo-uuid. Remove it after flag
  //  context_hub_callback_uuid_enabled is ramped up.
  inline bool isSystemServerConnected() REQUIRES(mLock) {
    Client *client = getClientByUuid(kSystemServerUuid);
    return client != nullptr && client->pid != Client::kPidUnset;
  }

  std::string getUuid(const std::shared_ptr<IContextHubCallback> &callback)
      REQUIRES(mLock);

  Client *getClientByField(
      const std::function<bool(const Client &client)> &fieldMatcher)
      REQUIRES(mLock);

  Client *getClientByClientId(HalClientId clientId) REQUIRES(mLock);

  Client *getClientByUuid(const std::string &uuid) REQUIRES(mLock);

  Client *getClientByProcessId(pid_t pid) REQUIRES(mLock);

  DeadClientUnlinker mDeadClientUnlinker{};

  std::string mClientMappingFilePath;

  // next available client id
  HalClientId mNextClientId = ::chre::kHostClientIdUnspecified;

  // reserved client ids that will not be used
  std::unordered_set<HalClientId> mReservedClientIds;

  // The lock guarding the access to clients' states and pending transactions
  std::mutex mLock;

  std::vector<Client> mClients GUARDED_BY(mLock);

  // States tracking pending transactions
  std::optional<PendingLoadTransaction> mPendingLoadTransaction
      GUARDED_BY(mLock) = std::nullopt;
  std::optional<PendingTransaction> mPendingUnloadTransaction
      GUARDED_BY(mLock) = std::nullopt;
};
}  // namespace android::hardware::contexthub::common::implementation

#endif  // ANDROID_HARDWARE_CONTEXTHUB_COMMON_HAL_CLIENT_MANAGER_H_
