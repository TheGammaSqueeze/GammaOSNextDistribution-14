/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "tinysys_chre_connection.h"
#include "chre_host/file_stream.h"
#include "chre_host/generated/host_messages_generated.h"
#include "chre_host/host_protocol_host.h"

#include <hardware_legacy/power.h>
#include <sys/ioctl.h>
#include <utils/SystemClock.h>
#include <cerrno>
#include <thread>

/* The definitions below must be the same as the ones defined in kernel. */
#define SCP_CHRE_MANAGER_STAT_UNINIT _IOW('a', 0, unsigned int)
#define SCP_CHRE_MANAGER_STAT_STOP _IOW('a', 1, unsigned int)
#define SCP_CHRE_MANAGER_STAT_START _IOW('a', 2, unsigned int)

namespace aidl::android::hardware::contexthub {

using namespace ::android::chre;
namespace fbs = ::chre::fbs;

namespace {

// The ChreStateMessage defines the message written by kernel indicating the
// current state of SCP. It must be consistent with the definition in the
// kernel.
struct ChreStateMessage {
  long nextStateAddress;
};

// Possible states of SCP.
enum ChreState {
  SCP_CHRE_UNINIT = 0,
  SCP_CHRE_STOP = 1,
  SCP_CHRE_START = 2,
};

ChreState chreCurrentState = SCP_CHRE_UNINIT;

unsigned getRequestCode(ChreState chreState) {
  switch (chreState) {
    case SCP_CHRE_UNINIT:
      return SCP_CHRE_MANAGER_STAT_UNINIT;
    case SCP_CHRE_STOP:
      return SCP_CHRE_MANAGER_STAT_STOP;
    case SCP_CHRE_START:
      return SCP_CHRE_MANAGER_STAT_START;
    default:
      LOGE("Unexpected CHRE state: %" PRIu32, chreState);
      assert(false);
  }
}
}  // namespace

bool TinysysChreConnection::init() {
  // Make sure the payload size is large enough for nanoapp binary fragment
  static_assert(kMaxPayloadBytes > CHRE_HOST_DEFAULT_FRAGMENT_SIZE &&
                kMaxPayloadBytes - CHRE_HOST_DEFAULT_FRAGMENT_SIZE >
                    kMaxPayloadOverheadBytes);
  mChreFileDescriptor =
      TEMP_FAILURE_RETRY(open(kChreFileDescriptorPath, O_RDWR));
  if (mChreFileDescriptor < 0) {
    LOGE("open chre device failed err=%d errno=%d\n", mChreFileDescriptor,
         errno);
    return false;
  }
  // launch the tasks
  mMessageListener = std::thread(messageListenerTask, this);
  mMessageSender = std::thread(messageSenderTask, this);
  mStateListener = std::thread(chreStateMonitorTask, this);
  mLpmaHandler.init();
  return true;
}

[[noreturn]] void TinysysChreConnection::messageListenerTask(
    TinysysChreConnection *chreConnection) {
  auto chreFd = chreConnection->getChreFileDescriptor();
  while (true) {
    {
      ssize_t payloadSize = TEMP_FAILURE_RETRY(
          read(chreFd, chreConnection->mPayload.get(), kMaxPayloadBytes));
      if (payloadSize == 0) {
        // Payload size 0 is a fake signal from kernel which is normal if the
        // device is in sleep.
        LOGV("%s: Received a payload size 0. Ignored. errno=%d", __func__,
             errno);
        continue;
      }
      if (payloadSize < 0) {
        LOGE("%s: read failed. payload size: %zu. errno=%d", __func__,
             payloadSize, errno);
        continue;
      }
      handleMessageFromChre(chreConnection, chreConnection->mPayload.get(),
                            payloadSize);
    }
  }
}

[[noreturn]] void TinysysChreConnection::chreStateMonitorTask(
    TinysysChreConnection *chreConnection) {
  int chreFd = chreConnection->getChreFileDescriptor();
  uint32_t nextState = 0;
  ChreStateMessage chreMessage{.nextStateAddress =
                                   reinterpret_cast<long>(&nextState)};
  while (true) {
    if (TEMP_FAILURE_RETRY(ioctl(chreFd, getRequestCode(chreCurrentState),
                                 (unsigned long)&chreMessage)) < 0) {
      LOGE("Unable to get an update for the CHRE state: errno=%d", errno);
      continue;
    }
    auto chreNextState = static_cast<ChreState>(nextState);
    if (chreCurrentState != chreNextState) {
      LOGI("CHRE state changes from %" PRIu32 " to %" PRIu32, chreCurrentState,
           chreNextState);
    }
    if (chreCurrentState == SCP_CHRE_STOP && chreNextState == SCP_CHRE_START) {
      int64_t startTime = ::android::elapsedRealtime();
      // Though usually CHRE is recovered within 1s after SCP is up, in a corner
      // case it can go beyond 5s. Wait for 10s to cover more extreme cases.
      chreConnection->waitChreBackOnline(
          /* timeoutMs= */ std::chrono::milliseconds(10000));
      LOGW("SCP restarted! CHRE recover time: %" PRIu64 "ms.",
           ::android::elapsedRealtime() - startTime);
      chreConnection->getCallback()->onChreRestarted();
    }
    chreCurrentState = chreNextState;
  }
}

[[noreturn]] void TinysysChreConnection::messageSenderTask(
    TinysysChreConnection *chreConnection) {
  LOGI("Message sender task is launched.");
  int chreFd = chreConnection->getChreFileDescriptor();
  while (true) {
    chreConnection->mQueue.waitForMessage();
    ChreConnectionMessage &message = chreConnection->mQueue.front();
    auto size =
        TEMP_FAILURE_RETRY(write(chreFd, &message, message.getMessageSize()));
    if (size < 0) {
      LOGE("Failed to write to chre file descriptor. errno=%d\n", errno);
    }
    chreConnection->mQueue.pop();
  }
}

bool TinysysChreConnection::sendMessage(void *data, size_t length) {
  if (length <= 0 || length > kMaxPayloadBytes) {
    LOGE("length %zu is not within the accepted range.", length);
    return false;
  }
  return mQueue.emplace(data, length);
}

void TinysysChreConnection::handleMessageFromChre(
    TinysysChreConnection *chreConnection, const unsigned char *messageBuffer,
    size_t messageLen) {
  // TODO(b/267188769): Move the wake lock acquisition/release to RAII
  // pattern.
  bool isWakelockAcquired =
      acquire_wake_lock(PARTIAL_WAKE_LOCK, kWakeLock) == 0;
  if (!isWakelockAcquired) {
    LOGE("Failed to acquire the wakelock before handling a message.");
  } else {
    LOGV("Wakelock is acquired before handling a message.");
  }
  HalClientId hostClientId;
  fbs::ChreMessage messageType = fbs::ChreMessage::NONE;
  if (!HostProtocolHost::extractHostClientIdAndType(
          messageBuffer, messageLen, &hostClientId, &messageType)) {
    LOGW("Failed to extract host client ID from message - sending broadcast");
    hostClientId = ::chre::kHostClientIdUnspecified;
  }
  LOGV("Received a message (type: %hhu, len: %zu) from CHRE for client %d",
       messageType, messageLen, hostClientId);

  switch (messageType) {
    case fbs::ChreMessage::LowPowerMicAccessRequest: {
      chreConnection->getLpmaHandler()->enable(/* enabled= */ true);
      break;
    }
    case fbs::ChreMessage::LowPowerMicAccessRelease: {
      chreConnection->getLpmaHandler()->enable(/* enabled= */ false);
      break;
    }
    case fbs::ChreMessage::PulseResponse: {
      chreConnection->notifyChreBackOnline();
      break;
    }
    case fbs::ChreMessage::MetricLog:
    case fbs::ChreMessage::NanConfigurationRequest:
    case fbs::ChreMessage::TimeSyncRequest:
    case fbs::ChreMessage::LogMessage: {
      LOGE("Unsupported message type %hhu received from CHRE.", messageType);
      break;
    }
    default: {
      chreConnection->getCallback()->handleMessageFromChre(messageBuffer,
                                                           messageLen);
      break;
    }
  }
  if (isWakelockAcquired) {
    if (release_wake_lock(kWakeLock)) {
      LOGE("Failed to release the wake lock");
    } else {
      LOGV("The wake lock is released after handling a message.");
    }
  }
}
}  // namespace aidl::android::hardware::contexthub
