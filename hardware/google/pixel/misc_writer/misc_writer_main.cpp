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

#include <getopt.h>
#include <stdint.h>
#include <stdlib.h>

#include <iostream>
#include <map>
#include <memory>
#include <optional>
#include <string>
#include <string_view>

#include <android-base/logging.h>
#include <android-base/parseint.h>

#include "misc_writer/misc_writer.h"

using namespace std::string_literals;
using android::hardware::google::pixel::MiscWriter;
using android::hardware::google::pixel::MiscWriterActions;

static int Usage(std::string_view name) {
  std::cerr << name << " usage:\n";
  std::cerr << name << " [--override-vendor-space-offset <offset>] --<misc_writer_action>\n";
  std::cerr << "Supported misc_writer_action is one of: \n";
  std::cerr << "  --set-dark-theme     Write the dark theme flag\n";
  std::cerr << "  --clear-dark-theme   Clear the dark theme flag\n";
  std::cerr << "  --set-sota           Write the silent OTA flag\n";
  std::cerr << "  --clear-sota         Clear the silent OTA flag\n";
  std::cerr << "  --set-sota-config    Set the silent OTA configs\n";
  std::cerr << "  --set-enable-pkvm    Write the enable pKVM flag\n";
  std::cerr << "  --set-disable-pkvm   Write the disable pKVM flag\n";
  std::cerr << "  --set-wrist-orientation <0-3> Write the wrist orientation flag\n";
  std::cerr << "  --clear-wrist-orientation     Clear the wrist orientation flag\n";
  std::cerr << "  --set-timeformat              Write the time format value (1=24hr, 0=12hr)\n";
  std::cerr << "  --set-timeoffset              Write the time offset value (tz_time - utc_time)\n";
  std::cerr << "  --set-max-ram-size <2048-65536> Write the sw limit max ram size in MB\n";
  std::cerr << "  --set-max-ram-size <-1>         Clear the sw limit max ram size\n";
  std::cerr << "  --set-timertcoffset           Write the time offset value (utc_time - rtc_time)\n";
  std::cerr << "  --set-minrtc                  Write the minimum expected rtc value for tilb\n";
  std::cerr << "  --set-dsttransition           Write the next dst transition in the current timezone\n";
  std::cerr << "  --set-dstoffset               Write the time offset during the next dst transition\n";
  std::cerr << "Writes the given hex string to the specified offset in vendor space in /misc "
               "partition.\nDefault offset is used for each action unless "
               "--override-vendor-space-offset is specified.\n";
  return EXIT_FAILURE;
}

// misc_writer is a vendor tool that writes data to the vendor space in /misc.
int main(int argc, char** argv) {
  constexpr struct option OPTIONS[] = {
    { "set-dark-theme", no_argument, nullptr, 0 },
    { "clear-dark-theme", no_argument, nullptr, 0 },
    { "set-sota", no_argument, nullptr, 0 },
    { "clear-sota", no_argument, nullptr, 0 },
    { "set-wrist-orientation", required_argument, nullptr, 0 },
    { "clear-wrist-orientation", no_argument, nullptr, 0 },
    { "override-vendor-space-offset", required_argument, nullptr, 0 },
    { "set-enable-pkvm", no_argument, nullptr, 0 },
    { "set-disable-pkvm", no_argument, nullptr, 0 },
    { "set-timeformat", required_argument, nullptr, 0},
    { "set-timeoffset", required_argument, nullptr, 0},
    { "set-max-ram-size", required_argument, nullptr, 0},
    { "set-timertcoffset", required_argument, nullptr, 0},
    { "set-minrtc", required_argument, nullptr, 0},
    { "set-sota-config", no_argument, nullptr, 0 },
    { "set-dsttransition", required_argument, nullptr, 0},
    { "set-dstoffset", required_argument, nullptr, 0 },
    { nullptr, 0, nullptr, 0 },
  };

  std::map<std::string, MiscWriterActions> action_map{
    { "set-dark-theme", MiscWriterActions::kSetDarkThemeFlag },
    { "clear-dark-theme", MiscWriterActions::kClearDarkThemeFlag },
    { "set-sota", MiscWriterActions::kSetSotaFlag },
    { "clear-sota", MiscWriterActions::kClearSotaFlag },
    { "set-enable-pkvm", MiscWriterActions::kSetEnablePkvmFlag },
    { "set-disable-pkvm", MiscWriterActions::kSetDisablePkvmFlag },
    { "clear-wrist-orientation", MiscWriterActions::kClearWristOrientationFlag },
    { "set-sota-config", MiscWriterActions::kSetSotaConfig },
  };

  std::unique_ptr<MiscWriter> misc_writer;
  std::optional<size_t> override_offset;

  int arg;
  int option_index = 0;
  while ((arg = getopt_long(argc, argv, "", OPTIONS, &option_index)) != -1) {
    if (arg != 0) {
      LOG(ERROR) << "Invalid command argument";
      return Usage(argv[0]);
    }
    auto option_name = OPTIONS[option_index].name;
    if (option_name == "override-vendor-space-offset"s) {
      LOG(WARNING) << "Overriding the vendor space offset in misc partition to " << optarg;
      size_t offset;
      if (!android::base::ParseUint(optarg, &offset)) {
        LOG(ERROR) << "Failed to parse the offset: " << optarg;
        return Usage(argv[0]);
      }
      override_offset = offset;
    } else if (option_name == "set-wrist-orientation"s) {
      int orientation;
      if (!android::base::ParseInt(optarg, &orientation)) {
        LOG(ERROR) << "Failed to parse the orientation: " << optarg;
        return Usage(argv[0]);
      }
      if (orientation < 0 || orientation > 3) {
        LOG(ERROR) << "Orientation out of range: " << optarg;
        return Usage(argv[0]);
      }
      if (misc_writer) {
        LOG(ERROR) << "Misc writer action has already been set";
        return Usage(argv[0]);
      }
      misc_writer = std::make_unique<MiscWriter>(MiscWriterActions::kSetWristOrientationFlag,
                                                     '0' + orientation);
    } else if (option_name == "set-timeformat"s) {
      int timeformat;
      if (!android::base::ParseInt(optarg, &timeformat)) {
        LOG(ERROR) << "Failed to parse the timeformat: " << optarg;
        return Usage(argv[0]);
      }
      if (timeformat < 0 || timeformat > 1) {
        LOG(ERROR) << "Time format out of range: " << optarg;
        return Usage(argv[0]);
      }
      if (misc_writer) {
        LOG(ERROR) << "Misc writer action has already been set";
        return Usage(argv[0]);
      }
      misc_writer = std::make_unique<MiscWriter>(MiscWriterActions::kWriteTimeFormat,
                                                     '0' + timeformat);
    } else if (option_name == "set-timeoffset"s) {
      int timeoffset;
      if (!android::base::ParseInt(optarg, &timeoffset)) {
        LOG(ERROR) << "Failed to parse the timeoffset: " << optarg;
        return Usage(argv[0]);
      }
      if (timeoffset < MiscWriter::kMinTimeOffset || timeoffset > MiscWriter::kMaxTimeOffset) {
        LOG(ERROR) << "Time offset out of range: " << optarg;
        return Usage(argv[0]);
      }
      if (misc_writer) {
        LOG(ERROR) << "Misc writer action has already been set";
        return Usage(argv[0]);
      }
      misc_writer = std::make_unique<MiscWriter>(MiscWriterActions::kWriteTimeOffset,
                                                     std::to_string(timeoffset));
    } else if (option_name == "set-max-ram-size"s) {
      int max_ram_size;
      if (!android::base::ParseInt(optarg, &max_ram_size)) {
        LOG(ERROR) << "Failed to parse the max_ram_size: " << optarg;
        return Usage(argv[0]);
      }
      if (max_ram_size != MiscWriter::kRamSizeDefault &&
          (max_ram_size < MiscWriter::kRamSizeMin || max_ram_size > MiscWriter::kRamSizeMax)) {
        LOG(ERROR) << "max_ram_size out of range: " << optarg;
        return Usage(argv[0]);
      }
      if (misc_writer) {
        LOG(ERROR) << "Misc writer action has already been set";
        return Usage(argv[0]);
      }

      if (max_ram_size == MiscWriter::kRamSizeDefault) {
        misc_writer = std::make_unique<MiscWriter>(MiscWriterActions::kClearMaxRamSize);
      } else {
        misc_writer = std::make_unique<MiscWriter>(MiscWriterActions::kSetMaxRamSize,
                                                   std::to_string(max_ram_size));
      }
    } else if (option_name == "set-timertcoffset"s) {
      long long int timertcoffset = strtoll(optarg, NULL, 10);
      if (0 == timertcoffset) {
        LOG(ERROR) << "Failed to parse the timertcoffset:" << optarg;
        return Usage(argv[0]);
      }
      if (misc_writer) {
        LOG(ERROR) << "Misc writer action has already been set";
        return Usage(argv[0]);
      }
      misc_writer = std::make_unique<MiscWriter>(MiscWriterActions::kWriteTimeRtcOffset,
                                                     std::to_string(timertcoffset));
    } else if (option_name == "set-minrtc"s) {
      long long int minrtc = strtoll(optarg, NULL, 10);
      if (0 == minrtc) {
        LOG(ERROR) << "Failed to parse the minrtc:" << optarg;
        return Usage(argv[0]);
      }
      if (misc_writer) {
        LOG(ERROR) << "Misc writer action has already been set";
        return Usage(argv[0]);
      }
      misc_writer = std::make_unique<MiscWriter>(MiscWriterActions::kWriteTimeMinRtc,
                                                     std::to_string(minrtc));
    } else if (auto iter = action_map.find(option_name); iter != action_map.end()) {
      if (misc_writer) {
        LOG(ERROR) << "Misc writer action has already been set";
        return Usage(argv[0]);
      }
      misc_writer = std::make_unique<MiscWriter>(iter->second);
    } else if (option_name == "set-dsttransition"s) {
      long long int dst_transition = strtoll(optarg, NULL, 10);
      if (0 == dst_transition) {
        LOG(ERROR) << "Failed to parse the dst transition:" << optarg;
        return Usage(argv[0]);
      }
      if (misc_writer) {
        LOG(ERROR) << "Misc writer action has already been set";
        return Usage(argv[0]);
      }
      misc_writer = std::make_unique<MiscWriter>(MiscWriterActions::kWriteDstTransition,
                                                     std::to_string(dst_transition));
    } else if (option_name == "set-dstoffset"s) {
      int dst_offset;
      if (!android::base::ParseInt(optarg, &dst_offset)) {
        LOG(ERROR) << "Failed to parse the dst offset: " << optarg;
        return Usage(argv[0]);
      }
      if (misc_writer) {
        LOG(ERROR) << "Misc writer action has already been set";
        return Usage(argv[0]);
      }
      misc_writer = std::make_unique<MiscWriter>(MiscWriterActions::kWriteDstOffset,
                                                     std::to_string(dst_offset));
    } else {
      LOG(FATAL) << "Unreachable path, option_name: " << option_name;
    }
  }

  if (!misc_writer) {
    LOG(ERROR) << "An action must be specified for misc writer";
    return Usage(argv[0]);
  }

  if (!misc_writer->PerformAction(override_offset)) {
    return EXIT_FAILURE;
  }

  return EXIT_SUCCESS;
}
