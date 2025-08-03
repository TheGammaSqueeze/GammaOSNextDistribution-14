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
#include "profiler.h"

#include <cutils/properties.h>
#include <hardware/google/camera/common/profiler/profiler.pb.h>
#include <log/log.h>
#include <sys/stat.h>

#include <fstream>
#include <mutex>
#include <unordered_map>
#include <vector>

namespace google {
namespace camera_common {
namespace {

#undef LOG_TAG
#define LOG_TAG "profiler"

float StandardDeviation(std::vector<float> samples, float mean) {
  int size = samples.size();

  double sum = 0;
  for (int i = 0; i < size; i++) {
    sum += pow((samples[i] - mean), 2);
  }

  return static_cast<float>(sqrt(sum / (size - 1)));
}

// Profiler implementatoin.
class ProfilerImpl : public Profiler {
 public:
  ProfilerImpl(SetPropFlag setting) : setting_(setting) {
    object_init_real_time_ = GetRealTimeNs();
    object_init_boot_time_ = GetBootTimeNs();
  };
  ~ProfilerImpl();

  // Setup the name of use case the profiler is running.
  // Argument:
  //  usecase: the name use case of the profiler is running.
  void SetUseCase(std::string usecase) override final {
    use_case_ = std::move(usecase);
  }

  // Set the file prefix name for dumpping the profiling file.
  // Argument:
  //  dump_file_prefix: file prefix name. In the current setting,
  //    "/data/vendor/camera/" is a valid folder for camera to dump file.
  //    A valid prefix can be "/data/vendor/camera/test_prefix_".
  void SetDumpFilePrefix(const std::string& dump_file_prefix) override final;

  // Start to profile.
  // We use start and end to choose which code snippet to be profile.
  // The user specifies the name, and the profiler will print the name and its
  // timing.
  // Arguments:
  //   name: the name of the node to be profiled.
  //   request_id: frame requesd id.
  void Start(const std::string& name,
             int request_id = kInvalidRequestId) override final;

  // End the profileing.
  // Arguments:
  //   name: the name of the node to be profiled. Should be the same in Start().
  //   request_id: frame requesd id.
  void End(const std::string& name,
           int request_id = kInvalidRequestId) override final;

  // Print out the profiling result in the standard output (ANDROID_LOG_ERROR).
  void PrintResult() override;

  // Profile the frame rate
  void ProfileFrameRate(const std::string&) override final;

  // Set the interval of FPS print
  // The unit is second and interval_seconds must >= 1
  void SetFpsPrintInterval(int32_t interval_seconds) override final;

  // Get the latency associated with the name
  std::vector<LatencyEvent> GetLatencyData() override final;

  std::string GetUseCase() const override final {
    return use_case_;
  }

 protected:
  // A structure to hold start time, end time, and count of profiling code
  // snippet.
  struct TimeSlot {
    int64_t start = 0;
    int64_t end = 0;
    int32_t count = 0;
    int32_t request_id = 0;
  };

  struct TimeSlotEvent {
    std::string name;
    TimeSlot slot;
  };

  // A structure to store node's profiling result.
  struct TimeResult {
    std::string node_name;
    float min_dt;
    float max_dt;
    float avg_dt;
    float avg_count;
    float fps;
    float mean_max_stddevs;
    TimeResult(std::string node_name, float min_dt, float max_dt, float avg_dt,
               float count, float fps, float mean_max_stddevs)
        : node_name(node_name),
          min_dt(min_dt),
          max_dt(max_dt),
          avg_dt(avg_dt),
          avg_count(count),
          fps(fps),
          mean_max_stddevs(mean_max_stddevs) {
    }
  };

  using TimeSeries = std::vector<TimeSlot>;
  using NodeTimingMap = std::unordered_map<std::string, TimeSeries>;
  using NodeFrameRateMap = std::unordered_map<std::string, TimeSlot>;

  static constexpr int64_t kNsPerSec = 1000000000;
  static constexpr float kNanoToMilli = 0.000001f;

  // The setting_ is used to memorize the getprop result.
  SetPropFlag setting_;
  // The map to record the timing of all nodes.
  NodeTimingMap timing_map_;
  // The map to record the timing to print fps when close.
  NodeFrameRateMap frame_rate_map_;
  // The map to record the timing to print fps per second.
  NodeFrameRateMap realtime_frame_rate_map_;
  // Use case name.
  std::string use_case_;
  // The prefix for the dump filename.
  std::string dump_file_prefix_;
  // Mutex lock.
  std::mutex lock_;

  // Get clock boot time.
  int64_t GetBootTimeNs() const {
    if (timespec now; clock_gettime(CLOCK_BOOTTIME, &now) == 0) {
      return now.tv_sec * kNsPerSec + now.tv_nsec;
    } else {
      ALOGE("clock_gettime failed");
      return -1;
    }
  }
  // Get clock real time.
  int64_t GetRealTimeNs() const {
    if (timespec now; clock_gettime(CLOCK_REALTIME, &now) == 0) {
      return now.tv_sec * kNsPerSec + now.tv_nsec;
    } else {
      ALOGE("clock_gettime failed");
      return -1;
    }
  }

  // Timestamp of the class object initialized using CLOCK_BOOTTIME.
  int64_t object_init_boot_time_;
  // Timestamp of the class object initialized using CLOCK_REALTIME.
  int64_t object_init_real_time_;

  // Create folder if not exist.
  void CreateFolder(const std::string& folder_path);

  // Dump the result to the disk.
  // Argument:
  //   filepath: file path to dump file.
  virtual void DumpResult(const std::string& filepath);

  // Dump result in text format.
  void DumpTxt(std::string_view filepath);

  // Dump result in proto binary format.
  void DumpPb(std::string_view filepath);

  // Dump result format extension: proto or text.
  constexpr static char kStrPb[] = ".pb";
  constexpr static char kStrTxt[] = ".txt";

  int32_t fps_print_interval_seconds_ = 1;
};

ProfilerImpl::~ProfilerImpl() {
  if (setting_ == SetPropFlag::kDisable || timing_map_.size() == 0) {
    return;
  }
  if (setting_ & SetPropFlag::kPrintBit) {
    PrintResult();
  }
  if (setting_ & SetPropFlag::kDumpBit) {
    std::string filename = std::to_string(object_init_real_time_);
    DumpResult(dump_file_prefix_ + use_case_ + "-TS" + filename);
  }
}

void ProfilerImpl::DumpResult(const std::string& filepath) {
  if (setting_ & SetPropFlag::kProto) {
    DumpPb(filepath + kStrPb);
  } else {
    DumpTxt(filepath + kStrTxt);
  }
}

void ProfilerImpl::CreateFolder(const std::string& folder_path) {
  struct stat folder_stat;
  memset(&folder_stat, 0, sizeof(folder_stat));
  if (stat(folder_path.c_str(), &folder_stat) != 0) {
    if (errno != ENOENT ||
        mkdir(folder_path.c_str(), S_IRWXU | S_IRWXG | S_IRWXO) == 0) {
      ALOGE("Failed to create %s. errno: %d", folder_path.c_str(), errno);
    }
  }
}

void ProfilerImpl::SetDumpFilePrefix(const std::string& dump_file_prefix) {
  dump_file_prefix_ = dump_file_prefix;
  if (setting_ & SetPropFlag::kDumpBit) {
    if (auto index = dump_file_prefix_.rfind('/'); index != std::string::npos) {
      CreateFolder(dump_file_prefix_.substr(0, index));
    }
  }
}

void ProfilerImpl::SetFpsPrintInterval(int32_t interval_seconds) {
  if (interval_seconds < 1) {
    ALOGE("Wrong interval: %d, must >= 1", interval_seconds);
    return;
  }
  fps_print_interval_seconds_ = interval_seconds;
}

void ProfilerImpl::ProfileFrameRate(const std::string& name) {
  std::lock_guard<std::mutex> lk(lock_);
  // Save the timeing for each whole process
  TimeSlot& frame_rate = frame_rate_map_[name];
  if (frame_rate.start == 0) {
    frame_rate.start = GetBootTimeNs();
    frame_rate.count = 0;
    frame_rate.end = 0;
  } else {
    ++frame_rate.count;
    frame_rate.end = GetBootTimeNs();
  }

  if ((setting_ & SetPropFlag::kPrintFpsPerIntervalBit) == 0) {
    return;
  }
  // Print FPS every second
  TimeSlot& realtime_frame_rate = realtime_frame_rate_map_[name];
  if (realtime_frame_rate.start == 0) {
    realtime_frame_rate.start = GetBootTimeNs();
    realtime_frame_rate.count = 0;
  } else {
    ++realtime_frame_rate.count;
    int64_t current = GetBootTimeNs();
    int64_t elapsed = current - realtime_frame_rate.start;
    if (elapsed > kNsPerSec * fps_print_interval_seconds_) {
      float fps =
          realtime_frame_rate.count * kNsPerSec / static_cast<float>(elapsed);
      float avg_fps = frame_rate.count * kNsPerSec /
                      static_cast<float>(frame_rate.end - frame_rate.start);
      ALOGI("%s: current FPS %3.2f, avg %3.2f", name.c_str(), fps, avg_fps);
      realtime_frame_rate.count = 0;
      realtime_frame_rate.start = current;
    }
  }
}

void ProfilerImpl::Start(const std::string& name, int request_id) {
  if (setting_ == SetPropFlag::kDisable) {
    return;
  }

  // When the request_id == kInvalidRequestId, it is served as a different
  // purpose, eg. profiling first frame latency, or HAL total runtime. The valid
  // request id is shifted by 1 to avoid the conflict.
  int valid_request_id = (request_id == kInvalidRequestId) ? 0 : request_id + 1;

  {
    std::lock_guard<std::mutex> lk(lock_);
    TimeSeries& time_series = timing_map_[name];
    for (int i = time_series.size(); i <= valid_request_id; ++i) {
      time_series.push_back(TimeSlot());
    }
    TimeSlot& slot = time_series[valid_request_id];
    slot.request_id = valid_request_id;
    slot.start += GetBootTimeNs();
  }

  if ((setting_ & SetPropFlag::kCalculateFpsOnEndBit) == 0) {
    ProfileFrameRate(name);
  }
}

void ProfilerImpl::End(const std::string& name, int request_id) {
  if (setting_ == SetPropFlag::kDisable) {
    return;
  }

  // When the request_id == kInvalidRequestId, it is served as a different
  // purpose, eg. profiling first frame latency, or HAL total runtime. The valid
  // request id is shifted by 1 to avoid the conflict.
  int valid_request_id = (request_id == kInvalidRequestId) ? 0 : request_id + 1;

  {
    std::lock_guard<std::mutex> lk(lock_);
    if (static_cast<std::size_t>(valid_request_id) < timing_map_[name].size()) {
      TimeSlot& slot = timing_map_[name][valid_request_id];
      slot.end += GetBootTimeNs();
      ++slot.count;
    }
  }

  if ((setting_ & SetPropFlag::kCalculateFpsOnEndBit) != 0) {
    ProfileFrameRate(name);
  }
}

void ProfilerImpl::PrintResult() {
  ALOGI("UseCase: %s. Profiled Frames: %d.", use_case_.c_str(),
        static_cast<int>(timing_map_.begin()->second.size()));

  std::vector<TimeResult> time_results;

  float sum_avg = 0.f;
  float max_max = 0.f;
  float sum_min = 0.f;
  float sum_max = 0.f;
  for (const auto& [node_name, time_series] : timing_map_) {
    int num_frames = 0;
    int num_samples = 0;
    float sum_dt = 0.f;
    float min_dt = std::numeric_limits<float>::max();
    float max_dt = 0.f;
    float mean_dt = 0.f;
    std::vector<float> elapses;
    for (const auto& slot : time_series) {
      if (slot.count > 0) {
        float elapsed = (slot.end - slot.start) * kNanoToMilli;
        sum_dt += elapsed;
        num_samples += slot.count;
        min_dt = std::min(min_dt, elapsed);
        max_dt = std::max(max_dt, elapsed);
        num_frames++;
        elapses.push_back(elapsed);
      }
    }
    if (num_samples == 0) {
      continue;
    }
    float avg = sum_dt / std::max(1, num_samples);
    float avg_count = static_cast<float>(num_samples) /
                      static_cast<float>(std::max(1, num_frames));
    mean_dt = avg * avg_count;
    sum_avg += mean_dt;
    sum_min += min_dt;
    sum_max += max_dt;
    max_max = std::max(max_max, max_dt);

    // calculate StandardDeviation
    float mean_max_stddevs = 0.f;
    if (elapses.size() > 1) {
      float dev_dt = StandardDeviation(elapses, mean_dt);
      mean_max_stddevs = (max_dt - mean_dt) / dev_dt;
    }

    TimeSlot& frame_rate = frame_rate_map_[node_name];
    int64_t duration = frame_rate.end - frame_rate.start;
    float fps = 0;
    if (duration > kNsPerSec) {
      fps = frame_rate.count * kNsPerSec / static_cast<float>(duration);
    }
    time_results.push_back(
        {node_name, min_dt, max_dt, mean_dt, avg_count, fps, mean_max_stddevs});
  }

  std::sort(time_results.begin(), time_results.end(),
            [](auto a, auto b) { return a.avg_dt > b.avg_dt; });

  for (const auto& result : time_results) {
    if (result.fps == 0) {
      ALOGI(
          "%51.51s Min: %8.3f ms,  Max: %8.3f ms,  Avg: %7.3f ms "
          "(Count = %3.1f),  mean_max_stddevs: %6.2f,  fps:    NA",
          result.node_name.c_str(), result.min_dt, result.max_dt, result.avg_dt,
          result.avg_count, result.mean_max_stddevs);
    } else {
      ALOGI(
          "%51.51s Min: %8.3f ms,  Max: %8.3f ms,  Avg: %7.3f ms "
          "(Count = %3.1f),  mean_max_stddevs: %6.2f,  fps: %8.2f",
          result.node_name.c_str(), result.min_dt, result.max_dt, result.avg_dt,
          result.avg_count, result.mean_max_stddevs, result.fps);
    }
  }

  ALOGI("%43.43s     MIN SUM: %8.3f ms,  MAX SUM: %8.3f ms,  AVG SUM: %7.3f ms",
        "", sum_min, sum_max, sum_avg);
  ALOGI("");
}

void ProfilerImpl::DumpTxt(std::string_view filepath) {
  // The dump result data is organized as 3 sections:
  //  1. detla time and fps of each frame.
  //  2. start time of each frame.
  //  3. end time of each frame.
  if (std::ofstream fout(filepath, std::ios::out); fout.is_open()) {
    fout << "// PROFILER_DELTA_TIME_AND_FPS, UNIT:MILLISECOND //\n";
    for (const auto& [node_name, time_series] : timing_map_) {
      fout << node_name << " ";
      for (const auto& time_slot : time_series) {
        float elapsed = static_cast<float>(time_slot.end - time_slot.start) /
                        std::max(1, time_slot.count);
        fout << elapsed * kNanoToMilli << " ";
      }
      fout << "\n";
      TimeSlot& frame_rate = frame_rate_map_[node_name];
      int64_t duration = frame_rate.end - frame_rate.start;
      float fps = 0;
      if (duration > kNsPerSec) {
        fps = frame_rate.count * kNsPerSec / static_cast<float>(duration);
      }
      if (fps > 0) {
        fout << node_name << " fps:" << fps;
      } else {
        fout << node_name << " fps: NA";
      }
      fout << "\n";
    }

    fout << "\n// PROFILER_START_TIME, AVG TIMESTAMP, UNIT:NANOSECOND //\n";
    for (const auto& [node_name, time_series] : timing_map_) {
      fout << node_name << " ";
      for (const auto& time_slot : time_series) {
        int64_t avg_time_stamp = time_slot.start / std::max(1, time_slot.count);
        fout << avg_time_stamp << " ";
      }
      fout << "\n";
    }

    fout << "\n// PROFILER_END_TIME, AVG TIMESTAMP,  UNIT:NANOSECOND //\n";
    for (const auto& [node_name, time_series] : timing_map_) {
      fout << node_name << " ";
      for (const auto& time_slot : time_series) {
        int64_t avg_time_stamp = time_slot.end / std::max(1, time_slot.count);
        fout << avg_time_stamp << " ";
      }
      fout << "\n";
    }
    fout.close();
  }
}

void ProfilerImpl::DumpPb(std::string_view filepath) {
  if (std::ofstream fout(filepath, std::ios::out); fout.is_open()) {
    profiler::ProfilingResult profiling_result;
    profiling_result.set_usecase(use_case_);
    profiling_result.set_profile_start_time_nanos(object_init_real_time_);
    profiling_result.set_profile_start_boottime_nanos(object_init_boot_time_);
    profiling_result.set_profile_end_time_nanos(GetRealTimeNs());

    for (const auto& [node_name, time_series] : timing_map_) {
      profiler::TimeSeries& target = *profiling_result.add_target();
      target.set_name(node_name);
      for (const auto& time_slot : time_series) {
        profiler::TimeStamp& time_stamp = *target.add_runtime();
        // A single node can be called multiple times in a frame. Every time the
        // node is called in the same frame, the profiler accumulates the
        // timestamp value in time_slot.start/end, and increments the count.
        // Therefore the result timestamp we stored is the `average` timestamp.
        // Note: consider using minimum-start, and maximum-end.
        time_stamp.set_start(time_slot.start / std::max(1, time_slot.count));
        time_stamp.set_end(time_slot.end / std::max(1, time_slot.count));
        time_stamp.set_count(time_slot.count);
        time_stamp.set_request_id(time_slot.request_id);
      }
    }
    profiling_result.SerializeToOstream(&fout);
    fout.close();
  }
}

// Get the latency associated with the name
std::vector<Profiler::LatencyEvent> ProfilerImpl::GetLatencyData() {
  std::vector<TimeSlotEvent> time_results;
  std::vector<LatencyEvent> latency_data;
  for (const auto& [node_name, time_series] : timing_map_) {
    for (const auto& slot : time_series) {
      if (slot.count > 0 && time_results.size() < time_results.max_size()) {
        time_results.push_back({node_name, slot});
      }
    }
  }
  std::sort(
      time_results.begin(), time_results.end(),
      [](const auto& a, const auto& b) { return a.slot.end < b.slot.end; });

  for (const auto& [node_name, slot] : time_results) {
    if (slot.count > 0) {
      float elapsed = (slot.end - slot.start) * kNanoToMilli;
      latency_data.push_back({node_name, elapsed});
    }
  }
  return latency_data;
}

class ProfilerStopwatchImpl : public ProfilerImpl {
 public:
  ProfilerStopwatchImpl(SetPropFlag setting) : ProfilerImpl(setting){};

  ~ProfilerStopwatchImpl() {
    if (setting_ == SetPropFlag::kDisable || timing_map_.size() == 0) {
      return;
    }
    if (setting_ & SetPropFlag::kPrintBit) {
      // Virtual function won't work in parent class's destructor. need to
      // call it by ourself.
      PrintResult();
      // Erase the print bit to prevent parent class print again.
      setting_ = static_cast<SetPropFlag>(setting_ & (~SetPropFlag::kPrintBit));
    }
    if (setting_ & SetPropFlag::kDumpBit) {
      DumpResult(dump_file_prefix_ + use_case_ + "-TS" +
                 std::to_string(object_init_real_time_) + ".txt");
      setting_ = static_cast<SetPropFlag>(setting_ & (~SetPropFlag::kDumpBit));
    }
  }

  // Print out the profiling result in the standard output (ANDROID_LOG_ERROR)
  // with stopwatch mode.
  void PrintResult() override {
    ALOGI("Profiling Case: %s", use_case_.c_str());

    // Sort by end time.
    std::vector<TimeSlotEvent> time_results;
    for (const auto& [node_name, time_series] : timing_map_) {
      for (const auto& slot : time_series) {
        if (slot.count > 0 && time_results.size() < time_results.max_size()) {
          time_results.push_back({node_name, slot});
        }
      }
    }
    std::sort(
        time_results.begin(), time_results.end(),
        [](const auto& a, const auto& b) { return a.slot.end < b.slot.end; });

    for (const auto& [node_name, slot] : time_results) {
      if (slot.count > 0) {
        float elapsed = (slot.end - slot.start) * kNanoToMilli;
        ALOGI("%51.51s: %8.3f ms", node_name.c_str(), elapsed);
      }
    }

    ALOGI("");
  }

  void DumpResult(const std::string& filepath) override {
    if (std::ofstream fout(filepath, std::ios::out); fout.is_open()) {
      for (const auto& [node_name, time_series] : timing_map_) {
        fout << node_name << " ";
        for (const auto& slot : time_series) {
          fout << (slot.end - slot.start) * kNanoToMilli << " ";
        }
        fout << "\n";
      }
      fout.close();
    }
  }
};

// Dummpy profiler class.
class ProfilerDummy : public Profiler {
 public:
  ProfilerDummy(){};
  ~ProfilerDummy(){};

  void SetUseCase(std::string) override final{};
  void SetDumpFilePrefix(const std::string&) override final{};
  void Start(const std::string&, int) override final{};
  void End(const std::string&, int) override final{};
  void PrintResult() override final{};
  void ProfileFrameRate(const std::string&) override final{};
  void SetFpsPrintInterval(int32_t) override final{};
  std::vector<LatencyEvent> GetLatencyData() override final {
    return {};
  }
  std::string GetUseCase() const override final {
    return "";
  }
};

}  // anonymous namespace

std::shared_ptr<Profiler> Profiler::Create(int option) {
  SetPropFlag flag = static_cast<SetPropFlag>(option);

  if (flag == SetPropFlag::kDisable) {
    return std::make_shared<ProfilerDummy>();
  } else if (flag & SetPropFlag::kStopWatch) {
    return std::make_shared<ProfilerStopwatchImpl>(flag);
  } else {
    return std::make_shared<ProfilerImpl>(flag);
  }
}

}  // namespace camera_common
}  // namespace google
