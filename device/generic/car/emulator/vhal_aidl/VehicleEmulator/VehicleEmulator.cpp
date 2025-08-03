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
#define LOG_TAG "VehicleEmulator"

#include "VehicleEmulator.h"

#include <utils/Log.h>
#include <utils/SystemClock.h>
#include <algorithm>
#include <vector>

#include <VehicleUtils.h>
#include <IVehicleHardware.h>
#include <PipeComm.h>
#include <SocketComm.h>

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace fake {

using ::aidl::android::hardware::automotive::vehicle::VehiclePropValue;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropConfig;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropertyStatus;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropertyType;

using ::android::hardware::automotive::vehicle::V2_0::impl::SocketComm;
using ::android::hardware::automotive::vehicle::V2_0::impl::PipeComm;
using ::android::hardware::automotive::vehicle::V2_0::impl::MessageSender;

VehicleEmulator::VehicleEmulator(EmulatedVehicleHardware* hal) : mHal(hal) {
    ALOGI("Starting SocketComm");
    mSocketComm = std::make_unique<SocketComm>(this);
    mSocketComm->start();

    if (hal->isInQemu()) {
        ALOGI("Starting PipeComm");
        mPipeComm = std::make_unique<PipeComm>(this);
        mPipeComm->start();
    }
}

VehicleEmulator::VehicleEmulator(
        std::unique_ptr<MessageSender> socketComm,
        std::unique_ptr<MessageSender> pipeComm,
        EmulatedVehicleHardware* hal) : mHal(hal), mSocketComm(std::move(socketComm)),
    mPipeComm(std::move(pipeComm)) {};

VehicleEmulator::~VehicleEmulator() {
    mSocketComm->stop();
    if (mPipeComm) {
        mPipeComm->stop();
    }
}

/**
 * This is called by the HAL when a property changes. We need to notify our clients that it has
 * changed.
 */
void VehicleEmulator::doSetValueFromClient(const VehiclePropValue& propValue) {
    vhal_proto::EmulatorMessage msg;
    vhal_proto::VehiclePropValue* val = msg.add_value();
    populateProtoVehiclePropValue(propValue, val);
    msg.set_status(vhal_proto::RESULT_OK);
    msg.set_msg_type(vhal_proto::SET_PROPERTY_ASYNC);

    mSocketComm->sendMessage(msg);
    if (mPipeComm) {
        mPipeComm->sendMessage(msg);
    }
}

void VehicleEmulator::doGetConfig(const VehicleEmulator::EmulatorMessage& rxMsg,
                                    VehicleEmulator::EmulatorMessage* respMsg) {
    respMsg->set_msg_type(vhal_proto::GET_CONFIG_RESP);

    if (rxMsg.prop_size() < 1) {
        ALOGE("Invalid GET_CONFIG_CMD msg, missing prop");
        respMsg->set_status(vhal_proto::ERROR_INVALID_OPERATION);
        return;
    }

    vhal_proto::VehiclePropGet getProp = rxMsg.prop(0);

    int32_t propId = getProp.prop();
    auto result = mHal->getPropConfig(propId);
    if (!result.ok()) {
        ALOGE("No config for property: %d\n", propId);
        respMsg->set_status(vhal_proto::ERROR_INVALID_PROPERTY);
        return;
    }

    vhal_proto::VehiclePropConfig* protoCfg = respMsg->add_config();
    populateProtoVehicleConfig(result.value(), protoCfg);
    respMsg->set_status(vhal_proto::RESULT_OK);
}

void VehicleEmulator::doGetConfigAll(const VehicleEmulator::EmulatorMessage& /* rxMsg */,
                                     VehicleEmulator::EmulatorMessage* respMsg) {
    respMsg->set_msg_type(vhal_proto::GET_CONFIG_ALL_RESP);

    std::vector<VehiclePropConfig> configs = mHal->getAllPropertyConfigs();
    respMsg->set_status(vhal_proto::RESULT_OK);

    for (auto& config : configs) {
        vhal_proto::VehiclePropConfig* protoCfg = respMsg->add_config();
        populateProtoVehicleConfig(config, protoCfg);
    }
}

void VehicleEmulator::doGetProperty(const VehicleEmulator::EmulatorMessage& rxMsg,
                                    VehicleEmulator::EmulatorMessage* respMsg) {
    respMsg->set_msg_type(vhal_proto::GET_PROPERTY_RESP);

    if (rxMsg.prop_size() < 1) {
        ALOGE("Invalid GET_PROPERTY_CMD msg, missing prop");
        respMsg->set_status(vhal_proto::ERROR_INVALID_OPERATION);
        return;
    }

    vhal_proto::VehiclePropGet getProp = rxMsg.prop(0);
    int32_t propId = getProp.prop();
    vhal_proto::Status status;

    ALOGD("get property: %d", propId);

    int32_t areaId = 0;
    if (getProp.has_area_id()) {
        areaId = getProp.area_id();
    }

    VehiclePropValue request = {
            .areaId = areaId,
            .prop = propId,
    };
    auto result = mHal->getValue(request);
    if (result.ok()) {
        vhal_proto::VehiclePropValue* protoVal = respMsg->add_value();
        populateProtoVehiclePropValue(*result.value(), protoVal);
        status = vhal_proto::RESULT_OK;
    } else {
        ALOGW("Failed to get value, error: %s", getErrorMsg(result).c_str());
        status = vhal_proto::ERROR_INVALID_PROPERTY;
    }

    respMsg->set_status(status);
}

void VehicleEmulator::doGetPropertyAll(const VehicleEmulator::EmulatorMessage& /* rxMsg */,
                                         VehicleEmulator::EmulatorMessage* respMsg) {
    respMsg->set_msg_type(vhal_proto::GET_PROPERTY_ALL_RESP);

    respMsg->set_status(vhal_proto::RESULT_OK);

    {
        for (const auto& prop : mHal->getAllProperties()) {
            vhal_proto::VehiclePropValue* protoVal = respMsg->add_value();
            populateProtoVehiclePropValue(*prop, protoVal);
        }
    }
}

void VehicleEmulator::doSetProperty(const VehicleEmulator::EmulatorMessage& rxMsg,
                                    VehicleEmulator::EmulatorMessage* respMsg) {
    respMsg->set_msg_type(vhal_proto::SET_PROPERTY_RESP);

    if (rxMsg.value_size() < 1) {
        ALOGE("Invalid SET_PROPERTY_CMD msg, missing value");
        respMsg->set_status(vhal_proto::ERROR_INVALID_OPERATION);
        return;
    }

    vhal_proto::VehiclePropValue protoVal = rxMsg.value(0);
    VehiclePropValue val = {
            .timestamp = elapsedRealtimeNano(),
            .areaId = protoVal.area_id(),
            .prop = protoVal.prop(),
            .status = static_cast<VehiclePropertyStatus>(protoVal.status()),
    };

    ALOGD("set property: %d", protoVal.prop());

    // Copy value data if it is set.  This automatically handles complex data types if needed.
    if (protoVal.has_string_value()) {
        val.value.stringValue = protoVal.string_value().c_str();
    }

    if (protoVal.has_bytes_value()) {
        val.value.byteValues = std::vector<uint8_t> { protoVal.bytes_value().begin(),
                                                 protoVal.bytes_value().end() };
    }

    if (protoVal.int32_values_size() > 0) {
        val.value.int32Values = std::vector<int32_t> { protoVal.int32_values().begin(),
                                                         protoVal.int32_values().end() };
    }

    if (protoVal.int64_values_size() > 0) {
        val.value.int64Values = std::vector<int64_t> { protoVal.int64_values().begin(),
                                                         protoVal.int64_values().end() };
    }

    if (protoVal.float_values_size() > 0) {
        val.value.floatValues = std::vector<float> { protoVal.float_values().begin(),
                                                     protoVal.float_values().end() };
    }

    auto result = mHal->setValue(val);
    respMsg->set_status(result.ok() ? vhal_proto::RESULT_OK : vhal_proto::ERROR_INVALID_PROPERTY);
}

void VehicleEmulator::doDebug(const vhal_proto::EmulatorMessage& rxMsg,
                                vhal_proto::EmulatorMessage* respMsg) {
    respMsg->set_msg_type(vhal_proto::DEBUG_RESP);

    auto protoCommands = rxMsg.debug_commands();
    std::vector<std::string> commands = std::vector<std::string>(
            protoCommands.begin(), protoCommands.end());
    DumpResult msg = mHal->dump(commands);
    respMsg->set_status(vhal_proto::RESULT_OK);
    respMsg->set_debug_result(msg.buffer);
}

void VehicleEmulator::processMessage(const vhal_proto::EmulatorMessage& rxMsg,
                                     vhal_proto::EmulatorMessage* respMsg) {
    switch (rxMsg.msg_type()) {
        case vhal_proto::GET_CONFIG_CMD:
            doGetConfig(rxMsg, respMsg);
            break;
        case vhal_proto::GET_CONFIG_ALL_CMD:
            doGetConfigAll(rxMsg, respMsg);
            break;
        case vhal_proto::GET_PROPERTY_CMD:
            doGetProperty(rxMsg, respMsg);
            break;
        case vhal_proto::GET_PROPERTY_ALL_CMD:
            doGetPropertyAll(rxMsg, respMsg);
            break;
        case vhal_proto::SET_PROPERTY_CMD:
            doSetProperty(rxMsg, respMsg);
            break;
        case vhal_proto::DEBUG_CMD:
            doDebug(rxMsg, respMsg);
            break;
        default:
            ALOGW("%s: Unknown message received, type = %d", __func__, rxMsg.msg_type());
            respMsg->set_status(vhal_proto::ERROR_UNIMPLEMENTED_CMD);
            break;
    }
}

void VehicleEmulator::populateProtoVehicleConfig(const VehiclePropConfig& cfg,
        vhal_proto::VehiclePropConfig* protoCfg) {
    protoCfg->set_prop(cfg.prop);
    protoCfg->set_access(toInt(cfg.access));
    protoCfg->set_change_mode(toInt(cfg.changeMode));
    protoCfg->set_value_type(toInt(getPropType(cfg.prop)));

    for (auto& configElement : cfg.configArray) {
        protoCfg->add_config_array(configElement);
    }

    if (cfg.configString.size() > 0) {
        protoCfg->set_config_string(cfg.configString.c_str(), cfg.configString.size());
    }

    protoCfg->clear_area_configs();
    for (auto& areaConfig : cfg.areaConfigs) {
        auto* protoACfg = protoCfg->add_area_configs();
        protoACfg->set_area_id(areaConfig.areaId);

        switch (getPropType(cfg.prop)) {
            case VehiclePropertyType::STRING:
            case VehiclePropertyType::BOOLEAN:
            case VehiclePropertyType::INT32_VEC:
            case VehiclePropertyType::INT64_VEC:
            case VehiclePropertyType::FLOAT_VEC:
            case VehiclePropertyType::BYTES:
            case VehiclePropertyType::MIXED:
                // Do nothing.  These types don't have min/max values
                break;
            case VehiclePropertyType::INT64:
                protoACfg->set_min_int64_value(areaConfig.minInt64Value);
                protoACfg->set_max_int64_value(areaConfig.maxInt64Value);
                break;
            case VehiclePropertyType::FLOAT:
                protoACfg->set_min_float_value(areaConfig.minFloatValue);
                protoACfg->set_max_float_value(areaConfig.maxFloatValue);
                break;
            case VehiclePropertyType::INT32:
                protoACfg->set_min_int32_value(areaConfig.minInt32Value);
                protoACfg->set_max_int32_value(areaConfig.maxInt32Value);
                break;
            default:
                ALOGW("%s: Unknown property type:  0x%x", __func__, toInt(getPropType(cfg.prop)));
                break;
        }
    }

    protoCfg->set_min_sample_rate(cfg.minSampleRate);
    protoCfg->set_max_sample_rate(cfg.maxSampleRate);
}

void VehicleEmulator::populateProtoVehiclePropValue(const VehiclePropValue& val,
        vhal_proto::VehiclePropValue* protoVal) {
    protoVal->set_prop(val.prop);
    protoVal->set_value_type(toInt(getPropType(val.prop)));
    protoVal->set_timestamp(val.timestamp);
    protoVal->set_status(static_cast<vhal_proto::VehiclePropStatus>(val.status));
    protoVal->set_area_id(val.areaId);

    // Copy value data if it is set.
    //  - for bytes and strings, this is indicated by size > 0
    //  - for int32, int64, and float, copy the values if vectors have data
    if (val.value.stringValue.size() > 0) {
        protoVal->set_string_value(val.value.stringValue.c_str(), val.value.stringValue.size());
    }

    if (val.value.byteValues.size() > 0) {
        protoVal->set_bytes_value(val.value.byteValues.data(), val.value.byteValues.size());
    }

    for (auto& int32Value : val.value.int32Values) {
        protoVal->add_int32_values(int32Value);
    }

    for (auto& int64Value : val.value.int64Values) {
        protoVal->add_int64_values(int64Value);
    }

    for (auto& floatValue : val.value.floatValues) {
        protoVal->add_float_values(floatValue);
    }
}

}  // namespace fake
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android
