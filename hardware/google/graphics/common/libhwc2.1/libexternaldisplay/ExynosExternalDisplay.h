/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef EXYNOS_EXTERNAL_DISPLAY_H
#define EXYNOS_EXTERNAL_DISPLAY_H

#include "ExynosDisplay.h"
#include <cutils/properties.h>

#define EXTERNAL_DISPLAY_SKIP_LAYER   0x00000100
#define SKIP_EXTERNAL_FRAME 0

class ExynosExternalDisplay : public ExynosDisplay {
    public:
        /* Methods */
        ExynosExternalDisplay(uint32_t index, ExynosDevice* device, const std::string& displayName);
        ~ExynosExternalDisplay();

        virtual void init();
        virtual void deInit();

        int getDisplayConfigs(uint32_t* outNumConfigs, hwc2_config_t* outConfigs);
        virtual int enable();
        int disable();

        /* validateDisplay(..., outNumTypes, outNumRequests)
         * Descriptor: HWC2_FUNCTION_VALIDATE_DISPLAY
         * HWC2_PFN_VALIDATE_DISPLAY
         */
        virtual int32_t validateDisplay(uint32_t* outNumTypes, uint32_t* outNumRequests);
        virtual int32_t canSkipValidate();
        virtual int32_t presentDisplay(int32_t* outRetireFence);
        virtual int openExternalDisplay();
        virtual void closeExternalDisplay();
        virtual int32_t getActiveConfig(hwc2_config_t* outconfig);
        virtual int32_t startPostProcessing();

        virtual int32_t setColorTransform(const float *, int32_t) { return HWC2_ERROR_NONE; }

        virtual int32_t setClientTarget(
                buffer_handle_t target,
                int32_t acquireFence, int32_t /*android_dataspace_t*/ dataspace);
        virtual int32_t setPowerMode(int32_t /*hwc2_power_mode_t*/ mode);
        virtual void initDisplayInterface(uint32_t interfaceType);
        bool checkRotate();
        bool handleRotate();
        virtual void handleHotplugEvent(bool hpdStatus);

        bool mEnabled;
        bool mBlanked;
        bool mVirtualDisplayState;
        bool mIsSkipFrame;
        int mExternalHdrSupported;
        Mutex mExternalMutex;

        int mSkipFrameCount;
        int mSkipStartFrame;

    protected:
        virtual bool getHDRException(ExynosLayer *layer);
    private:
        void reportUsage(bool enabled);
};

#endif

