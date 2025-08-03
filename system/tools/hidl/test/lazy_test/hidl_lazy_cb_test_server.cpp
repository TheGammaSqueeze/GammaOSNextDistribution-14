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

#include <android-base/logging.h>
#include <android/hardware/tests/lazy_cb/1.0/ILazyCb.h>
#include <hidl/HidlLazyUtils.h>
#include <hidl/HidlTransportSupport.h>

using android::OK;
using android::sp;
using android::hardware::configureRpcThreadpool;
using android::hardware::hidl_handle;
using android::hardware::joinRpcThreadpool;
using android::hardware::LazyServiceRegistrar;
using android::hardware::Return;
using android::hardware::tests::lazy_cb::V1_0::ILazyCb;

class LazyCb : public ILazyCb {
  public:
    LazyCb() : mFd(-1) {}
    void setCustomActiveServicesCallback();
    ::android::hardware::Return<bool> setEventFd(const hidl_handle& fds);

  private:
    int mFd;
};

void LazyCb::setCustomActiveServicesCallback() {
    auto lazyRegistrar = android::hardware::LazyServiceRegistrar::getInstance();
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

::android::hardware::Return<bool> LazyCb::setEventFd(const hidl_handle& fds) {
    mFd = dup(fds->data[0]);
    return Return<bool>(mFd >= 0);
}

int main() {
    configureRpcThreadpool(1, true /*willJoin*/);
    sp<LazyCb> service = new LazyCb();
    service->setCustomActiveServicesCallback();
    CHECK(OK == LazyServiceRegistrar::getInstance().registerService(service, "default"));
    joinRpcThreadpool();
    return EXIT_FAILURE;  // should not reach
}
