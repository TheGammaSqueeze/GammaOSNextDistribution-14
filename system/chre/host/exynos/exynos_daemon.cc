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

#include "exynos_daemon.h"
#include <sys/epoll.h>
#include <utils/SystemClock.h>
#include <array>
#include <csignal>
#include "chre_host/file_stream.h"

// Aliased for consistency with the way these symbols are referenced in
// CHRE-side code
namespace fbs = ::chre::fbs;

namespace android {
namespace chre {

namespace {

int createEpollFd(int fdToEpoll) {
  struct epoll_event event;
  event.data.fd = fdToEpoll;
  event.events = EPOLLIN | EPOLLWAKEUP;
  int epollFd = epoll_create1(EPOLL_CLOEXEC);
  if (epoll_ctl(epollFd, EPOLL_CTL_ADD, event.data.fd, &event) != 0) {
    LOGE("Failed to add control interface to msg read fd errno: %s",
         strerror(errno));
    epollFd = -1;
  }
  return epollFd;
}

}  // anonymous namespace

ExynosDaemon::ExynosDaemon() : mLpmaHandler(true /* LPMA enabled */) {
  // TODO(b/235631242): Implement this.
}

bool ExynosDaemon::init() {
  constexpr size_t kMaxTimeSyncRetries = 5;
  constexpr useconds_t kTimeSyncRetryDelayUs = 50000;  // 50 ms
  bool success = false;
  mNativeThreadHandle = 0;
  siginterrupt(SIGINT, true);
  std::signal(SIGINT, signalHandler);
  if ((mCommsReadFd = open(kCommsDeviceFilename, O_RDONLY | O_CLOEXEC)) < 0) {
    LOGE("Read FD open failed: %s", strerror(errno));
  } else if ((mCommsWriteFd =
                  open(kCommsDeviceFilename, O_WRONLY | O_CLOEXEC)) < 0) {
    LOGE("Write FD open failed: %s", strerror(errno));
  } else {
    mProcessThreadRunning = true;
    mIncomingMsgProcessThread =
        std::thread([&] { this->processIncomingMsgs(); });
    mNativeThreadHandle = mIncomingMsgProcessThread.native_handle();

    if (!sendTimeSyncWithRetry(kMaxTimeSyncRetries, kTimeSyncRetryDelayUs,
                               true /* logOnError */)) {
      LOGE("Failed to send initial time sync message");
    } else {
      loadPreloadedNanoapps();
      success = true;
      LOGD("CHRE daemon initialized successfully");
    }
  }
  return success;
}

void ExynosDaemon::deinit() {
  stopMsgProcessingThread();

  close(mCommsWriteFd);
  mCommsWriteFd = kInvalidFd;

  close(mCommsReadFd);
  mCommsReadFd = kInvalidFd;
}

void ExynosDaemon::run() {
  constexpr char kChreSocketName[] = "chre";
  auto serverCb = [&](uint16_t clientId, void *data, size_t len) {
    sendMessageToChre(clientId, data, len);
  };

  mServer.run(kChreSocketName, true /* allowSocketCreation */, serverCb);
}

void ExynosDaemon::stopMsgProcessingThread() {
  if (mProcessThreadRunning) {
    mProcessThreadRunning = false;
    pthread_kill(mNativeThreadHandle, SIGINT);
    if (mIncomingMsgProcessThread.joinable()) {
      mIncomingMsgProcessThread.join();
    }
  }
}

void ExynosDaemon::processIncomingMsgs() {
  std::array<uint8_t, kIpcMsgSizeMax> message;
  int epollFd = createEpollFd(mCommsReadFd);

  while (mProcessThreadRunning) {
    struct epoll_event retEvent;
    int nEvents = epoll_wait(epollFd, &retEvent, 1 /* maxEvents */,
                             -1 /* infinite timeout */);
    if (nEvents < 0) {
      // epoll_wait will get interrupted if the CHRE daemon is shutting down,
      // check this condition before logging an error.
      if (mProcessThreadRunning) {
        LOGE("Epolling failed: %s", strerror(errno));
      }
    } else if (nEvents == 0) {
      LOGW("Epoll returned with 0 FDs ready despite no timeout (errno: %s)",
           strerror(errno));
    } else {
      int bytesRead = read(mCommsReadFd, message.data(), message.size());
      if (bytesRead < 0) {
        LOGE("Failed to read from fd: %s", strerror(errno));
      } else if (bytesRead == 0) {
        LOGE("Read 0 bytes from fd");
      } else {
        onMessageReceived(message.data(), bytesRead);
      }
    }
  }
}

bool ExynosDaemon::doSendMessage(void *data, size_t length) {
  bool success = false;
  if (length > kIpcMsgSizeMax) {
    LOGE("Msg size %zu larger than max msg size %zu", length, kIpcMsgSizeMax);
  } else {
    ssize_t rv = write(mCommsWriteFd, data, length);

    if (rv < 0) {
      LOGE("Failed to send message: %s", strerror(errno));
    } else if (rv != length) {
      LOGW("Msg send data loss: %zd of %zu bytes were written", rv, length);
    } else {
      success = true;
    }
  }
  return success;
}

int64_t ExynosDaemon::getTimeOffset(bool *success) {
  // TODO(b/235631242): Implement this.
  *success = false;
  return 0;
}

void ExynosDaemon::loadPreloadedNanoapp(const std::string &directory,
                                        const std::string &name,
                                        uint32_t transactionId) {
  std::vector<uint8_t> headerBuffer;
  std::vector<uint8_t> nanoappBuffer;

  std::string headerFilename = directory + "/" + name + ".napp_header";
  std::string nanoappFilename = directory + "/" + name + ".so";

  if (readFileContents(headerFilename.c_str(), headerBuffer) &&
      readFileContents(nanoappFilename.c_str(), nanoappBuffer) &&
      !loadNanoapp(headerBuffer, nanoappBuffer, transactionId)) {
    LOGE("Failed to load nanoapp: '%s'", name.c_str());
  }
}

bool ExynosDaemon::loadNanoapp(const std::vector<uint8_t> &header,
                               const std::vector<uint8_t> &nanoapp,
                               uint32_t transactionId) {
  // This struct comes from build/build_template.mk and must not be modified.
  // Refer to that file for more details.
  struct NanoAppBinaryHeader {
    uint32_t headerVersion;
    uint32_t magic;
    uint64_t appId;
    uint32_t appVersion;
    uint32_t flags;
    uint64_t hwHubType;
    uint8_t targetChreApiMajorVersion;
    uint8_t targetChreApiMinorVersion;
    uint8_t reserved[6];
  } __attribute__((packed));

  bool success = false;
  if (header.size() != sizeof(NanoAppBinaryHeader)) {
    LOGE("Header size mismatch");
  } else {
    // The header blob contains the struct above.
    const auto *appHeader =
        reinterpret_cast<const NanoAppBinaryHeader *>(header.data());

    // Build the target API version from major and minor.
    uint32_t targetApiVersion = (appHeader->targetChreApiMajorVersion << 24) |
                                (appHeader->targetChreApiMinorVersion << 16);

    success = sendFragmentedNanoappLoad(
        appHeader->appId, appHeader->appVersion, appHeader->flags,
        targetApiVersion, nanoapp.data(), nanoapp.size(), transactionId);
  }

  return success;
}

bool ExynosDaemon::sendFragmentedNanoappLoad(
    uint64_t appId, uint32_t appVersion, uint32_t appFlags,
    uint32_t appTargetApiVersion, const uint8_t *appBinary, size_t appSize,
    uint32_t transactionId) {
  std::vector<uint8_t> binary(appSize);
  std::copy(appBinary, appBinary + appSize, binary.begin());

  FragmentedLoadTransaction transaction(transactionId, appId, appVersion,
                                        appFlags, appTargetApiVersion, binary);

  bool success = true;

  while (success && !transaction.isComplete()) {
    // Pad the builder to avoid allocation churn.
    const auto &fragment = transaction.getNextRequest();
    flatbuffers::FlatBufferBuilder builder(fragment.binary.size() + 128);
    HostProtocolHost::encodeFragmentedLoadNanoappRequest(
        builder, fragment, true /* respondBeforeStart */);
    success = sendFragmentAndWaitOnResponse(transactionId, builder,
                                            fragment.fragmentId, appId);
  }

  return success;
}

bool ExynosDaemon::sendFragmentAndWaitOnResponse(
    uint32_t transactionId, flatbuffers::FlatBufferBuilder &builder,
    uint32_t fragmentId, uint64_t appId) {
  bool success = true;
  std::unique_lock<std::mutex> lock(mPreloadedNanoappsMutex);

  mPreloadedNanoappPendingTransaction = {
      .transactionId = transactionId,
      .fragmentId = fragmentId,
      .nanoappId = appId,
  };
  mPreloadedNanoappPending = sendMessageToChre(
      kHostClientIdDaemon, builder.GetBufferPointer(), builder.GetSize());
  if (!mPreloadedNanoappPending) {
    LOGE("Failed to send nanoapp fragment");
    success = false;
  } else {
    std::chrono::seconds timeout(2);
    bool signaled = mPreloadedNanoappsCond.wait_for(
        lock, timeout, [this] { return !mPreloadedNanoappPending; });

    if (!signaled) {
      LOGE("Nanoapp fragment load timed out");
      success = false;
    }
  }
  return success;
}

void ExynosDaemon::handleDaemonMessage(const uint8_t *message) {
  std::unique_ptr<fbs::MessageContainerT> container =
      fbs::UnPackMessageContainer(message);
  if (container->message.type != fbs::ChreMessage::LoadNanoappResponse) {
    LOGE("Invalid message from CHRE directed to daemon");
  } else {
    const auto *response = container->message.AsLoadNanoappResponse();
    std::unique_lock<std::mutex> lock(mPreloadedNanoappsMutex);

    if (!mPreloadedNanoappPending) {
      LOGE("Received nanoapp load response with no pending load");
    } else if (mPreloadedNanoappPendingTransaction.transactionId !=
               response->transaction_id) {
      LOGE("Received nanoapp load response with invalid transaction id");
    } else if (mPreloadedNanoappPendingTransaction.fragmentId !=
               response->fragment_id) {
      LOGE("Received nanoapp load response with invalid fragment id");
    } else if (!response->success) {
#ifdef CHRE_DAEMON_METRIC_ENABLED
      std::vector<VendorAtomValue> values(3);
      values[0].set<VendorAtomValue::longValue>(
          mPreloadedNanoappPendingTransaction.nanoappId);
      values[1].set<VendorAtomValue::intValue>(
          Atoms::ChreHalNanoappLoadFailed::TYPE_PRELOADED);
      values[2].set<VendorAtomValue::intValue>(
          Atoms::ChreHalNanoappLoadFailed::REASON_ERROR_GENERIC);
      const VendorAtom atom{
          .atomId = Atoms::CHRE_HAL_NANOAPP_LOAD_FAILED,
          .values{std::move(values)},
      };
      ChreDaemonBase::reportMetric(atom);
#endif  // CHRE_DAEMON_METRIC_ENABLED

    } else {
      mPreloadedNanoappPending = false;
    }

    mPreloadedNanoappsCond.notify_all();
  }
}

}  // namespace chre
}  // namespace android
