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

/**
 * This class is a simple simulation of a typical CMOS cellphone imager chip,
 * which outputs 12-bit Bayer-mosaic raw images.
 *
 * Unlike most real image sensors, this one's native color space is linear sRGB.
 *
 * The sensor is abstracted as operating as a pipeline 3 stages deep;
 * conceptually, each frame to be captured goes through these three stages. The
 * processing step for the sensor is marked off by vertical sync signals, which
 * indicate the start of readout of the oldest frame. The interval between
 * processing steps depends on the frame duration of the frame currently being
 * captured. The stages are 1) configure, 2) capture, and 3) readout. During
 * configuration, the sensor's registers for settings such as exposure time,
 * frame duration, and gain are set for the next frame to be captured. In stage
 * 2, the image data for the frame is actually captured by the sensor. Finally,
 * in stage 3, the just-captured data is read out and sent to the rest of the
 * system.
 *
 * The sensor is assumed to be rolling-shutter, so low-numbered rows of the
 * sensor are exposed earlier in time than larger-numbered rows, with the time
 * offset between each row being equal to the row readout time.
 *
 * The characteristics of this sensor don't correspond to any actual sensor,
 * but are not far off typical sensors.
 *
 * Example timing diagram, with three frames:
 *  Frame 0-1: Frame duration 50 ms, exposure time 20 ms.
 *  Frame   2: Frame duration 75 ms, exposure time 65 ms.
 * Legend:
 *   C = update sensor registers for frame
 *   v = row in reset (vertical blanking interval)
 *   E = row capturing image data
 *   R = row being read out
 *   | = vertical sync signal
 *time(ms)|   0          55        105       155            230     270
 * Frame 0|   :configure : capture : readout :              :       :
 *  Row # | ..|CCCC______|_________|_________|              :       :
 *      0 |   :\          \vvvvvEEEER         \             :       :
 *    500 |   : \          \vvvvvEEEER         \            :       :
 *   1000 |   :  \          \vvvvvEEEER         \           :       :
 *   1500 |   :   \          \vvvvvEEEER         \          :       :
 *   2000 |   :    \__________\vvvvvEEEER_________\         :       :
 * Frame 1|   :           configure  capture      readout   :       :
 *  Row # |   :          |CCCC_____|_________|______________|       :
 *      0 |   :          :\         \vvvvvEEEER              \      :
 *    500 |   :          : \         \vvvvvEEEER              \     :
 *   1000 |   :          :  \         \vvvvvEEEER              \    :
 *   1500 |   :          :   \         \vvvvvEEEER              \   :
 *   2000 |   :          :    \_________\vvvvvEEEER______________\  :
 * Frame 2|   :          :          configure     capture    readout:
 *  Row # |   :          :         |CCCC_____|______________|_______|...
 *      0 |   :          :         :\         \vEEEEEEEEEEEEER       \
 *    500 |   :          :         : \         \vEEEEEEEEEEEEER       \
 *   1000 |   :          :         :  \         \vEEEEEEEEEEEEER       \
 *   1500 |   :          :         :   \         \vEEEEEEEEEEEEER       \
 *   2000 |   :          :         :    \_________\vEEEEEEEEEEEEER_______\
 */

#ifndef HW_EMULATOR_CAMERA2_SENSOR_H
#define HW_EMULATOR_CAMERA2_SENSOR_H

#include <android/hardware/graphics/common/1.2/types.h>
#include <hwl_types.h>

#include <algorithm>
#include <functional>

#include "Base.h"
#include "EmulatedScene.h"
#include "JpegCompressor.h"
#include "utils/Mutex.h"
#include "utils/StreamConfigurationMap.h"
#include "utils/Thread.h"
#include "utils/Timers.h"

namespace android {

using google_camera_hal::ColorSpaceProfile;
using google_camera_hal::DynamicRangeProfile;
using google_camera_hal::HwlPipelineCallback;
using google_camera_hal::HwlPipelineResult;
using google_camera_hal::StreamConfiguration;

using hardware::graphics::common::V1_2::Dataspace;

/*
 * Default to sRGB with D65 white point
 */
struct ColorFilterXYZ {
  float rX = 3.2406f;
  float rY = -1.5372f;
  float rZ = -0.4986f;
  float grX = -0.9689f;
  float grY = 1.8758f;
  float grZ = 0.0415f;
  float gbX = -0.9689f;
  float gbY = 1.8758f;
  float gbZ = 0.0415f;
  float bX = 0.0557f;
  float bY = -0.2040f;
  float bZ = 1.0570f;
};

struct ForwardMatrix {
  float rX = 0.4355f;
  float gX = 0.3848f;
  float bX = 0.1425f;
  float rY = 0.2216f;
  float gY = 0.7168f;
  float bY = 0.0605f;
  float rZ = 0.0137f;
  float gZ = 0.0967f;
  float bZ = 0.7139f;
};

struct RgbRgbMatrix {
  float rR;
  float gR;
  float bR;
  float rG;
  float gG;
  float bG;
  float rB;
  float gB;
  float bB;
};

typedef std::unordered_map<DynamicRangeProfile,
                           std::unordered_set<DynamicRangeProfile>>
    DynamicRangeProfileMap;

typedef std::unordered_map<
    ColorSpaceProfile,
    std::unordered_map<int, std::unordered_set<DynamicRangeProfile>>>
    ColorSpaceProfileMap;

struct SensorCharacteristics {
  size_t width = 0;
  size_t height = 0;
  size_t full_res_width = 0;
  size_t full_res_height = 0;
  nsecs_t exposure_time_range[2] = {0};
  nsecs_t frame_duration_range[2] = {0};
  int32_t sensitivity_range[2] = {0};
  camera_metadata_enum_android_sensor_info_color_filter_arrangement
      color_arangement = ANDROID_SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB;
  ColorFilterXYZ color_filter;
  ForwardMatrix forward_matrix;
  uint32_t max_raw_value = 0;
  uint32_t black_level_pattern[4] = {0};
  uint32_t max_raw_streams = 0;
  uint32_t max_processed_streams = 0;
  uint32_t max_stalling_streams = 0;
  uint32_t max_input_streams = 0;
  uint32_t physical_size[2] = {0};
  bool is_flash_supported = false;
  uint32_t lens_shading_map_size[2] = {0};
  uint32_t max_pipeline_depth = 0;
  uint32_t orientation = 0;
  bool is_front_facing = false;
  bool quad_bayer_sensor = false;
  bool is_10bit_dynamic_range_capable = false;
  DynamicRangeProfileMap dynamic_range_profiles;
  bool support_stream_use_case = false;
  int64_t end_valid_stream_use_case =
      ANDROID_SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_CALL;
  bool support_color_space_profiles = false;
  ColorSpaceProfileMap color_space_profiles;
  int32_t raw_crop_region_zoomed[4] = {0};
  int32_t raw_crop_region_unzoomed[4] = {0};
  int32_t timestamp_source = ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN;
};

// Maps logical/physical camera ids to sensor characteristics
typedef std::unordered_map<uint32_t, SensorCharacteristics> LogicalCharacteristics;

class EmulatedSensor : private Thread, public virtual RefBase {
 public:
  EmulatedSensor();
  ~EmulatedSensor();

  static android_pixel_format_t OverrideFormat(
      android_pixel_format_t format, DynamicRangeProfile dynamic_range_profile) {
    switch (dynamic_range_profile) {
      case ANDROID_REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_STANDARD:
        if (format == HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED) {
          return HAL_PIXEL_FORMAT_YCBCR_420_888;
        }
        break;
      case ANDROID_REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_HLG10:
        if (format == HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED) {
          return static_cast<android_pixel_format_t>(
              HAL_PIXEL_FORMAT_YCBCR_P010);
        }
        break;
      default:
        ALOGE("%s: Unsupported dynamic range profile 0x%x", __FUNCTION__,
              dynamic_range_profile);
    }

    return format;
  }

  static bool IsReprocessPathSupported(android_pixel_format_t input_format,
                                       android_pixel_format_t output_format) {
    if ((HAL_PIXEL_FORMAT_YCBCR_420_888 == input_format) &&
        ((HAL_PIXEL_FORMAT_YCBCR_420_888 == output_format) ||
         (HAL_PIXEL_FORMAT_BLOB == output_format))) {
      return true;
    }

    if (HAL_PIXEL_FORMAT_RAW16 == input_format &&
        HAL_PIXEL_FORMAT_RAW16 == output_format) {
      return true;
    }

    return false;
  }

  static bool AreCharacteristicsSupported(
      const SensorCharacteristics& characteristics);

  static bool IsStreamCombinationSupported(
      uint32_t logical_id, const StreamConfiguration& config,
      StreamConfigurationMap& map, StreamConfigurationMap& max_resolution_map,
      const PhysicalStreamConfigurationMap& physical_map,
      const PhysicalStreamConfigurationMap& physical_map_max_resolution,
      const LogicalCharacteristics& sensor_chars);

  static bool IsStreamCombinationSupported(
      uint32_t logical_id, const StreamConfiguration& config,
      StreamConfigurationMap& map,
      const PhysicalStreamConfigurationMap& physical_map,
      const LogicalCharacteristics& sensor_chars, bool is_max_res = false);

  /*
   * Power control
   */

  status_t StartUp(uint32_t logical_camera_id,
                   std::unique_ptr<LogicalCharacteristics> logical_chars);
  status_t ShutDown();

  /*
   * Physical camera settings control
   */
  struct SensorSettings {
    nsecs_t exposure_time = 0;
    nsecs_t frame_duration = 0;
    uint32_t gain = 0;  // ISO
    uint32_t lens_shading_map_mode;
    bool report_neutral_color_point = false;
    bool report_green_split = false;
    bool report_noise_profile = false;
    float zoom_ratio = 1.0f;
    bool report_rotate_and_crop = false;
    uint8_t rotate_and_crop = ANDROID_SCALER_ROTATE_AND_CROP_NONE;
    bool report_video_stab = false;
    uint8_t video_stab = ANDROID_CONTROL_VIDEO_STABILIZATION_MODE_OFF;
    bool report_edge_mode = false;
    uint8_t edge_mode = ANDROID_EDGE_MODE_OFF;
    uint8_t sensor_pixel_mode = ANDROID_SENSOR_PIXEL_MODE_DEFAULT;
    uint8_t test_pattern_mode = ANDROID_SENSOR_TEST_PATTERN_MODE_OFF;
    uint32_t test_pattern_data[4] = {0, 0, 0, 0};
    uint32_t screen_rotation = 0;
    uint32_t timestamp_source = ANDROID_SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN;
  };

  // Maps physical and logical camera ids to individual device settings
  typedef std::unordered_map<uint32_t, SensorSettings> LogicalCameraSettings;

  void SetCurrentRequest(std::unique_ptr<LogicalCameraSettings> logical_settings,
                         std::unique_ptr<HwlPipelineResult> result,
                         std::unique_ptr<HwlPipelineResult> partial_result,
                         std::unique_ptr<Buffers> input_buffers,
                         std::unique_ptr<Buffers> output_buffers);

  status_t Flush();

  /*
   * Synchronizing with sensor operation (vertical sync)
   */

  // Wait until the sensor outputs its next vertical sync signal, meaning it
  // is starting readout of its latest frame of data. Returns true if vertical
  // sync is signaled, false if the wait timed out.
  bool WaitForVSync(nsecs_t rel_time);

  static const nsecs_t kSupportedExposureTimeRange[2];
  static const nsecs_t kSupportedFrameDurationRange[2];
  static const int32_t kSupportedSensitivityRange[2];
  static const uint8_t kSupportedColorFilterArrangement;
  static const uint32_t kDefaultMaxRawValue;
  static const nsecs_t kDefaultExposureTime;
  static const int32_t kDefaultSensitivity;
  static const nsecs_t kDefaultFrameDuration;
  static const nsecs_t kReturnResultThreshod;
  static const uint32_t kDefaultBlackLevelPattern[4];
  static const camera_metadata_rational kDefaultColorTransform[9];
  static const float kDefaultColorCorrectionGains[4];
  static const float kDefaultToneMapCurveRed[4];
  static const float kDefaultToneMapCurveGreen[4];
  static const float kDefaultToneMapCurveBlue[4];
  static const uint8_t kPipelineDepth;

 private:
  // Scene stabilization
  static const uint32_t kRegularSceneHandshake;
  static const uint32_t kReducedSceneHandshake;

  /**
   * Logical characteristics
   */
  std::unique_ptr<LogicalCharacteristics> chars_;

  uint32_t logical_camera_id_ = 0;

  static const nsecs_t kMinVerticalBlank;

  // Sensor sensitivity, approximate

  static const float kSaturationVoltage;
  static const uint32_t kSaturationElectrons;
  static const float kVoltsPerLuxSecond;
  static const float kElectronsPerLuxSecond;

  static const float kReadNoiseStddevBeforeGain;  // In electrons
  static const float kReadNoiseStddevAfterGain;   // In raw digital units
  static const float kReadNoiseVarBeforeGain;
  static const float kReadNoiseVarAfterGain;
  static const camera_metadata_rational kNeutralColorPoint[3];
  static const float kGreenSplit;

  static const uint32_t kMaxRAWStreams;
  static const uint32_t kMaxProcessedStreams;
  static const uint32_t kMaxStallingStreams;
  static const uint32_t kMaxInputStreams;
  static const uint32_t kMaxLensShadingMapSize[2];
  static const int32_t kFixedBitPrecision;
  static const int32_t kSaturationPoint;

  std::vector<int32_t> gamma_table_sRGB_;
  std::vector<int32_t> gamma_table_smpte170m_;
  std::vector<int32_t> gamma_table_hlg_;

  Mutex control_mutex_;  // Lock before accessing control parameters
  // Start of control parameters
  Condition vsync_;
  bool got_vsync_;
  std::unique_ptr<LogicalCameraSettings> current_settings_;
  std::unique_ptr<HwlPipelineResult> current_result_;
  std::unique_ptr<HwlPipelineResult> partial_result_;
  std::unique_ptr<Buffers> current_output_buffers_;
  std::unique_ptr<Buffers> current_input_buffers_;
  std::unique_ptr<JpegCompressor> jpeg_compressor_;

  // End of control parameters

  unsigned int rand_seed_ = 1;

  /**
   * Inherited Thread virtual overrides, and members only used by the
   * processing thread
   */
  bool threadLoop() override;

  nsecs_t next_capture_time_;
  nsecs_t next_readout_time_;

  struct SensorBinningFactorInfo {
    bool has_raw_stream = false;
    bool has_non_raw_stream = false;
    bool quad_bayer_sensor = false;
    bool max_res_request = false;
    bool has_cropped_raw_stream = false;
    bool raw_in_sensor_zoom_applied = false;
  };

  std::map<uint32_t, SensorBinningFactorInfo> sensor_binning_factor_info_;

  std::unique_ptr<EmulatedScene> scene_;

  RgbRgbMatrix rgb_rgb_matrix_;

  static EmulatedScene::ColorChannels GetQuadBayerColor(uint32_t x, uint32_t y);

  static void RemosaicQuadBayerBlock(uint16_t* img_in, uint16_t* img_out,
                                     int xstart, int ystart,
                                     int row_stride_in_bytes);

  static status_t RemosaicRAW16Image(uint16_t* img_in, uint16_t* img_out,
                                     size_t row_stride_in_bytes,
                                     const SensorCharacteristics& chars);

  void CaptureRawBinned(uint8_t* img, size_t row_stride_in_bytes, uint32_t gain,
                        const SensorCharacteristics& chars);

  void CaptureRawFullRes(uint8_t* img, size_t row_stride_in_bytes,
                         uint32_t gain, const SensorCharacteristics& chars);
  void CaptureRawInSensorZoom(uint8_t* img, size_t row_stride_in_bytes,
                              uint32_t gain, const SensorCharacteristics& chars);
  void CaptureRaw(uint8_t* img, size_t row_stride_in_bytes, uint32_t gain,
                  const SensorCharacteristics& chars, bool in_sensor_zoom,
                  bool binned);

  enum RGBLayout { RGB, RGBA, ARGB };
  void CaptureRGB(uint8_t* img, uint32_t width, uint32_t height,
                  uint32_t stride, RGBLayout layout, uint32_t gain,
                  int32_t color_space, const SensorCharacteristics& chars);
  void CaptureYUV420(YCbCrPlanes yuv_layout, uint32_t width, uint32_t height,
                     uint32_t gain, float zoom_ratio, bool rotate,
                     int32_t color_space, const SensorCharacteristics& chars);
  void CaptureDepth(uint8_t* img, uint32_t gain, uint32_t width, uint32_t height,
                    uint32_t stride, const SensorCharacteristics& chars);
  void RgbToRgb(uint32_t* r_count, uint32_t* g_count, uint32_t* b_count);
  void CalculateRgbRgbMatrix(int32_t color_space,
                             const SensorCharacteristics& chars);

  struct YUV420Frame {
    uint32_t width = 0;
    uint32_t height = 0;
    YCbCrPlanes planes;
  };

  enum ProcessType { REPROCESS, HIGH_QUALITY, REGULAR };
  status_t ProcessYUV420(const YUV420Frame& input, const YUV420Frame& output,
                         uint32_t gain, ProcessType process_type,
                         float zoom_ratio, bool rotate_and_crop,
                         int32_t color_space,
                         const SensorCharacteristics& chars);

  inline int32_t ApplysRGBGamma(int32_t value, int32_t saturation);
  inline int32_t ApplySMPTE170MGamma(int32_t value, int32_t saturation);
  inline int32_t ApplyST2084Gamma(int32_t value, int32_t saturation);
  inline int32_t ApplyHLGGamma(int32_t value, int32_t saturation);
  inline int32_t GammaTable(int32_t value, int32_t color_space);

  bool WaitForVSyncLocked(nsecs_t reltime);
  void CalculateAndAppendNoiseProfile(float gain /*in ISO*/,
                                      float base_gain_factor,
                                      HalCameraMetadata* result /*out*/);

  void ReturnResults(HwlPipelineCallback callback,
                     std::unique_ptr<LogicalCameraSettings> settings,
                     std::unique_ptr<HwlPipelineResult> result,
                     bool reprocess_request,
                     std::unique_ptr<HwlPipelineResult> partial_result);

  static float GetBaseGainFactor(float max_raw_value) {
    return max_raw_value / EmulatedSensor::kSaturationElectrons;
  }

  nsecs_t getSystemTimeWithSource(uint32_t timestamp_source);
};

}  // namespace android

#endif  // HW_EMULATOR_CAMERA2_SENSOR_H
