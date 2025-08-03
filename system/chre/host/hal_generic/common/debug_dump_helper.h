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

#include <android-base/file.h>
#include <log/log.h>
#include <mutex>

namespace android {
namespace hardware {
namespace contexthub {

/**
 * Class to help request and synchronize dumping CHRE debug information.
 */
class DebugDumpHelper {
 public:
  virtual ~DebugDumpHelper() {}

  /**
   * Implementation specific method to send a debug dump request.
   *
   * @return true on a successful request, false otherwise.
   */
  virtual bool requestDebugDump() = 0;

  /**
   * Implementation specific method to write a string to a debug file.
   * Note that this function must only be called after a debug dump request
   * has been initiated via 'startDebugDump()'.
   *
   * @param str String to be written to the debug dump file. MUST be NULL
   *        terminated.
   */
  virtual void writeToDebugFile(const char *str) = 0;

  /**
   * Optional implementation specific method to write any debug info private to
   * said implementation.
   * Note that this function must only be called after a debug dump request
   * has been initiated via 'startDebugDump()'.
   */
  virtual void debugDumpFinish() {}

  bool checkDebugFd() {
    return mDebugFd != kInvalidFd;
  }

  int getDebugFd() const {
    return mDebugFd;
  }

  void invalidateDebugFd() {
    mDebugFd = kInvalidFd;
  }

  /**
   * Initiate a debug dump request.
   *
   * @param fd Posix file descriptor to write debug information into.
   */
  void debugDumpStart(int fd) {
    // Timeout inside CHRE is typically 5 seconds, grant 500ms extra here to let
    // the data reach us
    constexpr auto kDebugDumpTimeout = std::chrono::milliseconds(5500);
    mDebugFd = fd;
    if (mDebugFd < 0) {
      ALOGW("Can't dump debug info to invalid fd %d", mDebugFd);
    } else {
      writeToDebugFile("-- Dumping CHRE debug info --\n");

      ALOGV("Sending debug dump request");
      std::unique_lock<std::mutex> lock(mDebugDumpMutex);
      mDebugDumpPending = true;
      if (!requestDebugDump()) {
        ALOGW("Couldn't send debug dump request");
      } else {
        mDebugDumpCond.wait_for(lock, kDebugDumpTimeout,
                                [this]() { return !mDebugDumpPending; });
        if (mDebugDumpPending) {
          ALOGE("Timed out waiting on debug dump data");
          mDebugDumpPending = false;
        }
      }
    }
  }

  /**
   * Append to a debug dump file asynchronously. Note that a call to this
   * function will only go through if a debug dump request was already
   * initiated via debugDumpStart().
   *
   * @param str Debug string to append to the file.
   */
  void debugDumpAppend(std::string &str) {
    if (mDebugFd == kInvalidFd) {
      ALOGW("Got unexpected debug dump data message");
    } else {
      writeToDebugFile(str.c_str());
    }
  }

  /**
   * Called at the end of a debug dump request.
   */
  void debugDumpComplete() {
    std::lock_guard<std::mutex> lock(mDebugDumpMutex);
    if (!mDebugDumpPending) {
      ALOGI("Ignoring duplicate/unsolicited debug dump response");
    } else {
      mDebugDumpPending = false;
      mDebugDumpCond.notify_all();
      invalidateDebugFd();
    }
  }

 private:
  static constexpr int kInvalidFd = -1;
  int mDebugFd = kInvalidFd;
  bool mDebugDumpPending = false;
  std::mutex mDebugDumpMutex;
  std::condition_variable mDebugDumpCond;
};

}  // namespace contexthub
}  // namespace hardware
}  // namespace android