/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <binder/IBinder.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/LazyServiceRegistrar.h>
#include <utils/Log.h>
#include "LazyTestServiceCb.h"

using android::BBinder;
using android::IBinder;
using android::IPCThreadState;
using android::OK;
using android::sp;
using android::binder::LazyServiceRegistrar;
using android::binder::LazyTestServiceCb;

// TODO: shouldn't be expanded or copied - instead, merge with server.cpp
int main() {
  sp<LazyTestServiceCb> service = new LazyTestServiceCb();

  // Set the callback before registering the service
  service->setCustomActiveServicesCallback();

  auto lazyRegistrar = LazyServiceRegistrar::getInstance();
  LOG_ALWAYS_FATAL_IF(OK != lazyRegistrar.registerService(service, "aidl_lazy_cb_test"), "");

  IPCThreadState::self()->joinThreadPool();

  return 1;
}
