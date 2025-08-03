/*
 * Copyright (C) 2019 Google Inc. All Rights Reserved.
 */

#include "ExtraCanClient.h"

#include <android/binder_manager.h>
#include <android-base/logging.h>
#include <android/binder_process.h>
#include <hidl/HidlTransportSupport.h>


namespace android::hardware::automotive::vehicle::V2_0::impl {

using ::aidl::device::generic::car::emulator::IVehicleBus;
using ::aidl::android::hardware::automotive::vehicle::VehicleBus;

static void protocanbusService() {
    base::SetDefaultTag("ProtoCanBusSrv");
    base::SetMinimumLogSeverity(base::VERBOSE);
    ABinderProcess_setThreadPoolMaxThreadCount(4);
    LOG(DEBUG) << "ProtoCAN service starting...";

    std::shared_ptr<VehicleBus> vehicleBus = ::ndk::SharedRefBase::make<ExtraCanClient>();

    auto serviceName = std::string(IVehicleBus::descriptor) + "/protocanbus";
    auto status = AServiceManager_addService(vehicleBus->asBinder().get(),
                                             serviceName.c_str());
    CHECK_EQ(status, OK) << "Failed to register ProtoCAN VehicleBus HAL implementation";

    vehicleBus->start();
    ABinderProcess_startThreadPool();
    ABinderProcess_joinThreadPool();
};

}  // namespace android::hardware::automotive::vehicle::V2_0::impl

int main() {
    ::android::hardware::automotive::vehicle::V2_0::impl::protocanbusService();
    return 1;  // protocanbusService (joinRpcThreadpool) shouldn't exit
}
