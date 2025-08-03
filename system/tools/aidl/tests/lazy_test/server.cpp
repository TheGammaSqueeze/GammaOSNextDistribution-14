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

#include <android-base/logging.h>
#include <binder/IBinder.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/LazyServiceRegistrar.h>
#include "LazyTestService.h"

using android::BBinder;
using android::IBinder;
using android::IPCThreadState;
using android::OK;
using android::sp;
using android::binder::LazyServiceRegistrar;
using android::binder::LazyTestService;

void setupDoubleLazyServer() {
  sp<LazyTestService> service1 = sp<LazyTestService>::make();
  sp<LazyTestService> service2 = sp<LazyTestService>::make();

  // Simulate another callback here, to test to make sure the actual instance
  // we are relying on doesn't have its state messed up when multiple client
  // callbacks are registered.
  // DO NOT COPY - INTENTIONALLY TESTING BAD BEHAVIOR
  auto extra = LazyServiceRegistrar::createExtraTestInstance();
  extra.forcePersist(true);  // don't allow this instance to handle process lifetime
  CHECK_EQ(OK, extra.registerService(service1, "aidl_lazy_test_1"));
  // DO NOT COPY - INTENTIONALLY TESTING BAD BEHAVIOR

  auto lazyRegistrar = LazyServiceRegistrar::getInstance();
  CHECK_EQ(OK, lazyRegistrar.registerService(service1, "aidl_lazy_test_1"));
  CHECK_EQ(OK, lazyRegistrar.registerService(service2, "aidl_lazy_test_2"));
}

void setupQuitterServer() {
  auto lazyRegistrar = LazyServiceRegistrar::getInstance();
  lazyRegistrar.setActiveServicesCallback([](bool hasClients) mutable -> bool {
    // intentional bad behavior, for instance, simulating a system
    // shutdown at this time
    if (hasClients) exit(EXIT_SUCCESS);
    return false;
  });

  sp<LazyTestService> service = sp<LazyTestService>::make();
  CHECK_EQ(OK, lazyRegistrar.registerService(service, "aidl_lazy_test_quit"));
}

int main(int argc, char** argv) {
  if (argc == 1) {
    setupDoubleLazyServer();
  } else if (argc == 2 && argv[1] == std::string("quit")) {
    setupQuitterServer();
  } else {
    LOG_ALWAYS_FATAL("usage: %s [quit]", argv[0]);
  }

  IPCThreadState::self()->joinThreadPool();
  return 1;
}
