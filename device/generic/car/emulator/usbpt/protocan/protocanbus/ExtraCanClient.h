/*
 * Copyright (C) 2020 Google Inc. All Rights Reserved.
 */

#pragma once

#include "CanClient.h"

#include <optional>

namespace android::hardware::automotive::vehicle::V2_0::impl {

class ExtraCanClient : public can::V1_0::utils::CanClient {
  public:
    ExtraCanClient();

    void onReady(const sp<can::V1_0::ICanBus>& canBus) override;
    Return<void> onReceive(const can::V1_0::CanMessage& message) override;

  private:
    using VehiclePropValue =
            aidl::android::hardware::automotive::vehicle::VehiclePropValue;
    void appendKeyInput(std::vector<VehiclePropValue>& props, int32_t keyCode,
                        bool keyDown);
    void appendRepeatedKeyInput(std::vector<VehiclePropValue>& props,
                                int32_t keyCode, unsigned repeat);
};

}  // namespace android::hardware::automotive::vehicle::V2_0::impl
