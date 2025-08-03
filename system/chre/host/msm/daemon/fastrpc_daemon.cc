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

// Disable verbose logging
// TODO: use property_get_bool to make verbose logging runtime configurable
// #define LOG_NDEBUG 0

#include "fastrpc_daemon.h"

#include "generated/chre_slpi.h"

// TODO: The following conditional compilation needs to be removed, and done
// for all platforms after verifying that it works on older devices where
// we're currently not defining this macro
#ifdef CHRE_DAEMON_LOAD_INTO_SENSORSPD
#include "remote.h"

#define ITRANSPORT_PREFIX "'\":;./\\"
#endif  // CHRE_DAEMON_LOAD_INTO_SENSORSPD

// Aliased for consistency with the way these symbols are referenced in
// CHRE-side code
namespace fbs = ::chre::fbs;

namespace android {
namespace chre {

namespace {

#ifdef CHRE_DAEMON_LPMA_ENABLED
constexpr bool kLpmaAllowed = true;
#else
constexpr bool kLpmaAllowed = false;
#endif  // CHRE_DAEMON_LPMA_ENABLED

}  // namespace

FastRpcChreDaemon::FastRpcChreDaemon() : mLpmaHandler(kLpmaAllowed) {}

bool FastRpcChreDaemon::init() {
  constexpr size_t kMaxTimeSyncRetries = 5;
  constexpr useconds_t kTimeSyncRetryDelayUs = 50000;  // 50 ms

  int rc = -1;

#ifdef CHRE_DAEMON_LOAD_INTO_SENSORSPD
  remote_handle remote_handle_fd = 0xFFFFFFFF;
  if (remote_handle_open(ITRANSPORT_PREFIX "createstaticpd:sensorspd",
                         &remote_handle_fd)) {
    LOGE("Failed to open remote handle for sensorspd");
  } else {
    LOGD("Successfully opened remote handle for sensorspd");
  }
#endif  // CHRE_DAEMON_LOAD_INTO_SENSORSPD

  mLpmaHandler.init();

  if (!sendTimeSyncWithRetry(kMaxTimeSyncRetries, kTimeSyncRetryDelayUs,
                             true /* logOnError */)) {
    LOGE("Failed to send initial time sync message");
  } else if ((rc = chre_slpi_initialize_reverse_monitor()) !=
             CHRE_FASTRPC_SUCCESS) {
    LOGE("Failed to initialize reverse monitor: (err) %d", rc);
  } else if ((rc = chre_slpi_start_thread()) != CHRE_FASTRPC_SUCCESS) {
    LOGE("Failed to start CHRE: (err) %d", rc);
  } else {
    mMonitorThread = std::thread(&FastRpcChreDaemon::monitorThreadEntry, this);
    mMsgToHostThread =
        std::thread(&FastRpcChreDaemon::msgToHostThreadEntry, this);
    loadPreloadedNanoapps();
    LOGI("CHRE started");
  }

  return (rc == CHRE_FASTRPC_SUCCESS);
}

void FastRpcChreDaemon::deinit() {
  int rc;

  setShutdownRequested(true);

  if ((rc = chre_slpi_stop_thread()) != CHRE_FASTRPC_SUCCESS) {
    LOGE("Failed to stop CHRE: (err) %d", rc);
  }

  if (mMonitorThread.has_value()) {
    mMonitorThread->join();
  }
  if (mMsgToHostThread.has_value()) {
    mMsgToHostThread->join();
  }
}

void FastRpcChreDaemon::run() {
  constexpr char kChreSocketName[] = "chre";
  auto serverCb = [&](uint16_t clientId, void *data, size_t len) {
    if (mCrashDetected) {
      LOGW("Dropping data, CHRE restart in process...");
    } else {
      sendMessageToChre(clientId, data, len);
    }
  };

  // TODO: take 2nd argument as command-line parameter
  mServer.run(kChreSocketName, true /* allowSocketCreation */, serverCb);
}

bool FastRpcChreDaemon::doSendMessage(void *data, size_t length) {
  // This limitation is due to FastRPC, but there's no case
  // where we should come close to this limit
  constexpr size_t kMaxPayloadSize = 1024 * 1024;  // 1 MiB
  static_assert(kMaxPayloadSize <= INT32_MAX,
                "DSP uses 32-bit signed integers to represent message size");

  bool success = false;
  if (length > kMaxPayloadSize) {
    LOGE("Message too large (got %zu, max %zu bytes)", length, kMaxPayloadSize);
  } else {
    int ret = chre_slpi_deliver_message_from_host(
        static_cast<const unsigned char *>(data), static_cast<int>(length));
    if (ret != CHRE_FASTRPC_SUCCESS) {
      LOGE("Failed to deliver message from host to CHRE: %d", ret);
    } else {
      success = true;
    }
  }

  return success;
}

void FastRpcChreDaemon::monitorThreadEntry() {
  LOGD("Monitor thread started");

  int ret = chre_slpi_wait_on_thread_exit();
  if (!wasShutdownRequested()) {
    LOGE("Monitor detected unexpected CHRE thread exit (%d)", ret);
    onRemoteCrashDetected();
  }
  LOGD("Monitor thread exited");
}

void FastRpcChreDaemon::msgToHostThreadEntry() {
  unsigned char messageBuffer[4096];
  unsigned int messageLen;
  int result = 0;

  LOGD("MsgToHost thread started");

  while (true) {
    messageLen = 0;
    LOGV("Calling into chre_slpi_get_message_to_host");
    result = chre_slpi_get_message_to_host(messageBuffer, sizeof(messageBuffer),
                                           &messageLen);
    LOGV("Got message from CHRE with size %u (result %d)", messageLen, result);

    if (result == CHRE_FASTRPC_ERROR_SHUTTING_DOWN) {
      LOGD("CHRE shutting down, exiting CHRE->Host message thread");
      break;
    } else if (result == CHRE_FASTRPC_SUCCESS && messageLen > 0) {
      onMessageReceived(messageBuffer, messageLen);
    } else if (!wasShutdownRequested()) {
      LOGE("get_message_to_host returned unexpected error (%d)", result);
      onRemoteCrashDetected();
    } else {
      // Received an unknown result but a shutdown was requested. Break from
      // the loop to allow the daemon to cleanup.
      break;
    }
  }
  LOGD("Message to host thread exited");
}

int64_t FastRpcChreDaemon::getTimeOffset(bool *success) {
  int64_t timeOffset = 0;

#if defined(__aarch64__)
  // Reads the system time counter (CNTVCT) and its frequency (CNTFRQ)
  // CNTVCT is used in the sensors HAL for time synchronization.
  // More information can be found in the ARM reference manual
  // (http://infocenter.arm.com/help/index.jsp?topic=
  // /com.arm.doc.100048_0002_05_en/jfa1406793266982.html)
  // Use uint64_t to store since the MRS instruction uses 64 bit (X) registers
  // (http://infocenter.arm.com/help/topic/
  // com.arm.doc.den0024a/ch06s05s02.html)
  uint64_t qTimerCount = 0, qTimerFreq = 0;
  uint64_t hostTimeNano = elapsedRealtimeNano();
  asm volatile("mrs %0, cntvct_el0" : "=r"(qTimerCount));
  asm volatile("mrs %0, cntfrq_el0" : "=r"(qTimerFreq));

  constexpr uint64_t kOneSecondInNanoseconds = 1000000000;
  if (qTimerFreq != 0) {
    // Get the seconds part first, then convert the remainder to prevent
    // overflow
    uint64_t qTimerNanos = (qTimerCount / qTimerFreq);
    if (qTimerNanos > UINT64_MAX / kOneSecondInNanoseconds) {
      LOGE(
          "CNTVCT_EL0 conversion to nanoseconds overflowed during time sync. "
          "Aborting time sync.");
      *success = false;
    } else {
      qTimerNanos *= kOneSecondInNanoseconds;

      // Round the remainder portion to the nearest nanosecond
      uint64_t remainder = (qTimerCount % qTimerFreq);
      qTimerNanos +=
          (remainder * kOneSecondInNanoseconds + qTimerFreq / 2) / qTimerFreq;

      timeOffset = hostTimeNano - qTimerNanos;
      *success = true;
    }
  } else {
    LOGE("CNTFRQ_EL0 had 0 value. Aborting time sync.");
    *success = false;
  }
#else
#error "Unsupported CPU architecture type"
#endif

  return timeOffset;
}

void FastRpcChreDaemon::onRemoteCrashDetected() {
  // After a DSP crash, we delay a short period of time before exiting. This is
  // primarily to avoid any potential race conditions arising from trying to
  // initialize CHRE very early in the DSP boot flow. Normally the firmware is
  // reloaded within a second or so, but we use a longer time here to have some
  // padding to handle cases where the system is slower than usual, etc.
  constexpr auto kDelayAfterCrash = std::chrono::seconds(3);

  // It's technically fine if multiple threads race here, but to avoid duplicate
  // logs, give the first one to reach this point a shorter delay than others
  bool firstDetection = !mCrashDetected.exchange(true);
  auto delay = (firstDetection) ? kDelayAfterCrash : kDelayAfterCrash * 2;
  std::this_thread::sleep_for(delay);
  LOGE("Exiting daemon");
  std::exit(EXIT_FAILURE);
}

}  // namespace chre
}  // namespace android
