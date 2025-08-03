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


#include "EmulatedVehicleHardware.h"
#include "VehicleEmulator.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <PropertyUtils.h>
#include <VehicleUtils.h>

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace fake {

namespace {

using ::aidl::android::hardware::automotive::vehicle::VehicleProperty;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropertyAccess;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropertyChangeMode;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropertyType;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropertyStatus;
using ::aidl::android::hardware::automotive::vehicle::SetValueRequest;
using ::aidl::android::hardware::automotive::vehicle::SetValueResult;
using ::aidl::android::hardware::automotive::vehicle::StatusCode;

using ::android::hardware::automotive::vehicle::V2_0::impl::MessageSender;

using ::vhal_proto::EmulatorMessage;
using ::vhal_proto::GET_CONFIG_ALL_CMD;
using ::vhal_proto::GET_CONFIG_ALL_RESP;
using ::vhal_proto::GET_CONFIG_CMD;
using ::vhal_proto::GET_CONFIG_RESP;
using ::vhal_proto::GET_PROPERTY_CMD;
using ::vhal_proto::GET_PROPERTY_RESP;
using ::vhal_proto::GET_PROPERTY_ALL_CMD;
using ::vhal_proto::GET_PROPERTY_ALL_RESP;
using ::vhal_proto::SET_PROPERTY_CMD;
using ::vhal_proto::SET_PROPERTY_RESP;
using ::vhal_proto::SET_PROPERTY_ASYNC;
using ::vhal_proto::DEBUG_CMD;
using ::vhal_proto::DEBUG_RESP;
using ::vhal_proto::VehiclePropGet;
using ::vhal_proto::VehiclePropConfig;
using ::vhal_proto::VehiclePropValue;
using ::vhal_proto::VehicleAreaConfig;
using ::vhal_proto::RESULT_OK;
using ::vhal_proto::ERROR_INVALID_PROPERTY;
using ::vhal_proto::ERROR_INVALID_OPERATION;
using ::vhal_proto::VehiclePropStatus;

constexpr int INVALID_PROP_ID = 0;

}  // namespace


class TestConn : public MessageSender {
 public:
    void start() override {
        // Do nothing.
    }

    void stop() override {
        // Do nothing.
    }

    void sendMessage(const EmulatorMessage& msg) override {
        mMsg = msg;
    }

    EmulatorMessage getMessage() {
        return mMsg;
    }

 private:
    EmulatorMessage mMsg;
};


class VehicleEmulatorTest : public ::testing::Test {
 protected:
    void SetUp() override {
        std::unique_ptr<TestConn> socketComm = std::make_unique<TestConn>();
        mSocketComm = socketComm.get();
        std::unique_ptr<TestConn> pipeComm = std::make_unique<TestConn>();
        mPipeComm = pipeComm.get();
        // Cannot use make_unique here because the constructor is private.
        mHardware = std::unique_ptr<EmulatedVehicleHardware>(
                new EmulatedVehicleHardware(/*inQemu=*/true, std::move(socketComm),
                std::move(pipeComm)));
        mEmulator = mHardware->getEmulator();
        mSetValuesCallback = std::make_shared<IVehicleHardware::SetValuesCallback>(
                [this](std::vector<SetValueResult> results) { onSetValues(results); });
    }

    VehicleEmulator* getEmulator() {
        return mEmulator;
    }

    StatusCode setValues(const std::vector<SetValueRequest>& requests) {
        return mHardware->setValues(mSetValuesCallback, requests);
    }

    std::vector<SetValueResult> getSetValueResults() {
        return mSetValueResults;
    }

    EmulatorMessage getPipeCommMessage() {
        return mPipeComm->getMessage();
    }

    EmulatorMessage getSocketCommMessage() {
        return mSocketComm->getMessage();
    }

 private:
    TestConn* mPipeComm;
    TestConn* mSocketComm;
    std::unique_ptr<EmulatedVehicleHardware> mHardware;
    VehicleEmulator* mEmulator;
    std::vector<SetValueResult> mSetValueResults;
    std::shared_ptr<IVehicleHardware::SetValuesCallback> mSetValuesCallback;

    void onSetValues(std::vector<SetValueResult> results) {
        for (auto& result : results) {
            mSetValueResults.push_back(result);
        }
    }
};

TEST_F(VehicleEmulatorTest, testProcessGetConfig) {
    EmulatorMessage rxMsg;
    EmulatorMessage respMsg;

    rxMsg.set_msg_type(GET_CONFIG_CMD);
    VehiclePropGet* propGet = rxMsg.add_prop();
    int32_t propId = toInt(VehicleProperty::HVAC_FAN_SPEED);
    propGet->set_prop(propId);

    getEmulator()->processMessage(rxMsg, &respMsg);

    ASSERT_EQ(respMsg.status(), RESULT_OK);
    ASSERT_EQ(respMsg.msg_type(), GET_CONFIG_RESP);
    ASSERT_EQ(respMsg.config_size(), 1);

    VehiclePropConfig config = respMsg.config(0);

    // The definition for default config:
    // {.config = {.prop = toInt(VehicleProperty::HVAC_FAN_SPEED),
    //                 .access = VehiclePropertyAccess::READ_WRITE,
    //                 .changeMode = VehiclePropertyChangeMode::ON_CHANGE,
    //                 .areaConfigs = {VehicleAreaConfig{
    //                         .areaId = HVAC_ALL, .minInt32Value = 1, .maxInt32Value = 7}}},
    //      .initialValue = {.int32Values = {3}}},
    ASSERT_EQ(config.prop(), propId);
    ASSERT_EQ(config.access(), toInt(VehiclePropertyAccess::READ_WRITE));
    ASSERT_EQ(config.change_mode(), toInt(VehiclePropertyChangeMode::ON_CHANGE));
    ASSERT_EQ(config.value_type(), toInt(VehiclePropertyType::INT32));
    ASSERT_FALSE(config.has_config_flags());
    ASSERT_EQ(config.config_array_size(), 0);
    ASSERT_FALSE(config.has_config_string());

    ASSERT_EQ(config.area_configs_size(), 1);

    VehicleAreaConfig areaConfig = config.area_configs(0);

    ASSERT_EQ(areaConfig.area_id(), HVAC_ALL);
    ASSERT_TRUE(areaConfig.has_min_int32_value());
    ASSERT_EQ(areaConfig.min_int32_value(), 1);
    ASSERT_EQ(areaConfig.max_int32_value(), 7);
}

TEST_F(VehicleEmulatorTest, testProcessGetConfigErrorNoProp) {
    EmulatorMessage rxMsg;
    EmulatorMessage respMsg;

    rxMsg.set_msg_type(GET_CONFIG_CMD);

    getEmulator()->processMessage(rxMsg, &respMsg);

    ASSERT_EQ(respMsg.status(), ERROR_INVALID_OPERATION);
    ASSERT_EQ(respMsg.msg_type(), GET_CONFIG_RESP);
}

TEST_F(VehicleEmulatorTest, testProcessGetConfigErrorInvalidProp) {
    EmulatorMessage rxMsg;
    EmulatorMessage respMsg;

    rxMsg.set_msg_type(GET_CONFIG_CMD);
    VehiclePropGet* propGet = rxMsg.add_prop();
    propGet->set_prop(INVALID_PROP_ID);

    getEmulator()->processMessage(rxMsg, &respMsg);

    ASSERT_EQ(respMsg.status(), ERROR_INVALID_PROPERTY);
    ASSERT_EQ(respMsg.msg_type(), GET_CONFIG_RESP);
}

TEST_F(VehicleEmulatorTest, testProcessGetConfigAll) {
    EmulatorMessage rxMsg;
    EmulatorMessage respMsg;

    rxMsg.set_msg_type(GET_CONFIG_ALL_CMD);

    getEmulator()->processMessage(rxMsg, &respMsg);

    ASSERT_EQ(respMsg.status(), RESULT_OK);
    ASSERT_EQ(respMsg.msg_type(), GET_CONFIG_ALL_RESP);
    // We have at least 10 properties.
    ASSERT_GT(respMsg.config_size(), 10);
}

TEST_F(VehicleEmulatorTest, testProcessGetProperty) {
    EmulatorMessage rxMsg;
    EmulatorMessage respMsg;

    rxMsg.set_msg_type(GET_PROPERTY_CMD);
    VehiclePropGet* propGet = rxMsg.add_prop();
    int32_t propId = toInt(VehicleProperty::HVAC_FAN_SPEED);
    propGet->set_prop(propId);
    propGet->set_area_id(HVAC_ALL);

    getEmulator()->processMessage(rxMsg, &respMsg);

    ASSERT_EQ(respMsg.status(), RESULT_OK);
    ASSERT_EQ(respMsg.msg_type(), GET_PROPERTY_RESP);
    ASSERT_EQ(respMsg.value_size(), 1);

    VehiclePropValue gotValue = respMsg.value(0);

    ASSERT_EQ(gotValue.prop(), propId);
    ASSERT_EQ(gotValue.value_type(), toInt(VehiclePropertyType::INT32));
    ASSERT_EQ(gotValue.status(), VehiclePropStatus::AVAILABLE);
    ASSERT_EQ(gotValue.area_id(), HVAC_ALL);
    ASSERT_EQ(gotValue.int32_values_size(), 1);
    ASSERT_EQ(gotValue.int32_values(0), 3);
}

TEST_F(VehicleEmulatorTest, testProcessGetPropertyErrorNoProp) {
    EmulatorMessage rxMsg;
    EmulatorMessage respMsg;

    rxMsg.set_msg_type(GET_PROPERTY_CMD);

    getEmulator()->processMessage(rxMsg, &respMsg);

    ASSERT_EQ(respMsg.status(), ERROR_INVALID_OPERATION);
    ASSERT_EQ(respMsg.msg_type(), GET_PROPERTY_RESP);
}

TEST_F(VehicleEmulatorTest, testProcessGetPropertyErrorInvalidProp) {
    EmulatorMessage rxMsg;
    EmulatorMessage respMsg;

    rxMsg.set_msg_type(GET_PROPERTY_CMD);
    VehiclePropGet* propGet = rxMsg.add_prop();
    propGet->set_prop(INVALID_PROP_ID);

    getEmulator()->processMessage(rxMsg, &respMsg);

    ASSERT_EQ(respMsg.status(), ERROR_INVALID_PROPERTY);
    ASSERT_EQ(respMsg.msg_type(), GET_PROPERTY_RESP);
}

TEST_F(VehicleEmulatorTest, testProcessGetPropertyAll) {
    EmulatorMessage rxMsg;
    EmulatorMessage respMsg;

    rxMsg.set_msg_type(GET_PROPERTY_ALL_CMD);

    getEmulator()->processMessage(rxMsg, &respMsg);

    ASSERT_EQ(respMsg.status(), RESULT_OK);
    ASSERT_EQ(respMsg.msg_type(), GET_PROPERTY_ALL_RESP);
    // We have at least 10 properties.
    ASSERT_GT(respMsg.value_size(), 10);
}

TEST_F(VehicleEmulatorTest, testProcessSetProperty) {
    EmulatorMessage rxMsg;
    EmulatorMessage respMsg;
    int32_t testValue = 2;

    rxMsg.set_msg_type(SET_PROPERTY_CMD);
    VehiclePropValue* propValue = rxMsg.add_value();
    int32_t propId = toInt(VehicleProperty::HVAC_FAN_SPEED);
    propValue->set_prop(propId);
    propValue->set_area_id(HVAC_ALL);
    propValue->set_status(VehiclePropStatus::AVAILABLE);
    propValue->add_int32_values(testValue);

    getEmulator()->processMessage(rxMsg, &respMsg);

    ASSERT_EQ(respMsg.status(), RESULT_OK);
    ASSERT_EQ(respMsg.msg_type(), SET_PROPERTY_RESP);


    rxMsg.set_msg_type(GET_PROPERTY_CMD);
    VehiclePropGet* propGet = rxMsg.add_prop();
    propGet->set_prop(propId);
    propGet->set_area_id(HVAC_ALL);

    getEmulator()->processMessage(rxMsg, &respMsg);

    ASSERT_EQ(respMsg.status(), RESULT_OK);
    ASSERT_EQ(respMsg.msg_type(), GET_PROPERTY_RESP);
    ASSERT_EQ(respMsg.value_size(), 1);

    VehiclePropValue gotValue = respMsg.value(0);

    ASSERT_EQ(gotValue.prop(), propId);
    ASSERT_EQ(gotValue.int32_values_size(), 1);
    ASSERT_EQ(gotValue.int32_values(0), testValue);
}

TEST_F(VehicleEmulatorTest, testProcessSetPropertyErrorNoValue) {
    EmulatorMessage rxMsg;
    EmulatorMessage respMsg;

    rxMsg.set_msg_type(SET_PROPERTY_CMD);

    getEmulator()->processMessage(rxMsg, &respMsg);

    ASSERT_EQ(respMsg.status(), ERROR_INVALID_OPERATION);
    ASSERT_EQ(respMsg.msg_type(), SET_PROPERTY_RESP);
}

TEST_F(VehicleEmulatorTest, testProcessSetPropertyErrorInvalidProp) {
    EmulatorMessage rxMsg;
    EmulatorMessage respMsg;

    rxMsg.set_msg_type(SET_PROPERTY_CMD);
    VehiclePropValue* propValue = rxMsg.add_value();
    propValue->set_prop(INVALID_PROP_ID);

    getEmulator()->processMessage(rxMsg, &respMsg);

    ASSERT_EQ(respMsg.status(), ERROR_INVALID_PROPERTY);
    ASSERT_EQ(respMsg.msg_type(), SET_PROPERTY_RESP);
}

TEST_F(VehicleEmulatorTest, testProcessDebug) {
    EmulatorMessage rxMsg;
    EmulatorMessage respMsg;

    rxMsg.set_msg_type(DEBUG_CMD);
    rxMsg.add_debug_commands("blabla");

    getEmulator()->processMessage(rxMsg, &respMsg);

    ASSERT_EQ(respMsg.status(), RESULT_OK);
    ASSERT_EQ(respMsg.msg_type(), DEBUG_RESP);
    ASSERT_TRUE(respMsg.has_debug_result());
}

TEST_F(VehicleEmulatorTest, testSetValuesDisplayBrightness) {
    std::vector<SetValueRequest> requests = {
        {
            .requestId = 0,
            .value = {
                .prop = toInt(VehicleProperty::DISPLAY_BRIGHTNESS),
            }
        },
    };

    auto status = setValues(requests);

    ASSERT_EQ(status, StatusCode::OK);

    std::vector<SetValueResult> results = getSetValueResults();

    ASSERT_EQ(results, std::vector<SetValueResult>({
        {
            .requestId = 0,
            .status = StatusCode::OK,
        },
    }));
}

TEST_F(VehicleEmulatorTest, testSetValuesNormal) {
    int32_t testValue = 2;
    int32_t propId = toInt(VehicleProperty::HVAC_FAN_SPEED);
    std::vector<SetValueRequest> requests = {
        {
            .requestId = 0,
            .value = {
                .prop = propId,
                .areaId = HVAC_ALL,
                .value.int32Values = {testValue},
            }
        },
    };

    auto status = setValues(requests);

    ASSERT_EQ(status, StatusCode::OK);

    std::vector<SetValueResult> results = getSetValueResults();

    ASSERT_EQ(results, std::vector<SetValueResult>({
        {
            .requestId = 0,
            .status = StatusCode::OK,
        },
    }));

    // Messages that a property has been set are sent to emulator.
    EmulatorMessage pipeCommMsg = getPipeCommMessage();
    EmulatorMessage socketCommMsg = getSocketCommMessage();

    ASSERT_EQ(pipeCommMsg.status(), RESULT_OK);
    ASSERT_EQ(pipeCommMsg.msg_type(), SET_PROPERTY_ASYNC);
    ASSERT_EQ(pipeCommMsg.value_size(), 1);
    ASSERT_EQ(socketCommMsg.status(), RESULT_OK);
    ASSERT_EQ(socketCommMsg.msg_type(), SET_PROPERTY_ASYNC);
    ASSERT_EQ(socketCommMsg.value_size(), 1);

    VehiclePropValue gotValue = pipeCommMsg.value(0);

    ASSERT_EQ(gotValue.prop(), propId);
    ASSERT_EQ(gotValue.value_type(), toInt(VehiclePropertyType::INT32));
    ASSERT_EQ(gotValue.status(), VehiclePropStatus::AVAILABLE);
    ASSERT_EQ(gotValue.area_id(), HVAC_ALL);
    ASSERT_EQ(gotValue.int32_values_size(), 1);
    ASSERT_EQ(gotValue.int32_values(0), testValue);

    gotValue = socketCommMsg.value(0);

    ASSERT_EQ(gotValue.prop(), propId);
    ASSERT_EQ(gotValue.value_type(), toInt(VehiclePropertyType::INT32));
    ASSERT_EQ(gotValue.status(), VehiclePropStatus::AVAILABLE);
    ASSERT_EQ(gotValue.area_id(), HVAC_ALL);
    ASSERT_EQ(gotValue.int32_values_size(), 1);
    ASSERT_EQ(gotValue.int32_values(0), testValue);
}

}  // namespace fake
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android
