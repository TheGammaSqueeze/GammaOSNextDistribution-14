/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef ANDROID_EVENT_WORKER_H_
#define ANDROID_EVENT_WORKER_H_

#include <hardware/hardware.h>
#include <hardware/hwcomposer.h>
#include <stdint.h>
#include <utils/String8.h>

#include <map>

#include "drmdevice.h"
#include "worker.h"

namespace android {

class VsyncCallback {
    public:
        virtual ~VsyncCallback() {}
        virtual void Callback(int display, int64_t timestamp) = 0;
};

class VSyncWorker : public Worker {
    public:
        VSyncWorker();
        ~VSyncWorker() override;

        int Init(DrmDevice* drm, int display, const String8& displayTraceName);
        void RegisterCallback(std::shared_ptr<VsyncCallback> callback);

        void VSyncControl(bool enabled);

    protected:
        void Routine() override;

    private:
        int GetPhasedVSync(uint32_t vsyncPeriodNs, int64_t& expectTimeNs);
        int SyntheticWaitVBlank(int64_t& timestamp);

        DrmDevice* mDrmDevice;

        // shared_ptr since we need to use this outside of the thread lock (to
        // actually call the hook) and we don't want the memory freed until we're
        // done
        std::shared_ptr<VsyncCallback> mCallback = NULL;

        int mDisplay;
        std::atomic_bool mEnabled;
        int64_t mLastTimestampNs;
        String8 mHwVsyncPeriodTag;
        String8 mHwVsyncEnabledTag;
        String8 mDisplayTraceName;
};
}  // namespace android

#endif
