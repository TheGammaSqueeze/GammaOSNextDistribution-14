/*
 * Copyright (C) 2018 The Android Open Source Project
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

#define LOG_TAG "goog_gyro_direct"

#include <android/frameworks/sensorservice/1.0/ISensorManager.h>
#include <android/frameworks/sensorservice/1.0/types.h>
#include <android/hardware/sensors/1.0/types.h>
#include <hardware/sensors.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>

#include "goog_gralloc_wrapper.h"

#include "goog_gyro_direct.h"

namespace android {
namespace camera_sensor_listener {

using ::android::frameworks::sensorservice::V1_0::ISensorManager;
using ::android::frameworks::sensorservice::V1_0::Result;
using ::android::hardware::graphics::mapper::V3_0::IMapper;
using ::android::hardware::sensors::V1_0::RateLevel;
using ::android::hardware::sensors::V1_0::SensorFlagBits;
using ::android::hardware::sensors::V1_0::SensorsEventFormatOffset;
using ::android::hardware::sensors::V1_0::SensorType;

GoogGyroDirect::GoogGyroDirect(RateLevel rate_level,
                               size_t gyro_direct_buf_length)
    : gyro_direct_initialized_(false),
      gyro_direct_enabled_(false),
      gyro_direct_rate_level_(rate_level),
      gyro_direct_buf_length_(gyro_direct_buf_length),
      goog_gralloc_wrapper_ptr_(nullptr),
      gyro_direct_channel_native_buf_handle_(nullptr),
      gyro_direct_channel_addr_(nullptr) {
  goog_gralloc_wrapper_ptr_ = std::make_unique<GoogGrallocWrapper>();
}

GoogGyroDirect::~GoogGyroDirect() {
  if (gyro_direct_enabled_) {
    DisableDirectChannel();
  }
  if (gyro_direct_channel_addr_) {
    goog_gralloc_wrapper_ptr_->Unlock(gyro_direct_channel_native_buf_handle_);
  }
  if (gyro_direct_channel_native_buf_handle_) {
    goog_gralloc_wrapper_ptr_->FreeBuffer(
        gyro_direct_channel_native_buf_handle_);
  }
}

std::unique_ptr<GoogGyroDirect> GoogGyroDirect::Create(
    int64_t gyro_sampling_period_us, size_t event_queue_size) {
  // convert sampling period us to RateLevel.
  float sampling_rate =
      static_cast<float>(1e6) / static_cast<float>(gyro_sampling_period_us);
  RateLevel rate_level;
  if (sampling_rate < 110.f) {
    rate_level = RateLevel::NORMAL;
  } else if (sampling_rate >= 110.f && sampling_rate < 440.f) {
    rate_level = RateLevel::FAST;
  } else {
    rate_level = RateLevel::VERY_FAST;
  }

  std::unique_ptr<GoogGyroDirect> gyro_direct_ptr =
      std::unique_ptr<GoogGyroDirect>(
          new GoogGyroDirect(rate_level, event_queue_size));
  if (gyro_direct_ptr == nullptr) {
    ALOGE("%s %d failed to create GoogGyroDirect", __func__, __LINE__);
  } else {
    status_t result = gyro_direct_ptr->EnableDirectChannel();
    if (result != 0) {
      ALOGE("%s %d failed to enable GoogGyroDirect", __func__, __LINE__);
    } else {
      ALOGI("%s %d successfully enabled GoogGyroDirect", __func__, __LINE__);
    }
  }
  return gyro_direct_ptr;
}

status_t GoogGyroDirect::DisableDirectChannel() {
  if (gyro_direct_initialized_ && gyro_direct_enabled_) {
    gyro_direct_channel_->configure(sensor_info_.sensorHandle, RateLevel::STOP,
                                    [](const auto&, auto) {});
    gyro_direct_enabled_ = false;
  }
  return OK;
}

status_t GoogGyroDirect::EnableDirectChannel() {
  sp<ISensorManager> manager = ISensorManager::getService();
  if (manager == nullptr) {
    ALOGE("Cannot get ISensorManager");
    return UNKNOWN_ERROR;
  }
  bool find = false;
  manager->getDefaultSensor(SensorType::GYROSCOPE,
                            [&](const auto& info, auto result) {
                              if (result != Result::OK) {
                                ALOGE("Cannot find default gyroscope");
                                return;
                              }
                              sensor_info_ = info;
                              find = true;
                            });
  ALOGV("%s %d gyro sensor handle find %d", __func__, __LINE__, find);
  if (find == false) {
    gyro_direct_enabled_ = false;
    ALOGE("%s %d unable to find gyro sensor", __func__, __LINE__);
    return UNKNOWN_ERROR;
  }

  // Initialize gyro direct channel buffer.
  if (!gyro_direct_initialized_) {
    bool channel_supported =
        sensor_info_.flags & SensorFlagBits::DIRECT_CHANNEL_GRALLOC;
    if (!channel_supported) {
      ALOGE("%s %d Direct sesnor is not supported", __func__, __LINE__);
      return UNKNOWN_ERROR;
    }

    size_t buffer_size =
        static_cast<size_t>(SensorsEventFormatOffset::TOTAL_LENGTH) *
        gyro_direct_buf_length_;

    using android::hardware::graphics::common::V1_0::BufferUsage;
    using android::hardware::graphics::common::V1_2::PixelFormat;
    IMapper::BufferDescriptorInfo buf_desc_info = {
        .width = static_cast<uint32_t>(buffer_size),
        .height = 1,
        .layerCount = 1,
        .format = PixelFormat::BLOB,
        .usage = static_cast<uint64_t>(BufferUsage::SENSOR_DIRECT_DATA |
                                       BufferUsage::CPU_READ_OFTEN),
    };

    gyro_direct_channel_native_buf_handle_ =
        goog_gralloc_wrapper_ptr_->AllocateOneBuffer(buf_desc_info);
    if (gyro_direct_channel_native_buf_handle_ == nullptr) {
      ALOGE("%s %d Failed at allocating the channel native buffer", __func__,
            __LINE__);
      return NO_MEMORY;
    }

    IMapper::Rect region{0, 0, static_cast<int32_t>(buf_desc_info.width),
                         static_cast<int32_t>(buf_desc_info.height)};
    gyro_direct_channel_addr_ = goog_gralloc_wrapper_ptr_->Lock(
        gyro_direct_channel_native_buf_handle_, buf_desc_info.usage, region,
        /*fence=*/-1);
    if (gyro_direct_channel_addr_ == nullptr) {
      goog_gralloc_wrapper_ptr_->FreeBuffer(
          gyro_direct_channel_native_buf_handle_);
      gyro_direct_channel_native_buf_handle_ = nullptr;
      ALOGE("%s %d Failed at lock the gralloc buffer", __func__, __LINE__);
      return UNKNOWN_ERROR;
    }

    gyro_direct_initialized_ = true;
    manager->createGrallocDirectChannel(
        gyro_direct_channel_native_buf_handle_, buffer_size,
        [&](const auto& chan, auto result) {
          if (result != Result::OK) {
            gyro_direct_initialized_ = false;
            ALOGE("%s %d Failed to create gralloc direct channel", __func__,
                  __LINE__);
          } else {
            gyro_direct_channel_ = chan;
          }
        });
  }

  // configure gyro direct channel.
  gyro_direct_channel_->configure(
      sensor_info_.sensorHandle, gyro_direct_rate_level_,
      [&](const auto& t, auto result) {
        if (result != Result::OK) {
          ALOGE("%s %d Cannot configure direct report", __func__, __LINE__);
        }
      });

  gyro_direct_enabled_ = true;

  ALOGV("%s %d Direct sensor mdoe: %d %d", __func__, __LINE__,
        gyro_direct_initialized_, gyro_direct_enabled_);
  return OK;
}

void GoogGyroDirect::QueryGyroEventsBetweenTimestamps(
    int64_t start_time, int64_t end_time,
    std::vector<int64_t>* event_timestamps, std::vector<float>* motion_vector_x,
    std::vector<float>* motion_vector_y, std::vector<float>* motion_vector_z,
    std::vector<int64_t>* event_arrival_timestamps) const {
  event_timestamps->clear();
  motion_vector_x->clear();
  motion_vector_y->clear();
  motion_vector_z->clear();
  event_arrival_timestamps->clear();

  if (gyro_direct_channel_addr_ == nullptr) {
    return;
  }
  sensors_event_t* buffer_head_ptr =
      reinterpret_cast<sensors_event_t*>(gyro_direct_channel_addr_);

  // Copy shared buffer content to local buffer as lock is lacking.
  std::vector<sensors_event_t> event_vector;
  event_vector.insert(event_vector.begin(), buffer_head_ptr,
                      buffer_head_ptr + gyro_direct_buf_length_);

  int64_t event_arrival_time = elapsedRealtimeNano();
  int64_t earliest_timestamp = LLONG_MAX;
  int head_pos = -1;
  int num_events = event_vector.size();
  for (int i = 0; i < num_events; ++i) {
    if (event_vector[i].timestamp != 0 &&
        event_vector[i].timestamp < earliest_timestamp) {
      earliest_timestamp = event_vector[i].timestamp;
      head_pos = i;
    }
  }

  // Fill events within timestamps range to output vectors.
  if (head_pos == -1) {
    return;
  }
  for (int i = 0; i < num_events; ++i) {
    int index = (head_pos + i) % num_events;
    if (event_vector[index].timestamp <= start_time) {
      continue;
    }
    if (event_vector[index].timestamp > end_time) {
      break;
    }

    event_timestamps->push_back(event_vector[index].timestamp);
    motion_vector_x->push_back(event_vector[index].data[0]);
    motion_vector_y->push_back(event_vector[index].data[1]);
    motion_vector_z->push_back(event_vector[index].data[2]);
    event_arrival_timestamps->push_back(event_arrival_time);
  }
}

}  // namespace camera_sensor_listener
}  // namespace android
