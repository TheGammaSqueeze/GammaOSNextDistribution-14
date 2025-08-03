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

#include <ditto/result.h>
#include <ditto/statistics.h>
#include <ditto/timespec_utils.h>

#include <google/protobuf/util/json_util.h>

#include <algorithm>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <set>
#include <string>

const int kSampleDisplayWidth = 16;  // this width is used displaying a sample value
const int kTableWidth = 164;  // table width; can be adjusted in case of longer instruction paths
const char* kTableDivider = " | ";   // table character divider
const int kMaxHistogramHeight = 20;  // used for normalizing the histogram (represents the
                                     //  maximum height of the histogram)
const int kMaxHistogramWidth = 50;   // used for normalizing the histogram (represents the
                                     // maximum width of the histogram)
const char kCsvDelimiter = ',';      // delimiter used for .csv files
static int bin_size;                 // bin size corresponding to the normalization
                                     // of the Oy axis of the histograms

namespace dittosuite {

Result::Result(const std::string& name, const int repeat) : name_(name), repeat_(repeat) {}

void Result::AddMeasurement(const std::string& name, const std::vector<double>& samples) {
  samples_[name] = samples;
  AnalyseMeasurement(name);
}

void Result::AddSubResult(std::unique_ptr<Result> result) {
  sub_results_.push_back(std::move(result));
}

std::vector<double> Result::GetSamples(const std::string& measurement_name) const {
  return samples_.find(measurement_name)->second;
}

int Result::GetRepeat() const {
  return repeat_;
}

// analyse the measurement with the given name, and store
// the results in the statistics_ map
void Result::AnalyseMeasurement(const std::string& name) {
  statistics_[name].min = StatisticsGetMin(samples_[name]);
  statistics_[name].max = StatisticsGetMax(samples_[name]);
  statistics_[name].mean = StatisticsGetMean(samples_[name]);
  statistics_[name].median = StatisticsGetMedian(samples_[name]);
  statistics_[name].sd = StatisticsGetSd(samples_[name]);
}

std::string Result::ComputeNextInstructionPath(const std::string& instruction_path) {
  return instruction_path + (instruction_path != "" ? "/" : "") + name_;
}

void Result::Print(const ResultsOutput results_output, const std::string& instruction_path) {
  switch (results_output) {
    case ResultsOutput::kReport:
      PrintHistograms(instruction_path);
      PrintStatisticsTables();
      break;
    case ResultsOutput::kCsv:
      MakeStatisticsCsv();
      break;
    case ResultsOutput::kPb:
      PrintPb(ToPb());
      break;
    case ResultsOutput::kNull:
      break;
  }
}

void PrintTableBorder() {
  std::cout << std::setfill('-') << std::setw(kTableWidth) << "" << std::setfill(' ');
  std::cout << '\n';
}

void PrintStatisticsTableHeader() {
  std::cout << "\x1b[1m";  // beginning of bold
  std::cout << '\n';
  PrintTableBorder();
  std::cout << "| ";  // beginning of table row
  std::cout << std::setw(70) << std::left << "Instruction name";
  std::cout << kTableDivider;
  std::cout << std::setw(15) << std::right << " Min";
  std::cout << kTableDivider;
  std::cout << std::setw(15) << " Max";
  std::cout << kTableDivider;
  std::cout << std::setw(15) << " Mean";
  std::cout << kTableDivider;
  std::cout << std::setw(15) << " Median";
  std::cout << kTableDivider;
  std::cout << std::setw(15) << " SD";
  std::cout << kTableDivider;
  std::cout << '\n';
  PrintTableBorder();
  std::cout << "\x1b[0m";  // ending of bold
}

void PrintMeasurementInTable(const int64_t& measurement, const std::string& measurement_name) {
  if (measurement_name == "duration") {
    std::cout << std::setw(13) << measurement << "ns";
  } else if (measurement_name == "bandwidth") {
    std::cout << std::setw(11) << measurement << "KB/s";
  }
}

// Recursive function to print one row at a time
// of statistics table content (the instruction path, min, max and mean).
void Result::PrintStatisticsTableContent(const std::string& instruction_path,
                                         const std::string& measurement_name) {
  std::string next_instruction_path = ComputeNextInstructionPath(instruction_path);
  int subinstruction_level =
      std::count(next_instruction_path.begin(), next_instruction_path.end(), '/');
  // If the instruction path name contains too many subinstrions,
  // print only the last 2 preceded by "../".
  if (subinstruction_level > 2) {
    std::size_t first_truncate_pos = next_instruction_path.find('/');
    next_instruction_path = ".." + next_instruction_path.substr(first_truncate_pos);
  }

  // Print table row
  if (samples_.find(measurement_name) != samples_.end()) {
    std::cout << "| ";  // started new row
    std::cout << std::setw(70) << std::left << next_instruction_path << std::right;
    std::cout << kTableDivider;
    PrintMeasurementInTable(statistics_[measurement_name].min, measurement_name);
    std::cout << kTableDivider;
    PrintMeasurementInTable(statistics_[measurement_name].max, measurement_name);
    std::cout << kTableDivider;
    PrintMeasurementInTable(statistics_[measurement_name].mean, measurement_name);
    std::cout << kTableDivider;
    PrintMeasurementInTable(statistics_[measurement_name].median, measurement_name);
    std::cout << kTableDivider;
    std::cout << std::setw(15)
              << statistics_[measurement_name].sd;  // SD is always printed without measurement unit
    std::cout << kTableDivider;                     // ended current row
    std::cout << '\n';
    PrintTableBorder();
  }

  for (const auto& sub_result : sub_results_) {
    sub_result->PrintStatisticsTableContent(next_instruction_path, measurement_name);
  }
}

std::set<std::string> Result::GetMeasurementsNames() {
  std::set<std::string> names;

  for (const auto& it : samples_) {
    names.insert(it.first);
  }
  for (const auto& sub_result : sub_results_) {
    for (const auto& sub_name : sub_result->GetMeasurementsNames()) {
      names.insert(sub_name);
    }
  }

  return names;
}

void Result::PrintStatisticsTables() {
  std::set<std::string> measurement_names = GetMeasurementsNames();
  for (const auto& s : measurement_names) {
    std::cout << s << " statistics:";
    PrintStatisticsTableHeader();
    PrintStatisticsTableContent("", s);
    std::cout << '\n';
  }
}

void Result::PrintHistogramHeader(const std::string& measurement_name) {
  if (measurement_name == "duration") {
    std::cout.width(kSampleDisplayWidth - 3);
    std::cout << "Time(" << time_unit_.name << ") |";
    std::cout << " Normalized number of time samples\n";
  } else if (measurement_name == "bandwidth") {
    std::cout.width(kSampleDisplayWidth - 6);
    std::cout << "Bandwidth(" << bandwidth_unit_.name << ") |";
    std::cout << " Normalized number of bandwidth samples\n";
  }
  std::cout << std::setfill('-') << std::setw(kMaxHistogramWidth) << "" << std::setfill(' ');
  std::cout << '\n';
}

// makes (normalized) histogram from vector
void Result::MakeHistogramFromVector(const std::vector<int>& freq_vector, const int min_value) {
  int sum = 0;
  int max_frequency = *std::max_element(freq_vector.begin(), freq_vector.end());
  for (std::size_t i = 0; i < freq_vector.size(); i++) {
    std::cout.width(kSampleDisplayWidth);
    std::cout << min_value + bin_size * i << kTableDivider;

    int hist_width = ceil(static_cast<double>(freq_vector[i]) * kMaxHistogramWidth / max_frequency);
    std::cout << std::setfill('#') << std::setw(hist_width) << "" << std::setfill(' ');

    std::cout << " { " << freq_vector[i] << " }\n";

    sum += freq_vector[i];
  }

  std::cout << '\n';
  std::cout << "Total samples: { " << sum << " }\n";
}

// makes and returns the normalized frequency vector
std::vector<int> Result::ComputeNormalizedFrequencyVector(const std::string& measurement_name) {
  int64_t min_value = statistics_[measurement_name].min;
  if (measurement_name == "duration") {
    min_value /= time_unit_.dividing_factor;
  } else if (measurement_name == "bandwidth") {
    min_value /= bandwidth_unit_.dividing_factor;
  }

  std::vector<int> freq_vector(kMaxHistogramHeight, 0);
  for (const auto& sample : samples_[measurement_name]) {
    int64_t sample_copy = sample;
    if (measurement_name == "duration") {
      sample_copy /= time_unit_.dividing_factor;
    } else if (measurement_name == "bandwidth") {
      sample_copy /= bandwidth_unit_.dividing_factor;
    }
    int64_t bin = (sample_copy - min_value) / bin_size;

    freq_vector[bin]++;
  }
  return freq_vector;
}

Result::TimeUnit Result::GetTimeUnit(const int64_t min_value) {
  TimeUnit result;
  if (min_value <= 1e7) {
    // time unit in nanoseconds
    result.dividing_factor = 1;
    result.name = "ns";
  } else if (min_value <= 1e10) {
    // time unit in microseconds
    result.dividing_factor = 1e3;
    result.name = "us";
  } else if (min_value <= 1e13) {
    // time unit in milliseconds
    result.dividing_factor = 1e6;
    result.name = "ms";
  } else {
    // time unit in seconds
    result.dividing_factor = 1e9;
    result.name = "s";
  }
  return result;
}

Result::BandwidthUnit Result::GetBandwidthUnit(const int64_t min_value) {
  BandwidthUnit result;
  if (min_value <= (1 << 15)) {
    // bandwidth unit in KB/s
    result.dividing_factor = 1;
    result.name = "KiB/s";
  } else if (min_value <= (1 << 25)) {
    // bandwidth unit in MB/s
    result.dividing_factor = 1 << 10;
    result.name = "MiB/s";
  } else {
    // bandwidth unit in GB/s
    result.dividing_factor = 1 << 20;
    result.name = "GiB/s";
  }
  return result;
}

void Result::PrintHistograms(const std::string& instruction_path) {
  std::string next_instruction_path = ComputeNextInstructionPath(instruction_path);
  std::cout << "\x1b[1m";  // beginning of bold
  std::cout << "Instruction path: " << next_instruction_path;
  std::cout << "\x1b[0m";  // ending of bold
  std::cout << "\n\n";

  for (const auto& sample : samples_) {
    int64_t min_value = statistics_[sample.first].min;
    int64_t max_value = statistics_[sample.first].max;
    if (sample.first == "duration") {
      time_unit_ = GetTimeUnit(statistics_[sample.first].min);
      min_value /= time_unit_.dividing_factor;
      max_value /= time_unit_.dividing_factor;
    } else if (sample.first == "bandwidth") {
      bandwidth_unit_ = GetBandwidthUnit(min_value);
      min_value /= bandwidth_unit_.dividing_factor;
      max_value /= bandwidth_unit_.dividing_factor;
    }
    bin_size = (max_value - min_value) / kMaxHistogramHeight + 1;

    std::vector<int> freq_vector = ComputeNormalizedFrequencyVector(sample.first);
    PrintHistogramHeader(sample.first);
    MakeHistogramFromVector(freq_vector, min_value);
    std::cout << "\n\n";

    for (const auto& sub_result : sub_results_) {
      sub_result->PrintHistograms(next_instruction_path);
    }
  }
}

// Print statistic measurement with given name in .csv
void Result::PrintMeasurementStatisticInCsv(std::ostream& csv_stream, const std::string& name) {
  csv_stream << kCsvDelimiter;
  csv_stream << statistics_[name].min << kCsvDelimiter;
  csv_stream << statistics_[name].max << kCsvDelimiter;
  csv_stream << statistics_[name].mean << kCsvDelimiter;
  csv_stream << statistics_[name].median << kCsvDelimiter;
  csv_stream << statistics_[name].sd;
}

void PrintEmptyMeasurementInCsv(std::ostream& csv_stream) {
  csv_stream << std::setfill(kCsvDelimiter) << std::setw(5) << "" << std::setfill(' ');
}

// Recursive function to print one row at a time using the .csv stream given as a parameter
// of statistics table content (the instruction path, min, max, mean and SD).
void Result::PrintStatisticInCsv(std::ostream& csv_stream, const std::string& instruction_path,
                                 const std::set<std::string>& measurements_names) {
  std::string next_instruction_path = ComputeNextInstructionPath(instruction_path);

  // print one row in csv
  csv_stream << next_instruction_path;
  for (const auto& measurement : measurements_names) {
    if (statistics_.find(measurement) != statistics_.end()) {
      PrintMeasurementStatisticInCsv(csv_stream, measurement);
    } else {
      PrintEmptyMeasurementInCsv(csv_stream);
    }
  }
  csv_stream << '\n';

  for (const auto& sub_result : sub_results_) {
    sub_result->PrintStatisticInCsv(csv_stream, next_instruction_path, measurements_names);
  }
}

void PrintCsvHeader(std::ostream& csv_stream, const std::set<std::string>& measurement_names) {
  csv_stream << "Instruction path";
  for (const auto& measurement : measurement_names) {
    csv_stream << kCsvDelimiter;
    csv_stream << measurement << " min" << kCsvDelimiter;
    csv_stream << measurement << " max" << kCsvDelimiter;
    csv_stream << measurement << " mean" << kCsvDelimiter;
    csv_stream << measurement << " median" << kCsvDelimiter;
    csv_stream << measurement << " SD";
  }
  csv_stream << '\n';
}

void Result::MakeStatisticsCsv() {
  std::ostream csv_stream(std::cout.rdbuf());

  std::set<std::string> measurements_names = GetMeasurementsNames();
  PrintCsvHeader(csv_stream, measurements_names);

  PrintStatisticInCsv(csv_stream, "", measurements_names);
}

void Result::StoreStatisticsInPb(dittosuiteproto::Metrics* metrics,
                                           const std::string& name) {
  metrics->set_name(name);
  metrics->set_min(statistics_[name].min);
  metrics->set_max(statistics_[name].max);
  metrics->set_mean(statistics_[name].mean);
  metrics->set_median(statistics_[name].median);
  metrics->set_sd(statistics_[name].sd);
}

void Result::__ToPb(dittosuiteproto::Result* result_pb) {
  result_pb->set_name(name_);


  for (const auto &stats : statistics_) {
      StoreStatisticsInPb(result_pb->add_metrics(), stats.first);
  }

  for (const auto& sub_result : sub_results_) {
    sub_result->__ToPb(result_pb->add_sub_result());
  }
}

dittosuiteproto::Result Result::ToPb() {
  dittosuiteproto::Result result_pb;
  std::set<std::string> measurements_names = GetMeasurementsNames();

  __ToPb(&result_pb);

  return result_pb;
}

void Result::SetStatistics(const std::string& name, const Result::Statistics& statistics) {
  statistics_[name] = statistics;
}

void PrintPb(const dittosuiteproto::Result &pb) {
  std::string json;
  google::protobuf::util::JsonPrintOptions options;

  options.add_whitespace = true;
  google::protobuf::util::MessageToJsonString(pb, &json, options);

  std::ostream pb_stream(std::cout.rdbuf());
  pb_stream << json << std::endl;
}

std::unique_ptr<Result> Result::FromPb(const dittosuiteproto::Result& pb) {
  auto result = std::make_unique<Result>(pb.name(), 1);

  for (const auto& m : pb.metrics()) {
    Result::Statistics stats = {.min = m.min(), .max = m.max(), .median = m.median(), .sd = m.sd()};
    result->SetStatistics(m.name(), stats);
  }

  for (const auto& r : pb.sub_result()) {
    result->AddSubResult(Result::FromPb(r));
  }

  return result;
}

}  // namespace dittosuite
