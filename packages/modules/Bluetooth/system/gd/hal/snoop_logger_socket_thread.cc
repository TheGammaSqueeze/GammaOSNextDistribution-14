/******************************************************************************
 *
 *  Copyright (C) 2022 Google, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#include "hal/snoop_logger_socket_thread.h"

#include <arpa/inet.h>
#include <base/logging.h>
#include <fcntl.h>
#include <netinet/in.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/un.h>
#include <unistd.h>

#include "common/init_flags.h"
#include "hal/snoop_logger_common.h"
#include "os/log.h"

namespace bluetooth {
namespace hal {

SnoopLoggerSocketThread::SnoopLoggerSocketThread(std::unique_ptr<SnoopLoggerSocket>&& socket) {
  socket_ = std::move(socket);
  stop_thread_ = false;
  listen_thread_running_ = false;
}

SnoopLoggerSocketThread::~SnoopLoggerSocketThread() {
  Stop();
}

std::future<bool> SnoopLoggerSocketThread::Start() {
  LOG_DEBUG("");
  std::promise<bool> thread_started;
  auto future = thread_started.get_future();
  listen_thread_ = std::make_unique<std::thread>(&SnoopLoggerSocketThread::Run, this, std::move(thread_started));
  stop_thread_ = false;
  return std::move(future);
}

void SnoopLoggerSocketThread::Stop() {
  LOG_DEBUG("");

  stop_thread_ = true;
  socket_->NotifySocketListener();

  if (listen_thread_ && listen_thread_->joinable()) {
    listen_thread_->join();
    listen_thread_.reset();
  }
}

void SnoopLoggerSocketThread::Write(const void* data, size_t length) {
  socket_->Write(data, length);
}

bool SnoopLoggerSocketThread::ThreadIsRunning() const {
  return listen_thread_running_;
}

SnoopLoggerSocket* SnoopLoggerSocketThread::GetSocket() {
  return socket_.get();
}

void SnoopLoggerSocketThread::Run(std::promise<bool> thread_started) {
  LOG_DEBUG("");

  if (socket_->InitializeCommunications() != 0) {
    thread_started.set_value(false);
    return;
  }

  thread_started.set_value(true);

  while (!stop_thread_ && socket_->ProcessIncomingRequest()) {
  }

  socket_->Cleanup();
  listen_thread_running_ = false;
}

}  // namespace hal
}  // namespace bluetooth
