/*
 * Copyright 2020 The Android Open Source Project
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

#include "hal/snoop_logger_socket_thread.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <netinet/in.h>
#include <sys/socket.h>

#include <future>

#include "common/init_flags.h"
#include "hal/snoop_logger_common.h"
#include "hal/syscall_wrapper_impl.h"
#include "os/utils.h"

static const char* test_flags[] = {
    "INIT_logging_debug_enabled_for_all=true",
    nullptr,
};

namespace testing {

using bluetooth::hal::SnoopLoggerCommon;
using bluetooth::hal::SnoopLoggerSocket;
using bluetooth::hal::SnoopLoggerSocketThread;
using bluetooth::hal::SyscallWrapperImpl;

static constexpr int INVALID_FD = -1;

class SnoopLoggerSocketThreadModuleTest : public Test {
 protected:
  void SetUp() override {
    bluetooth::common::InitFlags::Load(test_flags);
  }

  void TearDown() override {}
};

TEST_F(SnoopLoggerSocketThreadModuleTest, socket_start_no_stop_test) {
  {
    SyscallWrapperImpl socket_if;
    SnoopLoggerSocketThread sls(std::make_unique<SnoopLoggerSocket>(&socket_if));
    auto thread_start_future = sls.Start();
    thread_start_future.wait();
    ASSERT_TRUE(thread_start_future.get());
  }

  // Destructor calls Stop();
}

TEST_F(SnoopLoggerSocketThreadModuleTest, socket_stop_no_start_test) {
  SyscallWrapperImpl socket_if;
  SnoopLoggerSocketThread sls(std::make_unique<SnoopLoggerSocket>(&socket_if));
  sls.Stop();

  ASSERT_FALSE(sls.ThreadIsRunning());
}

TEST_F(SnoopLoggerSocketThreadModuleTest, socket_start_stop_test) {
  SyscallWrapperImpl socket_if;
  SnoopLoggerSocketThread sls(std::make_unique<SnoopLoggerSocket>(&socket_if));
  auto thread_start_future = sls.Start();
  thread_start_future.wait();
  ASSERT_TRUE(thread_start_future.get());

  sls.Stop();

  ASSERT_FALSE(sls.ThreadIsRunning());
}

TEST_F(SnoopLoggerSocketThreadModuleTest, socket_repeated_start_stop_test) {
  int repeat = 10;
  {
    SyscallWrapperImpl socket_if;
    SnoopLoggerSocketThread sls(std::make_unique<SnoopLoggerSocket>(&socket_if));

    for (int i = 0; i < repeat; ++i) {
      auto thread_start_future = sls.Start();
      thread_start_future.wait();
      ASSERT_TRUE(thread_start_future.get());

      sls.Stop();

      ASSERT_FALSE(sls.ThreadIsRunning());
    }
  }
}

TEST_F(SnoopLoggerSocketThreadModuleTest, socket_connect_test) {
  int ret = 0;
  SyscallWrapperImpl socket_if;
  SnoopLoggerSocketThread sls(std::make_unique<SnoopLoggerSocket>(&socket_if));
  auto thread_start_future = sls.Start();
  thread_start_future.wait();
  ASSERT_TRUE(thread_start_future.get());

  // // Create a TCP socket file descriptor
  int socket_fd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, IPPROTO_TCP);
  ASSERT_TRUE(socket_fd != INVALID_FD);

  struct sockaddr_in addr;
  addr.sin_family = AF_INET;
  addr.sin_addr.s_addr = htonl(SnoopLoggerSocket::DEFAULT_LOCALHOST_);
  addr.sin_port = htons(SnoopLoggerSocket::DEFAULT_LISTEN_PORT_);

  // Connect to snoop logger socket
  RUN_NO_INTR(ret = connect(socket_fd, (struct sockaddr*)&addr, sizeof(addr)));
  ASSERT_TRUE(ret == 0);

  sls.Stop();

  ASSERT_FALSE(sls.ThreadIsRunning());
  close(socket_fd);
}

TEST_F(SnoopLoggerSocketThreadModuleTest, socket_connect_disconnect_test) {
  int ret = 0;
  SyscallWrapperImpl socket_if;
  SnoopLoggerSocketThread sls(std::make_unique<SnoopLoggerSocket>(&socket_if));
  auto thread_start_future = sls.Start();
  thread_start_future.wait();
  ASSERT_TRUE(thread_start_future.get());

  // // Create a TCP socket file descriptor
  int socket_fd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, IPPROTO_TCP);
  ASSERT_TRUE(socket_fd != INVALID_FD);

  struct sockaddr_in addr;
  addr.sin_family = AF_INET;
  addr.sin_addr.s_addr = htonl(SnoopLoggerSocket::DEFAULT_LOCALHOST_);
  addr.sin_port = htons(SnoopLoggerSocket::DEFAULT_LISTEN_PORT_);

  // Connect to snoop logger socket
  RUN_NO_INTR(ret = connect(socket_fd, (struct sockaddr*)&addr, sizeof(addr)));
  ASSERT_TRUE(ret == 0);

  // Close snoop logger socket
  RUN_NO_INTR(ret = close(socket_fd));
  ASSERT_TRUE(ret == 0);

  sls.Stop();

  ASSERT_FALSE(sls.ThreadIsRunning());
  close(socket_fd);
}

TEST_F(SnoopLoggerSocketThreadModuleTest, socket_send_no_start_test) {
  SyscallWrapperImpl socket_if;
  SnoopLoggerSocketThread sls(std::make_unique<SnoopLoggerSocket>(&socket_if));

  ASSERT_FALSE(sls.ThreadIsRunning());

  sls.Write(&SnoopLoggerCommon::kBtSnoopFileHeader, sizeof(SnoopLoggerCommon::FileHeaderType));

  ASSERT_FALSE(sls.ThreadIsRunning());
}

TEST_F(SnoopLoggerSocketThreadModuleTest, socket_send_before_connect_test) {
  int ret = 0;
  SyscallWrapperImpl socket_if;
  SnoopLoggerSocketThread sls(std::make_unique<SnoopLoggerSocket>(&socket_if));
  auto thread_start_future = sls.Start();
  thread_start_future.wait();
  ASSERT_TRUE(thread_start_future.get());

  char test_data[] = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0f};
  sls.Write(test_data, sizeof(test_data));

  // // Create a TCP socket file descriptor
  int socket_fd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, IPPROTO_TCP);
  ASSERT_TRUE(socket_fd != INVALID_FD);

  struct sockaddr_in addr;
  addr.sin_family = AF_INET;
  addr.sin_addr.s_addr = htonl(SnoopLoggerSocket::DEFAULT_LOCALHOST_);
  addr.sin_port = htons(SnoopLoggerSocket::DEFAULT_LISTEN_PORT_);

  // Connect to snoop logger socket
  RUN_NO_INTR(ret = connect(socket_fd, (struct sockaddr*)&addr, sizeof(addr)));
  ASSERT_TRUE(ret == 0);

  char recv_buf1[sizeof(SnoopLoggerCommon::FileHeaderType)];
  char recv_buf2[sizeof(test_data)];
  int bytes_read = -1;

  auto a = std::async(std::launch::async, [socket_fd, &recv_buf1, &recv_buf2] {
    recv(socket_fd, recv_buf1, sizeof(recv_buf1), 0);
    return recv(socket_fd, recv_buf2, sizeof(recv_buf2), MSG_DONTWAIT);
  });

  sls.GetSocket()->WaitForClientSocketConnected();
  a.wait();
  bytes_read = a.get();
  ASSERT_EQ(bytes_read, -1);
  close(socket_fd);
}

TEST_F(SnoopLoggerSocketThreadModuleTest, socket_recv_file_header_test) {
  int ret = 0;
  SyscallWrapperImpl socket_if;
  SnoopLoggerSocketThread sls(std::make_unique<SnoopLoggerSocket>(&socket_if));
  auto thread_start_future = sls.Start();
  thread_start_future.wait();
  ASSERT_TRUE(thread_start_future.get());

  // // Create a TCP socket file descriptor
  int socket_fd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, IPPROTO_TCP);
  ASSERT_TRUE(socket_fd != INVALID_FD);

  struct sockaddr_in addr;
  addr.sin_family = AF_INET;
  addr.sin_addr.s_addr = htonl(SnoopLoggerSocket::DEFAULT_LOCALHOST_);
  addr.sin_port = htons(SnoopLoggerSocket::DEFAULT_LISTEN_PORT_);

  // Connect to snoop logger socket
  RUN_NO_INTR(ret = connect(socket_fd, (struct sockaddr*)&addr, sizeof(addr)));
  ASSERT_TRUE(ret == 0);

  char recv_buf[sizeof(SnoopLoggerCommon::FileHeaderType)];
  int bytes_read = -1;

  auto a = std::async(std::launch::async, [socket_fd, &recv_buf] {
    return recv(socket_fd, recv_buf, sizeof(SnoopLoggerCommon::FileHeaderType), 0);
  });

  sls.GetSocket()->WaitForClientSocketConnected();

  a.wait();
  bytes_read = a.get();

  ASSERT_EQ(bytes_read, static_cast<int>(sizeof(SnoopLoggerCommon::FileHeaderType)));
  ASSERT_TRUE(std::memcmp(recv_buf, &SnoopLoggerCommon::kBtSnoopFileHeader, bytes_read) == 0);
  close(socket_fd);
}

TEST_F(SnoopLoggerSocketThreadModuleTest, socket_send_recv_test) {
  int ret = 0;
  SyscallWrapperImpl socket_if;
  SnoopLoggerSocketThread sls(std::make_unique<SnoopLoggerSocket>(&socket_if));
  auto thread_start_future = sls.Start();
  thread_start_future.wait();
  ASSERT_TRUE(thread_start_future.get());

  // // Create a TCP socket file descriptor
  int socket_fd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, IPPROTO_TCP);
  ASSERT_TRUE(socket_fd != INVALID_FD);

  struct sockaddr_in addr;
  addr.sin_family = AF_INET;
  addr.sin_addr.s_addr = htonl(SnoopLoggerSocket::DEFAULT_LOCALHOST_);
  addr.sin_port = htons(SnoopLoggerSocket::DEFAULT_LISTEN_PORT_);

  // Connect to snoop logger socket
  RUN_NO_INTR(ret = connect(socket_fd, (struct sockaddr*)&addr, sizeof(addr)));
  ASSERT_TRUE(ret == 0);

  char test_data[] = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0f};

  char recv_buf1[sizeof(SnoopLoggerCommon::FileHeaderType)];
  char recv_buf2[sizeof(test_data)];
  int bytes_read = -1;

  auto a = std::async(std::launch::async, [socket_fd, &recv_buf1, &recv_buf2] {
    recv(socket_fd, recv_buf1, sizeof(recv_buf1), 0);
    return recv(socket_fd, recv_buf2, sizeof(recv_buf2), 0);
  });

  sls.GetSocket()->WaitForClientSocketConnected();

  sls.Write(test_data, sizeof(test_data));
  a.wait();
  bytes_read = a.get();

  ASSERT_TRUE(std::memcmp(recv_buf1, &SnoopLoggerCommon::kBtSnoopFileHeader, sizeof(recv_buf1)) == 0);

  ASSERT_EQ(bytes_read, static_cast<int>(sizeof(test_data)));
  ASSERT_TRUE(std::memcmp(recv_buf2, test_data, bytes_read) == 0);
  close(socket_fd);
}

}  // namespace testing
