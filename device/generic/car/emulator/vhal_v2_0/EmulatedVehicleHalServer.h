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

#pragma once

#include <aidl/device/generic/car/emulator/IVehicleBus.h>
#include <aidl/device/generic/car/emulator/BnVehicleBusCallback.h>

#include <vhal_v2_0/DefaultVehicleHalServer.h>

#include "VehicleEmulator.h"

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

namespace impl {

// This contains the server operation for VHAL running in emulator.
class EmulatedVehicleHalServer : public DefaultVehicleHalServer, public EmulatedServerIface {
  public:
    using AidlVehiclePropValue = ::aidl::android::hardware::automotive::vehicle::VehiclePropValue;
    using IVehicleBus = ::aidl::device::generic::car::emulator::IVehicleBus;
    using BnVehicleBusCallback = ::aidl::device::generic::car::emulator::BnVehicleBusCallback;

    EmulatedVehicleHalServer();
    ~EmulatedVehicleHalServer();

    StatusCode onSetProperty(const VehiclePropValue& value, bool updateStatus) override;

    // Methods from EmulatedServerIface
    bool setPropertyFromVehicle(const VehiclePropValue& propValue);

    std::vector<VehiclePropValue> getAllProperties() const;

    std::vector<VehiclePropConfig> listProperties();

    VehiclePropValuePtr get(const VehiclePropValue& requestedPropValue, StatusCode* outStatus);

    IVehicleServer::DumpResult debug(const std::vector<std::string>& options);

  private:
    bool mInQEMU;

    class VehicleBusCallback : public BnVehicleBusCallback {
      public:
        VehicleBusCallback(EmulatedVehicleHalServer* vehicleHalServer) :
            mVehicleHalServer(vehicleHalServer) {}

        ::ndk::ScopedAStatus onNewPropValues(
            const std::vector<AidlVehiclePropValue>& propValues) override;

      private:
        EmulatedVehicleHalServer* mVehicleHalServer;

        VehiclePropValue makeHidlVehiclePropValue(const AidlVehiclePropValue& aidlPropValue);
    };
    std::shared_ptr<BnVehicleBusCallback> mVehicleBusCallback;
    std::vector<std::shared_ptr<IVehicleBus>> mVehicleBuses;

    void startVehicleBuses();
    void stopVehicleBuses();
};

}  // namespace impl

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android
