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

#pragma once

#include <atomic>
#include <condition_variable>
#include <future>
#include <memory>
#include <mutex>
#include <thread>

#include "hal/snoop_logger_socket.h"

namespace bluetooth {
namespace hal {

class SnoopLoggerSocketThread : public SnoopLoggerSocketInterface {
 public:
  SnoopLoggerSocketThread(std::unique_ptr<SnoopLoggerSocket>&& socket);
  SnoopLoggerSocketThread(const SnoopLoggerSocket&) = delete;
  SnoopLoggerSocketThread& operator=(const SnoopLoggerSocketThread&) = delete;
  virtual ~SnoopLoggerSocketThread();

  std::future<bool> Start();
  void Stop();
  void Write(const void* data, size_t length) override;
  bool ThreadIsRunning() const;

  SnoopLoggerSocket* GetSocket();

 private:
  void Run(std::promise<bool> thread_started);

  std::unique_ptr<SnoopLoggerSocket> socket_;

  // Socket thread for listening to incoming connections.
  std::unique_ptr<std::thread> listen_thread_;
  bool listen_thread_running_ = false;

  std::condition_variable listen_thread_running_cv_;
  std::mutex listen_thread_running_mutex_;
  std::atomic<bool> stop_thread_;
};

}  // namespace hal
}  // namespace bluetooth
