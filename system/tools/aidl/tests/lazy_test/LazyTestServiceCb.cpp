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

#include <sys/eventfd.h>

#include <binder/LazyServiceRegistrar.h>
#include "LazyTestServiceCb.h"

namespace android {
namespace binder {

void LazyTestServiceCb::setCustomActiveServicesCallback() {
  auto lazyRegistrar = LazyServiceRegistrar::getInstance();
  lazyRegistrar.setActiveServicesCallback([lazyRegistrar, this](bool hasClients) mutable -> bool {
    if (hasClients) {
      return false;
    }

    if (mFd < 0) {
      // Prevent shutdown (test will fail)
      return true;
    }

    // Unregister all services
    if (!lazyRegistrar.tryUnregister()) {
      // Prevent shutdown (test will fail)
      return true;
    }

    // Re-register all services
    lazyRegistrar.reRegister();

    // Unregister again before shutdown
    if (!lazyRegistrar.tryUnregister()) {
      // Prevent shutdown (test will fail)
      return true;
    }

    // Tell the test we're shutting down
    if (TEMP_FAILURE_RETRY(eventfd_write(mFd, /* value */ 1)) < 0) {
      // Prevent shutdown (test will fail)
      return true;
    }

    exit(EXIT_SUCCESS);
    // Unreachable
  });
}

Status LazyTestServiceCb::setEventFd(const ::android::os::ParcelFileDescriptor& parcelFd) {
  mFd = dup(parcelFd.get());

  return mFd < 0 ? Status::fromExceptionCode(Status::EX_ILLEGAL_ARGUMENT) : Status::ok();
}

}  // namespace binder
}  // namespace android
