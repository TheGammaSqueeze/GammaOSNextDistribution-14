/*
 * Copyright (C) 2020 Google Inc. All Rights Reserved.
 */

#include "ExtraCanClient.h"

#include <VehicleUtils.h>
#include <VehicleHalTypes.h>

#include <aidl/android/hardware/automotive/vehicle/VehicleDisplay.h>
#include <aidl/android/hardware/automotive/vehicle/VehicleHwKeyInputAction.h>
#include <android-base/logging.h>
#include <android/keycodes.h>
#include <libprotocan/MessageDef.h>

#include <cmath>
#include <set>

namespace android::hardware::automotive::vehicle::V2_0::impl {

using can::V1_0::CanMessage;
using can::V1_0::CanMessageId;
using can::V1_0::ICanBus;
using protocan::MessageDef;
using protocan::Signal;
using ::aidl::android::hardware::automotive::vehicle::VehicleArea;
using ::aidl::android::hardware::automotive::vehicle::VehicleDisplay;
using ::aidl::android::hardware::automotive::vehicle::VehicleHwKeyInputAction;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropertyStatus;
using ::aidl::android::hardware::automotive::vehicle::VehicleProperty;

namespace can {
// clang-format off

static const MessageDef EncEvent(0x18A, 5, {
    {"EncPos", Signal(0, 16)},
    {"EncDir", Signal(16, 2)},
    {"Buttons", Signal(24, 16)},
});

static const MessageDef ModuleControl(0x000, 2, {
    {"ReqState", Signal(0, 8)},
    {"Address", Signal(8, 8)},
});

// clang-format on
}  // namespace can

ExtraCanClient::ExtraCanClient() : CanClient("aae") {}

void ExtraCanClient::onReady(const sp<ICanBus>& canBus) {
    auto msg = can::ModuleControl.makeDefault();
    can::ModuleControl["ReqState"].set(msg, 1);
    canBus->send(msg);
}

void ExtraCanClient::appendKeyInput(std::vector<VehiclePropValue>& props, int32_t keyCode, bool keyDown) {
    VehiclePropValue prop = {};

    prop.areaId = toInt(VehicleArea::GLOBAL);
    prop.prop = toInt(VehicleProperty::HW_KEY_INPUT);
    prop.status = VehiclePropertyStatus::AVAILABLE;
    prop.value.int32Values = {
            toInt(keyDown ? VehicleHwKeyInputAction::ACTION_DOWN
                          : VehicleHwKeyInputAction::ACTION_UP),
            keyCode,
            toInt(VehicleDisplay::MAIN),
    };

    props.push_back(prop);
}

void ExtraCanClient::appendRepeatedKeyInput(std::vector<VehiclePropValue>& props, int32_t keyCode,
                                   unsigned repeat) {
    for (unsigned i = 0; i < repeat; i++) {
        appendKeyInput(props, keyCode, true);
        appendKeyInput(props, keyCode, false);
    }
}

std::set<int> decodeButtons(uint16_t val) {
    std::set<int> buttons;

    if (val & (1 << 0)) buttons.insert(AKEYCODE_BUTTON_A);     // NAV
    if (val & (1 << 1)) buttons.insert(AKEYCODE_BUTTON_B);     // TEL
    if (val & (1 << 2)) buttons.insert(AKEYCODE_MUSIC);        // MEDIA
    if (val & (1 << 3)) buttons.insert(AKEYCODE_MENU);         // MENU
    if (val & (1 << 4)) buttons.insert(AKEYCODE_BACK);         // BACK
    if (val & (1 << 5)) buttons.insert(AKEYCODE_ENTER);        // ENC-PUSH
    if (val & (1 << 8)) buttons.insert(AKEYCODE_DPAD_UP);      // DPAD-UP
    if (val & (1 << 9)) buttons.insert(AKEYCODE_DPAD_DOWN);    // DPAD-DOWN
    if (val & (1 << 10)) buttons.insert(AKEYCODE_DPAD_LEFT);   // DPAD-LEFT
    if (val & (1 << 11)) buttons.insert(AKEYCODE_DPAD_RIGHT);  // DPAD-RIGHT

    return buttons;
}

::android::hardware::Return<void> ExtraCanClient::onReceive(const CanMessage& message) {
    std::vector<VehiclePropValue> props;

    if (message.id == can::EncEvent.id) {
        if (!can::EncEvent.validate(message)) return {};
        LOG(INFO) << "EncPos: " << can::EncEvent["EncPos"].get(message);
        LOG(INFO) << "EncDir: " << can::EncEvent["EncDir"].get(message);
        LOG(INFO) << "Buttons: " << can::EncEvent["Buttons"].get(message);

        uint16_t encPos = can::EncEvent["EncPos"].get(message);
        static std::optional<uint16_t> prevEncPos;
        if (prevEncPos.has_value()) {
            int16_t diff = encPos - *prevEncPos;
            if (diff > 0) {
                appendRepeatedKeyInput(props, AKEYCODE_DPAD_DOWN, diff);
            } else if (diff < 0) {
                appendRepeatedKeyInput(props, AKEYCODE_DPAD_UP, -diff);
            }
        }
        prevEncPos = encPos;

        static std::set<int> oldButtons;
        auto newButtons = decodeButtons(can::EncEvent["Buttons"].get(message));
        for (int key : newButtons) {
            if (oldButtons.count(key) == 0) {
                appendKeyInput(props, key, true);
            }
        }
        for (int key : oldButtons) {
            if (newButtons.count(key) == 0) {
                appendKeyInput(props, key, false);
            }
        }
        oldButtons = newButtons;
    }

    updateTimestamps(props, message.timestamp);
    sendPropertyEvent(props);
    return {};
}

}  // namespace aidl::android::hardware::automotive::vehicle
