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

#include <gtest/gtest.h>
#include <sys/socket.h>
#include <sys/types.h>

#include <future>

#include "module.h"
#include "os/thread.h"
#include "shim/dumpsys.h"
#include "shim/dumpsys_args.h"
#include "test_data/dumpsys_test_data_bin.h"

namespace testing {

using bluetooth::TestModuleRegistry;
using namespace bluetooth;

namespace {

bool SimpleJsonValidator(int fd, int* dumpsys_byte_cnt) {
  char buf{0};
  bool within_double_quotes{false};
  int left_bracket{0}, right_bracket{0};
  while (read(fd, &buf, 1) != -1) {
    switch (buf) {
      (*dumpsys_byte_cnt)++;
      case '"':
        within_double_quotes = !within_double_quotes;
        break;
      case '{':
        if (!within_double_quotes) {
          left_bracket++;
        }
        break;
      case '}':
        if (!within_double_quotes) {
          right_bracket++;
        }
        break;
      default:
        break;
    }
  }
  return left_bracket == right_bracket;
}

}  // namespace

// To create dumpsys_test_header_bin.h:
// make bluetooth_flatbuffer_bundler
// ${ANDROID_BUILD_TOP}/out/host/linux-x86/bin/bluetooth_flatbuffer_bundler -w -m bluetooth.DumpsysData -f
// test_gen/dumpsys_test_data_bin -n bluetooth::test test_gen/*

class DumpsysTest : public Test {
 protected:
  void SetUp() override {
    dumpsys_module_ = new bluetooth::shim::Dumpsys(bluetooth::test::GetBundledSchemaData());
    fake_registry_.InjectTestModule(&shim::Dumpsys::Factory, dumpsys_module_);
  }

  void TearDown() override {
    fake_registry_.StopAll();
  }

  void Print() {
    dumpsys_module_->Dump(0, nullptr);
  }

  int GetSocketBufferSize(int sockfd) {
    int socket_buffer_size;
    socklen_t optlen = sizeof(socket_buffer_size);
    getsockopt(sockfd, SOL_SOCKET, SO_RCVBUF, (void*)&socket_buffer_size, &optlen);
    return socket_buffer_size;
  }

  void SetSocketBufferSize(int sockfd, int socket_buffer_size) {
    socklen_t optlen = sizeof(socket_buffer_size);
    ASSERT_EQ(0, setsockopt(sockfd, SOL_SOCKET, SO_RCVBUF, (const void*)&socket_buffer_size, optlen));
  }

  TestModuleRegistry fake_registry_;
  os::Thread& thread_ = fake_registry_.GetTestThread();
  bluetooth::shim::Dumpsys* dumpsys_module_ = nullptr;
  os::Handler* client_handler_ = nullptr;
};

TEST_F(DumpsysTest, dump_as_developer) {
  const char* args[]{bluetooth::shim::kArgumentDeveloper, nullptr};

  int sv[2];
  ASSERT_EQ(0, socketpair(AF_LOCAL, SOCK_STREAM | SOCK_NONBLOCK, 0, sv));
  int socket_buffer_size = GetSocketBufferSize(sv[0]);

  std::promise<void> promise;
  std::future future = promise.get_future();
  dumpsys_module_->Dump(sv[0], args, std::move(promise));
  future.wait();

  int dumpsys_byte_cnt = 0;
  ASSERT_TRUE(SimpleJsonValidator(sv[1], &dumpsys_byte_cnt));
  ASSERT_TRUE(dumpsys_byte_cnt < socket_buffer_size);
}

TEST_F(DumpsysTest, dump_as_user) {
  const char* args[]{"not-a-developer-option", nullptr};

  int sv[2];
  ASSERT_EQ(0, socketpair(AF_LOCAL, SOCK_STREAM | SOCK_NONBLOCK, 0, sv));
  int socket_buffer_size = GetSocketBufferSize(sv[0]);

  std::promise<void> promise;
  std::future future = promise.get_future();
  dumpsys_module_->Dump(sv[0], args, std::move(promise));
  future.wait();

  int dumpsys_byte_cnt = 0;
  ASSERT_TRUE(SimpleJsonValidator(sv[1], &dumpsys_byte_cnt));
  ASSERT_TRUE(dumpsys_byte_cnt < socket_buffer_size);
}

}  // namespace testing
