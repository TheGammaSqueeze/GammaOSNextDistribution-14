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

/**
 * @file
 * The daemon that hosts CHRE on a hexagon DSP via FastRPC. This is typically
 * the SLPI but could be the ADSP or another DSP that supports FastRPC.
 */

#ifndef CHRE_FASTRPC_DAEMON_H_
#define CHRE_FASTRPC_DAEMON_H_

#include "chre/platform/slpi/fastrpc.h"
#include "chre_host/fbs_daemon_base.h"
#include "chre_host/st_hal_lpma_handler.h"

#include <utils/SystemClock.h>
#include <atomic>
#include <optional>
#include <thread>

namespace android {
namespace chre {

class FastRpcChreDaemon : public FbsDaemonBase {
 public:
  FastRpcChreDaemon();

  ~FastRpcChreDaemon() {
    deinit();
  }

  /**
   * Initializes and starts the monitoring and message handling threads,
   * then proceeds to load any preloaded nanoapps. Also starts LPMA if
   * it's enabled.
   *
   * @return true on successful init
   */
  bool init();

  /**
   * Starts a socket server receive loop for inbound messages.
   */
  void run();

 protected:
  bool doSendMessage(void *data, size_t length) override;

  void configureLpma(bool enabled) override {
    mLpmaHandler.enable(enabled);
  }

 private:
  std::optional<std::thread> mMonitorThread;
  std::optional<std::thread> mMsgToHostThread;
  std::atomic_bool mCrashDetected = false;
  StHalLpmaHandler mLpmaHandler;

  /**
   * Shutsdown the daemon, stops all the worker threads created in init()
   * Since this is to be invoked at exit, it's mostly best effort, and is
   * invoked by the class destructor
   */
  void deinit();

  /**
   * Platform specific getTimeOffset for the FastRPC daemon
   *
   * @return clock drift offset in nanoseconds
   */
  int64_t getTimeOffset(bool *success) override;

  /**
   * Entry point for the thread that blocks in a FastRPC call to monitor for
   * abnormal exit of CHRE or reboot of the DSP.
   */
  void monitorThreadEntry();

  /**
   * Entry point for the thread that receives messages sent by CHRE.
   */
  void msgToHostThreadEntry();

  /**
   * Handles the case where the remote end (SLPI, ADSP, etc) has crashed.
   */
  void onRemoteCrashDetected();
};

}  // namespace chre
}  // namespace android

#endif  // CHRE_FASTRPC_DAEMON_H_
