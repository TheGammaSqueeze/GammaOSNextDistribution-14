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

#ifndef HARDWARE_GOOGLE_CAMERA_COMMON_PROFILER_H
#define HARDWARE_GOOGLE_CAMERA_COMMON_PROFILER_H

#include <cutils/properties.h>

#include <limits>
#include <memory>
#include <string>
#include <vector>

namespace google {
namespace camera_common {

// The goal of the Profiler is to profile the performance of camemra pipeline.
// However you can use it profile any procedure.
// The profiler prints out the result when the Profiler obejct is deconstructed.
//
// Setprops:
//  - To disable the profiler:
//    $ adb shell setprop persist.vendor.camera.profiler 0
//  - To print the profiling result in standard output:
//    $ adb shell setprop persist.vendor.camera.profiler 1
//  - To dump the profiling result to "/data/vendor/camera/profiler":
//    $ adb shell setprop persist.vendor.camera.profiler 2
//  - To print and dump the profiling result to "/data/vendor/camera/profiler":
//    $ adb shell setprop persist.vendor.camera.profiler 3
//  After add FPS option, here are the combination results.
//  Option 0 (kDisable): Disable Profiler
//  Option 1 (kPrintBit):
//    When close, Print the result
//    - Processing time
//    - FPS with total frames on process start function
//  Option 2 (kDumpBit):
//    When close, dump the result to file(dump_file_prefix)
//    - Processing time
//    - FPS with total frames on process start function
//  Option 3 (kPrintBit|kDumpBit):
//    When close, print and dump the result
//    - Processing time
//    - FPS with total frames on process start function
//  Option 8 (kPrintFpsPerIntervalBit):
//    Print FPS per interval on ProfileFrameRate function
//    The frequency is based on the value of SetFpsPrintInterval()
//  Option 9 (kPrintFpsPerIntervalBit|kPrintBit):
//    Print FPS per interval on process start and ProfileFrameRate function
//    When close, print the result
//    - Processing time
//    - FPS with total frames on process start function
//  Option 10 (kPrintFpsPerIntervalBit|kDumpBit):
//    Print FPS per interval on process start and ProfileFrameRate function
//    When close, dump the result
//    - Processing time
//    - FPS with total frames on process start function
//  Option 11 (kPrintFpsPerIntervalBit|kPrintBit|kDumpBit):
//    Print FPS per interval on process start and ProfileFrameRate function
//    When close, print and dump the result
//    - Processing time
//    - FPS with total frames on process start function
//  Option 16 (kCalculateFpsOnEndBit):
//    Calculate FPS on process end function instead of process start function
//  Option 17 (kCalculateFpsOnEndBit|kPrintBit):
//    When close, print the result
//    - Processing time
//    - FPS with total frames on process "end" function
//  Option 18 (kCalculateFpsOnEndBit|kDumpBit):
//    When close, dump the result
//    - Processing time
//    - FPS with total frames on process "end" function
//  Option 19 (kCalculateFpsOnEndBit|kPrintBitk|DumpBit):
//    When close, print and dump the result
//    - Processing time
//    - FPS with total frames on process "end" function
//  Option 25 (kCalculateFpsOnEndBit|kPrintFpsPerIntervalBit|kPrintBit):
//    Print FPS per interval on process start and ProfileFrameRate function
//    When close, print the result
//    - Processing time
//    - FPS with total frames on process "end" function
//  Option 26 (kCalculateFpsOnEndBit|kPrintFpsPerIntervalBit|DumpBit):
//    Print FPS per interval on process start and ProfileFrameRate function
//    When close, dump the result
//    - Processing time
//    - FPS with total frames on process "end" function
//  Option 27 (kCalculateFpsOnEndBit|kPrintFpsPerIntervalBit|kPrintBitk|DumpBit):
//    Print FPS per interval on process start and ProfileFrameRate function
//    When close, print and dump the result
//    - Processing time
//    - FPS with total frames on process "end" function
//
//  By default the profiler is disabled.
//
// Usage:
//  1. To Create a profiler, please call Profiler::Create(...).
//  2. Use Start() and End() to profile the enclosed code snippet.
//  3. Use SetUseCase to specify the name of the profiling target (purpose).
//  4  If you want to dump the profiling data to the disk, call
//     SetDumpFilePrefix(), which is default to "/vendor/camera/profiler/".
//     The dumped file name is the prefix name + usecase name.
//
// Example Code:
//  In the following example, we use a for loop to profile two fucntions Foo()
//  and Bar; Foo() run once, and Bar() run twice.
//
//   std::unique_ptr<Profiler> profiler = Profiler::Create(Profiler::kPrintBit);
//   profiler->SetUseCase("Profiling Example");
//
//   for (int i = 0; i < 100; i++) {
//     profiler->Start("Foo function", i);
//     Foo()
//     profiler->End("Foo function", i);
//
//     profiler->Start("Bar function", i);
//     Bar()
//     profiler->End("Bar function", i);
//
//     profiler->Start("Bar function", i);
//     Bar()
//     profiler->End("Bar function", i);
//   }
//
// Example Print Out:
//
// UseCase: Profiling Example. Profiled Frames: 100.
//      Foo function           Max:  0.012 ms.   Avg:  0.020 ms x 1 =  0.040 ms
//      Bar function           Max:  0.008 ms.   Avg:  0.019 ms x 2 =  0.039 ms
//                      SUM OF MAX:  0.020 ms,           SUM OF AVG =  0.079 ms
//
class Profiler {
 public:
  // Invalid request id.
  static constexpr int kInvalidRequestId = std::numeric_limits<int>::max();

  // Create profiler.
  static std::shared_ptr<Profiler> Create(int option);

  virtual ~Profiler() = default;

  struct LatencyEvent {
    std::string name;
    float duration;
  };

  // adb setprop options.
  enum SetPropFlag {
    kDisable = 0,
    kPrintBit = 1 << 0,
    kDumpBit = 1 << 1,
    kStopWatch = 1 << 2,
    // Print FPS per interval time based on the value of SetFpsPrintInterval()
    kPrintFpsPerIntervalBit = 1 << 3,
    // Calculate FPS on process end function instead of process start function
    kCalculateFpsOnEndBit = 1 << 4,
    // Dynamic start profiling.
    kDynamicStartBit = 1 << 5,
    // Dumps result using proto format.
    kProto = 1 << 6,
    // Customized profiler derived from Profiler
    kCustomProfiler = 1 << 7,
  };

  // Setup the name of use case the profiler is running.
  // Argument:
  //  usecase: the name use case of the profiler is running.
  virtual void SetUseCase(std::string usecase) = 0;

  // Set the file prefix name for dumpping the profiling file.
  // Argument:
  //  dump_file_prefix: file prefix name. In the current setting,
  //    "/data/vendor/camera/" is a valid folder for camera to dump file.
  //    A valid prefix can be "/data/vendor/camera/test_prefix_".
  virtual void SetDumpFilePrefix(const std::string& dump_file_prefix) = 0;

  // Start to profile.
  // We use start and end to choose which code snippet to be profile.
  // The user specifies the name, and the profiler will print the name and its
  // timing.
  // Arguments:
  //   name: the name of the node to be profiled.
  //   request_id: frame requesd id.
  virtual void Start(const std::string& name, int request_id) = 0;

  // End the profileing.
  // Arguments:
  //   name: the name of the node to be profiled. Should be the same in Start().
  //   request_id: frame requesd id.
  virtual void End(const std::string& name, int request_id) = 0;

  // Print out the profiling result in the standard output (ANDROID_LOG_ERROR).
  virtual void PrintResult() = 0;

  // Profile the frame rate
  // If only call this function without start() and End(),
  // creating profiler needs to set option with kPrintFpsPerIntervalBit bit.
  // It can print FPS every second.
  virtual void ProfileFrameRate(const std::string& name) = 0;

  // Set the interval of FPS print
  // The interval unit is second and interval_seconds must >= 1
  virtual void SetFpsPrintInterval(int32_t interval_seconds) = 0;

  virtual std::vector<LatencyEvent> GetLatencyData() = 0;

  virtual std::string GetUseCase() const = 0;

 protected:
  Profiler() = default;
};

// A scoped utility class to facilitate profiling.
class ScopedProfiler {
 public:
  // Constructor.
  // Arguments:
  //   profiler: profiler object.
  //   target: the name of the target to be profiled.
  //   request_id: frame requesd id.
  ScopedProfiler(std::shared_ptr<Profiler> profiler, const std::string target,
                 int request_id)
      : profiler_(profiler),
        target_(std::move(target)),
        request_id_(request_id) {
    profiler_->Start(target_, request_id_);
  }

  ScopedProfiler(std::shared_ptr<Profiler> profiler, const std::string target)
      : profiler_(profiler), target_(std::move(target)) {
    request_id_ = Profiler::kInvalidRequestId;
    profiler_->Start(target_, request_id_);
  }

  ScopedProfiler(const std::string target, int option)
      : target_(std::move(target)) {
    profiler_ = Profiler::Create(option);
    request_id_ = Profiler::kInvalidRequestId;
    profiler_->Start(target_, request_id_);
  }

  ~ScopedProfiler() {
    profiler_->End(target_, request_id_);
  }

 private:
  std::shared_ptr<Profiler> profiler_;
  const std::string target_;
  int request_id_;
};

}  // namespace camera_common
}  // namespace google

#endif  // HARDWARE_GOOGLE_CAMERA_COMMON_PROFILER_H
