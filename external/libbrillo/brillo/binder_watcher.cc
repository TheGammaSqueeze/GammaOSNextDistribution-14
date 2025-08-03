/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include <brillo/binder_watcher.h>

#include <base/bind.h>
#include <base/logging.h>
#include <binder/IPCThreadState.h>
#include <binder/ProcessState.h>

using android::IPCThreadState;
using android::ProcessState;

namespace {
// Called from the message loop whenever the binder file descriptor is ready.
void OnBinderReadReady() {
  IPCThreadState::self()->handlePolledCommands();
}
}  // namespace

namespace brillo {

BinderWatcher::BinderWatcher() = default;

BinderWatcher::~BinderWatcher() = default;

bool BinderWatcher::Init() {
  int binder_fd = -1;
  ProcessState::self()->setThreadPoolMaxThreadCount(0);
  IPCThreadState::self()->disableBackgroundScheduling(true);
  int err = IPCThreadState::self()->setupPolling(&binder_fd);
  if (err != 0) {
    LOG(ERROR) << "Error setting up binder polling: "
               << logging::SystemErrorCodeToString(err);
    return false;
  }
  if (binder_fd < 0) {
    LOG(ERROR) << "Invalid binder FD " << binder_fd;
    return false;
  }
  VLOG(1) << "Got binder FD " << binder_fd;

  watcher_ = base::FileDescriptorWatcher::WatchReadable(
      binder_fd,
      base::BindRepeating(&OnBinderReadReady));
  if (!watcher_) {
    LOG(ERROR) << "Failed to watch binder FD";
    return false;
  }
  return true;
}

}  // namespace brillo
