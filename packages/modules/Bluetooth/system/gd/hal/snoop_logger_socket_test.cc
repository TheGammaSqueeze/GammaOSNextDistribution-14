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

#include "hal/snoop_logger_socket.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <netinet/in.h>
#include <sys/socket.h>

#include <cerrno>

#include "common/init_flags.h"
#include "hal/snoop_logger_common.h"
#include "hal/syscall_wrapper_impl.h"
#include "hal/syscall_wrapper_mock.h"

static const char* test_flags[] = {
    "INIT_logging_debug_enabled_for_all=true",
    nullptr,
};

namespace testing {

using bluetooth::hal::SnoopLoggerCommon;
using bluetooth::hal::SnoopLoggerSocket;
using bluetooth::hal::SyscallWrapperImpl;
using bluetooth::hal::SyscallWrapperMock;

static constexpr int INVALID_FD = -1;

class SnoopLoggerSocketModuleTest : public Test {
 protected:
  void SetUp() override {
    bluetooth::common::InitFlags::Load(test_flags);
  }
  SnoopLoggerSocketModuleTest() : sls(&mock) {}

  void InitializeCommunicationsSuccess(SnoopLoggerSocket& sls, SyscallWrapperMock& mock) {
    ON_CALL(mock, Pipe2).WillByDefault(Invoke([&](int* fds, int) {
      fds[0] = listen_fd;
      fds[1] = write_fd;
      return 0;
    }));
    ON_CALL(mock, Socket).WillByDefault((Return(fd)));
    ON_CALL(mock, Setsockopt(Eq(fd), _, _, _, _)).WillByDefault((Return(0)));
    ON_CALL(mock, Bind(Eq(fd), _, _)).WillByDefault((Return(0)));
    ON_CALL(mock, Listen(Eq(fd), _)).WillByDefault((Return(0)));

    EXPECT_CALL(mock, FDZero);
    EXPECT_CALL(mock, Pipe2(_, _));
    EXPECT_CALL(mock, FDSet(Eq(listen_fd), _));
    EXPECT_CALL(mock, FDSet(Eq(fd), _));
    EXPECT_CALL(mock, Socket);
    EXPECT_CALL(mock, Setsockopt);
    EXPECT_CALL(mock, Bind);
    EXPECT_CALL(mock, Listen);

    ASSERT_EQ(sls.InitializeCommunications(), 0);

    // will be called in destructor
    EXPECT_CALL(mock, Close(Eq(fd)));
    EXPECT_CALL(mock, FDClr(Eq(fd), _));
    EXPECT_CALL(mock, Close(Eq(listen_fd)));
    EXPECT_CALL(mock, FDClr(Eq(listen_fd), _));
    EXPECT_CALL(mock, Close(Eq(write_fd)));
    EXPECT_CALL(mock, FDClr(Eq(write_fd), _));
  }

  void TearDown() override {}

  int fd = 11;
  const int listen_fd = 66;
  const int write_fd = 99;

  StrictMock<SyscallWrapperMock> mock;
  SnoopLoggerSocket sls;
};

TEST_F(SnoopLoggerSocketModuleTest, test_Constructor_GetSyscallWrapperInterface) {
  ASSERT_EQ(sls.GetSyscallWrapperInterface(), &mock);
}

TEST_F(SnoopLoggerSocketModuleTest, test_Destructor_implicit_cleanup) {}

TEST_F(SnoopLoggerSocketModuleTest, test_Cleanup_explicit) {
  sls.Cleanup();
}

TEST_F(SnoopLoggerSocketModuleTest, test_CreateSocket_fail_on_Socket) {
  ON_CALL(mock, Socket(_, _, _)).WillByDefault((Return(-1)));

  EXPECT_CALL(mock, Socket).Times(1);
  EXPECT_CALL(mock, GetErrno);
  ASSERT_EQ(sls.CreateSocket(), INVALID_FD);
}

TEST_F(SnoopLoggerSocketModuleTest, test_CreateSocket_fail_on_Setsockopt) {
  ON_CALL(mock, Socket(_, _, _)).WillByDefault((Return(fd)));
  ON_CALL(mock, Setsockopt(_, _, _, _, _)).WillByDefault((Return(-1)));

  EXPECT_CALL(mock, Socket);
  EXPECT_CALL(mock, Setsockopt);
  EXPECT_CALL(mock, Close);
  EXPECT_CALL(mock, FDClr(Eq(fd), _));
  EXPECT_CALL(mock, FDSet(Eq(fd), _));
  EXPECT_CALL(mock, GetErrno);
  ASSERT_EQ(sls.CreateSocket(), INVALID_FD);
}

TEST_F(SnoopLoggerSocketModuleTest, test_CreateSocket_fail_on_Bind) {
  ON_CALL(mock, Socket(_, _, _)).WillByDefault((Return(fd)));
  ON_CALL(mock, Setsockopt(_, _, _, _, _)).WillByDefault((Return(0)));
  ON_CALL(mock, Bind(_, _, _)).WillByDefault((Return(-1)));

  EXPECT_CALL(mock, Socket);
  EXPECT_CALL(mock, Setsockopt);
  EXPECT_CALL(mock, Bind);
  EXPECT_CALL(mock, Close);
  EXPECT_CALL(mock, FDSet(Eq(fd), _));
  EXPECT_CALL(mock, FDClr(Eq(fd), _));
  EXPECT_CALL(mock, GetErrno);
  ASSERT_EQ(sls.CreateSocket(), INVALID_FD);
}

TEST_F(SnoopLoggerSocketModuleTest, test_CreateSocket_fail_on_Listen) {
  ON_CALL(mock, Socket(_, _, _)).WillByDefault((Return(fd)));
  ON_CALL(mock, Setsockopt(_, _, _, _, _)).WillByDefault((Return(0)));
  ON_CALL(mock, Bind(_, _, _)).WillByDefault((Return(0)));
  ON_CALL(mock, Listen(_, _)).WillByDefault((Return(-1)));

  EXPECT_CALL(mock, Socket);
  EXPECT_CALL(mock, Setsockopt);
  EXPECT_CALL(mock, Bind);
  EXPECT_CALL(mock, Listen);
  EXPECT_CALL(mock, Close);
  EXPECT_CALL(mock, FDSet(Eq(fd), _));
  EXPECT_CALL(mock, FDClr(Eq(fd), _));
  EXPECT_CALL(mock, GetErrno);
  ASSERT_EQ(sls.CreateSocket(), INVALID_FD);
}

TEST_F(SnoopLoggerSocketModuleTest, test_CreateSocket_success) {
  ON_CALL(mock, Socket(_, _, _)).WillByDefault((Return(fd)));
  ON_CALL(mock, Setsockopt(_, _, _, _, _)).WillByDefault((Return(0)));
  ON_CALL(mock, Bind(_, _, _)).WillByDefault((Return(0)));
  ON_CALL(mock, Listen(_, _)).WillByDefault((Return(0)));

  EXPECT_CALL(mock, Socket);
  EXPECT_CALL(mock, Setsockopt);
  EXPECT_CALL(mock, Bind);
  EXPECT_CALL(mock, Listen);
  EXPECT_CALL(mock, FDSet(Eq(fd), _));
  ASSERT_EQ(sls.CreateSocket(), fd);
}

TEST_F(SnoopLoggerSocketModuleTest, test_Write_fd_invalid_fd) {
  fd = INVALID_FD;

  sls.Write(fd, NULL, 0);
}

TEST_F(SnoopLoggerSocketModuleTest, test_Write_fd_fail_on_Send_ECONNRESET) {
  char data[10];

  ON_CALL(mock, Send(_, _, _, _)).WillByDefault((Return(-1)));
  ON_CALL(mock, GetErrno()).WillByDefault((Return(ECONNRESET)));

  EXPECT_CALL(mock, Send(Eq(fd), Eq(data), Eq(sizeof(data)), _));
  EXPECT_CALL(mock, Close(Eq(fd)));
  EXPECT_CALL(mock, FDClr(Eq(fd), _));
  EXPECT_CALL(mock, GetErrno);

  sls.Write(fd, data, sizeof(data));
}

TEST_F(SnoopLoggerSocketModuleTest, test_Write_fd_fail_on_Send_EINVAL) {
  char data[10];

  ON_CALL(mock, Send(_, _, _, _)).WillByDefault((Return(-1)));
  ON_CALL(mock, GetErrno()).WillByDefault((Return(EINVAL)));

  EXPECT_CALL(mock, Send(Eq(fd), Eq(data), Eq(sizeof(data)), _));
  EXPECT_CALL(mock, GetErrno).Times(2);

  sls.Write(fd, data, sizeof(data));
}

TEST_F(SnoopLoggerSocketModuleTest, test_Write_fd_success) {
  int client_fd = 33;
  char data[10];

  EXPECT_CALL(mock, Send(client_fd, _, _, _)).Times(1);

  sls.Write(client_fd, data, sizeof(data));
}

TEST_F(SnoopLoggerSocketModuleTest, test_Write_fail_no_client) {
  char data[10];

  sls.Write(data, sizeof(data));
}

TEST_F(SnoopLoggerSocketModuleTest, test_Write_success) {
  int client_fd = 33;
  char data[10];

  EXPECT_CALL(mock, Send(client_fd, _, _, _)).Times(1);
  EXPECT_CALL(mock, Close(client_fd)).Times(1);

  sls.ClientSocketConnected(client_fd);

  sls.Write(data, sizeof(data));
  EXPECT_CALL(mock, FDClr(Eq(client_fd), _));
}

TEST_F(SnoopLoggerSocketModuleTest, test_Write_fd_fail_on_Send_EINTR) {
  char data[10];
  int intr_count = 5;

  ON_CALL(mock, Send)
      .WillByDefault(
          Invoke([&](int /* fd */, const void* /* buf */, size_t /* n */, int /* flags */) {
            if (intr_count > 0) {
              intr_count--;
              errno = EINTR;
              return -1;
            }
            errno = 0;
            return 0;
          }));

  EXPECT_CALL(mock, Send(Eq(fd), Eq(data), Eq(sizeof(data)), _)).Times(intr_count + 1);

  sls.Write(fd, data, sizeof(data));
}

TEST_F(SnoopLoggerSocketModuleTest, test_ClientSocketConnected) {
  ASSERT_FALSE(sls.IsClientSocketConnected());

  EXPECT_CALL(mock, Close(Eq(fd))).Times(1);
  EXPECT_CALL(mock, Close(Eq(fd + 1))).Times(1);
  EXPECT_CALL(mock, FDClr(Eq(fd), _));
  EXPECT_CALL(mock, FDClr(Eq(fd + 1), _));

  sls.ClientSocketConnected(fd);

  ASSERT_TRUE(sls.IsClientSocketConnected());

  sls.ClientSocketConnected(fd + 1);

  ASSERT_TRUE(sls.IsClientSocketConnected());
}

TEST_F(SnoopLoggerSocketModuleTest, test_WaitForClientSocketConnected) {
  ASSERT_FALSE(sls.IsClientSocketConnected());

  sls.ClientSocketConnected(fd);

  ASSERT_TRUE(sls.IsClientSocketConnected());

  ASSERT_TRUE(sls.WaitForClientSocketConnected());

  EXPECT_CALL(mock, Close(Eq(fd)));
  EXPECT_CALL(mock, FDClr(Eq(fd), _));
}

TEST_F(SnoopLoggerSocketModuleTest, test_InitializeClientSocket) {
  int client_fd = 10;

  EXPECT_CALL(mock, Send(client_fd, _, _, _)).Times(1);

  sls.InitializeClientSocket(client_fd);
}

TEST_F(SnoopLoggerSocketModuleTest, test_AcceptIncomingConnection_fail_on_accept_EINVAL) {
  int client_fd = 0;

  ON_CALL(mock, Accept(Eq(fd), _, _, _)).WillByDefault(Return(INVALID_FD));
  ON_CALL(mock, GetErrno()).WillByDefault(Return(EINVAL));

  EXPECT_CALL(mock, Accept(Eq(fd), _, _, _));
  EXPECT_CALL(mock, GetErrno);
  ASSERT_EQ(sls.AcceptIncomingConnection(fd, client_fd), EINVAL);
}

TEST_F(SnoopLoggerSocketModuleTest, test_AcceptIncomingConnection_fail_on_accept_EBADF) {
  int client_fd = 0;

  ON_CALL(mock, Accept(Eq(fd), _, _, _)).WillByDefault(Return(INVALID_FD));
  ON_CALL(mock, GetErrno()).WillByDefault(Return(EBADF));

  EXPECT_CALL(mock, Accept(Eq(fd), _, _, _));
  EXPECT_CALL(mock, GetErrno);
  ASSERT_EQ(sls.AcceptIncomingConnection(fd, client_fd), EBADF);
}

TEST_F(SnoopLoggerSocketModuleTest, test_AcceptIncomingConnection_fail_on_accept_EINTR) {
  int client_fd = 0;
  int intr_count = 5;

  ON_CALL(mock, Accept(Eq(fd), _, _, _))
      .WillByDefault(Invoke([&](int /* fd */,
                                struct sockaddr* /* addr */,
                                socklen_t* /* addr_len */,
                                int /* flags */) {
        if (intr_count > 0) {
          intr_count--;
          errno = EINTR;
          return -1;
        }
        errno = 0;
        return client_fd;
      }));

  EXPECT_CALL(mock, Accept(Eq(fd), _, _, _)).Times(intr_count + 1);  // 5 intr + 1 with errno = 0
  EXPECT_CALL(mock, GetErrno);
  ASSERT_EQ(sls.AcceptIncomingConnection(fd, client_fd), 0);
}

TEST_F(SnoopLoggerSocketModuleTest, test_AcceptIncomingConnection_fail_on_accept_other_errors) {
  int client_fd = 0;

  ON_CALL(mock, Accept(Eq(fd), _, _, _)).WillByDefault(Return(INVALID_FD));
  ON_CALL(mock, GetErrno()).WillByDefault(Return(EAGAIN));

  EXPECT_CALL(mock, Accept(Eq(fd), _, _, _));
  EXPECT_CALL(mock, GetErrno);
  ASSERT_EQ(sls.AcceptIncomingConnection(fd, client_fd), 0);
  ASSERT_EQ(client_fd, -1);
}

TEST_F(SnoopLoggerSocketModuleTest, test_AcceptIncomingConnection_success) {
  int client_fd = 13;
  int client_fd_out = 0;

  ON_CALL(mock, Accept(Eq(fd), _, _, _)).WillByDefault(Return(client_fd));
  ON_CALL(mock, GetErrno()).WillByDefault(Return(0));

  EXPECT_CALL(mock, Accept(Eq(fd), _, _, _));

  ASSERT_EQ(sls.AcceptIncomingConnection(fd, client_fd_out), 0);
  ASSERT_EQ(client_fd, client_fd_out);
}

TEST_F(SnoopLoggerSocketModuleTest, test_InitializeCommunications_fail_on_Pipe2) {
  int ret = -9;

  ON_CALL(mock, Pipe2(_, _)).WillByDefault(Invoke([ret](int* /* fds */, int) { return ret; }));
  EXPECT_CALL(mock, FDZero);
  EXPECT_CALL(mock, Pipe2(_, _));

  ASSERT_EQ(sls.InitializeCommunications(), ret);
}

TEST_F(SnoopLoggerSocketModuleTest, test_InitializeCommunications_fail_on_CreateSocket) {
  int ret = -9;

  ON_CALL(mock, Socket(_, _, _)).WillByDefault(Return(ret));
  ON_CALL(mock, Pipe2).WillByDefault(Invoke([&](int* fds, int) {
    fds[0] = listen_fd;
    fds[1] = write_fd;
    return 0;
  }));

  EXPECT_CALL(mock, FDZero);
  EXPECT_CALL(mock, Pipe2(_, _));
  EXPECT_CALL(mock, FDSet(listen_fd, _));
  EXPECT_CALL(mock, Socket);
  EXPECT_CALL(mock, GetErrno);

  EXPECT_CALL(mock, Close(Eq(listen_fd)));
  EXPECT_CALL(mock, FDClr(Eq(listen_fd), _));
  EXPECT_CALL(mock, Close(Eq(write_fd)));
  EXPECT_CALL(mock, FDClr(Eq(write_fd), _));

  ASSERT_EQ(sls.InitializeCommunications(), -1);
}

TEST_F(SnoopLoggerSocketModuleTest, test_InitializeCommunications_success) {
  ASSERT_NO_FATAL_FAILURE(InitializeCommunicationsSuccess(sls, mock));
}

TEST_F(SnoopLoggerSocketModuleTest, test_ProcessIncomingRequest_fail_on_Select_EINTR) {
  ON_CALL(mock, Select).WillByDefault((Return(-1)));
  ON_CALL(mock, GetErrno()).WillByDefault((Return(EINTR)));

  EXPECT_CALL(mock, Select);
  EXPECT_CALL(mock, GetErrno).Times(2);
  ASSERT_TRUE(sls.ProcessIncomingRequest());
}

TEST_F(SnoopLoggerSocketModuleTest, test_ProcessIncomingRequest_fail_on_Select_EINVAL) {
  ON_CALL(mock, Select).WillByDefault((Return(-1)));
  ON_CALL(mock, GetErrno()).WillByDefault((Return(EINVAL)));

  EXPECT_CALL(mock, Select);
  EXPECT_CALL(mock, GetErrno).Times(2);
  ASSERT_FALSE(sls.ProcessIncomingRequest());
}

TEST_F(SnoopLoggerSocketModuleTest, test_ProcessIncomingRequest_no_fds) {
  ON_CALL(mock, Select).WillByDefault((Return(0)));

  EXPECT_CALL(mock, Select);
  ASSERT_TRUE(sls.ProcessIncomingRequest());
}

TEST_F(SnoopLoggerSocketModuleTest, test_ProcessIncomingRequest_FDIsSet_false) {
  ASSERT_NO_FATAL_FAILURE(InitializeCommunicationsSuccess(sls, mock));

  ON_CALL(mock, Select).WillByDefault((Return(0)));
  ON_CALL(mock, FDIsSet(fd, _)).WillByDefault((Return(false)));
  ON_CALL(mock, FDIsSet(listen_fd, _)).WillByDefault((Return(false)));

  EXPECT_CALL(mock, Select);
  EXPECT_CALL(mock, FDIsSet(Eq(fd), _));
  EXPECT_CALL(mock, FDIsSet(Eq(listen_fd), _));
  ASSERT_TRUE(sls.ProcessIncomingRequest());
}

TEST_F(SnoopLoggerSocketModuleTest, test_ProcessIncomingRequest_signal_close) {
  ASSERT_NO_FATAL_FAILURE(InitializeCommunicationsSuccess(sls, mock));

  ON_CALL(mock, Select).WillByDefault((Return(0)));
  ON_CALL(mock, FDIsSet(fd, _)).WillByDefault((Return(false)));
  ON_CALL(mock, FDIsSet(listen_fd, _)).WillByDefault((Return(true)));

  EXPECT_CALL(mock, Select);
  EXPECT_CALL(mock, FDIsSet(Eq(fd), _));
  EXPECT_CALL(mock, FDIsSet(Eq(listen_fd), _));
  ASSERT_FALSE(sls.ProcessIncomingRequest());
}

TEST_F(SnoopLoggerSocketModuleTest, test_ProcessIncomingRequest_signal_incoming_connection_fail_on_accept_exit) {
  ASSERT_NO_FATAL_FAILURE(InitializeCommunicationsSuccess(sls, mock));

  ON_CALL(mock, Select).WillByDefault((Return(0)));
  ON_CALL(mock, FDIsSet(fd, _)).WillByDefault((Return(true)));
  ON_CALL(mock, FDIsSet(listen_fd, _)).WillByDefault((Return(false)));

  ON_CALL(mock, Accept(fd, _, _, _)).WillByDefault(Return(INVALID_FD));
  ON_CALL(mock, GetErrno()).WillByDefault(Return(EINVAL));

  EXPECT_CALL(mock, Select);
  EXPECT_CALL(mock, FDIsSet(Eq(fd), _));
  EXPECT_CALL(mock, Accept(Eq(fd), _, _, _));
  EXPECT_CALL(mock, GetErrno);
  ASSERT_FALSE(sls.ProcessIncomingRequest());
}

TEST_F(SnoopLoggerSocketModuleTest, test_ProcessIncomingRequest_signal_incoming_connection_fail_on_accept_continue) {
  ASSERT_NO_FATAL_FAILURE(InitializeCommunicationsSuccess(sls, mock));

  ON_CALL(mock, Select).WillByDefault((Return(0)));
  ON_CALL(mock, FDIsSet(fd, _)).WillByDefault((Return(true)));
  ON_CALL(mock, FDIsSet(listen_fd, _)).WillByDefault((Return(false)));

  ON_CALL(mock, Accept(fd, _, _, _)).WillByDefault(Return(INVALID_FD));
  ON_CALL(mock, GetErrno()).WillByDefault(Return(ENOMEM));

  EXPECT_CALL(mock, Select);
  EXPECT_CALL(mock, FDIsSet(Eq(fd), _));
  EXPECT_CALL(mock, Accept(Eq(fd), _, _, _));
  EXPECT_CALL(mock, GetErrno);
  ASSERT_TRUE(sls.ProcessIncomingRequest());
}

TEST_F(SnoopLoggerSocketModuleTest, test_ProcessIncomingRequest_signal_incoming_connection_success) {
  ASSERT_NO_FATAL_FAILURE(InitializeCommunicationsSuccess(sls, mock));

  int client_fd = 23;

  ON_CALL(mock, Select).WillByDefault((Return(0)));
  ON_CALL(mock, FDIsSet(fd, _)).WillByDefault((Return(true)));
  ON_CALL(mock, FDIsSet(listen_fd, _)).WillByDefault((Return(false)));

  ON_CALL(mock, Accept(fd, _, _, _)).WillByDefault(Return(client_fd));
  ON_CALL(mock, GetErrno()).WillByDefault(Return(0));

  EXPECT_CALL(mock, Send(client_fd, _, _, _)).Times(1);

  EXPECT_CALL(mock, Select);
  EXPECT_CALL(mock, FDIsSet(Eq(fd), _));
  EXPECT_CALL(mock, Accept(Eq(fd), _, _, _));
  ASSERT_TRUE(sls.ProcessIncomingRequest());

  EXPECT_CALL(mock, Close(Eq(client_fd)));
  EXPECT_CALL(mock, FDClr(Eq(client_fd), _));
}

TEST_F(SnoopLoggerSocketModuleTest, test_NotifySocketListener_no_fd) {
  ASSERT_EQ(sls.NotifySocketListener(), 0);
}

TEST_F(SnoopLoggerSocketModuleTest, test_NotifySocketListener_fail_on_write) {
  ASSERT_NO_FATAL_FAILURE(InitializeCommunicationsSuccess(sls, mock));

  ON_CALL(mock, Write).WillByDefault((Return(-1)));
  EXPECT_CALL(mock, Write(write_fd, _, _)).Times(1);

  ASSERT_EQ(sls.NotifySocketListener(), -1);
}

TEST_F(SnoopLoggerSocketModuleTest, test_NotifySocketListener_fail_on_write_EINTR_success) {
  ASSERT_NO_FATAL_FAILURE(InitializeCommunicationsSuccess(sls, mock));

  int intr_count = 5;

  ON_CALL(mock, Write).WillByDefault(Invoke([&](int, const void*, size_t count) {
    if (intr_count > 0) {
      intr_count--;
      errno = EINTR;
      return (ssize_t)-1;
    }
    errno = 0;
    return (ssize_t)count;
  }));

  EXPECT_CALL(mock, Write(write_fd, _, _)).Times(intr_count + 1);

  ASSERT_EQ(sls.NotifySocketListener(), 0);
}

TEST_F(SnoopLoggerSocketModuleTest, test_NotifySocketListener_success) {
  ASSERT_NO_FATAL_FAILURE(InitializeCommunicationsSuccess(sls, mock));

  ON_CALL(mock, Write).WillByDefault((Return(0)));

  EXPECT_CALL(mock, Write(write_fd, _, _));
  ASSERT_EQ(sls.NotifySocketListener(), 0);
}

}  // namespace testing
