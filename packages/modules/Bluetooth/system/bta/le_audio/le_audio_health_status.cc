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

#include "le_audio_health_status.h"

#include <vector>

#include "bta/include/bta_groups.h"
#include "common/strings.h"
#include "main/shim/metrics_api.h"
#include "os/log.h"
#include "osi/include/properties.h"

using bluetooth::common::ToString;
using bluetooth::groups::kGroupUnknown;
using le_audio::LeAudioDevice;
using le_audio::LeAudioHealthStatus;
using le_audio::LeAudioRecommendationActionCb;

namespace le_audio {
class LeAudioHealthStatusImpl;
LeAudioHealthStatusImpl* instance;

class LeAudioHealthStatusImpl : public LeAudioHealthStatus {
 public:
  LeAudioHealthStatusImpl(void) { LOG_DEBUG(" Initiated"); }

  ~LeAudioHealthStatusImpl(void) { clear_module(); }

  void RegisterCallback(LeAudioRecommendationActionCb cb) override {
    register_callback(std::move(cb));
  }

  void RemoveStatistics(const RawAddress& address, int group_id) override {
    LOG_DEBUG("%s, group_id: %d", ADDRESS_TO_LOGGABLE_CSTR(address), group_id);
    remove_device(address);
    remove_group(group_id);
  }

  void AddStatisticForDevice(const LeAudioDevice* device,
                             LeAudioHealthDeviceStatType type) override {
    if (device == nullptr) {
      LOG_ERROR("device is null");
      return;
    }

    const RawAddress& address = device->address_;
    LOG_DEBUG("%s, %s", ADDRESS_TO_LOGGABLE_CSTR(address),
              ToString(type).c_str());

    auto dev = find_device(address);
    if (dev == nullptr) {
      add_device(address);
      dev = find_device(address);
      if (dev == nullptr) {
        LOG_ERROR("Could not add device %s", ADDRESS_TO_LOGGABLE_CSTR(address));
        return;
      }
    }
    // log counter metrics
    log_counter_metrics_for_device(type, device->allowlist_flag_);

    LeAudioHealthBasedAction action;
    switch (type) {
      case LeAudioHealthDeviceStatType::VALID_DB:
        dev->is_valid_service_ = true;
        action = LeAudioHealthBasedAction::NONE;
        break;
      case LeAudioHealthDeviceStatType::INVALID_DB:
        dev->is_valid_service_ = false;
        action = LeAudioHealthBasedAction::DISABLE;
        break;
      case LeAudioHealthDeviceStatType::INVALID_CSIS:
        dev->is_valid_group_member_ = false;
        action = LeAudioHealthBasedAction::DISABLE;
        break;
      case LeAudioHealthDeviceStatType::VALID_CSIS:
        dev->is_valid_group_member_ = true;
        action = LeAudioHealthBasedAction::NONE;
        break;
    }

    if (dev->latest_recommendation_ != action) {
      dev->latest_recommendation_ = action;
      send_recommendation_for_device(address, action);
      return;
    }
  }

  void AddStatisticForGroup(const LeAudioDeviceGroup* device_group,
                            LeAudioHealthGroupStatType type) override {
    if (device_group == nullptr) {
      LOG_ERROR("device_group is null");
      return;
    }

    int group_id = device_group->group_id_;
    LOG_DEBUG("group_id: %d, %s", group_id, ToString(type).c_str());

    auto group = find_group(group_id);
    if (group == nullptr) {
      add_group(group_id);
      group = find_group(group_id);
      if (group == nullptr) {
        LOG_ERROR("Could not add group %d", group_id);
        return;
      }
    }

    LeAudioDevice* device = device_group->GetFirstDevice();
    if (device == nullptr) {
      LOG_ERROR("Front device is null. Number of devices: %d",
                device_group->Size());
      return;
    }
    // log counter metrics
    log_counter_metrics_for_group(type, device->allowlist_flag_);

    switch (type) {
      case LeAudioHealthGroupStatType::STREAM_CREATE_SUCCESS:
        group->stream_success_cnt_++;
        if (group->latest_recommendation_ == LeAudioHealthBasedAction::NONE) {
          return;
        }
        break;
      case LeAudioHealthGroupStatType::STREAM_CREATE_CIS_FAILED:
        group->stream_cis_failures_cnt_++;
        group->stream_failures_cnt_++;
        break;
      case LeAudioHealthGroupStatType::STREAM_CREATE_SIGNALING_FAILED:
        group->stream_signaling_failures_cnt_++;
        group->stream_failures_cnt_++;
        break;
      case LeAudioHealthGroupStatType::STREAM_CONTEXT_NOT_AVAILABLE:
        group->stream_context_not_avail_cnt_++;
        break;
    }

    LeAudioHealthBasedAction action = LeAudioHealthBasedAction::NONE;
    if (group->stream_success_cnt_ == 0) {
      /* Never succeed in stream creation */
      if ((group->stream_failures_cnt_ >=
           MAX_ALLOWED_FAILURES_IN_A_ROW_WITHOUT_SUCCESS)) {
        action = LeAudioHealthBasedAction::DISABLE;
      } else if (group->stream_context_not_avail_cnt_ >=
                 MAX_ALLOWED_FAILURES_IN_A_ROW_WITHOUT_SUCCESS) {
        action = LeAudioHealthBasedAction::INACTIVATE_GROUP;
        group->stream_context_not_avail_cnt_ = 0;
      }
    } else {
      /* Had some success before */
      if ((100 * group->stream_failures_cnt_ / group->stream_success_cnt_) >=
          THRESHOLD_FOR_DISABLE_CONSIDERATION) {
        action = LeAudioHealthBasedAction::CONSIDER_DISABLING;
      } else if (group->stream_context_not_avail_cnt_ >=
                 MAX_ALLOWED_FAILURES_IN_A_ROW_WITHOUT_SUCCESS) {
        action = LeAudioHealthBasedAction::INACTIVATE_GROUP;
        group->stream_context_not_avail_cnt_ = 0;
      }
    }

    if (group->latest_recommendation_ != action) {
      group->latest_recommendation_ = action;
      send_recommendation_for_group(group_id, action);
    }
  }

  void Dump(int fd) {
    dprintf(fd, "  LeAudioHealthStats: \n    groups:");
    for (const auto& g : group_stats_) {
      dumpsys_group(fd, g);
    }
    dprintf(fd, "\n    devices: ");
    for (const auto& dev : devices_stats_) {
      dumpsys_dev(fd, dev);
    }
    dprintf(fd, "\n");
  }

 private:
  static constexpr int MAX_ALLOWED_FAILURES_IN_A_ROW_WITHOUT_SUCCESS = 3;
  static constexpr int THRESHOLD_FOR_DISABLE_CONSIDERATION = 70;

  std::vector<LeAudioRecommendationActionCb> callbacks_;
  std::vector<device_stats> devices_stats_;
  std::vector<group_stats> group_stats_;

  void dumpsys_group(int fd, const group_stats& group) {
    std::stringstream stream;

    stream << "\n group_id: " << group.group_id_ << ": "
           << group.latest_recommendation_
           << ", success: " << group.stream_success_cnt_
           << ", fail total: " << group.stream_failures_cnt_
           << ", fail cis: " << group.stream_cis_failures_cnt_
           << ", fail signaling: " << group.stream_signaling_failures_cnt_
           << ", context not avail: " << group.stream_context_not_avail_cnt_;

    dprintf(fd, "%s", stream.str().c_str());
  }

  void dumpsys_dev(int fd, const device_stats& dev) {
    std::stringstream stream;

    stream << "\n " << ADDRESS_TO_LOGGABLE_STR(dev.address_) << ": "
           << dev.latest_recommendation_
           << (dev.is_valid_service_ ? " service: OK" : " service : NOK")
           << (dev.is_valid_group_member_ ? " csis: OK" : " csis : NOK");

    dprintf(fd, "%s", stream.str().c_str());
  }

  void clear_module(void) {
    devices_stats_.clear();
    group_stats_.clear();
    callbacks_.clear();
  }

  void send_recommendation_for_device(const RawAddress& address,
                                      LeAudioHealthBasedAction recommendation) {
    LOG_DEBUG("%s, %s", ADDRESS_TO_LOGGABLE_CSTR(address),
              ToString(recommendation).c_str());
    /* Notify new user about known groups */
    for (auto& cb : callbacks_) {
      cb.Run(address, kGroupUnknown, recommendation);
    }
  }

  void send_recommendation_for_group(
      int group_id, const LeAudioHealthBasedAction recommendation) {
    LOG_DEBUG("group_id: %d, %s", group_id, ToString(recommendation).c_str());
    /* Notify new user about known groups */
    for (auto& cb : callbacks_) {
      cb.Run(RawAddress::kEmpty, group_id, recommendation);
    }
  }

  void add_device(const RawAddress& address) {
    devices_stats_.emplace_back(device_stats(address));
  }

  void add_group(int group_id) {
    group_stats_.emplace_back(group_stats(group_id));
  }

  void remove_group(int group_id) {
    if (group_id == kGroupUnknown) {
      return;
    }
    auto iter = std::find_if(
        group_stats_.begin(), group_stats_.end(),
        [group_id](const auto& g) { return g.group_id_ == group_id; });
    if (iter != group_stats_.end()) {
      group_stats_.erase(iter);
    }
  }

  void remove_device(const RawAddress& address) {
    auto iter = std::find_if(
        devices_stats_.begin(), devices_stats_.end(),
        [address](const auto& d) { return d.address_ == address; });
    if (iter != devices_stats_.end()) {
      devices_stats_.erase(iter);
    }
  }

  void register_callback(LeAudioRecommendationActionCb cb) {
    callbacks_.push_back(std::move(cb));
  }

  device_stats* find_device(const RawAddress& address) {
    auto iter = std::find_if(
        devices_stats_.begin(), devices_stats_.end(),
        [address](const auto& d) { return d.address_ == address; });
    if (iter == devices_stats_.end()) return nullptr;

    return &(*iter);
  }

  group_stats* find_group(int group_id) {
    auto iter = std::find_if(
        group_stats_.begin(), group_stats_.end(),
        [group_id](const auto& g) { return g.group_id_ == group_id; });
    if (iter == group_stats_.end()) return nullptr;

    return &(*iter);
  }

  void log_counter_metrics_for_device(LeAudioHealthDeviceStatType type,
                                      bool in_allowlist) {
    LOG_DEBUG("in_allowlist: %d, type: %s", in_allowlist,
              ToString(type).c_str());
    android::bluetooth::CodePathCounterKeyEnum key;
    if (in_allowlist) {
      switch (type) {
        case LeAudioHealthDeviceStatType::VALID_DB:
        case LeAudioHealthDeviceStatType::VALID_CSIS:
          key = android::bluetooth::CodePathCounterKeyEnum::
              LE_AUDIO_ALLOWLIST_DEVICE_HEALTH_STATUS_GOOD;
          break;
        case LeAudioHealthDeviceStatType::INVALID_DB:
          key = android::bluetooth::CodePathCounterKeyEnum::
              LE_AUDIO_ALLOWLIST_DEVICE_HEALTH_STATUS_BAD_INVALID_DB;
          break;
        case LeAudioHealthDeviceStatType::INVALID_CSIS:
          key = android::bluetooth::CodePathCounterKeyEnum::
              LE_AUDIO_ALLOWLIST_DEVICE_HEALTH_STATUS_BAD_INVALID_CSIS;
          break;
        default:
          LOG_ERROR("Metric unhandled %d", type);
          return;
      }
    } else {
      switch (type) {
        case LeAudioHealthDeviceStatType::VALID_DB:
        case LeAudioHealthDeviceStatType::VALID_CSIS:
          key = android::bluetooth::CodePathCounterKeyEnum::
              LE_AUDIO_NONALLOWLIST_DEVICE_HEALTH_STATUS_GOOD;
          break;
        case LeAudioHealthDeviceStatType::INVALID_DB:
          key = android::bluetooth::CodePathCounterKeyEnum::
              LE_AUDIO_NONALLOWLIST_DEVICE_HEALTH_STATUS_BAD_INVALID_DB;
          break;
        case LeAudioHealthDeviceStatType::INVALID_CSIS:
          key = android::bluetooth::CodePathCounterKeyEnum::
              LE_AUDIO_NONALLOWLIST_DEVICE_HEALTH_STATUS_BAD_INVALID_CSIS;
          break;
        default:
          LOG_ERROR("Metric unhandled %d", type);
          return;
      }
    }
    bluetooth::shim::CountCounterMetrics(key, 1);
  }

  void log_counter_metrics_for_group(LeAudioHealthGroupStatType type,
                                     bool in_allowlist) {
    LOG_DEBUG("in_allowlist: %d, type: %s", in_allowlist,
              ToString(type).c_str());
    android::bluetooth::CodePathCounterKeyEnum key;
    if (in_allowlist) {
      switch (type) {
        case LeAudioHealthGroupStatType::STREAM_CREATE_SUCCESS:
          key = android::bluetooth::CodePathCounterKeyEnum::
              LE_AUDIO_ALLOWLIST_GROUP_HEALTH_STATUS_GOOD;
          break;
        case LeAudioHealthGroupStatType::STREAM_CREATE_CIS_FAILED:
          key = android::bluetooth::CodePathCounterKeyEnum::
              LE_AUDIO_ALLOWLIST_GROUP_HEALTH_STATUS_BAD_ONCE_CIS_FAILED;
          break;
        case LeAudioHealthGroupStatType::STREAM_CREATE_SIGNALING_FAILED:
          key = android::bluetooth::CodePathCounterKeyEnum::
              LE_AUDIO_ALLOWLIST_GROUP_HEALTH_STATUS_BAD_ONCE_SIGNALING_FAILED;
          break;
        default:
          LOG_ERROR("Metric unhandled %d", type);
          return;
      }
    } else {
      switch (type) {
        case LeAudioHealthGroupStatType::STREAM_CREATE_SUCCESS:
          key = android::bluetooth::CodePathCounterKeyEnum::
              LE_AUDIO_NONALLOWLIST_GROUP_HEALTH_STATUS_GOOD;
          break;
        case LeAudioHealthGroupStatType::STREAM_CREATE_CIS_FAILED:
          key = android::bluetooth::CodePathCounterKeyEnum::
              LE_AUDIO_NONALLOWLIST_GROUP_HEALTH_STATUS_BAD_ONCE_CIS_FAILED;
          break;
        case LeAudioHealthGroupStatType::STREAM_CREATE_SIGNALING_FAILED:
          key = android::bluetooth::CodePathCounterKeyEnum::
              LE_AUDIO_NONALLOWLIST_GROUP_HEALTH_STATUS_BAD_ONCE_SIGNALING_FAILED;
          break;
        default:
          LOG_ERROR("Metric unhandled %d", type);
          return;
      }
    }
    bluetooth::shim::CountCounterMetrics(key, 1);
  }
};
}  // namespace le_audio

LeAudioHealthStatus* LeAudioHealthStatus::Get(void) {
  if (instance) {
    return instance;
  }
  instance = new LeAudioHealthStatusImpl();
  return instance;
}

void LeAudioHealthStatus::DebugDump(int fd) {
  if (instance) {
    instance->Dump(fd);
  }
}

void LeAudioHealthStatus::Cleanup(void) {
  if (!instance) {
    return;
  }
  auto ptr = instance;
  instance = nullptr;
  delete ptr;
}
