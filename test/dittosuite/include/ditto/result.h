// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#ifdef __ANDROID__
#include <result.pb.h>
#else
#include "schema/result.pb.h"
#endif

#include <time.h>

#include <map>
#include <memory>
#include <set>
#include <string>
#include <vector>

namespace dittosuite {

enum class ResultsOutput { kNull, kReport, kCsv, kPb };

class Result {
 public:
  struct Statistics {
    double min, max, mean, median, sd;
  };

  explicit Result(const std::string& name, int repeat);

  void AddMeasurement(const std::string& type, const std::vector<double>& samples);
  void AddSubResult(std::unique_ptr<Result> result);
  std::vector<double> GetSamples(const std::string& measurement_name) const;
  int GetRepeat() const;
  void Print(ResultsOutput results_output, const std::string& instruction_path);

  void SetStatistics(const std::string& name, const Statistics& stats);

  dittosuiteproto::Result ToPb();
  static std::unique_ptr<Result> FromPb(const dittosuiteproto::Result &pb);

 private:
  struct TimeUnit {
    int dividing_factor;  // dividing factor used for transforming the current time
                          // unit (ns) in another one (ex 1000 for microseconds)
    std::string name;
  };
  struct BandwidthUnit {
    int dividing_factor;  // dividing factor used for transforming the bandwidth
                          // unit (KB/s) in another one (ex GB/s)
    std::string name;
  };
  TimeUnit time_unit_;
  BandwidthUnit bandwidth_unit_;
  std::string name_;
  std::map<std::string, std::vector<double>> samples_;
  std::map<std::string, Statistics> statistics_;
  std::vector<std::unique_ptr<Result>> sub_results_;
  int repeat_;

  void PrintHistograms(const std::string& instruction_path);
  void PrintStatisticsTables();
  void MakeStatisticsCsv();
  void MakeStatisticsPb();

  void AnalyseMeasurement(const std::string& name);
  std::vector<int> ComputeNormalizedFrequencyVector(const std::string& measurement_name);
  std::set<std::string> GetMeasurementsNames();
  void PrintStatisticsTableContent(const std::string& instruction_path,
                                   const std::string& measurement_name);

  std::string ComputeNextInstructionPath(const std::string& instruction_path);
  void PrintStatisticInCsv(std::ostream& csv_stream, const std::string& instruction_path,
                           const std::set<std::string>& measurements_names);
  void PrintHistogramHeader(const std::string& measurement_name);
  void MakeHistogramFromVector(const std::vector<int>& freq_vector, int min_value);
  TimeUnit GetTimeUnit(int64_t min_value);
  BandwidthUnit GetBandwidthUnit(int64_t min_value);
  void PrintMeasurementStatisticInCsv(std::ostream& csv_stream, const std::string& name);

  void __ToPb(dittosuiteproto::Result* result_pb);
  void StoreStatisticsInPb(dittosuiteproto::Metrics* metrics, const std::string& name);
};

void PrintPb(const dittosuiteproto::Result &pb);

}  // namespace dittosuite
