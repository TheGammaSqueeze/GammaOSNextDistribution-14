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

#include "pixel-display.h"

#include <aidlcommonsupport/NativeHandle.h>
#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <sys/types.h>
#include <utils/Errors.h>

#include "ExynosDisplay.h"
#include "ExynosPrimaryDisplay.h"
#include "HistogramController.h"

extern int32_t load_png_image(const char *filepath, buffer_handle_t buffer);

using ::aidl::com::google::hardware::pixel::display::Display;

void PixelDisplayInit(ExynosDisplay *exynos_display, const std::string_view instance_str) {
    ABinderProcess_setThreadPoolMaxThreadCount(0);

    std::shared_ptr<Display> display = ndk::SharedRefBase::make<Display>(exynos_display);
    const std::string instance = std::string() + Display::descriptor + "/" + std::string(instance_str).c_str();
    binder_status_t status =
            AServiceManager_addService(display->asBinder().get(), instance.c_str());
    LOG(INFO) << instance.c_str() << " service start...";
    CHECK(status == STATUS_OK);

    ABinderProcess_startThreadPool();
}

int32_t readCompensationImage(const aidl::android::hardware::common::NativeHandle &handle,
                              const std::string &imageName) {
    ALOGI("setCompensationImageHandle, imageName = %s", imageName.c_str());

    std::string shadowCompensationImage("/mnt/vendor/persist/display/");
    shadowCompensationImage.append(imageName);

    native_handle_t *clone = makeFromAidl(handle);

    return load_png_image(shadowCompensationImage.c_str(), static_cast<buffer_handle_t>(clone));
}

namespace aidl {
namespace com {
namespace google {
namespace hardware {
namespace pixel {
namespace display {

// ----------------------------------------------------------------------------

ndk::ScopedAStatus Display::isHbmSupported(bool *_aidl_return) {
    *_aidl_return = false;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Display::setHbmState(HbmState state) {
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::getHbmState(HbmState *_aidl_return) {
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::isLbeSupported(bool *_aidl_return) {
    if (mDisplay) {
        *_aidl_return = mDisplay->isLbeSupported();
        return ndk::ScopedAStatus::ok();
    }
    *_aidl_return = false;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Display::setLbeState(LbeState state) {
    if (mDisplay) {
        mDisplay->setLbeState(state);
        return ndk::ScopedAStatus::ok();
    }
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::setLbeAmbientLight(int ambientLux) {
    if (mDisplay) {
        mDisplay->setLbeAmbientLight(ambientLux);
        return ndk::ScopedAStatus::ok();
    }
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::getLbeState(LbeState *_aidl_return) {
    if (mDisplay) {
        *_aidl_return = mDisplay->getLbeState();
        return ndk::ScopedAStatus::ok();
    }
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::isLhbmSupported(bool *_aidl_return) {
    if (mDisplay) {
        *_aidl_return = mDisplay->isLhbmSupported();
        return ndk::ScopedAStatus::ok();
    }
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::setLhbmState(bool enabled) {
    if (mDisplay && mDisplay->isLhbmSupported()) {
        int32_t ret = mDisplay->setLhbmState(enabled);
        if (!ret)
            return ndk::ScopedAStatus::ok();
        else if (ret == TIMED_OUT)
            return ndk::ScopedAStatus::fromExceptionCode(STATUS_TIMED_OUT);
    }
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::getLhbmState(bool *_aidl_return) {
    if (mDisplay && mDisplay->isLhbmSupported()) {
        *_aidl_return = mDisplay->getLhbmState();
        return ndk::ScopedAStatus::ok();
    }
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::setPeakRefreshRate(int rate) {
    if (mDisplay && mDisplay->mOperationRateManager) {
        mDisplay->mOperationRateManager->onPeakRefreshRate(rate);
        return ndk::ScopedAStatus::ok();
    }
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::setLowPowerMode(bool enabled) {
    if (mDisplay && mDisplay->mOperationRateManager) {
        mDisplay->mOperationRateManager->onLowPowerMode(enabled);
        return ndk::ScopedAStatus::ok();
    }
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::isOperationRateSupported(bool *_aidl_return) {
    if (mDisplay) {
        *_aidl_return = mDisplay->isOperationRateSupported();
        return ndk::ScopedAStatus::ok();
    }
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::setCompensationImageHandle(const NativeHandle &native_handle,
                                                       const std::string &imageName,
                                                       int *_aidl_return) {
    if (mDisplay && mDisplay->getPanelCalibrationStatus() == PanelCalibrationStatus::ORIGINAL) {
        *_aidl_return = readCompensationImage(native_handle, imageName);
    } else {
        *_aidl_return = -1;
    }
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Display::setMinIdleRefreshRate(int fps, int *_aidl_return) {
    if (mDisplay) {
        *_aidl_return = mDisplay->setMinIdleRefreshRate(fps, RrThrottleRequester::PIXEL_DISP);
        return ndk::ScopedAStatus::ok();
    }
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::setRefreshRateThrottle(int delayMs, int *_aidl_return) {
    if (mDisplay) {
        if (delayMs < 0) {
            *_aidl_return = BAD_VALUE;
            ALOGW("%s fail: delayMs(%d) is less than 0", __func__, delayMs);
            return ndk::ScopedAStatus::ok();
        }

        *_aidl_return =
                mDisplay->setRefreshRateThrottleNanos(std::chrono::duration_cast<
                                                              std::chrono::nanoseconds>(
                                                              std::chrono::milliseconds(delayMs))
                                                              .count(),
                                                      RrThrottleRequester::PIXEL_DISP);
        return ndk::ScopedAStatus::ok();
    }
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

bool Display::runMediator(const RoiRect &roi, const Weight &weight, const HistogramPos &pos,
                            std::vector<char16_t> *histogrambuffer) {
    bool isConfigChanged;
    histogram::HistogramMediator::HistogramConfig pendingConfig(roi, weight, pos);

    {
        std::scoped_lock lock(mMediator.mConfigMutex);
        isConfigChanged = mMediator.mConfig != pendingConfig;

        if (isConfigChanged) {
            if (mMediator.setRoiWeightThreshold(roi, weight, pos) != HistogramErrorCode::NONE) {
                ALOGE("histogram error, SET_ROI_WEIGHT_THRESHOLD ERROR\n");
                return false;
            }
            mMediator.mConfig = pendingConfig;
        }
    }

    if (!mMediator.histRequested() &&
        mMediator.requestHist() == HistogramErrorCode::ENABLE_HIST_ERROR) {
        ALOGE("histogram error, ENABLE_HIST ERROR\n");
    }

    /*
     * DPU driver maintains always-on histogram engine state with up to date histogram data.
     * Therefore we don't have explicitly to trigger onRefresh in case histogram configuration
     * does not change.
     */
    if (isConfigChanged) {
        mDisplay->mDevice->onRefresh(mDisplay->mDisplayId);
    }

    if (mMediator.collectRoiLuma(histogrambuffer) != HistogramErrorCode::NONE) {
        ALOGE("histogram error, COLLECT_ROI_LUMA ERROR\n");
        return false;
    }
    return true;
}

ndk::ScopedAStatus Display::histogramSample(const RoiRect &roi, const Weight &weight,
                                            HistogramPos pos, Priority pri,
                                            std::vector<char16_t> *histogrambuffer,
                                            HistogramErrorCode *_aidl_return) {
    if (!mDisplay) {
        ALOGI("mDisplay is NULL \n");
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }
    if (histogrambuffer == nullptr) {
        ALOGE("histogrambuffer is null");
        *_aidl_return = HistogramErrorCode::BAD_HIST_DATA;
        return ndk::ScopedAStatus::ok();
    }
    if (mDisplay->isPowerModeOff() == true) {
        *_aidl_return = HistogramErrorCode::DISPLAY_POWEROFF; // panel is off
        return ndk::ScopedAStatus::ok();
    }
    if (mDisplay->isSecureContentPresenting() == true) {
        *_aidl_return = HistogramErrorCode::DRM_PLAYING; // panel is playing DRM content
        return ndk::ScopedAStatus::ok();
    }
    if ((roi.left < 0) || (roi.top < 0) || ((roi.right - roi.left) <= 0) ||
        ((roi.bottom - roi.top) <= 0)) {
        *_aidl_return = HistogramErrorCode::BAD_ROI;
        ALOGE("histogram error, BAD_ROI (%d, %d, %d, %d) \n", roi.left, roi.top, roi.right,
              roi.bottom);
        return ndk::ScopedAStatus::ok();
    }
    if ((weight.weightR + weight.weightG + weight.weightB) != (histogram::WEIGHT_SUM)) {
        *_aidl_return = HistogramErrorCode::BAD_WEIGHT;
        ALOGE("histogram error, BAD_WEIGHT(%d, %d, %d)\n", weight.weightR, weight.weightG,
              weight.weightB);
        return ndk::ScopedAStatus::ok();
    }
    if (pos != HistogramPos::POST && pos != HistogramPos::PRE) {
        *_aidl_return = HistogramErrorCode::BAD_POSITION;
        ALOGE("histogram error, BAD_POSITION(%d)\n", (int)pos);
        return ndk::ScopedAStatus::ok();
    }
    if (pri != Priority::NORMAL && pri != Priority::PRIORITY) {
        *_aidl_return = HistogramErrorCode::BAD_PRIORITY;
        ALOGE("histogram error, BAD_PRIORITY(%d)\n", (int)pri);
        return ndk::ScopedAStatus::ok();
    }
    RoiRect roiCaled = mMediator.calRoi(roi); // fit roi coordinates to RRS
    runMediator(roiCaled, weight, pos, histogrambuffer);
    if (mDisplay->isSecureContentPresenting() == true) {
        /* clear data to avoid leakage */
        std::fill(histogrambuffer->begin(), histogrambuffer->end(), 0);
        histogrambuffer->clear();
        *_aidl_return = HistogramErrorCode::DRM_PLAYING; // panel is playing DRM content
        return ndk::ScopedAStatus::ok();
    }

    *_aidl_return = HistogramErrorCode::NONE;
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Display::getPanelCalibrationStatus(PanelCalibrationStatus *_aidl_return){
    if (mDisplay) {
        *_aidl_return = mDisplay->getPanelCalibrationStatus();
        return ndk::ScopedAStatus::ok();
    }
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::isDbmSupported(bool *_aidl_return) {
    if (!mDisplay) {
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }
    *_aidl_return = mDisplay->isDbmSupported();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Display::setDbmState(bool enabled) {
    if (!mDisplay) {
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }
    mDisplay->setDbmState(enabled);
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus Display::getHistogramCapability(HistogramCapability *_aidl_return) {
    if (mDisplay && mDisplay->mHistogramController) {
        return mDisplay->mHistogramController->getHistogramCapability(_aidl_return);
    }
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::registerHistogram(const ndk::SpAIBinder &token,
                                              const HistogramConfig &histogramConfig,
                                              HistogramErrorCode *_aidl_return) {
    if (mDisplay && mDisplay->mHistogramController) {
        return mDisplay->mHistogramController->registerHistogram(token, histogramConfig,
                                                                 _aidl_return);
    }
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::queryHistogram(const ndk::SpAIBinder &token,
                                           std::vector<char16_t> *histogramBuffer,
                                           HistogramErrorCode *_aidl_return) {
    if (mDisplay && mDisplay->mHistogramController) {
        return mDisplay->mHistogramController->queryHistogram(token, histogramBuffer, _aidl_return);
    }
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::reconfigHistogram(const ndk::SpAIBinder &token,
                                              const HistogramConfig &histogramConfig,
                                              HistogramErrorCode *_aidl_return) {
    if (mDisplay && mDisplay->mHistogramController) {
        return mDisplay->mHistogramController->reconfigHistogram(token, histogramConfig,
                                                                 _aidl_return);
    }
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

ndk::ScopedAStatus Display::unregisterHistogram(const ndk::SpAIBinder &token,
                                                HistogramErrorCode *_aidl_return) {
    if (mDisplay && mDisplay->mHistogramController) {
        return mDisplay->mHistogramController->unregisterHistogram(token, _aidl_return);
    }
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}

} // namespace display
} // namespace pixel
} // namespace hardware
} // namespace google
} // namespace com
} // namespace aidl
