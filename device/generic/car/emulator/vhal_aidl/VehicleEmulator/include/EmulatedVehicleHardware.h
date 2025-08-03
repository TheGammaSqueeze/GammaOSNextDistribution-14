/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef ANDROID_VEHICLEEMULATOR_EMULATEDVEHICLEHARDWARE_H
#define ANDROID_VEHICLEEMULATOR_EMULATEDVEHICLEHARDWARE_H

#include <aidl/device/generic/car/emulator/IVehicleBus.h>
#include <aidl/device/generic/car/emulator/BnVehicleBusCallback.h>

#include <CommConn.h>
#include <FakeVehicleHardware.h>
#include <VehicleUtils.h>

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace fake {

class VehicleEmulator;
class VehicleEmulatorTest;

class EmulatedVehicleHardware : public FakeVehicleHardware {
  public:
    using AidlVehiclePropValue = aidl::android::hardware::automotive::vehicle::VehiclePropValue;
    using IVehicleBus = aidl::device::generic::car::emulator::IVehicleBus;
    using BnVehicleBusCallback = aidl::device::generic::car::emulator::BnVehicleBusCallback;
    using ConfigResultType = android::base::Result<aidl::android::hardware::automotive::vehicle::VehiclePropConfig, VhalError>;

    EmulatedVehicleHardware();
    EmulatedVehicleHardware(std::string_view default_config_dir,
                            std::string_view override_config_dir,
                            bool force_override);

    ~EmulatedVehicleHardware();

    aidl::android::hardware::automotive::vehicle::StatusCode setValues(
            std::shared_ptr<const SetValuesCallback> callback,
            const std::vector<aidl::android::hardware::automotive::vehicle::SetValueRequest>&
                    requests) override;

    std::vector<VehiclePropValuePool::RecyclableType> getAllProperties() const;

    ConfigResultType getPropConfig(int32_t propId) const;

  private:
    friend class VehicleEmulator;
    friend class VehicleEmulatorTest;

    bool mInQemu;

    class VehicleBusCallback : public BnVehicleBusCallback {
      public:
        VehicleBusCallback(
            EmulatedVehicleHardware* vehicleHardware) :
            mVehicleHardware(vehicleHardware) {}

        ndk::ScopedAStatus onNewPropValues(
            const std::vector<AidlVehiclePropValue>& propValues) override;

      private:
        EmulatedVehicleHardware* mVehicleHardware;
    };
    std::shared_ptr<BnVehicleBusCallback> mVehicleBusCallback;
    std::vector<std::shared_ptr<IVehicleBus>> mVehicleBuses;
    std::unique_ptr<VehicleEmulator> mEmulator;

    // determine if it's running inside Android Emulator
    static bool isInQemu();
    void Init();
    void startVehicleBuses();
    void stopVehicleBuses();

    // For testing only.
    EmulatedVehicleHardware(
        bool inQEMU,
        std::unique_ptr<V2_0::impl::MessageSender> socketComm,
        std::unique_ptr<V2_0::impl::MessageSender> pipeComm);

    // For testing only.
    VehicleEmulator* getEmulator();
};

}  // namespace fake
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_VEHICLEEMULATOR_EMULATEDVEHICLEHARDWARE_H
