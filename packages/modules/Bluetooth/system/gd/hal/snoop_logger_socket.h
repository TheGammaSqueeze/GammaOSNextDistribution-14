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

#include <condition_variable>
#include <memory>
#include <mutex>
#include <thread>

#include "hal/snoop_logger_socket_interface.h"
#include "hal/syscall_wrapper_interface.h"

namespace bluetooth {
namespace hal {

class SnoopLoggerSocket {
 public:
  static constexpr int DEFAULT_LOCALHOST_ = 0x7F000001;
  static constexpr int DEFAULT_LISTEN_PORT_ = 8872;

  SnoopLoggerSocket(
      SyscallWrapperInterface* syscall_if, int address = DEFAULT_LOCALHOST_, int port = DEFAULT_LISTEN_PORT_);
  SnoopLoggerSocket(const SnoopLoggerSocket&) = delete;
  SnoopLoggerSocket& operator=(const SnoopLoggerSocket&) = delete;
  virtual ~SnoopLoggerSocket();

  int InitializeCommunications();
  bool ProcessIncomingRequest();
  void Cleanup();
  bool IsClientSocketConnected() const;
  bool WaitForClientSocketConnected();
  int NotifySocketListener();
  void Write(const void* data, size_t length);

  int AcceptIncomingConnection(int listen_socket, int& client_socket);
  int CreateSocket();
  void ClientSocketConnected(int client_socket);
  void InitializeClientSocket(int client_socket);
  void SafeCloseSocket(int& fd);
  void Write(int& client_socket, const void* data, size_t length);

  SyscallWrapperInterface* GetSyscallWrapperInterface() const;

 private:
  // Pointer to syscall interface
  SyscallWrapperInterface* syscall_if_;

  // Server socket address and port.
  int socket_address_;
  int socket_port_;

  // A pair of FD to send information to the listen thread.
  int notification_listen_fd_;
  int notification_write_fd_;

  // Server socket
  int listen_socket_;

  // Socket FDs for listening for connections
  // and for communitcation with listener thread.
  fd_set save_sock_fds_;
  int fd_max_;

  // Reference to connected client socket.
  std::mutex client_socket_mutex_;
  int client_socket_;
  std::condition_variable client_socket_cv_;
};

}  // namespace hal
}  // namespace bluetooth
