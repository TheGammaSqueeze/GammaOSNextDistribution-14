/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef CHRE_CORE_EVENT_LOOP_MANAGER_H_
#define CHRE_CORE_EVENT_LOOP_MANAGER_H_

#include "chre/core/debug_dump_manager.h"
#include "chre/core/event_loop.h"
#include "chre/core/event_loop_common.h"
#include "chre/core/host_comms_manager.h"
#include "chre/core/host_endpoint_manager.h"
#include "chre/core/settings.h"
#include "chre/core/system_health_monitor.h"
#include "chre/platform/atomic.h"
#include "chre/platform/memory_manager.h"
#include "chre/platform/mutex.h"
#include "chre/util/always_false.h"
#include "chre/util/fixed_size_vector.h"
#include "chre/util/non_copyable.h"
#include "chre/util/singleton.h"
#include "chre/util/unique_ptr.h"
#include "chre_api/chre/event.h"

#ifdef CHRE_AUDIO_SUPPORT_ENABLED
#include "chre/core/audio_request_manager.h"
#endif  // CHRE_AUDIO_SUPPORT_ENABLED

#ifdef CHRE_BLE_SUPPORT_ENABLED
#include "chre/core/ble_request_manager.h"
#endif  // CHRE_BLE_SUPPORT_ENABLED

#ifdef CHRE_GNSS_SUPPORT_ENABLED
#include "chre/core/gnss_manager.h"
#endif  // CHRE_GNSS_SUPPORT_ENABLED

#ifdef CHRE_SENSORS_SUPPORT_ENABLED
#include "chre/core/sensor_request_manager.h"
#endif  // CHRE_SENSORS_SUPPORT_ENABLED

#ifdef CHRE_WIFI_SUPPORT_ENABLED
#include "chre/core/wifi_request_manager.h"
#endif  // CHRE_WIFI_SUPPORT_ENABLED

#ifdef CHRE_WWAN_SUPPORT_ENABLED
#include "chre/core/wwan_request_manager.h"
#endif  // CHRE_WWAN_SUPPORT_ENABLED

#ifdef CHRE_TELEMETRY_SUPPORT_ENABLED
#include "chre/core/telemetry_manager.h"
#endif  // CHRE_TELEMETRY_SUPPORT_ENABLED

#include <cstddef>

namespace chre {

template <typename T>
using TypedSystemEventCallbackFunction = void(SystemCallbackType type,
                                              UniquePtr<T> &&data);

/**
 * A class that keeps track of all event loops in the system. This class
 * represents the top-level object in CHRE. It will own all resources that are
 * shared by all event loops.
 */
class EventLoopManager : public NonCopyable {
 public:
  /**
   * Validates that a CHRE API is invoked from a valid nanoapp context and
   * returns a pointer to the currently executing nanoapp. This should be
   * called by most CHRE API methods that require accessing details about the
   * event loop or the nanoapp itself. If the current event loop or nanoapp are
   * null, this is an assertion error.
   *
   * @param functionName The name of the CHRE API. This should be __func__.
   * @param eventLoop Optional output parameter, which will be populated with
   *        the EventLoop that is currently executing if this function is
   *        successful
   * @return A pointer to the currently executing nanoapp or null if outside
   *         the context of a nanoapp.
   */
  static Nanoapp *validateChreApiCall(const char *functionName);

  /**
   * Leverages the event queue mechanism to schedule a CHRE system callback to
   * be invoked at some point in the future from within the context of the
   * "main" EventLoop. Which EventLoop is considered to be the "main" one is
   * currently not specified, but it is required to be exactly one EventLoop
   * that does not change at runtime.
   *
   * This function is safe to call from any thread.
   *
   * @param type An identifier for the callback, which is passed through to the
   *        callback as a uint16_t, and can also be useful for debugging
   * @param data Arbitrary data to provide to the callback
   * @param callback Function to invoke from within the main CHRE thread
   * @param extraData Additional arbitrary data to provide to the callback
   * @return If true, the callback was deferred successfully; false otherwise.
   */
  bool deferCallback(SystemCallbackType type, void *data,
                     SystemEventCallbackFunction *callback,
                     void *extraData = nullptr) {
    return mEventLoop.postSystemEvent(static_cast<uint16_t>(type), data,
                                      callback, extraData);
  }

  /**
   * Alternative version of deferCallback which accepts a UniquePtr for the data
   * passed to the callback. This overload helps ensure that type continuity is
   * maintained with the callback, and also helps to ensure that the memory is
   * not leaked, including when CHRE is shutting down.
   *
   * Safe to call from any thread.
   *
   * @param type An identifier for the callback, which is passed through as
   *        uint16_t, and can also be useful for debugging
   * @param data Pointer to arbitrary data to provide to the callback
   * @param callback Function to invoke from within the main CHRE thread
   * @return If true, the callback was deferred successfully; false otherwise.
   */
  template <typename T>
  bool deferCallback(SystemCallbackType type, UniquePtr<T> &&data,
                     TypedSystemEventCallbackFunction<T> *callback) {
    auto outerCallback = [](uint16_t callbackType, void *eventData,
                            void *extraData) {
      // Re-wrap eventData in UniquePtr so its destructor will get called and
      // the memory will be freed once we leave this scope
      UniquePtr<T> dataWrapped = UniquePtr<T>(static_cast<T *>(eventData));
      auto *innerCallback =
          reinterpret_cast<TypedSystemEventCallbackFunction<T> *>(extraData);
      innerCallback(static_cast<SystemCallbackType>(callbackType),
                    std::move(dataWrapped));
    };
    // Pass the "inner" callback (the caller's callback) through to the "outer"
    // callback using the extraData parameter. Note that we're leveraging the
    // C++11 ability to cast a function pointer to void*
    bool status = mEventLoop.postSystemEvent(
        static_cast<uint16_t>(type), data.get(), outerCallback,
        reinterpret_cast<void *>(callback));
    if (status) {
      data.release();
    }
    return status;
  }

  //! Override that allows passing a lambda for the callback
  template <typename T, typename LambdaT>
  bool deferCallback(SystemCallbackType type, UniquePtr<T> &&data,
                     LambdaT callback) {
    return deferCallback(
        type, std::move(data),
        static_cast<TypedSystemEventCallbackFunction<T> *>(callback));
  }

  //! Disallows passing a null callback, as we don't include a null check in the
  //! outer callback to reduce code size. Note that this doesn't prevent the
  //! caller from passing a variable which is set to nullptr at runtime, but
  //! generally the callback is always known at compile time.
  template <typename T>
  void deferCallback(SystemCallbackType /*type*/, UniquePtr<T> && /*data*/,
                     std::nullptr_t /*callback*/) {
    static_assert(AlwaysFalse<T>::value,
                  "deferCallback(SystemCallbackType, UniquePtr<T>, nullptr) is "
                  "not allowed");
  }

  /**
   * Schedules a CHRE system callback to be invoked at some point in the future
   * after a specified amount of time, in the context of the "main" CHRE
   * EventLoop.
   *
   * This function is safe to call from any thread.
   *
   * @param type An identifier for the callback, which is passed through to the
   *        callback as a uint16_t, and can also be useful for debugging
   * @param data Arbitrary data to provide to the callback
   * @param callback Function to invoke from within the main CHRE event loop -
   *        note that extraData is always passed back as nullptr
   * @param delay The delay to postpone posting the event
   * @return TimerHandle of the requested timer.
   *
   * @see deferCallback
   */
  TimerHandle setDelayedCallback(SystemCallbackType type, void *data,
                                 SystemEventCallbackFunction *callback,
                                 Nanoseconds delay) {
    return mEventLoop.getTimerPool().setSystemTimer(delay, callback, type,
                                                    data);
  }

  /**
   * Cancels a delayed callback previously scheduled by setDelayedCallback.
   *
   * This function is safe to call from any thread.
   *
   * @param timerHandle The TimerHandle returned by setDelayedCallback
   *
   * @return true if the callback was successfully cancelled
   */
  bool cancelDelayedCallback(TimerHandle timerHandle) {
    return mEventLoop.getTimerPool().cancelSystemTimer(timerHandle);
  }

  /**
   * Returns a guaranteed unique instance identifier to associate with a newly
   * constructed nanoapp.
   *
   * @return a unique instance ID
   */
  uint16_t getNextInstanceId();

#ifdef CHRE_AUDIO_SUPPORT_ENABLED
  /**
   * @return A reference to the audio request manager. This allows interacting
   *         with the audio subsystem and manages requests from various
   *         nanoapps.
   */
  AudioRequestManager &getAudioRequestManager() {
    return mAudioRequestManager;
  }
#endif  // CHRE_AUDIO_SUPPORT_ENABLED

#ifdef CHRE_BLE_SUPPORT_ENABLED
  /**
   * @return A reference to the ble request manager. This allows interacting
   *         with the ble subsystem and manages requests from various
   *         nanoapps.
   */
  BleRequestManager &getBleRequestManager() {
    return mBleRequestManager;
  }
#endif  // CHRE_BLE_SUPPORT_ENABLED

  /**
   * @return The event loop managed by this event loop manager.
   */
  EventLoop &getEventLoop() {
    return mEventLoop;
  }

#ifdef CHRE_GNSS_SUPPORT_ENABLED
  /**
   * @return A reference to the GNSS request manager. This allows interacting
   *         with the platform GNSS subsystem and manages requests from various
   *         nanoapps.
   */
  GnssManager &getGnssManager() {
    return mGnssManager;
  }
#endif  // CHRE_GNSS_SUPPORT_ENABLED

  /**
   * @return A reference to the host communications manager that enables
   *         transferring arbitrary data between the host processor and CHRE.
   */
  HostCommsManager &getHostCommsManager() {
    return mHostCommsManager;
  }

  HostEndpointManager &getHostEndpointManager() {
    return mHostEndpointManager;
  }

#ifdef CHRE_SENSORS_SUPPORT_ENABLED
  /**
   * @return Returns a reference to the sensor request manager. This allows
   *         interacting with the platform sensors and managing requests from
   *         various nanoapps.
   */
  SensorRequestManager &getSensorRequestManager() {
    return mSensorRequestManager;
  }
#endif  // CHRE_SENSORS_SUPPORT_ENABLED

#ifdef CHRE_WIFI_SUPPORT_ENABLED
  /**
   * @return Returns a reference to the wifi request manager. This allows
   *         interacting with the platform wifi subsystem and manages the
   *         requests from various nanoapps.
   */
  WifiRequestManager &getWifiRequestManager() {
    return mWifiRequestManager;
  }
#endif  // CHRE_WIFI_SUPPORT_ENABLED

#ifdef CHRE_WWAN_SUPPORT_ENABLED
  /**
   * @return A reference to the WWAN request manager. This allows interacting
   *         with the platform WWAN subsystem and manages requests from various
   *         nanoapps.
   */
  WwanRequestManager &getWwanRequestManager() {
    return mWwanRequestManager;
  }
#endif  // CHRE_WWAN_SUPPORT_ENABLED

  /**
   * @return A reference to the memory manager. This allows central control of
   *         the heap space allocated by nanoapps.
   */
  MemoryManager &getMemoryManager() {
    return mMemoryManager;
  }

  /**
   * @return A reference to the debug dump manager. This allows central control
   *         of the debug dump process.
   */
  DebugDumpManager &getDebugDumpManager() {
    return mDebugDumpManager;
  }

#ifdef CHRE_TELEMETRY_SUPPORT_ENABLED
  /**
   * @return A reference to the telemetry manager.
   */
  TelemetryManager &getTelemetryManager() {
    return mTelemetryManager;
  }
#endif  // CHRE_TELEMETRY_SUPPORT_ENABLED

  /**
   * @return A reference to the setting manager.
   */
  SettingManager &getSettingManager() {
    return mSettingManager;
  }

  SystemHealthMonitor &getSystemHealthMonitor() {
    return mSystemHealthMonitor;
  }

  /**
   * Performs second-stage initialization of things that are not necessarily
   * required at construction time but need to be completed prior to executing
   * any nanoapps.
   */
  void lateInit();

 private:
  //! The instance ID generated by getNextInstanceId().
  AtomicUint32 mNextInstanceId{kSystemInstanceId + 1};

#ifdef CHRE_AUDIO_SUPPORT_ENABLED
  //! The audio request manager handles requests for all nanoapps and manages
  //! the state of the audio subsystem that the runtime subscribes to.
  AudioRequestManager mAudioRequestManager;
#endif

#ifdef CHRE_BLE_SUPPORT_ENABLED
  //! The BLE request manager handles requests for all nanoapps and manages
  //! the state of the BLE subsystem that the runtime subscribes to.
  BleRequestManager mBleRequestManager;
#endif  // CHRE_BLE_SUPPORT_ENABLED

  //! The event loop managed by this event loop manager.
  EventLoop mEventLoop;

#ifdef CHRE_GNSS_SUPPORT_ENABLED
  //! The GnssManager that handles requests for all nanoapps. This manages the
  //! state of the GNSS subsystem that the runtime subscribes to.
  GnssManager mGnssManager;
#endif  // CHRE_GNSS_SUPPORT_ENABLED

  //! Handles communications with the host processor.
  HostCommsManager mHostCommsManager;

  HostEndpointManager mHostEndpointManager;

  SystemHealthMonitor mSystemHealthMonitor;

#ifdef CHRE_SENSORS_SUPPORT_ENABLED
  //! The SensorRequestManager that handles requests for all nanoapps. This
  //! manages the state of all sensors that runtime subscribes to.
  SensorRequestManager mSensorRequestManager;
#endif  // CHRE_SENSORS_SUPPORT_ENABLED

#ifdef CHRE_WIFI_SUPPORT_ENABLED
  //! The WifiRequestManager that handles requests for nanoapps. This manages
  //! the state of the wifi subsystem that the runtime subscribes to.
  WifiRequestManager mWifiRequestManager;
#endif  // CHRE_WIFI_SUPPORT_ENABLED

#ifdef CHRE_WWAN_SUPPORT_ENABLED
  //! The WwanRequestManager that handles requests for nanoapps. This manages
  //! the state of the WWAN subsystem that the runtime subscribes to.
  WwanRequestManager mWwanRequestManager;
#endif  // CHRE_WWAN_SUPPORT_ENABLED

  //! The MemoryManager that handles malloc/free call from nanoapps and also
  //! controls upper limits on the heap allocation amount.
  MemoryManager mMemoryManager;

  //! The DebugDumpManager that handles the debug dump process.
  DebugDumpManager mDebugDumpManager;

#ifdef CHRE_TELEMETRY_SUPPORT_ENABLED
  //! The TelemetryManager that handles metric collection/reporting.
  TelemetryManager mTelemetryManager;
#endif  // CHRE_TELEMETRY_SUPPORT_ENABLED

  //! The SettingManager that manages setting states.
  SettingManager mSettingManager;
};

//! Provide an alias to the EventLoopManager singleton.
typedef Singleton<EventLoopManager> EventLoopManagerSingleton;

//! Extern the explicit EventLoopManagerSingleton to force non-inline method
//! calls. This reduces codesize considerably.
extern template class Singleton<EventLoopManager>;

#ifdef CHRE_SENSORS_SUPPORT_ENABLED
inline SensorRequestManager &getSensorRequestManager() {
  return EventLoopManagerSingleton::get()->getSensorRequestManager();
}
#endif  // CHRE_SENSORS_SUPPORT_ENABLED

}  // namespace chre

#endif  // CHRE_CORE_EVENT_LOOP_MANAGER_H_
