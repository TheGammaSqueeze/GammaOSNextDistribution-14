/*
 * Copyright (C) 2020 The Android Open Source Project
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

// TODO(b/298459533): metrics_reporter_in_the_daemon ramp up -> remove old
// code

#include <cstdlib>
#include <fstream>

#include "chre_host/fbs_daemon_base.h"
#include "chre_host/log.h"
#include "chre_host/napp_header.h"

#include <json/json.h>

#ifdef CHRE_DAEMON_METRIC_ENABLED
#include <aidl/android/frameworks/stats/IStats.h>
#include <android/binder_manager.h>
#include <android_chre_flags.h>
#include <chre_atoms_log.h>
#endif  // CHRE_DAEMON_METRIC_ENABLED

// Aliased for consistency with the way these symbols are referenced in
// CHRE-side code
namespace fbs = ::chre::fbs;

namespace android {
namespace chre {

#ifdef CHRE_DAEMON_METRIC_ENABLED
using ::aidl::android::frameworks::stats::IStats;
using ::aidl::android::frameworks::stats::VendorAtom;
using ::aidl::android::frameworks::stats::VendorAtomValue;
using ::android::chre::Atoms::ChreHalNanoappLoadFailed;
using ::android::chre::flags::metrics_reporter_in_the_daemon;
#endif  // CHRE_DAEMON_METRIC_ENABLED

bool FbsDaemonBase::sendNanoappLoad(uint64_t appId, uint32_t appVersion,
                                    uint32_t appTargetApiVersion,
                                    const std::string &appBinaryName,
                                    uint32_t transactionId) {
  flatbuffers::FlatBufferBuilder builder;
  HostProtocolHost::encodeLoadNanoappRequestForFile(
      builder, transactionId, appId, appVersion, appTargetApiVersion,
      appBinaryName.c_str());

  bool success = sendMessageToChre(
      kHostClientIdDaemon, builder.GetBufferPointer(), builder.GetSize());

  if (!success) {
    LOGE("Failed to send nanoapp filename.");
  } else {
    Transaction transaction = {
        .transactionId = transactionId,
        .nanoappId = appId,
    };
    mPreloadedNanoappPendingTransactions.push(transaction);
  }

  return success;
}

bool FbsDaemonBase::sendTimeSync(bool logOnError) {
  bool success = false;
  int64_t timeOffset = getTimeOffset(&success);

  if (success) {
    flatbuffers::FlatBufferBuilder builder(64);
    HostProtocolHost::encodeTimeSyncMessage(builder, timeOffset);
    success = sendMessageToChre(kHostClientIdDaemon, builder.GetBufferPointer(),
                                builder.GetSize());

    if (!success && logOnError) {
      LOGE("Failed to deliver time sync message from host to CHRE");
    }
  }

  return success;
}

bool FbsDaemonBase::sendMessageToChre(uint16_t clientId, void *data,
                                      size_t length) {
  bool success = false;
  if (!HostProtocolHost::mutateHostClientId(data, length, clientId)) {
    LOGE("Couldn't set host client ID in message container!");
  } else {
    LOGV("Delivering message from host (size %zu)", length);
    getLogger().dump(static_cast<const uint8_t *>(data), length);
    success = doSendMessage(data, length);
  }

  return success;
}

void FbsDaemonBase::onMessageReceived(const unsigned char *messageBuffer,
                                      size_t messageLen) {
  getLogger().dump(messageBuffer, messageLen);

  uint16_t hostClientId;
  fbs::ChreMessage messageType;
  if (!HostProtocolHost::extractHostClientIdAndType(
          messageBuffer, messageLen, &hostClientId, &messageType)) {
    LOGW("Failed to extract host client ID from message - sending broadcast");
    hostClientId = ::chre::kHostClientIdUnspecified;
  }

  if (messageType == fbs::ChreMessage::LogMessage) {
    std::unique_ptr<fbs::MessageContainerT> container =
        fbs::UnPackMessageContainer(messageBuffer);
    const auto *logMessage = container->message.AsLogMessage();
    const std::vector<int8_t> &logData = logMessage->buffer;

    getLogger().log(reinterpret_cast<const uint8_t *>(logData.data()),
                    logData.size());
  } else if (messageType == fbs::ChreMessage::LogMessageV2) {
    std::unique_ptr<fbs::MessageContainerT> container =
        fbs::UnPackMessageContainer(messageBuffer);
    const auto *logMessage = container->message.AsLogMessageV2();
    const std::vector<int8_t> &logDataBuffer = logMessage->buffer;
    const auto *logData =
        reinterpret_cast<const uint8_t *>(logDataBuffer.data());
    uint32_t numLogsDropped = logMessage->num_logs_dropped;

    getLogger().logV2(logData, logDataBuffer.size(), numLogsDropped);
  } else if (messageType == fbs::ChreMessage::TimeSyncRequest) {
    sendTimeSync(true /* logOnError */);
  } else if (messageType == fbs::ChreMessage::LowPowerMicAccessRequest) {
    configureLpma(true /* enabled */);
  } else if (messageType == fbs::ChreMessage::LowPowerMicAccessRelease) {
    configureLpma(false /* enabled */);
  } else if (messageType == fbs::ChreMessage::MetricLog) {
#ifdef CHRE_DAEMON_METRIC_ENABLED
    std::unique_ptr<fbs::MessageContainerT> container =
        fbs::UnPackMessageContainer(messageBuffer);
    const auto *metricMsg = container->message.AsMetricLog();
    handleMetricLog(metricMsg);
#endif  // CHRE_DAEMON_METRIC_ENABLED
  } else if (messageType == fbs::ChreMessage::NanConfigurationRequest) {
    std::unique_ptr<fbs::MessageContainerT> container =
        fbs::UnPackMessageContainer(messageBuffer);
    handleNanConfigurationRequest(
        container->message.AsNanConfigurationRequest());
  } else if (messageType == fbs::ChreMessage::NanoappTokenDatabaseInfo) {
    // TODO(b/242760291): Use this info to map nanoapp log detokenizers with
    // instance ID in log message parser.
  } else if (hostClientId == kHostClientIdDaemon) {
    handleDaemonMessage(messageBuffer);
  } else if (hostClientId == ::chre::kHostClientIdUnspecified) {
    mServer.sendToAllClients(messageBuffer, static_cast<size_t>(messageLen));
  } else {
    mServer.sendToClientById(messageBuffer, static_cast<size_t>(messageLen),
                             hostClientId);
  }
}

void FbsDaemonBase::handleDaemonMessage(const uint8_t *message) {
  std::unique_ptr<fbs::MessageContainerT> container =
      fbs::UnPackMessageContainer(message);
  if (container->message.type != fbs::ChreMessage::LoadNanoappResponse) {
    LOGE("Invalid message from CHRE directed to daemon");
  } else {
    const auto *response = container->message.AsLoadNanoappResponse();
    if (mPreloadedNanoappPendingTransactions.empty()) {
      LOGE("Received nanoapp load response with no pending load");
    } else if (mPreloadedNanoappPendingTransactions.front().transactionId !=
               response->transaction_id) {
      LOGE("Received nanoapp load response with ID %" PRIu32
           " expected transaction id %" PRIu32,
           response->transaction_id,
           mPreloadedNanoappPendingTransactions.front().transactionId);
    } else {
      if (!response->success) {
        LOGE("Received unsuccessful nanoapp load response with ID %" PRIu32,
             mPreloadedNanoappPendingTransactions.front().transactionId);

#ifdef CHRE_DAEMON_METRIC_ENABLED
        if (metrics_reporter_in_the_daemon()) {
          if (!mMetricsReporter.logNanoappLoadFailed(
                  mPreloadedNanoappPendingTransactions.front().nanoappId,
                  ChreHalNanoappLoadFailed::TYPE_PRELOADED,
                  ChreHalNanoappLoadFailed::REASON_ERROR_GENERIC)) {
            LOGE("Could not log the nanoapp load failed metric");
          }
        } else {
          std::vector<VendorAtomValue> values(3);
          values[0].set<VendorAtomValue::longValue>(
              mPreloadedNanoappPendingTransactions.front().nanoappId);
          values[1].set<VendorAtomValue::intValue>(
              Atoms::ChreHalNanoappLoadFailed::TYPE_PRELOADED);
          values[2].set<VendorAtomValue::intValue>(
              Atoms::ChreHalNanoappLoadFailed::REASON_ERROR_GENERIC);
          const VendorAtom atom{
              .atomId = Atoms::CHRE_HAL_NANOAPP_LOAD_FAILED,
              .values{std::move(values)},
          };
          reportMetric(atom);
        }
#endif  // CHRE_DAEMON_METRIC_ENABLED
      }
      mPreloadedNanoappPendingTransactions.pop();
    }
  }
}

}  // namespace chre
}  // namespace android
