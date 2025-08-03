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

#ifndef EXYNOS_DISPLAY_MODULE_ZUMA_H
#define EXYNOS_DISPLAY_MODULE_ZUMA_H

#include "../../gs201/libhwc2.1/libmaindisplay/ExynosPrimaryDisplayModule.h"

namespace zuma {

using namespace displaycolor;

class ExynosPrimaryDisplayModule : public gs201::ExynosPrimaryDisplayModule {
    public:
        ExynosPrimaryDisplayModule(uint32_t index, ExynosDevice* device,
                                   const std::string& displayName);
        ~ExynosPrimaryDisplayModule();
        virtual int32_t validateWinConfigData();
        void checkPreblendingRequirement() override;

    protected:
        class OperationRateManager
              : public gs201::ExynosPrimaryDisplayModule::OperationRateManager {
        public:
            OperationRateManager(ExynosPrimaryDisplay* display, int32_t hsHz, int32_t nsHz);
            virtual ~OperationRateManager();

            int32_t onLowPowerMode(bool enabled) override;
            int32_t onPeakRefreshRate(uint32_t rate) override;
            int32_t onConfig(hwc2_config_t cfg) override;
            int32_t onBrightness(uint32_t dbv) override;
            int32_t onPowerMode(int32_t mode) override;
            int32_t getTargetOperationRate() const override;

        private:
            enum class DispOpCondition : uint32_t {
                PANEL_SET_POWER = 0,
                SET_CONFIG,
                SET_DBV,
                MAX,
            };

            int32_t updateOperationRateLocked(const DispOpCondition cond);
            int32_t setTargetOperationRate(const int32_t rate);

            ExynosPrimaryDisplay* mDisplay;
            int32_t mDisplayHsOperationRate;
            int32_t mDisplayNsOperationRate;
            int32_t mDisplayTargetOperationRate;
            int32_t mDisplayNsMinDbv;
            int32_t mDisplayPeakRefreshRate;
            int32_t mDisplayRefreshRate;
            int32_t mDisplayLastDbv;
            int32_t mDisplayDbv;
            std::optional<hwc2_power_mode_t> mDisplayPowerMode;
            bool mDisplayLowBatteryModeEnabled;
            Mutex mLock;

            static constexpr uint32_t BRIGHTNESS_DELTA_THRESHOLD = 10;
            static constexpr uint32_t LP_OP_RATE = 30;
        };
};

}  // namespace zuma

#endif // EXYNOS_DISPLAY_MODULE_ZUMA_H
