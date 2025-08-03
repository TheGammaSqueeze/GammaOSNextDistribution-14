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

#define LOG_TAG "EmulatedVehicleHalServer"

#include <android/binder_manager.h>
#include <utils/SystemClock.h>
#include <vhal_v2_0/VehicleUtils.h>

#include "EmulatedVehicleHalServer.h"

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

namespace impl {

EmulatedVehicleHalServer::EmulatedVehicleHalServer(): DefaultVehicleHalServer() {
    mInQEMU = isInQEMU();
    ALOGD("mInQEMU=%s", mInQEMU ? "true" : "false");

    mVehicleBusCallback = ::ndk::SharedRefBase::make<VehicleBusCallback>(this);
    startVehicleBuses();
}

EmulatedVehicleHalServer::~EmulatedVehicleHalServer() {
    stopVehicleBuses();
}

StatusCode EmulatedVehicleHalServer::onSetProperty(const VehiclePropValue& value,
                                                   bool updateStatus) {
    if (mInQEMU && value.prop == toInt(VehicleProperty::DISPLAY_BRIGHTNESS)) {
        // Emulator does not support remote brightness control, b/139959479
        // do not send it down so that it does not bring unnecessary property change event
        // return other error code, such NOT_AVAILABLE, causes Emulator to be freezing
        // TODO: return StatusCode::NOT_AVAILABLE once the above issue is fixed
        return StatusCode::OK;
    }

    return DefaultVehicleHalServer::onSetProperty(value, updateStatus);
}

bool EmulatedVehicleHalServer::setPropertyFromVehicle(const VehiclePropValue& propValue) {
    auto updatedPropValue = getValuePool()->obtain(propValue);
    updatedPropValue->timestamp = elapsedRealtimeNano();
    mServerSidePropStore.writeValue(*updatedPropValue, true);
    onPropertyValueFromCar(*updatedPropValue, true);
    return true;
}

std::vector<VehiclePropValue> EmulatedVehicleHalServer::getAllProperties() const {
    return mServerSidePropStore.readAllValues();
}

std::vector<VehiclePropConfig> EmulatedVehicleHalServer::listProperties() {
    return mServerSidePropStore.getAllConfigs();
}

EmulatedVehicleHalServer::VehiclePropValuePtr EmulatedVehicleHalServer::get(
        const VehiclePropValue& requestedPropValue, StatusCode* outStatus) {
    EmulatedVehicleHalServer::VehiclePropValuePtr v = nullptr;
    auto prop = mServerSidePropStore.readValueOrNull(requestedPropValue);
    if (prop != nullptr) {
        v = getValuePool()->obtain(*prop);
    }

    if (!v) {
        *outStatus = StatusCode::INVALID_ARG;
    } else if (v->status == VehiclePropertyStatus::AVAILABLE) {
        *outStatus = StatusCode::OK;
    } else {
        *outStatus = StatusCode::TRY_AGAIN;
    }

    if (v.get()) {
        v->timestamp = elapsedRealtimeNano();
    }
    return v;
}

void EmulatedVehicleHalServer::startVehicleBuses() {
    std::vector<std::string> names;
    AServiceManager_forEachDeclaredInstance(IVehicleBus::descriptor, static_cast<void*>(&names),
        [](const char* instance, void* context) {
            auto fullName = std::string(IVehicleBus::descriptor) + "/" + instance;
            static_cast<std::vector<std::string>*>(context)->emplace_back(fullName);
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

void EmulatedVehicleHalServer::stopVehicleBuses() {
    for (const auto& vehicleBus : mVehicleBuses) {
        vehicleBus->unsetOnNewPropValuesCallback(mVehicleBusCallback);
    }
}

VehiclePropValue EmulatedVehicleHalServer::VehicleBusCallback::makeHidlVehiclePropValue(
    const AidlVehiclePropValue& aidlPropValue) {
    VehiclePropValue hidlPropValue;
    hidlPropValue.timestamp = aidlPropValue.timestamp;
    hidlPropValue.areaId = aidlPropValue.areaId;
    hidlPropValue.prop = aidlPropValue.prop;
    hidlPropValue.status = static_cast<VehiclePropertyStatus>(aidlPropValue.status);
    hidlPropValue.value.int32Values = aidlPropValue.value.int32Values;
    hidlPropValue.value.floatValues = aidlPropValue.value.floatValues;
    hidlPropValue.value.int64Values = aidlPropValue.value.int64Values;
    hidlPropValue.value.bytes = aidlPropValue.value.byteValues;
    hidlPropValue.value.stringValue = aidlPropValue.value.stringValue;
    return hidlPropValue;
}

::ndk::ScopedAStatus EmulatedVehicleHalServer::VehicleBusCallback::onNewPropValues(
     const std::vector<AidlVehiclePropValue>& aidlPropValues) {
    for (const auto& aidlPropValue : aidlPropValues) {
        mVehicleHalServer->onPropertyValueFromCar(
            makeHidlVehiclePropValue(aidlPropValue), true);
    }
    return ::ndk::ScopedAStatus::ok();
}

IVehicleServer::DumpResult EmulatedVehicleHalServer::debug(const std::vector<std::string>& options){
    return DefaultVehicleHalServer::onDump(options);
}

}  // namespace impl

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android
