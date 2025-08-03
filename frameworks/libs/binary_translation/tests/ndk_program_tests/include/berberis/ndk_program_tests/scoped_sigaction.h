/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef BERBERIS_NDK_PROGRAM_TESTS_SCOPED_SIGACTION_H_
#define BERBERIS_NDK_PROGRAM_TESTS_SCOPED_SIGACTION_H_

#include "gtest/gtest.h"

#include <signal.h>

class ScopedSigaction {
 public:
  ScopedSigaction(int sig, const struct sigaction* act) : sig_(sig) { Init(act); }

  ~ScopedSigaction() { Fini(); }

 private:
  void Init(const struct sigaction* act) { ASSERT_EQ(sigaction(sig_, act, &old_act_), 0); }

  void Fini() { ASSERT_EQ(sigaction(sig_, &old_act_, nullptr), 0); }

  int sig_;
  struct sigaction old_act_;
};

#endif  // BERBERIS_NDK_PROGRAM_TESTS_SCOPED_SIGACTION_H_
