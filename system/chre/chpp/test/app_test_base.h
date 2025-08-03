/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <thread>

#include "chpp/app.h"
#include "chpp/clients/loopback.h"
#include "chpp/log.h"
#include "chpp/macros.h"
#include "chpp/platform/platform_link.h"
#include "chpp/transport.h"

namespace chpp {

/*
 * A base class for all CHPP app level tests.
 */
class AppTestBase : public testing::Test {
 protected:
  void SetUp() override;
  void TearDown() override;

  ChppLinuxLinkState mClientLinkContext = {};
  ChppTransportState mClientTransportContext = {};
  ChppAppState mClientAppContext = {};

  ChppLinuxLinkState mServiceLinkContext = {};
  ChppTransportState mServiceTransportContext = {};
  ChppAppState mServiceAppContext = {};

  pthread_t mClientWorkThread;
  pthread_t mServiceWorkThread;
};

}  // namespace chpp
