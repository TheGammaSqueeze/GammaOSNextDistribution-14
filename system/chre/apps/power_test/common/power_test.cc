/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <cinttypes>

#include "chre/util/macros.h"
#include "chre_api/chre.h"
#include "common.h"
#include "generated/chre_power_test_generated.h"
#include "include/request_manager.h"
#include "request_manager.h"

#ifdef CHRE_NANOAPP_INTERNAL
namespace chre {
namespace {
#endif  // CHRE_NANOAPP_INTERNAL

using chre::power_test::MessageType;

bool nanoappStart() {
  LOGI("App started on platform ID %" PRIx64, chreGetPlatformId());

  RequestManagerSingleton::init();

  return true;
}

void nanoappHandleEvent(uint32_t senderInstanceId, uint16_t eventType,
                        const void *eventData) {
  UNUSED_VAR(senderInstanceId);

  switch (eventType) {
    case CHRE_EVENT_MESSAGE_FROM_HOST: {
      auto *msg = static_cast<const chreMessageFromHostData *>(eventData);
      RequestManagerSingleton::get()->handleMessageFromHost(*msg);
      break;
    }
    case CHRE_EVENT_TIMER:
      RequestManagerSingleton::get()->handleTimerEvent(eventData);
      break;
    case CHRE_EVENT_WIFI_NAN_IDENTIFIER_RESULT: {
      auto *event =
          static_cast<const struct chreWifiNanIdentifierEvent *>(eventData);
      RequestManagerSingleton::get()->handleNanIdResult(event);
      break;
    }
    case CHRE_EVENT_WIFI_NAN_DISCOVERY_RESULT: {
      auto *event =
          static_cast<const struct chreWifiNanDiscoveryEvent *>(eventData);
      LOGD("NAN discovery subId %" PRIu32 " pubId %" PRIu32, event->subscribeId,
           event->publishId);
      RequestManagerSingleton::get()->requestNanRanging(event);
      break;
    }
    case CHRE_EVENT_WIFI_NAN_SESSION_LOST: {
      auto *event =
          static_cast<const struct chreWifiNanSessionLostEvent *>(eventData);
      LOGD("NAN lost session ID %" PRIu32 " peer ID %" PRIu32, event->id,
           event->peerId);
      break;
    }
    case CHRE_EVENT_WIFI_NAN_SESSION_TERMINATED: {
      auto *event =
          static_cast<const struct chreWifiNanSessionTerminatedEvent *>(
              eventData);
      LOGD("NAN session ID %" PRIu32 " terminated due to %d", event->id,
           event->reason);
      break;
    }
    case CHRE_EVENT_WIFI_ASYNC_RESULT: {
      const struct chreAsyncResult *event =
          static_cast<const struct chreAsyncResult *>(eventData);
      LOGD("Wifi async result type %" PRIu8 " success %d error %" PRIu8,
           event->requestType, event->success, event->errorCode);
      break;
    }
    case CHRE_EVENT_WIFI_SCAN_RESULT: {
      const struct chreWifiScanEvent *event =
          static_cast<const struct chreWifiScanEvent *>(eventData);
      LOGD("Wifi scan received with %" PRIu8 " results, scanType %" PRIu8
           ", radioChainPref %" PRIu8,
           event->resultCount, event->scanType, event->radioChainPref);
      break;
    }
    case CHRE_EVENT_WIFI_RANGING_RESULT: {
      auto *event = static_cast<const struct chreWifiRangingEvent *>(eventData);
      LOGD("Wifi ranging result received with %" PRIu8 " results",
           event->resultCount);
      for (uint8_t i = 0; i < event->resultCount; ++i) {
        LOGD("Ranging result #%" PRIu8 " status %" PRIu8 " rssi %" PRId8
             " distance (mm) %" PRIu32,
             i, event->results[i].status, event->results[i].rssi,
             event->results[i].distance);
      }
      break;
    }
    case CHRE_EVENT_GNSS_ASYNC_RESULT: {
      const struct chreAsyncResult *event =
          static_cast<const struct chreAsyncResult *>(eventData);
      LOGD("GNSS async result type %" PRIu8 " success %d error %" PRIu8,
           event->requestType, event->success, event->errorCode);
      break;
    }
    case CHRE_EVENT_GNSS_LOCATION:
      LOGD("GNSS location received");
      break;
    case CHRE_EVENT_GNSS_DATA:
      LOGD("GNSS measurement received");
      break;
    case CHRE_EVENT_WWAN_CELL_INFO_RESULT:
      LOGD("Cell info received");
      break;
    case CHRE_EVENT_SENSOR_SAMPLING_CHANGE: {
      const struct chreSensorSamplingStatusEvent *event =
          static_cast<const struct chreSensorSamplingStatusEvent *>(eventData);
      LOGD("Sensor sampling status change handle %" PRIu32
           " enabled %d interval %" PRIu64 " latency %" PRIu64,
           event->sensorHandle, event->status.enabled, event->status.interval,
           event->status.latency);
      break;
    }
    case CHRE_EVENT_AUDIO_DATA: {
      const struct chreAudioDataEvent *event =
          static_cast<const struct chreAudioDataEvent *>(eventData);
      LOGD("Audio data received with %" PRIu32 " samples", event->sampleCount);
      break;
    }
    case CHRE_EVENT_AUDIO_SAMPLING_CHANGE: {
      const struct chreAudioSourceStatusEvent *event =
          static_cast<const struct chreAudioSourceStatusEvent *>(eventData);
      LOGD("Audio sampling status event for handle %" PRIu32 ", suspended: %d",
           event->handle, event->status.suspended);
      break;
    }
    default:
      // TODO: Make this log less as sensor events will spam the logcat if debug
      // logging is enabled.
      LOGV("Received event type %" PRIu16, eventType);
  }
}

void nanoappEnd() {
  RequestManagerSingleton::deinit();
  LOGI("Stopped");
}

#ifdef CHRE_NANOAPP_INTERNAL
}  // anonymous namespace
}  // namespace chre

#include "chre/platform/static_nanoapp_init.h"
#include "chre/util/nanoapp/app_id.h"
#include "chre/util/system/napp_permissions.h"

using chre::NanoappPermissions;

CHRE_STATIC_NANOAPP_INIT(PowerTest, chre::kPowerTestAppId, 0,
                         NanoappPermissions::CHRE_PERMS_AUDIO |
                             NanoappPermissions::CHRE_PERMS_GNSS |
                             NanoappPermissions::CHRE_PERMS_WIFI |
                             NanoappPermissions::CHRE_PERMS_WWAN);
#endif  // CHRE_NANOAPP_INTERNAL
