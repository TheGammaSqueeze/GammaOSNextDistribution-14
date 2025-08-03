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

// Undefine the NAN macro (similar to how it's done in the wifi utils library)
// to avoid symbol clashes between the NAN (Not-A-Number) macro in the bionic
// library headers, and the NAN (Neighbor-Aware-Networking) enum value in the
// WiFi ext interface.
#ifdef NAN
#undef NAN
#endif

#include <condition_variable>
#include <cstdint>
#include <mutex>
#include <string>
#include <thread>

#include <aidl/android/hardware/wifi/WifiStatusCode.h>
#include <aidl/vendor/google/wifi_ext/BnWifiExtChreCallback.h>
#include <aidl/vendor/google/wifi_ext/IWifiExt.h>
#include <android/binder_manager.h>

#include "chre_host/log.h"

namespace android {
namespace chre {

/**
 * Handles interactions with the Wifi Ext HAL, to issue configuration
 * requests to enable or disable NAN (Neighbor-Aware Networking) functionality.
 */
class WifiExtHalHandler {
 public:
  using WifiStatusCode = aidl::android::hardware::wifi::WifiStatusCode;
  using IWifiExt = aidl::vendor::google::wifi_ext::IWifiExt;
  using BnWifiExtChreNanCallback =
      aidl::vendor::google::wifi_ext::BnWifiExtChreCallback;
  using WifiChreNanRttState =
      aidl::vendor::google::wifi_ext::WifiChreNanRttState;

  ~WifiExtHalHandler();

  /**
   * Construct a new Wifi Ext Hal Handler object, initiate a connection to
   * the Wifi ext HAL service.
   *
   * @param statusChangeCallback Callback set by the daemon to be invoked on a
   *        status change to NAN's enablement.
   */
  WifiExtHalHandler(const std::function<void(bool)> &statusChangeCallback);

  /**
   * Invoked by the CHRE daemon when it receives a request to enable or disable
   * NAN from CHRE.
   *
   * @param enable true if CHRE is requesting NAN to be enabled, false if the
   *        request is for a disable.
   */
  void handleConfigurationRequest(bool enable);

 private:
  //! CHRE NAN availability status change handler.
  class WifiExtCallback : public BnWifiExtChreNanCallback {
   public:
    WifiExtCallback(std::function<void(bool)> cb) : mCallback(cb) {}

    ndk::ScopedAStatus onChreNanRttStateChanged(WifiChreNanRttState state) {
      bool enabled = (state == WifiChreNanRttState::CHRE_AVAILABLE);
      onStatusChanged(enabled);
      return ndk::ScopedAStatus::ok();
    }

    void onStatusChanged(bool enabled) {
      mCallback(enabled);
    }

   private:
    std::function<void(bool)> mCallback;
  };

  bool mThreadRunning = true;
  std::thread mThread;
  std::mutex mMutex;
  std::condition_variable mCondVar;

  //! Flag used to indicate the state of the configuration request ('enable' if
  //! true, 'disable' otherwise) if it has a value.
  std::optional<bool> mEnableConfig;

  AIBinder_DeathRecipient *mDeathRecipient;
  std::shared_ptr<IWifiExt> mService;
  std::shared_ptr<WifiExtCallback> mCallback;

  /**
   * Entry point for the thread that handles all interactions with the WiFi ext
   * HAL. This is required since a connection initiation can potentially block
   * indefinitely.
   */
  void wifiExtHandlerThreadEntry();

  /**
   * Notifies the WifiExtHalHandler processing thread of a daemon shutdown.
   */
  void notifyThreadToExit();

  /**
   * Checks for a valid connection to the Wifi ext HAL service, reconnects if
   * not already connected.
   *
   * @return true if connected or upon successful reconnection, false
   *         otherwise.
   */
  bool checkWifiExtHalConnected();

  /**
   * Invoked by the HAL service death callback.
   */
  static void onWifiExtHalServiceDeath(void *cookie);

  /**
   * Dispatch a configuration request to the WiFi Ext HAL.
   *
   * @param enable true if the request is to enable NAN, false if
   *        to disable.
   */
  void dispatchConfigurationRequest(bool enable);
};

}  // namespace chre
}  // namespace android
