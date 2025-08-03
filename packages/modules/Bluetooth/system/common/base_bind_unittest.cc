/*
 * Copyright 2021 The Android Open Source Project
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
#include <base/functional/bind.h>
#include <base/functional/callback.h>
#include <base/threading/platform_thread.h>
#include <gtest/gtest.h>
#include <sys/capability.h>
#include <syscall.h>

#include <condition_variable>
#include <memory>
#include <mutex>

#include "os/log.h"

class BaseBindThreadTest : public ::testing::Test {
 public:
 protected:
};

namespace {
struct Vars {
  int a{0};
  int b{0};
  int c{0};

  bool operator==(const Vars& rhs) const {
    return (a == rhs.a && b == rhs.b && c == rhs.c);
  }

} g_vars;

void func() {}
void func_a(int a) { g_vars.a = a; }
void func_ab(int a, int b) {
  func_a(a);
  g_vars.b = b;
}
void func_abc(int a, int b, int c) {
  func_ab(a, b);
  g_vars.c = c;
}
}  // namespace

TEST_F(BaseBindThreadTest, simple) {
  struct Vars v;
  g_vars = {};
  base::Callback<void()> cb0 = base::Bind(&func);
  cb0.Run();
  ASSERT_EQ(g_vars, v);

  v = {};
  v.a = 1;
  g_vars = {};
  base::Callback<void()> cb1 = base::Bind(&func_a, 1);
  cb1.Run();
  ASSERT_EQ(g_vars, v);

  v = {};
  v.a = 1;
  v.b = 2;
  g_vars = {};
  base::Callback<void()> cb2 = base::Bind(&func_ab, 1, 2);
  cb2.Run();
  ASSERT_EQ(g_vars, v);

  v = {};
  v.a = 1;
  v.b = 2;
  v.c = 3;
  g_vars = {};
  base::Callback<void()> cb3 = base::Bind(&func_abc, 1, 2, 3);
  cb3.Run();
  ASSERT_EQ(g_vars, v);
}

TEST_F(BaseBindThreadTest, bind_first_arg) {
  struct Vars v;
  g_vars = {};
  base::Callback<void()> cb0 = base::Bind(&func);
  cb0.Run();
  ASSERT_EQ(g_vars, v);

  v = {};
  v.a = 1;
  g_vars = {};
  base::Callback<void()> cb1 = base::Bind(&func_a, 1);
  cb1.Run();
  ASSERT_EQ(g_vars, v);

  v = {};
  v.a = 1;
  v.b = 2;
  g_vars = {};
  base::Callback<void(int)> cb2 = base::Bind(&func_ab, 1);
  cb2.Run(2);
  ASSERT_EQ(g_vars, v);

  v = {};
  v.a = 1;
  v.b = 2;
  v.c = 3;
  g_vars = {};
  base::Callback<void(int, int)> cb3 = base::Bind(&func_abc, 1);
  cb3.Run(2, 3);
  ASSERT_EQ(g_vars, v);
}
