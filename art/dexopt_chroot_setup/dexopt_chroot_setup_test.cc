/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include "dexopt_chroot_setup.h"

#include "base/common_art_test.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace art {
namespace dexopt_chroot_setup {
namespace {

class DexoptChrootSetupTest : public CommonArtTest {
 protected:
  void SetUp() override {
    CommonArtTest::SetUp();
    dexopt_chroot_setup_ = ndk::SharedRefBase::make<DexoptChrootSetup>();
  }

  std::shared_ptr<DexoptChrootSetup> dexopt_chroot_setup_;
};

TEST_F(DexoptChrootSetupTest, HelloWorld) { EXPECT_NE(dexopt_chroot_setup_, nullptr); }

}  // namespace
}  // namespace dexopt_chroot_setup
}  // namespace art
