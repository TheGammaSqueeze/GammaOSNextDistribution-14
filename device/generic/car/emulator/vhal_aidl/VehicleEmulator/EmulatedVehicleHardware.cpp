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

#define LOG_TAG "EmulatedVehicleHardware"

#include "EmulatedVehicleHardware.h"
#include "VehicleEmulator.h"

#include <VehicleHalTypes.h>
#include <VehicleUtils.h>
#include <android-base/properties.h>
#include <android/binder_manager.h>
#include <utils/Log.h>

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace fake {

using ::aidl::android::hardware::automotive::vehicle::StatusCode;
using ::aidl::android::hardware::automotive::vehicle::SetValueRequest;
using ::aidl::android::hardware::automotive::vehicle::SetValueResult;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropConfig;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropValue;
using ::aidl::android::hardware::automotive::vehicle::VehicleProperty;

using ::android::base::Result;
using ::android::hardware::automotive::vehicle::V2_0::impl::MessageSender;

EmulatedVehicleHardware::EmulatedVehicleHardware() {
    Init();
}

EmulatedVehicleHardware::EmulatedVehicleHardware(
    std::string_view default_config_dir, std::string_view override_config_dir,
    bool force_override)
    : FakeVehicleHardware(std::string(default_config_dir),
                          std::string(override_config_dir), force_override) {
  Init();
}

EmulatedVehicleHardware::EmulatedVehicleHardware(
        bool inQemu,
        std::unique_ptr<MessageSender> socketComm,
        std::unique_ptr<MessageSender> pipeComm) {
    mInQemu = inQemu;
    mEmulator = std::make_unique<VehicleEmulator>(std::move(socketComm), std::move(pipeComm), this);
}

VehicleEmulator* EmulatedVehicleHardware::getEmulator() {
    return mEmulator.get();
}

EmulatedVehicleHardware::~EmulatedVehicleHardware() {
    mEmulator.reset();
    stopVehicleBuses();
}

void EmulatedVehicleHardware::Init() {
    mInQemu = isInQemu();
    ALOGD("mInQemu=%s", mInQemu ? "true" : "false");

    mVehicleBusCallback = ::ndk::SharedRefBase::make<VehicleBusCallback>(this);
    mEmulator = std::make_unique<VehicleEmulator>(this);
    startVehicleBuses();
}

StatusCode EmulatedVehicleHardware::setValues(
            std::shared_ptr<const SetValuesCallback> callback,
            const std::vector<SetValueRequest>& requests) {
    std::vector<SetValueResult> results;

    for (const auto& request: requests) {
        const VehiclePropValue& value = request.value;
        int propId = value.prop;

        ALOGD("Set value for property ID: %d", propId);

        if (mInQemu && propId == toInt(VehicleProperty::DISPLAY_BRIGHTNESS)) {
            ALOGD("Return OKAY for DISPLAY_BRIGHTNESS in QEMU");

            // Emulator does not support remote brightness control, b/139959479
            // do not send it down so that it does not bring unnecessary property change event
            // return other error code, such NOT_AVAILABLE, causes Emulator to be freezing
            // TODO: return StatusCode::NOT_AVAILABLE once the above issue is fixed
            results.push_back({
                .requestId = request.requestId,
                .status = StatusCode::OK,
            });
            continue;
        }

        SetValueResult setValueResult;
        setValueResult.requestId = request.requestId;

        if (auto result = setValue(value); !result.ok()) {
            ALOGE("failed to set value, error: %s, code: %d", getErrorMsg(result).c_str(),
                    getIntErrorCode(result));
            setValueResult.status = getErrorCode(result);
        } else {
            setValueResult.status = StatusCode::OK;
            // Inform the emulator about a new value change.
            mEmulator->doSetValueFromClient(value);
        }

        results.push_back(std::move(setValueResult));
    }
    // In real Vehicle HAL, the values would be sent to vehicle bus. But here, we just assume
    // it is done and notify the client.
    (*callback)(std::move(results));

    return StatusCode::OK;
}

void EmulatedVehicleHardware::startVehicleBuses() {
    std::vector<std::string> names;
    AServiceManager_forEachDeclaredInstance(IVehicleBus::descriptor, static_cast<void*>(&names),
        [](const char* instance, void* context) {
            auto fullName = std::string(IVehicleBus::descriptor) + "/" + instance;
            static_cast<std::vector<std::string>*>(context)->push_back(fullName);
        });

    for (const auto& fullName : names) {
        ::ndk::SpAIBinder binder(AServiceManager_waitForService(fullName.c_str()));
        if (binder.get() == nullptr) {
            ALOGE("%s binder returned null", fullName.c_str());
            continue;
        }
        std::shared_ptr<IVehicleBus> vehicleBus = IVehicleBus::fromBinder(binder);
        if (vehicleBus == nullptr) {
            ALOGE("Couldn't open %s", fullName.c_str());
            continue;
        }

        vehicleBus->setOnNewPropValuesCallback(mVehicleBusCallback);
        mVehicleBuses.push_back(vehicleBus);
    }
}

::ndk::ScopedAStatus EmulatedVehicleHardware::VehicleBusCallback::onNewPropValues(
        const std::vector<AidlVehiclePropValue>& aidlPropValues) {
    for (const auto& aidlPropValue : aidlPropValues) {
        if (auto result = mVehicleHardware->setValue(aidlPropValue); !result.ok()) {
            ALOGE("Failed to set value, error: %s", getErrorMsg(result).c_str());
            continue;
        }
    }
    return ::ndk::ScopedAStatus::ok();
}

void EmulatedVehicleHardware::stopVehicleBuses() {
    for (const auto& vehicleBus : mVehicleBuses) {
        vehicleBus->unsetOnNewPropValuesCallback(mVehicleBusCallback);
    }
}

std::vector<VehiclePropValuePool::RecyclableType> EmulatedVehicleHardware::getAllProperties()
        const {
    return mServerSidePropStore->readAllValues();
}

EmulatedVehicleHardware::ConfigResultType EmulatedVehicleHardware::getPropConfig(int32_t propId)
        const {
    return mServerSidePropStore->getPropConfig(propId);
}

bool EmulatedVehicleHardware::isInQemu() {
    return android::base::GetBoolProperty("ro.boot.qemu", false);
}

}  // namespace fake
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android

