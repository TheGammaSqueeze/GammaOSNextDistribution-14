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

#include <AtomicState.h>

#include <chrono>
#include <thread>

#include <gtest/gtest.h>

using namespace std::chrono_literals;

namespace android {

enum AtomicStateTestEnum {
  A,
  B,
  C,
  D,
  E,
};

class AtomicStateTest : public testing::Test {
 protected:
  AtomicStateTest() : state_(A) {}
  virtual void SetUp() {}
  virtual void TearDown() {}

  AtomicState<AtomicStateTestEnum> state_;
};

TEST_F(AtomicStateTest, transition) {
  ASSERT_EQ(A, state_.state_);

  // Starts as A, transition from B fails
  ASSERT_FALSE(state_.transition(B, C));
  ASSERT_EQ(A, state_.state_);

  // transition from A to B
  ASSERT_TRUE(state_.transition(A, B));
  ASSERT_EQ(B, state_.state_);

  // State is B, transition from A fails
  ASSERT_FALSE(state_.transition(A, B));
  ASSERT_EQ(B, state_.state_);

  // State is B, transition_or from A calls the lambda
  bool lambda = false;
  state_.transition_or(A, B, [&] {
    lambda = true;
    return B;
  });
  ASSERT_TRUE(lambda);
  ASSERT_EQ(B, state_.state_);

  // State is C, transition_or from B to C does not call the lambda
  lambda = false;
  state_.transition_or(B, C, [&] {
    lambda = true;
    return C;
  });
  ASSERT_FALSE(lambda);
  ASSERT_EQ(C, state_.state_);
}

TEST_F(AtomicStateTest, wait) {
  ASSERT_EQ(A, state_.state_);

  // Starts as A, wait_for_either_of B, C returns false
  ASSERT_FALSE(state_.wait_for_either_of(B, C, 10ms));

  // Starts as A, wait_for_either_of A, B returns true
  ASSERT_TRUE(state_.wait_for_either_of(A, B, 1s));

  {
    std::thread t([&] {
      usleep(10000);
      state_.set(B);
    });

    // Wait ing for B or C returns true after state is set to B
    ASSERT_TRUE(state_.wait_for_either_of(B, C, 1s));

    t.join();
  }

  ASSERT_EQ(B, state_.state_);
  {
    std::thread t([&] {
      usleep(10000);
      state_.transition(B, C);
    });

    // Waiting for A or C returns true after state is transitioned to C
    ASSERT_TRUE(state_.wait_for_either_of(A, C, 1s));

    t.join();
  }

  ASSERT_EQ(C, state_.state_);
  {
    std::thread t([&] {
      usleep(10000);
      state_.transition(C, D);
    });

    // Waiting for A or B returns false after state is transitioned to D
    ASSERT_FALSE(state_.wait_for_either_of(A, B, 100ms));

    t.join();
  }
}

}  // namespace android
