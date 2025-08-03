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

#pragma once

#include <stddef.h>
#include <stdint.h>

#include <optional>
#include <string>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

enum class MiscWriterActions : int32_t {
  kSetDarkThemeFlag = 0,
  kClearDarkThemeFlag,
  kSetSotaFlag,
  kClearSotaFlag,
  kSetEnablePkvmFlag,
  kSetDisablePkvmFlag,
  kSetWristOrientationFlag,
  kClearWristOrientationFlag,
  kWriteTimeFormat,
  kWriteTimeOffset,
  kSetMaxRamSize,
  kClearMaxRamSize,
  kWriteTimeRtcOffset,
  kWriteTimeMinRtc,
  kSetSotaConfig,
  kWriteDstTransition,
  kWriteDstOffset,

  kUnset = -1,
};

class MiscWriter {
 public:
  // sync with bootloader's abl bootloader_message.h
  static constexpr uint32_t kThemeFlagOffsetInVendorSpace = 0;
  static constexpr char kDarkThemeFlag[] = "theme-dark";
  static constexpr uint32_t kSotaFlagOffsetInVendorSpace = 32;
  static constexpr char kSotaFlag[] = "enable-sota";
  static constexpr uint32_t kPkvmFlagOffsetInVendorSpace = 64;
  static constexpr char kEnablePkvmFlag[] = "enable-pkvm";
  static constexpr char kDisablePkvmFlag[] = "disable-pkvm";
  static constexpr uint32_t kWristOrientationFlagOffsetInVendorSpace = 96;
  static constexpr char kWristOrientationFlag[] = "wrist-orientation=";
  static constexpr uint32_t kTimeFormatValOffsetInVendorSpace = 128;
  static constexpr char kTimeFormat[] = "timeformat=";
  static constexpr uint32_t kTimeOffsetValOffsetInVendorSpace = 160;
  static constexpr char kTimeOffset[] = "timeoffset=";
  static constexpr uint32_t kMaxRamSizeOffsetInVendorSpace = 192;
  static constexpr char kMaxRamSize[] = "max-ram-size=";
  static constexpr uint32_t kSotaStateOffsetInVendorSpace = 224;
  static constexpr uint32_t kRTimeRtcOffsetValOffsetInVendorSpace = 264;
  static constexpr char kTimeRtcOffset[] = "timertcoffset=";
  static constexpr uint32_t kRTimeMinRtcValOffsetInVendorSpace = 296;
  static constexpr char kTimeMinRtc[] = "timeminrtc=";
  static constexpr uint32_t kFaceauthEvalValOffsetInVendorSpace = 328;
  static constexpr uint32_t kSotaScheduleShipmodeOffsetInVendorSpace = 360;
  static constexpr uint32_t kDstTransitionOffsetInVendorSpace = 392;
  static constexpr char kDstTransition[] = "dst-transition=";
  static constexpr uint32_t kDstOffsetOffsetInVendorSpace = 424;
  static constexpr char kDstOffset[] = "dst-offset=";
  // Next available space = 456

  // Minimum and maximum valid value for max-ram-size
  static constexpr int32_t kRamSizeDefault = -1;
  static constexpr uint32_t kRamSizeMin = 2048;
  static constexpr uint32_t kRamSizeMax = 65536;

  // Minimum and maximum time zone are -12 and 14 hours from GMT
  static constexpr int32_t kMinTimeOffset = -12 * 60 * 60 * 1000;
  static constexpr int32_t kMaxTimeOffset = 14 * 60 * 60 * 1000;

  // Returns true of |size| bytes data starting from |offset| is fully inside the vendor space.
  static bool OffsetAndSizeInVendorSpace(size_t offset, size_t size);
  // Writes the given data to the vendor space in /misc partition, at the given offset. Note that
  // offset is in relative to the start of the vendor space.
  static bool WriteMiscPartitionVendorSpace(const void* data, size_t size, size_t offset,
                                            std::string* err);

  explicit MiscWriter(const MiscWriterActions& action) : action_(action) {}
  explicit MiscWriter(const MiscWriterActions &action, const char data)
      : action_(action), chardata_(data) {}
  explicit MiscWriter(const MiscWriterActions &action, std::string data)
      : action_(action), stringdata_(data) {}

  // Performs the stored MiscWriterActions. If |override_offset| is set, writes to the input offset
  // in the vendor space of /misc instead of the default offset.
  bool PerformAction(std::optional<size_t> override_offset = std::nullopt);

 private:
  MiscWriterActions action_{ MiscWriterActions::kUnset };
  char chardata_{'0'};
  std::string stringdata_;
};

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android
