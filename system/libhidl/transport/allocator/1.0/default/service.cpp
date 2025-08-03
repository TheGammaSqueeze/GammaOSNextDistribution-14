#define LOG_TAG "android.hidl.allocator@1.0-service"

#include "AshmemAllocator.h"

#include <android-base/logging.h>
#include <android/hidl/allocator/1.0/IAllocator.h>
#include <android/hidl/manager/1.2/IServiceManager.h>
#include <cutils/properties.h>
#include <hidl/HidlTransportSupport.h>

using android::sp;
using android::status_t;
using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;
using android::hidl::allocator::V1_0::IAllocator;
using android::hidl::allocator::V1_0::implementation::AshmemAllocator;
using android::hidl::manager::V1_2::IServiceManager;

static constexpr char kInstanceName[] = "ashmem";

int main() {
    configureRpcThreadpool(1, true /* callerWillJoin */);

    sp<IAllocator> allocator = new AshmemAllocator();

    IServiceManager::Transport transport =
            android::hardware::defaultServiceManager1_2()->getTransport(IAllocator::descriptor,
                                                                        kInstanceName);
    if (transport == IServiceManager::Transport::HWBINDER) {
        status_t status = allocator->registerAsService(kInstanceName);
        if (android::OK != status) {
            LOG(FATAL) << "Unable to register allocator service: " << status;
            return -1;
        }
    } else {
        LOG(INFO) << IAllocator::descriptor << "/" << kInstanceName
                  << " is not registered in the VINTF manifest as it is deprecated.";
        // The transport won't change at run time, so make sure we don't restart
        int rc = property_set("hidl_memory.disabled", "true");
        if (rc) {
            LOG_ALWAYS_FATAL("Failed to set \"hidl_memory.disabled\" (error %d).\"", rc);
        }
        return 0;
    }

    joinRpcThreadpool();

    return -1;
}
