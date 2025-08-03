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

#define ATRACE_TAG (ATRACE_TAG_GRAPHICS | ATRACE_TAG_HAL)

#define LOG_TAG "hwc-vsync-worker"

#include "vsyncworker.h"

#include <hardware/hardware.h>
#include <log/log.h>
#include <stdlib.h>
#include <time.h>
#include <utils/Trace.h>
#include <xf86drm.h>
#include <xf86drmMode.h>

#include <map>

#include "drmdevice.h"
#include "worker.h"

using namespace std::chrono_literals;

constexpr auto nsecsPerSec = std::chrono::nanoseconds(1s).count();

namespace android {

VSyncWorker::VSyncWorker()
    : Worker("vsync", 2, true),
      mDrmDevice(NULL),
      mDisplay(-1),
      mEnabled(false),
      mLastTimestampNs(-1) {}

VSyncWorker::~VSyncWorker() {
    Exit();
}

int VSyncWorker::Init(DrmDevice *drm, int display, const String8 &displayTraceName) {
    mDrmDevice = drm;
    mDisplay = display;
    mDisplayTraceName = displayTraceName;
    mHwVsyncPeriodTag.appendFormat("HWVsyncPeriod for %s", displayTraceName.c_str());
    mHwVsyncEnabledTag.appendFormat("HWCVsync for %s", displayTraceName.c_str());

    return InitWorker();
}

void VSyncWorker::RegisterCallback(std::shared_ptr<VsyncCallback> callback) {
    Lock();
    mCallback = callback;
    Unlock();
}

void VSyncWorker::VSyncControl(bool enabled) {
    Lock();
    mEnabled = enabled;
    mLastTimestampNs = -1;
    Unlock();

    ATRACE_INT(mHwVsyncEnabledTag.c_str(), static_cast<int32_t>(enabled));
    ATRACE_INT64(mHwVsyncPeriodTag.c_str(), 0);
    Signal();
}

/*
 * Returns the timestamp of the next vsync in phase with mLastTimestampNs.
 * For example:
 *  mLastTimestampNs = 137
 *  vsyncPeriodNs = 50
 *  currentTimeNs = 683
 *
 *  expectTimeNs = (50 * ((683 - 137) / 50 + 1)) + 137
 *  expectTimeNs = 687
 *
 *  Thus, we must sleep until timestamp 687 to maintain phase with the last
 *  timestamp. But if we don't know last vblank timestamp, sleep one vblank
 *  then try to get vblank from driver again.
 */
int VSyncWorker::GetPhasedVSync(uint32_t vsyncPeriodNs, int64_t &expectTimeNs) {
    struct timespec now;
    if (clock_gettime(CLOCK_MONOTONIC, &now)) {
        ALOGE("clock_gettime failed %d", errno);
        return -EPERM;
    }

    int64_t currentTimeNs = now.tv_sec * nsecsPerSec + now.tv_nsec;
    if (mLastTimestampNs < 0) {
        expectTimeNs = currentTimeNs + vsyncPeriodNs;
        return -EAGAIN;
    }

    expectTimeNs = vsyncPeriodNs * ((currentTimeNs - mLastTimestampNs) / vsyncPeriodNs + 1)
                    + mLastTimestampNs;

    return 0;
}

int VSyncWorker::SyntheticWaitVBlank(int64_t &timestampNs) {
    uint32_t vsyncPeriodNs = kDefaultVsyncPeriodNanoSecond;
    int32_t refreshRate = kDefaultRefreshRateFrequency;

    DrmConnector *conn = mDrmDevice->GetConnectorForDisplay(mDisplay);
    if (conn && conn->active_mode().te_period() != 0.0f &&
            conn->active_mode().v_refresh() != 0.0f) {
        vsyncPeriodNs = static_cast<uint32_t>(conn->active_mode().te_period());
        refreshRate = static_cast<int32_t>(conn->active_mode().v_refresh());
    } else {
        ALOGW("Vsync worker active with conn=%p vsync=%u refresh=%d\n", conn,
            conn ? static_cast<uint32_t>(conn->active_mode().te_period()) :
                    kDefaultVsyncPeriodNanoSecond,
            conn ? static_cast<int32_t>(conn->active_mode().v_refresh()) :
                    kDefaultRefreshRateFrequency);
    }

    int64_t phasedTimestampNs;
    int ret = GetPhasedVSync(vsyncPeriodNs, phasedTimestampNs);
    if (ret && ret != -EAGAIN) return -1;

    struct timespec vsync;
    vsync.tv_sec = phasedTimestampNs / nsecsPerSec;
    vsync.tv_nsec = phasedTimestampNs % nsecsPerSec;

    int err;
    do {
        err = clock_nanosleep(CLOCK_MONOTONIC, TIMER_ABSTIME, &vsync, nullptr);
    } while (err == EINTR);
    if (err || ret) return -1;

    timestampNs = (int64_t)vsync.tv_sec * nsecsPerSec + (int64_t)vsync.tv_nsec;

    return 0;
}

void VSyncWorker::Routine() {
    int ret;

    Lock();
    if (!mEnabled) {
        ret = WaitForSignalOrExitLocked();
        if (ret == -EINTR) {
            Unlock();
            return;
        }
    }

    int display = mDisplay;
    std::shared_ptr<VsyncCallback> callback(mCallback);
    Unlock();

    DrmCrtc *crtc = mDrmDevice->GetCrtcForDisplay(display);
    if (!crtc) {
        ALOGE("Failed to get crtc for display");
        return;
    }
    uint32_t highCrtc = (crtc->pipe() << DRM_VBLANK_HIGH_CRTC_SHIFT);

    drmVBlank vblank;
    memset(&vblank, 0, sizeof(vblank));
    vblank.request.type =
        (drmVBlankSeqType)(DRM_VBLANK_RELATIVE | (highCrtc & DRM_VBLANK_HIGH_CRTC_MASK));
    vblank.request.sequence = 1;

    int64_t timestampNs;
    ret = drmWaitVBlank(mDrmDevice->fd(), &vblank);
    if (ret) {
        if (SyntheticWaitVBlank(timestampNs)) {
            // postpone the callback until we get a real value from the hardware
            return;
        }
    } else {
        timestampNs = (int64_t)vblank.reply.tval_sec * nsecsPerSec +
                (int64_t)vblank.reply.tval_usec * 1000;
    }

    /*
     * VSync could be disabled during routine execution so it could potentially
     * lead to crash since callback's inner hook could be invalid anymore. We have
     * no control over lifetime of this hook, therefore we can't rely that it'll
     * be valid after vsync disabling.
     *
     * Blocking VSyncControl to wait until routine
     * will finish execution is logically correct way to fix this issue, but it
     * creates visible lags and stutters, so we have to resort to other ways of
     * mitigating this issue.
     *
     * Doing check before attempt to invoke callback drastically shortens the
     * window when such situation could happen and that allows us to practically
     * avoid this issue.
     *
     * Please note that issue described below is different one and it is related
     * to RegisterCallback, not to disabling vsync via VSyncControl.
     */
    if (!mEnabled)
        return;
    /*
     * There's a race here where a change in mCallback will not take effect until
     * the next subsequent requested vsync. This is unavoidable since we can't
     * call the vsync hook while holding the thread lock.
     *
     * We could shorten the race window by caching mCallback right before calling
     * the hook. However, in practice, mCallback is only updated once, so it's not
     * worth the overhead.
     */
    if (callback) callback->Callback(display, timestampNs);

    if (mLastTimestampNs >= 0) {
        int64_t period = timestampNs - mLastTimestampNs;
        ATRACE_INT64(mHwVsyncPeriodTag.c_str(), period);
        ALOGV("HW vsync period %" PRId64 "ns for %s", period, mDisplayTraceName.c_str());
    }

    mLastTimestampNs = timestampNs;
}
}  // namespace android
