/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <base/functional/callback.h>

#include <ostream>

#include "device_groups.h"
#include "devices.h"
#include "hardware/bt_le_audio.h"
#include "types/raw_address.h"

using bluetooth::le_audio::LeAudioHealthBasedAction;

namespace le_audio {
using LeAudioRecommendationActionCb = base::RepeatingCallback<void(
    const RawAddress& address, int group_id, LeAudioHealthBasedAction action)>;

/* This should be set by the client of this module to provide information about
 * basic LeAudio support of the device which is exposing ASCS UUIDs. Should be
 * used with AddStatisticForDevice API
 */
enum class LeAudioHealthDeviceStatType {
  /* Should be used whenever LeAudio device has invalid GATT Database structure.
   * e.g. missing mandatory services or characteristics. */
  INVALID_DB = 0,
  /* Should be used when LeAudio devie GATT DB contains at least mandatory
   * services and characteristics. */
  VALID_DB,
  /* Should be used when device expose CSIS support but service is not valid. */
  INVALID_CSIS,
  /* Should be used when device expose CSIS and Group ID has been
   * successfully assigned to device. */
  VALID_CSIS,
};

/* When LeAudio device (s) are ready to use, we look at those as a group.
 * Using Group stats we measure how good we are in creating streams.
 * Should be used with AddStatisticForGroup API
 */
enum class LeAudioHealthGroupStatType {
  /* Whenever stream is successfully established. */
  STREAM_CREATE_SUCCESS,
  /* Whenever stream creation failes due to CIS failures */
  STREAM_CREATE_CIS_FAILED,
  /* Whenever stream creation failes due to ASCS signaling failures
   * e.g. ASE does not go to the proper State on time
   */
  STREAM_CREATE_SIGNALING_FAILED,
  /* Context stream not available */
  STREAM_CONTEXT_NOT_AVAILABLE,
};

class LeAudioHealthStatus {
 public:
  virtual ~LeAudioHealthStatus(void) = default;
  static LeAudioHealthStatus* Get(void);
  static void Cleanup(void);
  static void DebugDump(int fd);

  virtual void RegisterCallback(LeAudioRecommendationActionCb cb) = 0;
  virtual void AddStatisticForDevice(const LeAudioDevice* device,
                                     LeAudioHealthDeviceStatType type) = 0;
  virtual void AddStatisticForGroup(const LeAudioDeviceGroup* group,
                                    LeAudioHealthGroupStatType type) = 0;
  virtual void RemoveStatistics(const RawAddress& address, int group) = 0;

  struct group_stats {
    group_stats(int group_id)
        : group_id_(group_id),
          latest_recommendation_(LeAudioHealthBasedAction::NONE),
          stream_success_cnt_(0),
          stream_failures_cnt_(0),
          stream_cis_failures_cnt_(0),
          stream_signaling_failures_cnt_(0),
          stream_context_not_avail_cnt_(0){};

    int group_id_;
    LeAudioHealthBasedAction latest_recommendation_;

    int stream_success_cnt_;
    int stream_failures_cnt_;
    int stream_cis_failures_cnt_;
    int stream_signaling_failures_cnt_;
    int stream_context_not_avail_cnt_;
  };

  struct device_stats {
    device_stats(RawAddress address)
        : address_(address),
          latest_recommendation_(LeAudioHealthBasedAction::NONE),
          is_valid_service_(true),
          is_valid_group_member_(true){};
    RawAddress address_;
    LeAudioHealthBasedAction latest_recommendation_;

    bool is_valid_service_;
    bool is_valid_group_member_;
  };
};

inline std::ostream& operator<<(
    std::ostream& os, const le_audio::LeAudioHealthGroupStatType& stat) {
  switch (stat) {
    case le_audio::LeAudioHealthGroupStatType::STREAM_CREATE_SUCCESS:
      os << "STREAM_CREATE_SUCCESS";
      break;
    case le_audio::LeAudioHealthGroupStatType::STREAM_CREATE_CIS_FAILED:
      os << "STREAM_CREATE_CIS_FAILED";
      break;
    case le_audio::LeAudioHealthGroupStatType::STREAM_CREATE_SIGNALING_FAILED:
      os << "STREAM_CREATE_SIGNALING_FAILED";
      break;
    case le_audio::LeAudioHealthGroupStatType::STREAM_CONTEXT_NOT_AVAILABLE:
      os << "STREAM_CONTEXT_NOT_AVAILABLE";
      break;
    default:
      os << "UNKNOWN";
      break;
  }
  return os;
}

inline std::ostream& operator<<(
    std::ostream& os, const le_audio::LeAudioHealthDeviceStatType& stat) {
  switch (stat) {
    case le_audio::LeAudioHealthDeviceStatType::INVALID_DB:
      os << "INVALID_DB";
      break;
    case le_audio::LeAudioHealthDeviceStatType::VALID_DB:
      os << "VALID_DB";
      break;
    case le_audio::LeAudioHealthDeviceStatType::INVALID_CSIS:
      os << "INVALID_CSIS";
      break;
    case le_audio::LeAudioHealthDeviceStatType::VALID_CSIS:
      os << "VALID_CSIS";
      break;
    default:
      os << "UNKNOWN";
      break;
  }
  return os;
}
}  // namespace le_audio