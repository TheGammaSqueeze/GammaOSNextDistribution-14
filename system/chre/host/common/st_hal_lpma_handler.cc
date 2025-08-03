/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <cassert>
#include <cinttypes>

#include "chre_host/st_hal_lpma_handler.h"

namespace android {
namespace chre {

namespace {

constexpr char kChreWakeLockName[] = "chre_lpma_handler";

void acquireWakeLock() {
  int rc;
  if ((rc = acquire_wake_lock(PARTIAL_WAKE_LOCK, kChreWakeLockName)) != 0) {
    LOGE("Failed to acquire wakelock (err %d)", rc);
  }
}

void releaseWakeLock() {
  int rc;
  static bool wakeLockInitialRelease = true;

  // It's expected to get an error when we first try to release the
  // wakelock
  // as it won't exist unless it was leaked previously - don't output a
  // false warning for this case
  if (((rc = release_wake_lock(kChreWakeLockName)) != 0) &&
      !wakeLockInitialRelease) {
    LOGE("Failed to release wakelock (err %d)", rc);
  }

  wakeLockInitialRelease = false;
}

}  // anonymous namespace

StHalLpmaHandler::StHalLpmaHandler(bool allowed) : mIsLpmaAllowed(allowed) {
#ifdef CHRE_ST_LPMA_HANDLER_AIDL
  // TODO(b/278167963): Add death recipient
#else
  auto cb = [&]() { onStHalServiceDeath(); };
  mDeathRecipient = new StHalDeathRecipient(cb);
#endif  // CHRE_ST_LPMA_HANDLER_AIDL
}

StHalLpmaHandler::~StHalLpmaHandler() {
  if (mTargetLpmaEnabled) {
    stopAndUnload();
  }
  if (mThread.has_value()) {
    mStThreadShouldExit = true;
    mCondVar.notify_all();
    mThread->join();
  }
  releaseWakeLock();
}

void StHalLpmaHandler::init() {
  if (mIsLpmaAllowed) {
    mThread = std::thread(&StHalLpmaHandler::stHalLpmaHandlerThreadEntry, this);
  }
}

void StHalLpmaHandler::enable(bool enabled) {
  if (mIsLpmaAllowed) {
    std::lock_guard<std::mutex> lock(mMutex);
    mTargetLpmaEnabled = enabled;
    mCondVarPredicate = true;
    mCondVar.notify_one();
  } else {
    LOGE("Trying to modify LPMA state when LPMA is disabled");
  }
}

bool StHalLpmaHandler::loadAndStart() {
  if (load()) {
    if (start()) {
      return true;
    } else {
      unload();
    }
  }
  return false;
}

void StHalLpmaHandler::stopAndUnload() {
  stop();
  unload();
}

void StHalLpmaHandler::stHalRequestAndProcessLocked(
    std::unique_lock<std::mutex> const &locked) {
  // Cannot use assert(locked.owns_lock()) since locked are not use in other
  // places and non-debug version will not use assert. Compiler will not compile
  // if there is unused parameter.
  if (!locked.owns_lock()) {
    assert(false);
  }
  if (mCurrentLpmaEnabled == mTargetLpmaEnabled) {
    return;
  } else if (mTargetLpmaEnabled && loadAndStart()) {
    mCurrentLpmaEnabled = mTargetLpmaEnabled;
  } else if (!mTargetLpmaEnabled) {
    // Regardless of whether the use case fails to unload, set the
    // currentLpmaEnabled to the targetLpmaEnabled. This will allow the next
    // enable request to proceed. After a failure to unload occurs, the
    // supplied handle is invalid and should not be unloaded again.
    stopAndUnload();
    mCurrentLpmaEnabled = mTargetLpmaEnabled;
  }
}

void StHalLpmaHandler::stHalLpmaHandlerThreadEntry() {
  LOGD("Starting LPMA thread");
  constexpr useconds_t kInitialRetryDelayUs = 500000;
  constexpr int kRetryGrowthFactor = 2;
  constexpr int kRetryGrowthLimit = 5;     // Terminates at 8s retry interval.
  constexpr int kRetryWakeLockLimit = 10;  // Retry with a wakelock 10 times.
  std::unique_lock<std::mutex> lock(mMutex);
  while (!mStThreadShouldExit) {
    stHalRequestAndProcessLocked(lock);
    bool retryNeeded = (mCurrentLpmaEnabled != mTargetLpmaEnabled);
    releaseWakeLock();

    if (retryNeeded) {
      if (mRetryCount < kRetryGrowthLimit) {
        mRetryCount += 1;
        mRetryDelay = mRetryDelay * kRetryGrowthFactor;
      }
      mCondVar.wait_for(lock, std::chrono::microseconds(mRetryDelay), [this] {
        return mCondVarPredicate || mStThreadShouldExit;
      });
    } else {
      mRetryCount = 0;
      mRetryDelay = kInitialRetryDelayUs;
      mCondVar.wait(
          lock, [this] { return mCondVarPredicate || mStThreadShouldExit; });
    }
    mCondVarPredicate = false;
    if (mRetryCount <= kRetryWakeLockLimit) {
      acquireWakeLock();
    }
  }
}

void StHalLpmaHandler::onStHalServiceDeath() {
  LOGE("ST HAL Service Died");
  std::lock_guard<std::mutex> lock(mMutex);
  mStHalService = nullptr;
  if (mTargetLpmaEnabled) {
    // ST HAL has died, so assume that the sound model is no longer active,
    // and trigger a reload of the sound model.
    mCurrentLpmaEnabled = false;
    mCondVarPredicate = true;
    mCondVar.notify_one();
  }
}

#ifdef CHRE_ST_LPMA_HANDLER_AIDL
void StHalLpmaHandler::checkConnectionToStHalServiceLocked() {
  if (mStHalService == nullptr) {
    auto aidlServiceName =
        std::string() + ISoundTriggerHw::descriptor + "/default";
    ndk::SpAIBinder binder(
        AServiceManager_waitForService(aidlServiceName.c_str()));
    if (binder.get() != nullptr) {
      LOGI("Connected to ST HAL service");
      mStHalService = ISoundTriggerHw::fromBinder(binder);
      // TODO(b/278167963): Add death recipient
    }
  }
}

bool StHalLpmaHandler::load() {
  LOGV("Loading LPMA");

  bool loaded = false;
  checkConnectionToStHalServiceLocked();

  aidl::android::media::soundtrigger::SoundModel soundModel;
  soundModel.type = aidl::android::media::soundtrigger::SoundModelType::GENERIC;
  soundModel.vendorUuid = "57caddb1-acdb-4dce-8cb0-2e95a2313aee";
  soundModel.dataSize = 0;
  auto status =
      mStHalService->loadSoundModel(soundModel, nullptr, &mLpmaHandle);
  if (status.isOk()) {
    LOGI("Loaded LPMA");
    loaded = true;
  } else {
    LOGE("Failed to load LPMA with error code %" PRId32,
         status.getExceptionCode());
  }
  return loaded;
}

void StHalLpmaHandler::unload() {
  checkConnectionToStHalServiceLocked();
  auto status = mStHalService->unloadSoundModel(mLpmaHandle);
  if (!status.isOk()) {
    LOGE("Failed to unload LPMA with error code %" PRId32,
         status.getExceptionCode());
  }
}

bool StHalLpmaHandler::start() {
  // TODO(b/278167963): Implement this
  return true;
}

void StHalLpmaHandler::stop() {
  // TODO(b/278167963): Implement this
}

#else

void StHalLpmaHandler::checkConnectionToStHalServiceLocked() {
  if (mStHalService == nullptr) {
    mStHalService = ISoundTriggerHw::getService();
    if (mStHalService != nullptr) {
      LOGI("Connected to ST HAL service");
      mStHalService->linkToDeath(mDeathRecipient, 0 /* flags */);
    }
  }
}

bool StHalLpmaHandler::load() {
  constexpr uint8_t kUuidNode[] = {0x2E, 0x95, 0xA2, 0x31, 0x3A, 0xEE};

  LOGV("Loading LPMA");

  ISoundTriggerHw::SoundModel soundModel;
  soundModel.type = SoundModelType::GENERIC;
  soundModel.vendorUuid.timeLow = 0x57CADDB1;
  soundModel.vendorUuid.timeMid = 0xACDB;
  soundModel.vendorUuid.versionAndTimeHigh = 0x4DCE;
  soundModel.vendorUuid.variantAndClockSeqHigh = 0x8CB0;

  memcpy(&soundModel.vendorUuid.node[0], kUuidNode, sizeof(kUuidNode));
  soundModel.data.resize(1);  // Insert an empty byte to bypass HAL NULL checks.

  bool loaded = false;
  checkConnectionToStHalServiceLocked();
  int32_t loadResult;
  Return<void> hidlResult = mStHalService->loadSoundModel(
      soundModel, nullptr /* callback */, 0 /* cookie */,
      [&](int32_t retval, SoundModelHandle handle) {
        loadResult = retval;
        mLpmaHandle = handle;
      });

  if (hidlResult.isOk()) {
    if (loadResult == 0) {
      LOGD("Loaded LPMA");
      loaded = true;
    } else {
      LOGE("Failed to load LPMA with %" PRId32, loadResult);
    }
  } else {
    LOGE("Failed to load LPMA due to hidl error %s",
         hidlResult.description().c_str());
  }

  return loaded;
}

void StHalLpmaHandler::unload() {
  checkConnectionToStHalServiceLocked();
  Return<int32_t> hidlResult = mStHalService->unloadSoundModel(mLpmaHandle);
  mLpmaHandle = 0;

  if (hidlResult.isOk()) {
    if (hidlResult != 0) {
      LOGE("Failed to unload LPMA with %" PRId32, int32_t(hidlResult));
    }
  } else {
    LOGE("Failed to unload LPMA due to hidl error %s",
         hidlResult.description().c_str());
  }
}

bool StHalLpmaHandler::start() {
#ifdef CHRE_LPMA_REQUEST_START_RECOGNITION
  // mLpmaHandle
  ISoundTriggerHw::RecognitionConfig config = {};

  Return<int32_t> hidlResult = mStHalService->startRecognition(
      mLpmaHandle, config, nullptr /* callback */, 0 /* cookie */);

  int32_t result = hidlResult.withDefault(-EPIPE);
  if (result != 0) {
    LOGE("Failed to start LPMA: %" PRId32, result);
  }
  return (result == 0);
#else
  return true;
#endif  // CHRE_LPMA_REQUEST_START_RECOGNITION
}

void StHalLpmaHandler::stop() {
#ifdef CHRE_LPMA_REQUEST_START_RECOGNITION
  Return<int32_t> hidlResult = mStHalService->stopRecognition(mLpmaHandle);

  int32_t result = hidlResult.withDefault(-EPIPE);
  if (result != 0) {
    LOGW("Failed to stop LPMA: %" PRId32, result);
  }
#endif  // CHRE_LPMA_REQUEST_START_RECOGNITION
}

#endif  // CHRE_ST_LPMA_HANDLER_AIDL

}  // namespace chre
}  // namespace android
