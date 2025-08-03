/*
 * Copyright (C) 2012 The Android Open Source Project
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

//#define LOG_NDEBUG 0
//#define LOG_NNDEBUG 0
#include "system/graphics-base-v1.1.h"
#define LOG_TAG "EmulatedSensor"
#define ATRACE_TAG ATRACE_TAG_CAMERA

#ifdef LOG_NNDEBUG
#define ALOGVV(...) ALOGV(__VA_ARGS__)
#else
#define ALOGVV(...) ((void)0)
#endif

#include <android/hardware/graphics/common/1.2/types.h>
#include <cutils/properties.h>
#include <inttypes.h>
#include <libyuv.h>
#include <memory.h>
#include <system/camera_metadata.h>
#include <utils/Log.h>
#include <utils/Trace.h>

#include <cmath>
#include <cstdlib>

#include "EmulatedSensor.h"
#include "utils/ExifUtils.h"
#include "utils/HWLUtils.h"

namespace android {

using android::google_camera_hal::ErrorCode;
using google_camera_hal::HalCameraMetadata;
using google_camera_hal::MessageType;
using google_camera_hal::NotifyMessage;

using android::hardware::graphics::common::V1_2::Dataspace;

// Copied from ColorSpace.java (see Named)
enum ColorSpaceNamed {
  SRGB,
  LINEAR_SRGB,
  EXTENDED_SRGB,
  LINEAR_EXTENDED_SRGB,
  BT709,
  BT2020,
  DCI_P3,
  DISPLAY_P3,
  NTSC_1953,
  SMPTE_C,
  ADOBE_RGB,
  PRO_PHOTO_RGB,
  ACES,
  ACESCG,
  CIE_XYZ,
  CIE_LAB
};

const uint32_t EmulatedSensor::kRegularSceneHandshake = 1; // Scene handshake divider
const uint32_t EmulatedSensor::kReducedSceneHandshake = 2; // Scene handshake divider

// 1 us - 30 sec
const nsecs_t EmulatedSensor::kSupportedExposureTimeRange[2] = {1000LL,
                                                                30000000000LL};

// ~1/30 s - 30 sec
const nsecs_t EmulatedSensor::kSupportedFrameDurationRange[2] = {33331760LL,
                                                                 30000000000LL};

const int32_t EmulatedSensor::kSupportedSensitivityRange[2] = {100, 1600};
const int32_t EmulatedSensor::kDefaultSensitivity = 100;  // ISO
const nsecs_t EmulatedSensor::kDefaultExposureTime = ms2ns(15);
const nsecs_t EmulatedSensor::kDefaultFrameDuration = ms2ns(33);
// Deadline within we should return the results as soon as possible to
// avoid skewing the frame cycle due to external delays.
const nsecs_t EmulatedSensor::kReturnResultThreshod = 3 * kDefaultFrameDuration;

// Sensor defaults
const uint8_t EmulatedSensor::kSupportedColorFilterArrangement =
    ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB;
const uint32_t EmulatedSensor::kDefaultMaxRawValue = 4000;
const uint32_t EmulatedSensor::kDefaultBlackLevelPattern[4] = {1000, 1000, 1000,
                                                               1000};

const nsecs_t EmulatedSensor::kMinVerticalBlank = 10000L;

// Sensor sensitivity
const float EmulatedSensor::kSaturationVoltage = 0.520f;
const uint32_t EmulatedSensor::kSaturationElectrons = 2000;
const float EmulatedSensor::kVoltsPerLuxSecond = 0.100f;

const float EmulatedSensor::kElectronsPerLuxSecond =
    EmulatedSensor::kSaturationElectrons / EmulatedSensor::kSaturationVoltage *
    EmulatedSensor::kVoltsPerLuxSecond;

const float EmulatedSensor::kReadNoiseStddevBeforeGain = 1.177;  // in electrons
const float EmulatedSensor::kReadNoiseStddevAfterGain =
    2.100;  // in digital counts
const float EmulatedSensor::kReadNoiseVarBeforeGain =
    EmulatedSensor::kReadNoiseStddevBeforeGain *
    EmulatedSensor::kReadNoiseStddevBeforeGain;
const float EmulatedSensor::kReadNoiseVarAfterGain =
    EmulatedSensor::kReadNoiseStddevAfterGain *
    EmulatedSensor::kReadNoiseStddevAfterGain;

const uint32_t EmulatedSensor::kMaxRAWStreams = 1;
const uint32_t EmulatedSensor::kMaxProcessedStreams = 3;
const uint32_t EmulatedSensor::kMaxStallingStreams = 2;
const uint32_t EmulatedSensor::kMaxInputStreams = 1;

const uint32_t EmulatedSensor::kMaxLensShadingMapSize[2]{64, 64};
const int32_t EmulatedSensor::kFixedBitPrecision = 64;  // 6-bit
// In fixed-point math, saturation point of sensor after gain
const int32_t EmulatedSensor::kSaturationPoint = kFixedBitPrecision * 255;
const camera_metadata_rational EmulatedSensor::kNeutralColorPoint[3] = {
    {255, 1}, {255, 1}, {255, 1}};
const float EmulatedSensor::kGreenSplit = 1.f;  // No divergence
// Reduce memory usage by allowing only one buffer in sensor, one in jpeg
// compressor and one pending request to avoid stalls.
const uint8_t EmulatedSensor::kPipelineDepth = 3;

const camera_metadata_rational EmulatedSensor::kDefaultColorTransform[9] = {
    {1, 1}, {0, 1}, {0, 1}, {0, 1}, {1, 1}, {0, 1}, {0, 1}, {0, 1}, {1, 1}};
const float EmulatedSensor::kDefaultColorCorrectionGains[4] = {1.0f, 1.0f, 1.0f,
                                                               1.0f};

const float EmulatedSensor::kDefaultToneMapCurveRed[4] = {.0f, .0f, 1.f, 1.f};
const float EmulatedSensor::kDefaultToneMapCurveGreen[4] = {.0f, .0f, 1.f, 1.f};
const float EmulatedSensor::kDefaultToneMapCurveBlue[4] = {.0f, .0f, 1.f, 1.f};

// All XY matrix coefficients sourced from
// https://developer.android.com/reference/kotlin/android/graphics/ColorSpace.Named
// and XYZ coefficients calculated using the method found in
// ColorSpace.Rgb.computeXyzMatrix
struct XyzMatrix {
  float xR = 3.2406f;
  float yR = -1.5372f;
  float zR = -0.4986f;
  float xG = -0.9689f;
  float yG = 1.8758f;
  float zG = 0.0415f;
  float xB = 0.0557f;
  float yB = -0.2040f;
  float zB = 1.0570f;
};

static const XyzMatrix kSrgbXyzMatrix = {3.2406f,  -1.5372f, -0.4986f,
                                         -0.9689f, 1.8758f,  0.0415f,
                                         0.0557f,  -0.2040f, 1.0570f};

static const XyzMatrix kDisplayP3Matrix = {2.4931f,  -0.9316f, -0.4023f,
                                           -0.8291f, 1.7627f,  0.0234f,
                                           0.0361f,  -0.0761f, 0.9570f};

static const XyzMatrix kBt709Matrix = {3.2410f,  -1.5374f, -0.4986f,
                                       -0.9692f, 1.8760f,  0.0416f,
                                       0.0556f,  -0.2040f, 1.0570f};

static const XyzMatrix kBt2020Matrix = {1.7167f,  -0.3556f, -0.2534f,
                                        -0.6666f, 1.6164f,  0.0158f,
                                        0.0177f,  -0.0428f, 0.9421f};

/** A few utility functions for math, normal distributions */

// Take advantage of IEEE floating-point format to calculate an approximate
// square root. Accurate to within +-3.6%
float sqrtf_approx(float r) {
  // Modifier is based on IEEE floating-point representation; the
  // manipulations boil down to finding approximate log2, dividing by two, and
  // then inverting the log2. A bias is added to make the relative error
  // symmetric about the real answer.
  const int32_t modifier = 0x1FBB4000;

  int32_t r_i = *(int32_t*)(&r);
  r_i = (r_i >> 1) + modifier;

  return *(float*)(&r_i);
}

EmulatedSensor::EmulatedSensor() : Thread(false), got_vsync_(false) {
  gamma_table_sRGB_.resize(kSaturationPoint + 1);
  gamma_table_smpte170m_.resize(kSaturationPoint + 1);
  gamma_table_hlg_.resize(kSaturationPoint + 1);
  for (int32_t i = 0; i <= kSaturationPoint; i++) {
    gamma_table_sRGB_[i] = ApplysRGBGamma(i, kSaturationPoint);
    gamma_table_smpte170m_[i] = ApplySMPTE170MGamma(i, kSaturationPoint);
    gamma_table_hlg_[i] = ApplyHLGGamma(i, kSaturationPoint);
  }
}

EmulatedSensor::~EmulatedSensor() {
  ShutDown();
}

bool EmulatedSensor::AreCharacteristicsSupported(
    const SensorCharacteristics& characteristics) {
  if ((characteristics.width == 0) || (characteristics.height == 0)) {
    ALOGE("%s: Invalid sensor size %zux%zu", __FUNCTION__,
          characteristics.width, characteristics.height);
    return false;
  }

  if ((characteristics.full_res_width == 0) ||
      (characteristics.full_res_height == 0)) {
    ALOGE("%s: Invalid sensor full res size %zux%zu", __FUNCTION__,
          characteristics.full_res_width, characteristics.full_res_height);
    return false;
  }

  if (characteristics.is_10bit_dynamic_range_capable) {
    // We support only HLG10 at the moment
    const auto& hlg10_entry = characteristics.dynamic_range_profiles.find(
        ANDROID_REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_HLG10);
    if ((characteristics.dynamic_range_profiles.size() != 1) ||
        (hlg10_entry == characteristics.dynamic_range_profiles.end())) {
      ALOGE("%s: Only support for HLG10 is available!", __FUNCTION__);
      return false;
    }
  }

  if ((characteristics.exposure_time_range[0] >=
       characteristics.exposure_time_range[1]) ||
      ((characteristics.exposure_time_range[0] < kSupportedExposureTimeRange[0]) ||
       (characteristics.exposure_time_range[1] >
        kSupportedExposureTimeRange[1]))) {
    ALOGE("%s: Unsupported exposure range", __FUNCTION__);
    return false;
  }

  if ((characteristics.frame_duration_range[0] >=
       characteristics.frame_duration_range[1]) ||
      ((characteristics.frame_duration_range[0] <
        kSupportedFrameDurationRange[0]) ||
       (characteristics.frame_duration_range[1] >
        kSupportedFrameDurationRange[1]))) {
    ALOGE("%s: Unsupported frame duration range", __FUNCTION__);
    return false;
  }

  if ((characteristics.sensitivity_range[0] >=
       characteristics.sensitivity_range[1]) ||
      ((characteristics.sensitivity_range[0] < kSupportedSensitivityRange[0]) ||
       (characteristics.sensitivity_range[1] > kSupportedSensitivityRange[1])) ||
      (!((kDefaultSensitivity >= characteristics.sensitivity_range[0]) &&
         (kDefaultSensitivity <= characteristics.sensitivity_range[1])))) {
    ALOGE("%s: Unsupported sensitivity range", __FUNCTION__);
    return false;
  }

  if (characteristics.color_arangement != kSupportedColorFilterArrangement) {
    ALOGE("%s: Unsupported color arrangement!", __FUNCTION__);
    return false;
  }

  for (const auto& blackLevel : characteristics.black_level_pattern) {
    if (blackLevel >= characteristics.max_raw_value) {
      ALOGE("%s: Black level matches or exceeds max RAW value!", __FUNCTION__);
      return false;
    }
  }

  if ((characteristics.frame_duration_range[0] / characteristics.height) == 0) {
    ALOGE("%s: Zero row readout time!", __FUNCTION__);
    return false;
  }

  if (characteristics.max_raw_streams > kMaxRAWStreams) {
    ALOGE("%s: RAW streams maximum %u exceeds supported maximum %u",
          __FUNCTION__, characteristics.max_raw_streams, kMaxRAWStreams);
    return false;
  }

  if (characteristics.max_processed_streams > kMaxProcessedStreams) {
    ALOGE("%s: Processed streams maximum %u exceeds supported maximum %u",
          __FUNCTION__, characteristics.max_processed_streams,
          kMaxProcessedStreams);
    return false;
  }

  if (characteristics.max_stalling_streams > kMaxStallingStreams) {
    ALOGE("%s: Stalling streams maximum %u exceeds supported maximum %u",
          __FUNCTION__, characteristics.max_stalling_streams,
          kMaxStallingStreams);
    return false;
  }

  if (characteristics.max_input_streams > kMaxInputStreams) {
    ALOGE("%s: Input streams maximum %u exceeds supported maximum %u",
          __FUNCTION__, characteristics.max_input_streams, kMaxInputStreams);
    return false;
  }

  if ((characteristics.lens_shading_map_size[0] > kMaxLensShadingMapSize[0]) ||
      (characteristics.lens_shading_map_size[1] > kMaxLensShadingMapSize[1])) {
    ALOGE("%s: Lens shading map [%dx%d] exceeds supprorted maximum [%dx%d]",
          __FUNCTION__, characteristics.lens_shading_map_size[0],
          characteristics.lens_shading_map_size[1], kMaxLensShadingMapSize[0],
          kMaxLensShadingMapSize[1]);
    return false;
  }

  if (characteristics.max_pipeline_depth < kPipelineDepth) {
    ALOGE("%s: Pipeline depth %d smaller than supprorted minimum %d",
          __FUNCTION__, characteristics.max_pipeline_depth, kPipelineDepth);
    return false;
  }

  return true;
}

static void SplitStreamCombination(
    const StreamConfiguration& original_config,
    StreamConfiguration* default_mode_config,
    StreamConfiguration* max_resolution_mode_config,
    StreamConfiguration* input_stream_config) {
  // Go through the streams
  if (default_mode_config == nullptr || max_resolution_mode_config == nullptr ||
      input_stream_config == nullptr) {
    ALOGE("%s: Input stream / output stream configs are nullptr", __FUNCTION__);
    return;
  }
  for (const auto& stream : original_config.streams) {
    if (stream.stream_type == google_camera_hal::StreamType::kInput) {
      input_stream_config->streams.push_back(stream);
      continue;
    }
    if (stream.intended_for_default_resolution_mode) {
      default_mode_config->streams.push_back(stream);
    }
    if (stream.intended_for_max_resolution_mode) {
      max_resolution_mode_config->streams.push_back(stream);
    }
  }
}

bool EmulatedSensor::IsStreamCombinationSupported(
    uint32_t logical_id, const StreamConfiguration& config,
    StreamConfigurationMap& default_config_map,
    StreamConfigurationMap& max_resolution_config_map,
    const PhysicalStreamConfigurationMap& physical_map,
    const PhysicalStreamConfigurationMap& physical_map_max_resolution,
    const LogicalCharacteristics& sensor_chars) {
  StreamConfiguration default_mode_config, max_resolution_mode_config,
      input_stream_config;
  SplitStreamCombination(config, &default_mode_config,
                         &max_resolution_mode_config, &input_stream_config);

  return IsStreamCombinationSupported(logical_id, default_mode_config,
                                      default_config_map, physical_map,
                                      sensor_chars) &&
         IsStreamCombinationSupported(
             logical_id, max_resolution_mode_config, max_resolution_config_map,
             physical_map_max_resolution, sensor_chars, /*is_max_res*/ true) &&

         (IsStreamCombinationSupported(logical_id, input_stream_config,
                                       default_config_map, physical_map,
                                       sensor_chars) ||
          IsStreamCombinationSupported(
              logical_id, input_stream_config, max_resolution_config_map,
              physical_map_max_resolution, sensor_chars, /*is_max_res*/ true));
}

bool EmulatedSensor::IsStreamCombinationSupported(
    uint32_t logical_id, const StreamConfiguration& config,
    StreamConfigurationMap& config_map,
    const PhysicalStreamConfigurationMap& physical_map,
    const LogicalCharacteristics& sensor_chars, bool is_max_res) {
  uint32_t input_stream_count = 0;
  // Map from physical camera id to number of streams for that physical camera
  std::map<uint32_t, uint32_t> raw_stream_count;
  std::map<uint32_t, uint32_t> processed_stream_count;
  std::map<uint32_t, uint32_t> stalling_stream_count;

  // Only allow the stream configurations specified in
  // dynamicSizeStreamConfigurations.
  for (const auto& stream : config.streams) {
    bool is_dynamic_output =
        (stream.is_physical_camera_stream && stream.group_id != -1);
    if (stream.rotation != google_camera_hal::StreamRotation::kRotation0) {
      ALOGE("%s: Stream rotation: 0x%x not supported!", __FUNCTION__,
            stream.rotation);
      return false;
    }

    if (stream.stream_type == google_camera_hal::StreamType::kInput) {
      if (sensor_chars.at(logical_id).max_input_streams == 0) {
        ALOGE("%s: Input streams are not supported on this device!",
              __FUNCTION__);
        return false;
      }

      auto const& supported_outputs =
          config_map.GetValidOutputFormatsForInput(stream.format);
      if (supported_outputs.empty()) {
        ALOGE("%s: Input stream with format: 0x%x no supported on this device!",
              __FUNCTION__, stream.format);
        return false;
      }

      input_stream_count++;
    } else {
      if (stream.is_physical_camera_stream &&
          physical_map.find(stream.physical_camera_id) == physical_map.end()) {
        ALOGE("%s: Invalid physical camera id %d", __FUNCTION__,
              stream.physical_camera_id);
        return false;
      }

      if (is_dynamic_output) {
        auto dynamic_physical_output_formats =
            physical_map.at(stream.physical_camera_id)
                ->GetDynamicPhysicalStreamOutputFormats();
        if (dynamic_physical_output_formats.find(stream.format) ==
            dynamic_physical_output_formats.end()) {
          ALOGE("%s: Unsupported physical stream format %d", __FUNCTION__,
                stream.format);
          return false;
        }
      }

      if (stream.dynamic_profile !=
          ANDROID_REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_STANDARD) {
        const SensorCharacteristics& sensor_char =
            stream.is_physical_camera_stream
                ? sensor_chars.at(stream.physical_camera_id)
                : sensor_chars.at(logical_id);
        if (!sensor_char.is_10bit_dynamic_range_capable) {
          ALOGE("%s: 10-bit dynamic range output not supported on this device!",
                __FUNCTION__);
          return false;
        }

        if ((stream.format != HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED) &&
            (static_cast<android_pixel_format_v1_1_t>(stream.format) !=
             HAL_PIXEL_FORMAT_YCBCR_P010)) {
          ALOGE(
              "%s: 10-bit dynamic range profile 0x%x not supported on a non "
              "10-bit output stream"
              " pixel format 0x%x",
              __FUNCTION__, stream.dynamic_profile, stream.format);
          return false;
        }

        if ((static_cast<android_pixel_format_v1_1_t>(stream.format) ==
             HAL_PIXEL_FORMAT_YCBCR_P010) &&
            ((stream.data_space !=
              static_cast<android_dataspace_t>(Dataspace::BT2020_ITU_HLG)) &&
             (stream.data_space !=
              static_cast<android_dataspace_t>(Dataspace::BT2020_HLG)) &&
             (stream.data_space !=
              static_cast<android_dataspace_t>(Dataspace::UNKNOWN)))) {
          ALOGE(
              "%s: Unsupported stream data space 0x%x for 10-bit YUV "
              "output",
              __FUNCTION__, stream.data_space);
          return false;
        }
      }

      switch (stream.format) {
        case HAL_PIXEL_FORMAT_BLOB:
          if ((stream.data_space != HAL_DATASPACE_V0_JFIF) &&
              (stream.data_space != HAL_DATASPACE_UNKNOWN)) {
            ALOGE("%s: Unsupported Blob dataspace 0x%x", __FUNCTION__,
                  stream.data_space);
            return false;
          }
          if (stream.is_physical_camera_stream) {
            stalling_stream_count[stream.physical_camera_id]++;
          } else {
            for (const auto& p : physical_map) {
              stalling_stream_count[p.first]++;
            }
          }
          break;
        case HAL_PIXEL_FORMAT_RAW16: {
          const SensorCharacteristics& sensor_char =
              stream.is_physical_camera_stream
                  ? sensor_chars.at(stream.physical_camera_id)
                  : sensor_chars.at(logical_id);
          auto sensor_height =
              is_max_res ? sensor_char.full_res_height : sensor_char.height;
          auto sensor_width =
              is_max_res ? sensor_char.full_res_width : sensor_char.width;
          if (stream.height != sensor_height || stream.width != sensor_width) {
            ALOGE(
                "%s, RAW16 buffer height %d and width %d must match sensor "
                "height: %zu"
                " and width: %zu",
                __FUNCTION__, stream.height, stream.width, sensor_height,
                sensor_width);
            return false;
          }
          if (stream.is_physical_camera_stream) {
            raw_stream_count[stream.physical_camera_id]++;
          } else {
            for (const auto& p : physical_map) {
              raw_stream_count[p.first]++;
            }
          }
        } break;
        default:
          if (stream.is_physical_camera_stream) {
            processed_stream_count[stream.physical_camera_id]++;
          } else {
            for (const auto& p : physical_map) {
              processed_stream_count[p.first]++;
            }
          }
      }

      auto output_sizes =
          is_dynamic_output
              ? physical_map.at(stream.physical_camera_id)
                    ->GetDynamicPhysicalStreamOutputSizes(stream.format)
              : stream.is_physical_camera_stream
                    ? physical_map.at(stream.physical_camera_id)
                          ->GetOutputSizes(stream.format)
                    : config_map.GetOutputSizes(stream.format);

      auto stream_size = std::make_pair(stream.width, stream.height);
      if (output_sizes.find(stream_size) == output_sizes.end()) {
        ALOGE("%s: Stream with size %dx%d and format 0x%x is not supported!",
              __FUNCTION__, stream.width, stream.height, stream.format);
        return false;
      }
    }

    if (!sensor_chars.at(logical_id).support_stream_use_case) {
      if (stream.use_case != ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_DEFAULT) {
        ALOGE("%s: Camera device doesn't support non-default stream use case!",
              __FUNCTION__);
        return false;
      }
    } else if (stream.use_case >
               sensor_chars.at(logical_id).end_valid_stream_use_case) {
      ALOGE("%s: Stream with use case %d is not supported!", __FUNCTION__,
            stream.use_case);
      return false;
    } else if (stream.use_case !=
               ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_DEFAULT) {
      if (stream.use_case ==
              ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_STILL_CAPTURE) {
        if (stream.format != HAL_PIXEL_FORMAT_YCBCR_420_888 &&
            stream.format != HAL_PIXEL_FORMAT_BLOB) {
          ALOGE("%s: Stream with use case %d isn't compatible with format %d",
              __FUNCTION__, stream.use_case, stream.format);
          return false;
        }
      } else if ((stream.format == HAL_PIXEL_FORMAT_RAW16) ^
                 (stream.use_case ==
                  ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_CROPPED_RAW)) {
        // Either both stream use case == CROPPED_RAW and format == RAW16, or
        // stream use case != CROPPED_RAW and format != RAW16 for the
        // combination to be valid.
        ALOGE(
            "%s: Stream with use case CROPPED_RAW isn't compatible with non "
            "RAW_SENSOR formats",
            __FUNCTION__);
        return false;

      } else if (stream.format != HAL_PIXEL_FORMAT_YCBCR_420_888 &&
                 stream.format != HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED &&
                 stream.format != HAL_PIXEL_FORMAT_RAW16) {
        ALOGE("%s: Stream with use case %d isn't compatible with format %d",
              __FUNCTION__, stream.use_case, stream.format);
        return false;
      }
    }
  }

  for (const auto& raw_count : raw_stream_count) {
    unsigned int max_raw_streams =
        sensor_chars.at(raw_count.first).max_raw_streams +
        (is_max_res
             ? 1
             : 0);  // The extra raw stream is allowed for remosaic reprocessing.
    if (raw_count.second > max_raw_streams) {
      ALOGE("%s: RAW streams maximum %u exceeds supported maximum %u",
            __FUNCTION__, raw_count.second, max_raw_streams);
      return false;
    }
  }

  for (const auto& stalling_count : stalling_stream_count) {
    if (stalling_count.second >
        sensor_chars.at(stalling_count.first).max_stalling_streams) {
      ALOGE("%s: Stalling streams maximum %u exceeds supported maximum %u",
            __FUNCTION__, stalling_count.second,
            sensor_chars.at(stalling_count.first).max_stalling_streams);
      return false;
    }
  }

  for (const auto& processed_count : processed_stream_count) {
    if (processed_count.second >
        sensor_chars.at(processed_count.first).max_processed_streams) {
      ALOGE("%s: Processed streams maximum %u exceeds supported maximum %u",
            __FUNCTION__, processed_count.second,
            sensor_chars.at(processed_count.first).max_processed_streams);
      return false;
    }
  }

  if (input_stream_count > sensor_chars.at(logical_id).max_input_streams) {
    ALOGE("%s: Input stream maximum %u exceeds supported maximum %u",
          __FUNCTION__, input_stream_count,
          sensor_chars.at(logical_id).max_input_streams);
    return false;
  }

  // TODO: Check session parameters. For now assuming all combinations
  // are supported.

  return true;
}

status_t EmulatedSensor::StartUp(
    uint32_t logical_camera_id,
    std::unique_ptr<LogicalCharacteristics> logical_chars) {
  if (isRunning()) {
    return OK;
  }

  if (logical_chars.get() == nullptr) {
    return BAD_VALUE;
  }

  chars_ = std::move(logical_chars);
  auto device_chars = chars_->find(logical_camera_id);
  if (device_chars == chars_->end()) {
    ALOGE(
        "%s: Logical camera id: %u absent from logical camera characteristics!",
        __FUNCTION__, logical_camera_id);
    return BAD_VALUE;
  }

  for (const auto& it : *chars_) {
    if (!AreCharacteristicsSupported(it.second)) {
      ALOGE("%s: Sensor characteristics for camera id: %u not supported!",
            __FUNCTION__, it.first);
      return BAD_VALUE;
    }
  }

  logical_camera_id_ = logical_camera_id;
  scene_ = std::make_unique<EmulatedScene>(
      device_chars->second.full_res_width, device_chars->second.full_res_height,
      kElectronsPerLuxSecond, device_chars->second.orientation,
      device_chars->second.is_front_facing);
  jpeg_compressor_ = std::make_unique<JpegCompressor>();

  auto res = run(LOG_TAG, ANDROID_PRIORITY_URGENT_DISPLAY);
  if (res != OK) {
    ALOGE("Unable to start up sensor capture thread: %d", res);
  }

  return res;
}

status_t EmulatedSensor::ShutDown() {
  int res;
  res = requestExitAndWait();
  if (res != OK) {
    ALOGE("Unable to shut down sensor capture thread: %d", res);
  }
  return res;
}

void EmulatedSensor::SetCurrentRequest(
    std::unique_ptr<LogicalCameraSettings> logical_settings,
    std::unique_ptr<HwlPipelineResult> result,
    std::unique_ptr<HwlPipelineResult> partial_result,
    std::unique_ptr<Buffers> input_buffers,
    std::unique_ptr<Buffers> output_buffers) {
  Mutex::Autolock lock(control_mutex_);
  current_settings_ = std::move(logical_settings);
  current_result_ = std::move(result);
  current_input_buffers_ = std::move(input_buffers);
  current_output_buffers_ = std::move(output_buffers);
  partial_result_ = std::move(partial_result);
}

bool EmulatedSensor::WaitForVSyncLocked(nsecs_t reltime) {
  got_vsync_ = false;
  while (!got_vsync_) {
    auto res = vsync_.waitRelative(control_mutex_, reltime);
    if (res != OK && res != TIMED_OUT) {
      ALOGE("%s: Error waiting for VSync signal: %d", __FUNCTION__, res);
      return false;
    }
  }

  return got_vsync_;
}

bool EmulatedSensor::WaitForVSync(nsecs_t reltime) {
  Mutex::Autolock lock(control_mutex_);

  return WaitForVSyncLocked(reltime);
}

status_t EmulatedSensor::Flush() {
  Mutex::Autolock lock(control_mutex_);
  auto ret = WaitForVSyncLocked(kSupportedFrameDurationRange[1]);

  // First recreate the jpeg compressor. This will abort any ongoing processing
  // and flush any pending jobs.
  jpeg_compressor_ = std::make_unique<JpegCompressor>();

  // Then return any pending frames here
  if ((current_input_buffers_.get() != nullptr) &&
      (!current_input_buffers_->empty())) {
    current_input_buffers_->clear();
  }
  if ((current_output_buffers_.get() != nullptr) &&
      (!current_output_buffers_->empty())) {
    for (const auto& buffer : *current_output_buffers_) {
      buffer->stream_buffer.status = BufferStatus::kError;
    }

    if ((current_result_.get() != nullptr) &&
        (current_result_->result_metadata.get() != nullptr)) {
      if (current_output_buffers_->at(0)->callback.notify != nullptr) {
        NotifyMessage msg{
            .type = MessageType::kError,
            .message.error = {
                .frame_number = current_output_buffers_->at(0)->frame_number,
                .error_stream_id = -1,
                .error_code = ErrorCode::kErrorResult,
            }};

        current_output_buffers_->at(0)->callback.notify(
            current_result_->pipeline_id, msg);
      }
    }

    current_output_buffers_->clear();
  }

  return ret ? OK : TIMED_OUT;
}

nsecs_t EmulatedSensor::getSystemTimeWithSource(uint32_t timestamp_source) {
  if (timestamp_source == ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME) {
    return systemTime(SYSTEM_TIME_BOOTTIME);
  }
  return systemTime(SYSTEM_TIME_MONOTONIC);
}

bool EmulatedSensor::threadLoop() {
  ATRACE_CALL();
  /**
   * Sensor capture operation main loop.
   *
   */

  /**
   * Stage 1: Read in latest control parameters
   */
  std::unique_ptr<Buffers> next_buffers;
  std::unique_ptr<Buffers> next_input_buffer;
  std::unique_ptr<HwlPipelineResult> next_result;
  std::unique_ptr<HwlPipelineResult> partial_result;
  std::unique_ptr<LogicalCameraSettings> settings;
  HwlPipelineCallback callback = {
      .process_pipeline_result = nullptr,
      .process_pipeline_batch_result = nullptr,
      .notify = nullptr,
  };
  {
    Mutex::Autolock lock(control_mutex_);
    std::swap(settings, current_settings_);
    std::swap(next_buffers, current_output_buffers_);
    std::swap(next_input_buffer, current_input_buffers_);
    std::swap(next_result, current_result_);
    std::swap(partial_result, partial_result_);

    // Signal VSync for start of readout
    ALOGVV("Sensor VSync");
    got_vsync_ = true;
    vsync_.signal();
  }

  auto frame_duration = EmulatedSensor::kSupportedFrameDurationRange[0];
  auto exposure_time = EmulatedSensor::kSupportedExposureTimeRange[0];
  uint32_t timestamp_source = ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN;
  // Frame duration must always be the same among all physical devices
  if ((settings.get() != nullptr) && (!settings->empty())) {
    frame_duration = settings->begin()->second.frame_duration;
    exposure_time = settings->begin()->second.exposure_time;
    timestamp_source = settings->begin()->second.timestamp_source;
  }

  nsecs_t start_real_time = getSystemTimeWithSource(timestamp_source);
  // Stagefright cares about system time for timestamps, so base simulated
  // time on that.
  nsecs_t frame_end_real_time = start_real_time + frame_duration;

  /**
   * Stage 2: Capture new image
   */
  next_capture_time_ = frame_end_real_time;
  next_readout_time_ = frame_end_real_time + exposure_time;

  sensor_binning_factor_info_.clear();

  bool reprocess_request = false;
  if ((next_input_buffer.get() != nullptr) && (!next_input_buffer->empty())) {
    if (next_input_buffer->size() > 1) {
      ALOGW("%s: Reprocess supports only single input!", __FUNCTION__);
    }

      camera_metadata_ro_entry_t entry;
      auto ret =
          next_result->result_metadata->Get(ANDROID_SENSOR_TIMESTAMP, &entry);
      if ((ret == OK) && (entry.count == 1)) {
        next_capture_time_ = entry.data.i64[0];
      } else {
        ALOGW("%s: Reprocess timestamp absent!", __FUNCTION__);
      }

      ret = next_result->result_metadata->Get(ANDROID_SENSOR_EXPOSURE_TIME,
                                              &entry);
      if ((ret == OK) && (entry.count == 1)) {
        next_readout_time_ = next_capture_time_ + entry.data.i64[0];
      } else {
        next_readout_time_ = next_capture_time_;
      }

      reprocess_request = true;
  }

  if ((next_buffers != nullptr) && (settings != nullptr)) {
    callback = next_buffers->at(0)->callback;
    if (callback.notify != nullptr) {
      NotifyMessage msg{
          .type = MessageType::kShutter,
          .message.shutter = {
              .frame_number = next_buffers->at(0)->frame_number,
              .timestamp_ns = static_cast<uint64_t>(next_capture_time_),
              .readout_timestamp_ns =
                  static_cast<uint64_t>(next_readout_time_)}};
      callback.notify(next_result->pipeline_id, msg);
    }
    auto b = next_buffers->begin();
    while (b != next_buffers->end()) {
      auto device_settings = settings->find((*b)->camera_id);
      if (device_settings == settings->end()) {
        ALOGE("%s: Sensor settings absent for device: %d", __func__,
              (*b)->camera_id);
        b = next_buffers->erase(b);
        continue;
      }

      auto device_chars = chars_->find((*b)->camera_id);
      if (device_chars == chars_->end()) {
        ALOGE("%s: Sensor characteristics absent for device: %d", __func__,
              (*b)->camera_id);
        b = next_buffers->erase(b);
        continue;
      }

      sensor_binning_factor_info_[(*b)->camera_id].quad_bayer_sensor =
          device_chars->second.quad_bayer_sensor;

      ALOGVV("Starting next capture: Exposure: %" PRIu64 " ms, gain: %d",
             ns2ms(device_settings->second.exposure_time),
             device_settings->second.gain);

      scene_->Initialize(device_chars->second.full_res_width,
                         device_chars->second.full_res_height,
                         kElectronsPerLuxSecond);
      scene_->SetExposureDuration((float)device_settings->second.exposure_time /
                                  1e9);
      scene_->SetColorFilterXYZ(device_chars->second.color_filter.rX,
                                device_chars->second.color_filter.rY,
                                device_chars->second.color_filter.rZ,
                                device_chars->second.color_filter.grX,
                                device_chars->second.color_filter.grY,
                                device_chars->second.color_filter.grZ,
                                device_chars->second.color_filter.gbX,
                                device_chars->second.color_filter.gbY,
                                device_chars->second.color_filter.gbZ,
                                device_chars->second.color_filter.bX,
                                device_chars->second.color_filter.bY,
                                device_chars->second.color_filter.bZ);
      scene_->SetTestPattern(device_settings->second.test_pattern_mode ==
                             ANDROID_SENSOR_TEST_PATTERN_MODE_SOLID_COLOR);
      scene_->SetTestPatternData(device_settings->second.test_pattern_data);
      scene_->SetScreenRotation(device_settings->second.screen_rotation);

      uint32_t handshake_divider =
          (device_settings->second.video_stab ==
           ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_ON) ||
                  (device_settings->second.video_stab ==
                   ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION)
              ? kReducedSceneHandshake
              : kRegularSceneHandshake;
      scene_->CalculateScene(next_capture_time_, handshake_divider);

      (*b)->stream_buffer.status = BufferStatus::kOk;
      bool max_res_mode = device_settings->second.sensor_pixel_mode;
      sensor_binning_factor_info_[(*b)->camera_id].max_res_request =
          max_res_mode;
      switch ((*b)->format) {
        case PixelFormat::RAW16:
          sensor_binning_factor_info_[(*b)->camera_id].has_raw_stream = true;
          if (!sensor_binning_factor_info_[(*b)->camera_id]
                   .has_cropped_raw_stream &&
              (*b)->use_case ==
                  ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_CROPPED_RAW) {
            sensor_binning_factor_info_[(*b)->camera_id].has_cropped_raw_stream =
                true;
          }
          break;
        default:
          sensor_binning_factor_info_[(*b)->camera_id].has_non_raw_stream = true;
      }

      // TODO: remove hack. Implement RAW -> YUV / JPEG reprocessing http://b/192382904
      bool treat_as_reprocess =
          (device_chars->second.quad_bayer_sensor && reprocess_request &&
           (*next_input_buffer->begin())->format == PixelFormat::RAW16)
              ? false
              : reprocess_request;
      ProcessType process_type = treat_as_reprocess ? REPROCESS
                                 : (device_settings->second.edge_mode ==
                                    ANDROID_EDGE_MODE_HIGH_QUALITY)
                                     ? HIGH_QUALITY
                                     : REGULAR;

      if ((*b)->color_space !=
          ANDROID_REQUEST_AVAILABLE_COLOR_SPACE_PROFILES_MAP_UNSPECIFIED) {
        CalculateRgbRgbMatrix((*b)->color_space, device_chars->second);
      }

      switch ((*b)->format) {
        case PixelFormat::RAW16:
          if (!reprocess_request) {
            uint64_t min_full_res_raw_size =
                2 * device_chars->second.full_res_width *
                device_chars->second.full_res_height;
            uint64_t min_default_raw_size =
                2 * device_chars->second.width * device_chars->second.height;
            bool default_mode_for_qb =
                device_chars->second.quad_bayer_sensor && !max_res_mode;
            size_t buffer_size = (*b)->plane.img.buffer_size;
            if (default_mode_for_qb) {
              if (buffer_size < min_default_raw_size) {
                ALOGE(
                    "%s: Output buffer size too small for RAW capture in "
                    "default "
                    "mode, "
                    "expected %" PRIu64 ", got %zu, for camera id %d",
                    __FUNCTION__, min_default_raw_size, buffer_size,
                    (*b)->camera_id);
                (*b)->stream_buffer.status = BufferStatus::kError;
                break;
              }
            } else if (buffer_size < min_full_res_raw_size) {
              ALOGE(
                  "%s: Output buffer size too small for RAW capture in max res "
                  "mode, "
                  "expected %" PRIu64 ", got %zu, for camera id %d",
                  __FUNCTION__, min_full_res_raw_size, buffer_size,
                  (*b)->camera_id);
              (*b)->stream_buffer.status = BufferStatus::kError;
              break;
            }
            if (default_mode_for_qb) {
              if (device_settings->second.zoom_ratio > 2.0f &&
                  ((*b)->use_case ==
                   ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_CROPPED_RAW)) {
                sensor_binning_factor_info_[(*b)->camera_id]
                    .raw_in_sensor_zoom_applied = true;
                CaptureRawInSensorZoom(
                    (*b)->plane.img.img, (*b)->plane.img.stride_in_bytes,
                    device_settings->second.gain, device_chars->second);

              } else {
                CaptureRawBinned(
                    (*b)->plane.img.img, (*b)->plane.img.stride_in_bytes,
                    device_settings->second.gain, device_chars->second);
              }
            } else {
              CaptureRawFullRes(
                  (*b)->plane.img.img, (*b)->plane.img.stride_in_bytes,
                  device_settings->second.gain, device_chars->second);
            }
          } else {
            if (!device_chars->second.quad_bayer_sensor) {
              ALOGE(
                  "%s: Reprocess requests with output format %x no supported!",
                  __FUNCTION__, (*b)->format);
              (*b)->stream_buffer.status = BufferStatus::kError;
              break;
            }
            // Remosaic the RAW input buffer
            if ((*next_input_buffer->begin())->width != (*b)->width ||
                (*next_input_buffer->begin())->height != (*b)->height) {
              ALOGE(
                  "%s: RAW16 input dimensions %dx%d don't match output buffer "
                  "dimensions %dx%d",
                  __FUNCTION__, (*next_input_buffer->begin())->width,
                  (*next_input_buffer->begin())->height, (*b)->width,
                  (*b)->height);
              (*b)->stream_buffer.status = BufferStatus::kError;
              break;
            }
            ALOGV("%s remosaic Raw16 Image", __FUNCTION__);
            RemosaicRAW16Image(
                (uint16_t*)(*next_input_buffer->begin())->plane.img.img,
                (uint16_t*)(*b)->plane.img.img, (*b)->plane.img.stride_in_bytes,
                device_chars->second);
          }
          break;
        case PixelFormat::RGB_888:
          if (!reprocess_request) {
            CaptureRGB((*b)->plane.img.img, (*b)->width, (*b)->height,
                       (*b)->plane.img.stride_in_bytes, RGBLayout::RGB,
                       device_settings->second.gain, (*b)->color_space,
                       device_chars->second);
          } else {
            ALOGE("%s: Reprocess requests with output format %x no supported!",
                  __FUNCTION__, (*b)->format);
            (*b)->stream_buffer.status = BufferStatus::kError;
          }
          break;
        case PixelFormat::RGBA_8888:
          if (!reprocess_request) {
            CaptureRGB((*b)->plane.img.img, (*b)->width, (*b)->height,
                       (*b)->plane.img.stride_in_bytes, RGBLayout::RGBA,
                       device_settings->second.gain, (*b)->color_space,
                       device_chars->second);
          } else {
            ALOGE("%s: Reprocess requests with output format %x no supported!",
                  __FUNCTION__, (*b)->format);
            (*b)->stream_buffer.status = BufferStatus::kError;
          }
          break;
        case PixelFormat::BLOB:
          if ((*b)->dataSpace == HAL_DATASPACE_V0_JFIF) {
            YUV420Frame yuv_input{
                .width = treat_as_reprocess
                             ? (*next_input_buffer->begin())->width
                             : 0,
                .height = treat_as_reprocess
                              ? (*next_input_buffer->begin())->height
                              : 0,
                .planes = treat_as_reprocess
                              ? (*next_input_buffer->begin())->plane.img_y_crcb
                              : YCbCrPlanes{}};
            auto jpeg_input = std::make_unique<JpegYUV420Input>();
            jpeg_input->width = (*b)->width;
            jpeg_input->height = (*b)->height;
            jpeg_input->color_space = (*b)->color_space;
            auto img =
                new uint8_t[(jpeg_input->width * jpeg_input->height * 3) / 2];
            jpeg_input->yuv_planes = {
                .img_y = img,
                .img_cb = img + jpeg_input->width * jpeg_input->height,
                .img_cr = img + (jpeg_input->width * jpeg_input->height * 5) / 4,
                .y_stride = jpeg_input->width,
                .cbcr_stride = jpeg_input->width / 2,
                .cbcr_step = 1};
            jpeg_input->buffer_owner = true;
            YUV420Frame yuv_output{.width = jpeg_input->width,
                                   .height = jpeg_input->height,
                                   .planes = jpeg_input->yuv_planes};

            bool rotate = device_settings->second.rotate_and_crop ==
                          ANDROID_SCALER_ROTATE_AND_CROP_90;
            auto ret = ProcessYUV420(yuv_input, yuv_output,
                                     device_settings->second.gain, process_type,
                                     device_settings->second.zoom_ratio, rotate,
                                     (*b)->color_space, device_chars->second);
            if (ret != 0) {
              (*b)->stream_buffer.status = BufferStatus::kError;
              break;
            }

            auto jpeg_job = std::make_unique<JpegYUV420Job>();
            jpeg_job->exif_utils = std::unique_ptr<ExifUtils>(
                ExifUtils::Create(device_chars->second));
            jpeg_job->input = std::move(jpeg_input);
            // If jpeg compression is successful, then the jpeg compressor
            // must set the corresponding status.
            (*b)->stream_buffer.status = BufferStatus::kError;
            std::swap(jpeg_job->output, *b);
            jpeg_job->result_metadata =
                HalCameraMetadata::Clone(next_result->result_metadata.get());

            Mutex::Autolock lock(control_mutex_);
            jpeg_compressor_->QueueYUV420(std::move(jpeg_job));
          } else {
            ALOGE("%s: Format %x with dataspace %x is TODO", __FUNCTION__,
                  (*b)->format, (*b)->dataSpace);
            (*b)->stream_buffer.status = BufferStatus::kError;
          }
          break;
        case PixelFormat::YCRCB_420_SP:
        case PixelFormat::YCBCR_420_888: {
          YUV420Frame yuv_input{
              .width =
                  treat_as_reprocess ? (*next_input_buffer->begin())->width : 0,
              .height =
                  treat_as_reprocess ? (*next_input_buffer->begin())->height : 0,
              .planes = treat_as_reprocess
                            ? (*next_input_buffer->begin())->plane.img_y_crcb
                            : YCbCrPlanes{}};
          YUV420Frame yuv_output{.width = (*b)->width,
                                 .height = (*b)->height,
                                 .planes = (*b)->plane.img_y_crcb};
          bool rotate = device_settings->second.rotate_and_crop ==
                        ANDROID_SCALER_ROTATE_AND_CROP_90;
          auto ret =
              ProcessYUV420(yuv_input, yuv_output, device_settings->second.gain,
                            process_type, device_settings->second.zoom_ratio,
                            rotate, (*b)->color_space, device_chars->second);
          if (ret != 0) {
            (*b)->stream_buffer.status = BufferStatus::kError;
          }
        } break;
        case PixelFormat::Y16:
          if (!reprocess_request) {
            if ((*b)->dataSpace == HAL_DATASPACE_DEPTH) {
              CaptureDepth((*b)->plane.img.img, device_settings->second.gain,
                           (*b)->width, (*b)->height,
                           (*b)->plane.img.stride_in_bytes,
                           device_chars->second);
            } else {
              ALOGE("%s: Format %x with dataspace %x is TODO", __FUNCTION__,
                    (*b)->format, (*b)->dataSpace);
              (*b)->stream_buffer.status = BufferStatus::kError;
            }
          } else {
            ALOGE("%s: Reprocess requests with output format %x no supported!",
                  __FUNCTION__, (*b)->format);
            (*b)->stream_buffer.status = BufferStatus::kError;
          }
          break;
        case PixelFormat::YCBCR_P010:
            if (!reprocess_request) {
              bool rotate = device_settings->second.rotate_and_crop ==
                            ANDROID_SCALER_ROTATE_AND_CROP_90;
              YUV420Frame yuv_input{};
              YUV420Frame yuv_output{.width = (*b)->width,
                                     .height = (*b)->height,
                                     .planes = (*b)->plane.img_y_crcb};
              ProcessYUV420(yuv_input, yuv_output, device_settings->second.gain,
                            process_type, device_settings->second.zoom_ratio,
                            rotate, (*b)->color_space, device_chars->second);
            } else {
              ALOGE(
                  "%s: Reprocess requests with output format %x no supported!",
                  __FUNCTION__, (*b)->format);
              (*b)->stream_buffer.status = BufferStatus::kError;
            }
            break;
        default:
          ALOGE("%s: Unknown format %x, no output", __FUNCTION__, (*b)->format);
          (*b)->stream_buffer.status = BufferStatus::kError;
          break;
      }

      b = next_buffers->erase(b);
    }
  }

  if (reprocess_request) {
    auto input_buffer = next_input_buffer->begin();
    while (input_buffer != next_input_buffer->end()) {
      (*input_buffer++)->stream_buffer.status = BufferStatus::kOk;
    }
    next_input_buffer->clear();
  }

  nsecs_t work_done_real_time = getSystemTimeWithSource(timestamp_source);
  // Returning the results at this point is not entirely correct from timing
  // perspective. Under ideal conditions where 'ReturnResults' completes
  // in less than 'time_accuracy' we need to return the results after the
  // frame cycle expires. However under real conditions various system
  // components like SurfaceFlinger, Encoder, LMK etc. could be consuming most
  // of the resources and the duration of "ReturnResults" can get comparable to
  // 'kDefaultFrameDuration'. This will skew the frame cycle and can result in
  // potential frame drops. To avoid this scenario when we are running under
  // tight deadlines (less than 'kReturnResultThreshod') try to return the
  // results immediately. In all other cases with more relaxed deadlines
  // the occasional bump during 'ReturnResults' should not have any
  // noticeable effect.
  if ((work_done_real_time + kReturnResultThreshod) > frame_end_real_time) {
    ReturnResults(callback, std::move(settings), std::move(next_result),
                  reprocess_request, std::move(partial_result));
  }

  work_done_real_time = getSystemTimeWithSource(timestamp_source);
  ALOGVV("Sensor vertical blanking interval");
  const nsecs_t time_accuracy = 2e6;  // 2 ms of imprecision is ok
  if (work_done_real_time < frame_end_real_time - time_accuracy) {
    timespec t;
    t.tv_sec = (frame_end_real_time - work_done_real_time) / 1000000000L;
    t.tv_nsec = (frame_end_real_time - work_done_real_time) % 1000000000L;

    int ret;
    do {
      ret = nanosleep(&t, &t);
    } while (ret != 0);
  }

  ReturnResults(callback, std::move(settings), std::move(next_result),
                reprocess_request, std::move(partial_result));

  return true;
};

void EmulatedSensor::ReturnResults(
    HwlPipelineCallback callback,
    std::unique_ptr<LogicalCameraSettings> settings,
    std::unique_ptr<HwlPipelineResult> result, bool reprocess_request,
    std::unique_ptr<HwlPipelineResult> partial_result) {
  if ((callback.process_pipeline_result != nullptr) &&
      (result.get() != nullptr) && (result->result_metadata.get() != nullptr)) {
    auto logical_settings = settings->find(logical_camera_id_);
    if (logical_settings == settings->end()) {
      ALOGE("%s: Logical camera id: %u not found in settings!", __FUNCTION__,
            logical_camera_id_);
      return;
    }
    auto device_chars = chars_->find(logical_camera_id_);
    if (device_chars == chars_->end()) {
      ALOGE("%s: Sensor characteristics absent for device: %d", __func__,
            logical_camera_id_);
      return;
    }
    result->result_metadata->Set(ANDROID_SENSOR_TIMESTAMP, &next_capture_time_,
                                 1);

    camera_metadata_ro_entry_t lensEntry;
    auto lensRet = result->result_metadata->Get(
        ANDROID_STATISTICS_LENS_INTRINSIC_SAMPLES, &lensEntry);
    if ((lensRet == OK) && (lensEntry.count > 0)) {
      result->result_metadata->Set(ANDROID_STATISTICS_LENS_INTRINSIC_TIMESTAMPS,
                                   &next_capture_time_, 1);
    }

    uint8_t raw_binned_factor_used = false;
    if (sensor_binning_factor_info_.find(logical_camera_id_) !=
        sensor_binning_factor_info_.end()) {
      auto& info = sensor_binning_factor_info_[logical_camera_id_];
      // Logical stream was included in the request
      if (!reprocess_request && info.quad_bayer_sensor && info.max_res_request &&
          info.has_raw_stream && !info.has_non_raw_stream) {
        raw_binned_factor_used = true;
      }
      result->result_metadata->Set(ANDROID_SENSOR_RAW_BINNING_FACTOR_USED,
                                   &raw_binned_factor_used, 1);
      if (info.has_cropped_raw_stream) {
        if (info.raw_in_sensor_zoom_applied) {
          result->result_metadata->Set(
              ANDROID_SCALER_RAW_CROP_REGION,
              device_chars->second.raw_crop_region_zoomed, 4);

        } else {
          result->result_metadata->Set(
              ANDROID_SCALER_RAW_CROP_REGION,
              device_chars->second.raw_crop_region_unzoomed, 4);
        }
      }
    }
    if (logical_settings->second.lens_shading_map_mode ==
        ANDROID_STATISTICS_LENS_SHADING_MAP_MODE_ON) {
      if ((device_chars->second.lens_shading_map_size[0] > 0) &&
          (device_chars->second.lens_shading_map_size[1] > 0)) {
        // Perfect lens, no actual shading needed.
        std::vector<float> lens_shading_map(
            device_chars->second.lens_shading_map_size[0] *
                device_chars->second.lens_shading_map_size[1] * 4,
            1.f);

        result->result_metadata->Set(ANDROID_STATISTICS_LENS_SHADING_MAP,
                                     lens_shading_map.data(),
                                     lens_shading_map.size());
      }
    }
    if (logical_settings->second.report_video_stab) {
      result->result_metadata->Set(ANDROID_CONTROL_VIDEO_STABILIZATION_MODE,
                                   &logical_settings->second.video_stab, 1);
    }
    if (logical_settings->second.report_edge_mode) {
      result->result_metadata->Set(ANDROID_EDGE_MODE,
                                   &logical_settings->second.edge_mode, 1);
    }
    if (logical_settings->second.report_neutral_color_point) {
      result->result_metadata->Set(ANDROID_SENSOR_NEUTRAL_COLOR_POINT,
                                   kNeutralColorPoint,
                                   ARRAY_SIZE(kNeutralColorPoint));
    }
    if (logical_settings->second.report_green_split) {
      result->result_metadata->Set(ANDROID_SENSOR_GREEN_SPLIT, &kGreenSplit, 1);
    }
    if (logical_settings->second.report_noise_profile) {
      CalculateAndAppendNoiseProfile(
          logical_settings->second.gain,
          GetBaseGainFactor(device_chars->second.max_raw_value),
          result->result_metadata.get());
    }
    if (logical_settings->second.report_rotate_and_crop) {
      result->result_metadata->Set(ANDROID_SCALER_ROTATE_AND_CROP,
          &logical_settings->second.rotate_and_crop, 1);
    }

    if (!result->physical_camera_results.empty()) {
      for (auto& it : result->physical_camera_results) {
        auto physical_settings = settings->find(it.first);
        if (physical_settings == settings->end()) {
          ALOGE("%s: Physical settings for camera id: %u are absent!",
                __FUNCTION__, it.first);
          continue;
        }
        uint8_t raw_binned_factor_used = false;
        if (sensor_binning_factor_info_.find(it.first) !=
            sensor_binning_factor_info_.end()) {
          auto& info = sensor_binning_factor_info_[it.first];
          // physical stream was included in the request
          if (!reprocess_request && info.quad_bayer_sensor &&
              info.max_res_request && info.has_raw_stream &&
              !info.has_non_raw_stream) {
            raw_binned_factor_used = true;
          }
          it.second->Set(ANDROID_SENSOR_RAW_BINNING_FACTOR_USED,
                         &raw_binned_factor_used, 1);
        }
        // Sensor timestamp for all physical devices must be the same.
        it.second->Set(ANDROID_SENSOR_TIMESTAMP, &next_capture_time_, 1);
        if (physical_settings->second.report_neutral_color_point) {
          it.second->Set(ANDROID_SENSOR_NEUTRAL_COLOR_POINT, kNeutralColorPoint,
                         ARRAY_SIZE(kNeutralColorPoint));
        }
        if (physical_settings->second.report_green_split) {
          it.second->Set(ANDROID_SENSOR_GREEN_SPLIT, &kGreenSplit, 1);
        }
        if (physical_settings->second.report_noise_profile) {
          auto device_chars = chars_->find(it.first);
          if (device_chars == chars_->end()) {
            ALOGE("%s: Sensor characteristics absent for device: %d", __func__,
                  it.first);
          }
          CalculateAndAppendNoiseProfile(
              physical_settings->second.gain,
              GetBaseGainFactor(device_chars->second.max_raw_value),
              it.second.get());
        }
      }
    }

    // Partial result count for partial result is set to a value
    // only when partial results are supported
    if (partial_result->partial_result != 0) {
      callback.process_pipeline_result(std::move(partial_result));
    }
    callback.process_pipeline_result(std::move(result));
  }
}

void EmulatedSensor::CalculateAndAppendNoiseProfile(
    float gain /*in ISO*/, float base_gain_factor,
    HalCameraMetadata* result /*out*/) {
  if (result != nullptr) {
    float total_gain = gain / 100.0 * base_gain_factor;
    float noise_var_gain = total_gain * total_gain;
    float read_noise_var =
        kReadNoiseVarBeforeGain * noise_var_gain + kReadNoiseVarAfterGain;
    // Noise profile is the same across all 4 CFA channels
    double noise_profile[2 * 4] = {
        noise_var_gain, read_noise_var, noise_var_gain, read_noise_var,
        noise_var_gain, read_noise_var, noise_var_gain, read_noise_var};
    result->Set(ANDROID_SENSOR_NOISE_PROFILE, noise_profile,
                ARRAY_SIZE(noise_profile));
  }
}

EmulatedScene::ColorChannels EmulatedSensor::GetQuadBayerColor(uint32_t x,
                                                               uint32_t y) {
  // Row within larger set of quad bayer filter
  uint32_t row_mod = y % 4;
  // Column within larger set of quad bayer filter
  uint32_t col_mod = x % 4;

  // Row is within the left quadrants of a quad bayer sensor
  if (row_mod < 2) {
    if (col_mod < 2) {
      return EmulatedScene::ColorChannels::R;
    }
    return EmulatedScene::ColorChannels::Gr;
  } else {
    if (col_mod < 2) {
      return EmulatedScene::ColorChannels::Gb;
    }
    return EmulatedScene::ColorChannels::B;
  }
}

void EmulatedSensor::RemosaicQuadBayerBlock(uint16_t* img_in, uint16_t* img_out,
                                            int xstart, int ystart,
                                            int row_stride_in_bytes) {
  uint32_t quad_block_copy_idx_map[16] = {0, 2, 1, 3, 8,  10, 6,  11,
                                          4, 9, 5, 7, 12, 14, 13, 15};
  uint16_t quad_block_copy[16];
  uint32_t i = 0;
  for (uint32_t row = 0; row < 4; row++) {
    uint16_t* quad_bayer_row =
        img_in + (ystart + row) * (row_stride_in_bytes / 2) + xstart;
    for (uint32_t j = 0; j < 4; j++, i++) {
      quad_block_copy[i] = quad_bayer_row[j];
    }
  }

  for (uint32_t row = 0; row < 4; row++) {
    uint16_t* regular_bayer_row =
        img_out + (ystart + row) * (row_stride_in_bytes / 2) + xstart;
    for (uint32_t j = 0; j < 4; j++, i++) {
      uint32_t idx = quad_block_copy_idx_map[row + 4 * j];
      regular_bayer_row[j] = quad_block_copy[idx];
    }
  }
}

status_t EmulatedSensor::RemosaicRAW16Image(uint16_t* img_in, uint16_t* img_out,
                                            size_t row_stride_in_bytes,
                                            const SensorCharacteristics& chars) {
  if (chars.full_res_width % 2 != 0 || chars.full_res_height % 2 != 0) {
    ALOGE(
        "%s RAW16 Image with quad CFA, height %zu and width %zu, not multiples "
        "of 4",
        __FUNCTION__, chars.full_res_height, chars.full_res_width);
    return BAD_VALUE;
  }
  for (uint32_t i = 0; i < chars.full_res_width; i += 4) {
    for (uint32_t j = 0; j < chars.full_res_height; j += 4) {
      RemosaicQuadBayerBlock(img_in, img_out, i, j, row_stride_in_bytes);
    }
  }
  return OK;
}

void EmulatedSensor::CaptureRawBinned(uint8_t* img, size_t row_stride_in_bytes,
                                      uint32_t gain,
                                      const SensorCharacteristics& chars) {
  CaptureRaw(img, row_stride_in_bytes, gain, chars, /*in_sensor_zoom*/ false,
             /*binned*/ true);
  return;
}

void EmulatedSensor::CaptureRawInSensorZoom(uint8_t* img,
                                            size_t row_stride_in_bytes,
                                            uint32_t gain,
                                            const SensorCharacteristics& chars) {
  CaptureRaw(img, row_stride_in_bytes, gain, chars, /*in_sensor_zoom*/ true,
             /*binned*/ false);
  return;
}

void EmulatedSensor::CaptureRawFullRes(uint8_t* img, size_t row_stride_in_bytes,
                                       uint32_t gain,
                                       const SensorCharacteristics& chars) {
  CaptureRaw(img, row_stride_in_bytes, gain, chars, /*inSensorZoom*/ false,
             /*binned*/ false);
  return;
}

void EmulatedSensor::CaptureRaw(uint8_t* img, size_t row_stride_in_bytes,
                                uint32_t gain,
                                const SensorCharacteristics& chars,
                                bool in_sensor_zoom, bool binned) {
  ATRACE_CALL();
  if (in_sensor_zoom && binned) {
    ALOGE("%s: Can't perform in-sensor zoom in binned mode", __FUNCTION__);
    return;
  }
  float total_gain = gain / 100.0 * GetBaseGainFactor(chars.max_raw_value);
  float noise_var_gain = total_gain * total_gain;
  float read_noise_var =
      kReadNoiseVarBeforeGain * noise_var_gain + kReadNoiseVarAfterGain;

  scene_->SetReadoutPixel(0, 0);
  // RGGB
  int bayer_select[4] = {EmulatedScene::R, EmulatedScene::Gr, EmulatedScene::Gb,
                         EmulatedScene::B};
  const float raw_zoom_ratio = in_sensor_zoom ? 2.0f : 1.0f;
  unsigned int image_width =
      in_sensor_zoom || binned ? chars.width : chars.full_res_width;
  unsigned int image_height =
      in_sensor_zoom || binned ? chars.height : chars.full_res_height;
  const float norm_left_top = 0.5f - 0.5f / raw_zoom_ratio;
  for (unsigned int out_y = 0; out_y < image_height; out_y++) {
    int* bayer_row = bayer_select + (out_y & 0x1) * 2;
    uint16_t* px = (uint16_t*)img + out_y * (row_stride_in_bytes / 2);

    float norm_y = out_y / (image_height * raw_zoom_ratio);
    int y = static_cast<int>(chars.full_res_height * (norm_left_top + norm_y));
    y = std::min(std::max(y, 0), (int)chars.full_res_height - 1);

    for (unsigned int out_x = 0; out_x < image_width; out_x++) {
      int color_idx = chars.quad_bayer_sensor && !(in_sensor_zoom || binned)
                          ? GetQuadBayerColor(out_x, out_y)
                          : bayer_row[out_x & 0x1];
      float norm_x = out_x / (image_width * raw_zoom_ratio);
      int x = static_cast<int>(chars.full_res_width * (norm_left_top + norm_x));
      x = std::min(std::max(x, 0), (int)chars.full_res_width - 1);

      uint32_t electron_count;
      scene_->SetReadoutPixel(x, y);
      electron_count = scene_->GetPixelElectrons()[color_idx];

      // TODO: Better pixel saturation curve?
      electron_count = (electron_count < kSaturationElectrons)
                           ? electron_count
                           : kSaturationElectrons;

      // TODO: Better A/D saturation curve?
      uint16_t raw_count = electron_count * total_gain;
      raw_count =
          (raw_count < chars.max_raw_value) ? raw_count : chars.max_raw_value;

      // Calculate noise value
      // TODO: Use more-correct Gaussian instead of uniform noise
      float photon_noise_var = electron_count * noise_var_gain;
      float noise_stddev = sqrtf_approx(read_noise_var + photon_noise_var);
      // Scaled to roughly match gaussian/uniform noise stddev
      float noise_sample = rand_r(&rand_seed_) * (2.5 / (1.0 + RAND_MAX)) - 1.25;

      raw_count += chars.black_level_pattern[color_idx];
      raw_count += noise_stddev * noise_sample;

      *px++ = raw_count;
    }
    // TODO: Handle this better
    // simulatedTime += mRowReadoutTime;
  }
  ALOGVV("Raw sensor image captured");
}

void EmulatedSensor::CaptureRGB(uint8_t* img, uint32_t width, uint32_t height,
                                uint32_t stride, RGBLayout layout,
                                uint32_t gain, int32_t color_space,
                                const SensorCharacteristics& chars) {
  ATRACE_CALL();
  float total_gain = gain / 100.0 * GetBaseGainFactor(chars.max_raw_value);
  // In fixed-point math, calculate total scaling from electrons to 8bpp
  int scale64x = 64 * total_gain * 255 / chars.max_raw_value;
  uint32_t inc_h = ceil((float)chars.full_res_width / width);
  uint32_t inc_v = ceil((float)chars.full_res_height / height);

  for (unsigned int y = 0, outy = 0; y < chars.full_res_height;
       y += inc_v, outy++) {
    scene_->SetReadoutPixel(0, y);
    uint8_t* px = img + outy * stride;
    for (unsigned int x = 0; x < chars.full_res_width; x += inc_h) {
      uint32_t r_count, g_count, b_count;
      // TODO: Perfect demosaicing is a cheat
      const uint32_t* pixel = scene_->GetPixelElectrons();
      r_count = pixel[EmulatedScene::R] * scale64x;
      g_count = pixel[EmulatedScene::Gr] * scale64x;
      b_count = pixel[EmulatedScene::B] * scale64x;

      if (color_space !=
          ANDROID_REQUEST_AVAILABLE_COLOR_SPACE_PROFILES_MAP_UNSPECIFIED) {
        RgbToRgb(&r_count, &g_count, &b_count);
      }

      uint8_t r = r_count < 255 * 64 ? r_count / 64 : 255;
      uint8_t g = g_count < 255 * 64 ? g_count / 64 : 255;
      uint8_t b = b_count < 255 * 64 ? b_count / 64 : 255;
      switch (layout) {
        case RGB:
          *px++ = r;
          *px++ = g;
          *px++ = b;
          break;
        case RGBA:
          *px++ = r;
          *px++ = g;
          *px++ = b;
          *px++ = 255;
          break;
        case ARGB:
          *px++ = 255;
          *px++ = r;
          *px++ = g;
          *px++ = b;
          break;
        default:
          ALOGE("%s: RGB layout: %d not supported", __FUNCTION__, layout);
          return;
      }
      for (unsigned int j = 1; j < inc_h; j++) scene_->GetPixelElectrons();
    }
  }
  ALOGVV("RGB sensor image captured");
}

void EmulatedSensor::CaptureYUV420(YCbCrPlanes yuv_layout, uint32_t width,
                                   uint32_t height, uint32_t gain,
                                   float zoom_ratio, bool rotate,
                                   int32_t color_space,
                                   const SensorCharacteristics& chars) {
  ATRACE_CALL();
  float total_gain = gain / 100.0 * GetBaseGainFactor(chars.max_raw_value);
  // Using fixed-point math with 6 bits of fractional precision.
  // In fixed-point math, calculate total scaling from electrons to 8bpp
  const int scale64x =
      kFixedBitPrecision * total_gain * 255 / chars.max_raw_value;
  // Fixed-point coefficients for RGB-YUV transform
  // Based on JFIF RGB->YUV transform.
  // Cb/Cr offset scaled by 64x twice since they're applied post-multiply
  const int rgb_to_y[] = {19, 37, 7};
  const int rgb_to_cb[] = {-10, -21, 32, 524288};
  const int rgb_to_cr[] = {32, -26, -5, 524288};
  // Scale back to 8bpp non-fixed-point
  const int scale_out = 64;
  const int scale_out_sq = scale_out * scale_out;  // after multiplies

  // inc = how many pixels to skip while reading every next pixel
  const float aspect_ratio = static_cast<float>(width) / height;

  // precalculate normalized coordinates and dimensions
  const float norm_left_top = 0.5f - 0.5f / zoom_ratio;
  const float norm_rot_top = norm_left_top;
  const float norm_width = 1 / zoom_ratio;
  const float norm_rot_width = norm_width / aspect_ratio;
  const float norm_rot_height = norm_width;
  const float norm_rot_left =
      norm_left_top + (norm_width + norm_rot_width) * 0.5f;

  for (unsigned int out_y = 0; out_y < height; out_y++) {
    uint8_t* px_y = yuv_layout.img_y + out_y * yuv_layout.y_stride;
    uint8_t* px_cb = yuv_layout.img_cb + (out_y / 2) * yuv_layout.cbcr_stride;
    uint8_t* px_cr = yuv_layout.img_cr + (out_y / 2) * yuv_layout.cbcr_stride;

    for (unsigned int out_x = 0; out_x < width; out_x++) {
      int x, y;
      float norm_x = out_x / (width * zoom_ratio);
      float norm_y = out_y / (height * zoom_ratio);
      if (rotate) {
        x = static_cast<int>(chars.full_res_width *
                             (norm_rot_left - norm_y * norm_rot_width));
        y = static_cast<int>(chars.full_res_height *
                             (norm_rot_top + norm_x * norm_rot_height));
      } else {
        x = static_cast<int>(chars.full_res_width * (norm_left_top + norm_x));
        y = static_cast<int>(chars.full_res_height * (norm_left_top + norm_y));
      }
      x = std::min(std::max(x, 0), (int)chars.full_res_width - 1);
      y = std::min(std::max(y, 0), (int)chars.full_res_height - 1);
      scene_->SetReadoutPixel(x, y);

      uint32_t r_count, g_count, b_count;
      // TODO: Perfect demosaicing is a cheat
      const uint32_t* pixel = rotate ? scene_->GetPixelElectronsColumn()
                                     : scene_->GetPixelElectrons();
      r_count = pixel[EmulatedScene::R] * scale64x;
      g_count = pixel[EmulatedScene::Gr] * scale64x;
      b_count = pixel[EmulatedScene::B] * scale64x;

      if (color_space !=
          ANDROID_REQUEST_AVAILABLE_COLOR_SPACE_PROFILES_MAP_UNSPECIFIED) {
        RgbToRgb(&r_count, &g_count, &b_count);
      }

      r_count = r_count < kSaturationPoint ? r_count : kSaturationPoint;
      g_count = g_count < kSaturationPoint ? g_count : kSaturationPoint;
      b_count = b_count < kSaturationPoint ? b_count : kSaturationPoint;

      // Gamma correction
      r_count = GammaTable(r_count, color_space);
      g_count = GammaTable(g_count, color_space);
      b_count = GammaTable(b_count, color_space);

      uint8_t y8 = (rgb_to_y[0] * r_count + rgb_to_y[1] * g_count +
                    rgb_to_y[2] * b_count) /
                   scale_out_sq;
      if (yuv_layout.bytesPerPixel == 1) {
        *px_y = y8;
      } else if (yuv_layout.bytesPerPixel == 2) {
        *(reinterpret_cast<uint16_t*>(px_y)) = htole16(y8 << 8);
      } else {
        ALOGE("%s: Unsupported bytes per pixel value: %zu", __func__,
              yuv_layout.bytesPerPixel);
        return;
      }
      px_y += yuv_layout.bytesPerPixel;

      if (out_y % 2 == 0 && out_x % 2 == 0) {
        uint8_t cb8 = (rgb_to_cb[0] * r_count + rgb_to_cb[1] * g_count +
                       rgb_to_cb[2] * b_count + rgb_to_cb[3]) /
                      scale_out_sq;
        uint8_t cr8 = (rgb_to_cr[0] * r_count + rgb_to_cr[1] * g_count +
                       rgb_to_cr[2] * b_count + rgb_to_cr[3]) /
                      scale_out_sq;
        if (yuv_layout.bytesPerPixel == 1) {
          *px_cb = cb8;
          *px_cr = cr8;
        } else if (yuv_layout.bytesPerPixel == 2) {
          *(reinterpret_cast<uint16_t*>(px_cb)) = htole16(cb8 << 8);
          *(reinterpret_cast<uint16_t*>(px_cr)) = htole16(cr8 << 8);
        } else {
          ALOGE("%s: Unsupported bytes per pixel value: %zu", __func__,
                yuv_layout.bytesPerPixel);
          return;
        }
        px_cr += yuv_layout.cbcr_step;
        px_cb += yuv_layout.cbcr_step;
      }
    }
  }
  ALOGVV("YUV420 sensor image captured");
}

void EmulatedSensor::CaptureDepth(uint8_t* img, uint32_t gain, uint32_t width,
                                  uint32_t height, uint32_t stride,
                                  const SensorCharacteristics& chars) {
  ATRACE_CALL();
  float total_gain = gain / 100.0 * GetBaseGainFactor(chars.max_raw_value);
  // In fixed-point math, calculate scaling factor to 13bpp millimeters
  int scale64x = 64 * total_gain * 8191 / chars.max_raw_value;
  uint32_t inc_h = ceil((float)chars.full_res_width / width);
  uint32_t inc_v = ceil((float)chars.full_res_height / height);

  for (unsigned int y = 0, out_y = 0; y < chars.full_res_height;
       y += inc_v, out_y++) {
    scene_->SetReadoutPixel(0, y);
    uint16_t* px = (uint16_t*)(img + (out_y * stride));
    for (unsigned int x = 0; x < chars.full_res_width; x += inc_h) {
      uint32_t depth_count;
      // TODO: Make up real depth scene instead of using green channel
      // as depth
      const uint32_t* pixel = scene_->GetPixelElectrons();
      depth_count = pixel[EmulatedScene::Gr] * scale64x;

      *px++ = depth_count < 8191 * 64 ? depth_count / 64 : 0;
      for (unsigned int j = 1; j < inc_h; j++) scene_->GetPixelElectrons();
    }
    // TODO: Handle this better
    // simulatedTime += mRowReadoutTime;
  }
  ALOGVV("Depth sensor image captured");
}

status_t EmulatedSensor::ProcessYUV420(const YUV420Frame& input,
                                       const YUV420Frame& output, uint32_t gain,
                                       ProcessType process_type,
                                       float zoom_ratio, bool rotate_and_crop,
                                       int32_t color_space,
                                       const SensorCharacteristics& chars) {
  ATRACE_CALL();
  size_t input_width, input_height;
  YCbCrPlanes input_planes, output_planes;
  std::vector<uint8_t> temp_yuv, temp_output_uv, temp_input_uv;

  // Overwrite HIGH_QUALITY to REGULAR for Emulator if property
  // ro.boot.qemu.camera_hq_edge_processing is false;
  if (process_type == HIGH_QUALITY &&
      !property_get_bool("ro.boot.qemu.camera_hq_edge_processing", true)) {
    process_type = REGULAR;
  }

  size_t bytes_per_pixel = output.planes.bytesPerPixel;
  switch (process_type) {
    case HIGH_QUALITY:
      CaptureYUV420(output.planes, output.width, output.height, gain,
                    zoom_ratio, rotate_and_crop, color_space, chars);
      return OK;
    case REPROCESS:
      input_width = input.width;
      input_height = input.height;
      input_planes = input.planes;

      // libyuv only supports planar YUV420 during scaling.
      // Split the input U/V plane in separate planes if needed.
      if (input_planes.cbcr_step == 2) {
        temp_input_uv.resize(input_width * input_height / 2);
        auto temp_uv_buffer = temp_input_uv.data();
        input_planes.img_cb = temp_uv_buffer;
        input_planes.img_cr = temp_uv_buffer + (input_width * input_height) / 4;
        input_planes.cbcr_stride = input_width / 2;
        if (input.planes.img_cb < input.planes.img_cr) {
          libyuv::SplitUVPlane(input.planes.img_cb, input.planes.cbcr_stride,
                               input_planes.img_cb, input_planes.cbcr_stride,
                               input_planes.img_cr, input_planes.cbcr_stride,
                               input_width / 2, input_height / 2);
        } else {
          libyuv::SplitUVPlane(input.planes.img_cr, input.planes.cbcr_stride,
                               input_planes.img_cr, input_planes.cbcr_stride,
                               input_planes.img_cb, input_planes.cbcr_stride,
                               input_width / 2, input_height / 2);
        }
      }
      break;
    case REGULAR:
    default:
      // Generate the smallest possible frame with the expected AR and
      // then scale using libyuv.
      float aspect_ratio = static_cast<float>(output.width) / output.height;
      zoom_ratio = std::max(1.f, zoom_ratio);
      input_width = EmulatedScene::kSceneWidth * aspect_ratio;
      input_height = EmulatedScene::kSceneHeight;
      temp_yuv.reserve((input_width * input_height * 3 * bytes_per_pixel) / 2);
      auto temp_yuv_buffer = temp_yuv.data();
      input_planes = {
          .img_y = temp_yuv_buffer,
          .img_cb =
              temp_yuv_buffer + input_width * input_height * bytes_per_pixel,
          .img_cr = temp_yuv_buffer +
                    (input_width * input_height * bytes_per_pixel * 5) / 4,
          .y_stride = static_cast<uint32_t>(input_width * bytes_per_pixel),
          .cbcr_stride =
              static_cast<uint32_t>(input_width * bytes_per_pixel) / 2,
          .cbcr_step = 1,
          .bytesPerPixel = bytes_per_pixel};
      CaptureYUV420(input_planes, input_width, input_height, gain, zoom_ratio,
                    rotate_and_crop, color_space, chars);
  }

  output_planes = output.planes;
  // libyuv only supports planar YUV420 during scaling.
  // Treat the output UV space as planar first and then
  // interleave in the second step.
  if (output_planes.cbcr_step == 2) {
    temp_output_uv.resize(output.width * output.height * bytes_per_pixel / 2);
    auto temp_uv_buffer = temp_output_uv.data();
    output_planes.img_cb = temp_uv_buffer;
    output_planes.img_cr =
        temp_uv_buffer + output.width * output.height * bytes_per_pixel / 4;
    output_planes.cbcr_stride = output.width * bytes_per_pixel / 2;
  }

  // NOTE: libyuv takes strides in pixels, not bytes.
  int ret = 0;
  if (bytes_per_pixel == 2) {
    ret = I420Scale_16((const uint16_t*)input_planes.img_y,
                       input_planes.y_stride / bytes_per_pixel,
                       (const uint16_t*)input_planes.img_cb,
                       input_planes.cbcr_stride / bytes_per_pixel,
                       (const uint16_t*)input_planes.img_cr,
                       input_planes.cbcr_stride / bytes_per_pixel, input_width,
                       input_height, (uint16_t*)output_planes.img_y,
                       output_planes.y_stride / bytes_per_pixel,
                       (uint16_t*)output_planes.img_cb,
                       output_planes.cbcr_stride / bytes_per_pixel,
                       (uint16_t*)output_planes.img_cr,
                       output_planes.cbcr_stride / bytes_per_pixel,
                       output.width, output.height, libyuv::kFilterNone);
  } else {
    ret = I420Scale(input_planes.img_y, input_planes.y_stride,
                    input_planes.img_cb, input_planes.cbcr_stride,
                    input_planes.img_cr, input_planes.cbcr_stride, input_width,
                    input_height, output_planes.img_y, output_planes.y_stride,
                    output_planes.img_cb, output_planes.cbcr_stride,
                    output_planes.img_cr, output_planes.cbcr_stride,
                    output.width, output.height, libyuv::kFilterNone);
  }
  if (ret != 0) {
    ALOGE("%s: Failed during YUV scaling: %d", __FUNCTION__, ret);
    return ret;
  }

  // Merge U/V Planes for the interleaved case
  if (output_planes.cbcr_step == 2) {
    if (output.planes.img_cb < output.planes.img_cr) {
      if (bytes_per_pixel == 2) {
        libyuv::MergeUVPlane_16((const uint16_t*)output_planes.img_cb,
                                output_planes.cbcr_stride / bytes_per_pixel,
                                (const uint16_t*)output_planes.img_cr,
                                output_planes.cbcr_stride / bytes_per_pixel,
                                (uint16_t*)output.planes.img_cb,
                                output.planes.cbcr_stride / bytes_per_pixel,
                                output.width / 2, output.height / 2,
                                /*depth*/ 16);
      } else {
        libyuv::MergeUVPlane(output_planes.img_cb, output_planes.cbcr_stride,
                             output_planes.img_cr, output_planes.cbcr_stride,
                             output.planes.img_cb, output.planes.cbcr_stride,
                             output.width / 2, output.height / 2);
      }
    } else {
      if (bytes_per_pixel == 2) {
        libyuv::MergeUVPlane_16((const uint16_t*)output_planes.img_cr,
                                output_planes.cbcr_stride / bytes_per_pixel,
                                (const uint16_t*)output_planes.img_cb,
                                output_planes.cbcr_stride / bytes_per_pixel,
                                (uint16_t*)output.planes.img_cr,
                                output.planes.cbcr_stride / bytes_per_pixel,
                                output.width / 2, output.height / 2,
                                /*depth*/ 16);
      } else {
        libyuv::MergeUVPlane(output_planes.img_cr, output_planes.cbcr_stride,
                             output_planes.img_cb, output_planes.cbcr_stride,
                             output.planes.img_cr, output.planes.cbcr_stride,
                             output.width / 2, output.height / 2);
      }
    }
  }

  return ret;
}

int32_t EmulatedSensor::ApplysRGBGamma(int32_t value, int32_t saturation) {
  float n_value = (static_cast<float>(value) / saturation);
  n_value = (n_value <= 0.0031308f)
                ? n_value * 12.92f
                : 1.055f * pow(n_value, 0.4166667f) - 0.055f;
  return n_value * saturation;
}

int32_t EmulatedSensor::ApplySMPTE170MGamma(int32_t value, int32_t saturation) {
  float n_value = (static_cast<float>(value) / saturation);
  n_value = (n_value <= 0.018f) ? n_value * 4.5f
                                : 1.099f * pow(n_value, 0.45f) - 0.099f;
  return n_value * saturation;
}

int32_t EmulatedSensor::ApplyST2084Gamma(int32_t value, int32_t saturation) {
  float n_value = (static_cast<float>(value) / saturation);
  float c2 = 32.f * 2413.f / 4096.f;
  float c3 = 32.f * 2392.f / 4096.f;
  float c1 = c3 - c2 + 1.f;
  float m = 128.f * 2523.f / 4096.f;
  float n = 0.25f * 2610.f / 4096.f;
  n_value = pow((c1 + c2 * pow(n_value, n)) / (1 + c3 * pow(n_value, n)), m);
  return n_value * saturation;
}

int32_t EmulatedSensor::ApplyHLGGamma(int32_t value, int32_t saturation) {
  float n_value = (static_cast<float>(value) / saturation);
  // The full HLG gamma curve has additional parameters for n_value > 1, but n_value
  // in the emulated camera is always <= 1 due to lack of HDR display features.
  n_value = 0.5f * pow(n_value, 0.5f);
  return n_value * saturation;
}

int32_t EmulatedSensor::GammaTable(int32_t value, int32_t color_space) {
  switch (color_space) {
    case ColorSpaceNamed::BT709:
      return gamma_table_smpte170m_[value];
    case ColorSpaceNamed::BT2020:
      return gamma_table_hlg_[value];  // Assume HLG
    case ColorSpaceNamed::DISPLAY_P3:
    case ColorSpaceNamed::SRGB:
    default:
      return gamma_table_sRGB_[value];
  }

  return 0;
}

void EmulatedSensor::RgbToRgb(uint32_t* r_count, uint32_t* g_count,
                              uint32_t* b_count) {
  uint32_t r = *r_count;
  uint32_t g = *g_count;
  uint32_t b = *b_count;
  *r_count = (uint32_t)std::max(
      r * rgb_rgb_matrix_.rR + g * rgb_rgb_matrix_.gR + b * rgb_rgb_matrix_.bR,
      0.0f);
  *g_count = (uint32_t)std::max(
      r * rgb_rgb_matrix_.rG + g * rgb_rgb_matrix_.gG + b * rgb_rgb_matrix_.bG,
      0.0f);
  *b_count = (uint32_t)std::max(
      r * rgb_rgb_matrix_.rB + g * rgb_rgb_matrix_.gB + b * rgb_rgb_matrix_.bB,
      0.0f);
}

void EmulatedSensor::CalculateRgbRgbMatrix(int32_t color_space,
                                           const SensorCharacteristics& chars) {
  const XyzMatrix* xyzMatrix;
  switch (color_space) {
    case ColorSpaceNamed::DISPLAY_P3:
      xyzMatrix = &kDisplayP3Matrix;
      break;
    case ColorSpaceNamed::BT709:
      xyzMatrix = &kBt709Matrix;
      break;
    case ColorSpaceNamed::BT2020:
      xyzMatrix = &kBt2020Matrix;
      break;
    case ColorSpaceNamed::SRGB:
    default:
      xyzMatrix = &kSrgbXyzMatrix;
      break;
  }

  rgb_rgb_matrix_.rR = xyzMatrix->xR * chars.forward_matrix.rX +
                       xyzMatrix->yR * chars.forward_matrix.rY +
                       xyzMatrix->zR * chars.forward_matrix.rZ;
  rgb_rgb_matrix_.gR = xyzMatrix->xR * chars.forward_matrix.gX +
                       xyzMatrix->yR * chars.forward_matrix.gY +
                       xyzMatrix->zR * chars.forward_matrix.gZ;
  rgb_rgb_matrix_.bR = xyzMatrix->xR * chars.forward_matrix.bX +
                       xyzMatrix->yR * chars.forward_matrix.bY +
                       xyzMatrix->zR * chars.forward_matrix.bZ;
  rgb_rgb_matrix_.rG = xyzMatrix->xG * chars.forward_matrix.rX +
                       xyzMatrix->yG * chars.forward_matrix.rY +
                       xyzMatrix->zG * chars.forward_matrix.rZ;
  rgb_rgb_matrix_.gG = xyzMatrix->xG * chars.forward_matrix.gX +
                       xyzMatrix->yG * chars.forward_matrix.gY +
                       xyzMatrix->zG * chars.forward_matrix.gZ;
  rgb_rgb_matrix_.bG = xyzMatrix->xG * chars.forward_matrix.bX +
                       xyzMatrix->yG * chars.forward_matrix.bY +
                       xyzMatrix->zG * chars.forward_matrix.bZ;
  rgb_rgb_matrix_.rB = xyzMatrix->xB * chars.forward_matrix.rX +
                       xyzMatrix->yB * chars.forward_matrix.rY +
                       xyzMatrix->zB * chars.forward_matrix.rZ;
  rgb_rgb_matrix_.gB = xyzMatrix->xB * chars.forward_matrix.gX +
                       xyzMatrix->yB * chars.forward_matrix.gY +
                       xyzMatrix->zB * chars.forward_matrix.gZ;
  rgb_rgb_matrix_.bB = xyzMatrix->xB * chars.forward_matrix.bX +
                       xyzMatrix->yB * chars.forward_matrix.bY +
                       xyzMatrix->zB * chars.forward_matrix.bZ;
}

}  // namespace android
