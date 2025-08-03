/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <sstream>

#include "android-base/file.h"
#include "android-base/logging.h"
#include "base/macros.h"
#include "base/scoped_flock.h"
#include "metrics.h"

#pragma clang diagnostic push
#pragma clang diagnostic error "-Wconversion"

namespace art {
namespace metrics {

std::string DatumName(DatumId datum) {
  switch (datum) {
#define ART_METRIC(name, Kind, ...) \
  case DatumId::k##name:  \
    return #name;
    ART_METRICS(ART_METRIC)
#undef ART_METRIC

    default:
      LOG(FATAL) << "Unknown datum id: " << static_cast<unsigned>(datum);
      UNREACHABLE();
  }
}

SessionData SessionData::CreateDefault() {
#ifdef _WIN32
  int32_t uid = kInvalidUserId;  // Windows does not support getuid();
#else
  int32_t uid = static_cast<int32_t>(getuid());
#endif

  return SessionData{
      .session_id = kInvalidSessionId,
      .uid = uid,
      .compilation_reason = CompilationReason::kUnknown,
      .compiler_filter = CompilerFilterReporting::kUnknown,
  };
}

ArtMetrics::ArtMetrics()
    : beginning_timestamp_{MilliTime()},
      last_report_timestamp_{beginning_timestamp_}
#define ART_METRIC(name, Kind, ...) \
  , name##_ {}
      ART_METRICS(ART_METRIC)
#undef ART_METRIC
{
}

void ArtMetrics::ReportAllMetricsAndResetValueMetrics(
    const std::vector<MetricsBackend*>& backends) {
  uint64_t current_timestamp_ = MilliTime();
  for (auto& backend : backends) {
    backend->BeginReport(current_timestamp_ - beginning_timestamp_);
  }

#define REPORT_METRIC(name, Kind, ...) name()->Report(backends);
  ART_EVENT_METRICS(REPORT_METRIC)
#undef REPORT_METRIC

  // Update ART_DATUM_DELTA_TIME_ELAPSED_MS before ART Value Metrics are reported.
  TimeElapsedDelta()->Add(current_timestamp_ - last_report_timestamp_);

#define REPORT_METRIC(name, Kind, ...) name()->ReportAndReset(backends);
  ART_VALUE_METRICS(REPORT_METRIC)
#undef REPORT_METRIC

  for (auto& backend : backends) {
    backend->EndReport();
  }

  // Save the current timestamp to be able to calculate the elapsed time for the next report cycle.
  last_report_timestamp_ = current_timestamp_;
}

void ArtMetrics::DumpForSigQuit(std::ostream& os) {
  StringBackend backend(std::make_unique<TextFormatter>());
  ReportAllMetricsAndResetValueMetrics({&backend});
  os << backend.GetAndResetBuffer();
}

void ArtMetrics::Reset() {
  beginning_timestamp_ = MilliTime();
#define RESET_METRIC(name, ...) name##_.Reset();
  ART_METRICS(RESET_METRIC)
#undef RESET_METRIC
}

StringBackend::StringBackend(std::unique_ptr<MetricsFormatter> formatter)
    : formatter_(std::move(formatter)) {}

std::string StringBackend::GetAndResetBuffer() {
  return formatter_->GetAndResetBuffer();
}

void StringBackend::BeginOrUpdateSession(const SessionData& session_data) {
  session_data_ = session_data;
}

void StringBackend::BeginReport(uint64_t timestamp_since_start_ms) {
  formatter_->FormatBeginReport(timestamp_since_start_ms, session_data_);
}

void StringBackend::EndReport() {
  formatter_->FormatEndReport();
}

void StringBackend::ReportCounter(DatumId counter_type, uint64_t value) {
  formatter_->FormatReportCounter(counter_type, value);
}

void StringBackend::ReportHistogram(DatumId histogram_type,
                                    int64_t minimum_value_,
                                    int64_t maximum_value_,
                                    const std::vector<uint32_t>& buckets) {
  formatter_->FormatReportHistogram(histogram_type, minimum_value_, maximum_value_, buckets);
}

void TextFormatter::FormatBeginReport(uint64_t timestamp_since_start_ms,
                                      const std::optional<SessionData>& session_data) {
  os_ << "\n*** ART internal metrics ***\n";
  os_ << "  Metadata:\n";
  os_ << "    timestamp_since_start_ms: " << timestamp_since_start_ms << "\n";
  if (session_data.has_value()) {
    os_ << "    session_id: " << session_data->session_id << "\n";
    os_ << "    uid: " << session_data->uid << "\n";
    os_ << "    compilation_reason: " << CompilationReasonName(session_data->compilation_reason)
        << "\n";
    os_ << "    compiler_filter: " << CompilerFilterReportingName(session_data->compiler_filter)
        << "\n";
  }
  os_ << "  Metrics:\n";
}

void TextFormatter::FormatReportCounter(DatumId counter_type, uint64_t value) {
  os_ << "    " << DatumName(counter_type) << ": count = " << value << "\n";
}

void TextFormatter::FormatReportHistogram(DatumId histogram_type,
                                          int64_t minimum_value_,
                                          int64_t maximum_value_,
                                          const std::vector<uint32_t>& buckets) {
  os_ << "    " << DatumName(histogram_type) << ": range = "
      << minimum_value_ << "..." << maximum_value_;
  if (!buckets.empty()) {
    os_ << ", buckets: ";
    bool first = true;
    for (const auto& count : buckets) {
      if (!first) {
        os_ << ",";
      }
      first = false;
      os_ << count;
    }
    os_ << "\n";
  } else {
    os_ << ", no buckets\n";
  }
}

void TextFormatter::FormatEndReport() {
  os_ << "*** Done dumping ART internal metrics ***\n";
}

std::string TextFormatter::GetAndResetBuffer() {
  std::string result = os_.str();
  os_.clear();
  os_.str("");
  return result;
}

void XmlFormatter::FormatBeginReport(uint64_t timestamp_millis,
                                     const std::optional<SessionData>& session_data) {
  tinyxml2::XMLElement* art_runtime_metrics = document_.NewElement("art_runtime_metrics");
  document_.InsertEndChild(art_runtime_metrics);

  art_runtime_metrics->InsertNewChildElement("version")->SetText(version.data());

  tinyxml2::XMLElement* metadata = art_runtime_metrics->InsertNewChildElement("metadata");
  metadata->InsertNewChildElement("timestamp_since_start_ms")->SetText(timestamp_millis);

  if (session_data.has_value()) {
    metadata->InsertNewChildElement("session_id")->SetText(session_data->session_id);
    metadata->InsertNewChildElement("uid")->SetText(session_data->uid);
    metadata
        ->InsertNewChildElement("compilation_reason")
        ->SetText(CompilationReasonName(session_data->compilation_reason));
    metadata
        ->InsertNewChildElement("compiler_filter")
        ->SetText(CompilerFilterReportingName(session_data->compiler_filter));
  }

  art_runtime_metrics->InsertNewChildElement("metrics");
}

void XmlFormatter::FormatReportCounter(DatumId counter_type, uint64_t value) {
  tinyxml2::XMLElement* metrics = document_.RootElement()->FirstChildElement("metrics");

  tinyxml2::XMLElement* counter = metrics->InsertNewChildElement(DatumName(counter_type).data());
  counter->InsertNewChildElement("counter_type")->SetText("count");
  counter->InsertNewChildElement("value")->SetText(value);
}

void XmlFormatter::FormatReportHistogram(DatumId histogram_type,
                                         int64_t low_value,
                                         int64_t high_value,
                                         const std::vector<uint32_t>& buckets) {
  tinyxml2::XMLElement* metrics = document_.RootElement()->FirstChildElement("metrics");

  tinyxml2::XMLElement* histogram =
      metrics->InsertNewChildElement(DatumName(histogram_type).data());
  histogram->InsertNewChildElement("counter_type")->SetText("histogram");
  histogram->InsertNewChildElement("minimum_value")->SetText(low_value);
  histogram->InsertNewChildElement("maximum_value")->SetText(high_value);

  tinyxml2::XMLElement* buckets_element = histogram->InsertNewChildElement("buckets");
  for (const auto& count : buckets) {
    buckets_element->InsertNewChildElement("bucket")->SetText(count);
  }
}

void XmlFormatter::FormatEndReport() {}

std::string XmlFormatter::GetAndResetBuffer() {
  tinyxml2::XMLPrinter printer(/*file=*/nullptr, /*compact=*/true);
  document_.Print(&printer);
  std::string result = printer.CStr();
  document_.Clear();

  return result;
}

LogBackend::LogBackend(std::unique_ptr<MetricsFormatter> formatter,
                       android::base::LogSeverity level)
  : StringBackend{std::move(formatter)}, level_{level}
{}

void LogBackend::BeginReport(uint64_t timestamp_since_start_ms) {
  StringBackend::GetAndResetBuffer();
  StringBackend::BeginReport(timestamp_since_start_ms);
}

void LogBackend::EndReport() {
  StringBackend::EndReport();
  LOG_STREAM(level_) << StringBackend::GetAndResetBuffer();
}

FileBackend::FileBackend(std::unique_ptr<MetricsFormatter> formatter,
                         const std::string& filename)
  : StringBackend{std::move(formatter)}, filename_{filename}
{}

void FileBackend::BeginReport(uint64_t timestamp_since_start_ms) {
  StringBackend::GetAndResetBuffer();
  StringBackend::BeginReport(timestamp_since_start_ms);
}

void FileBackend::EndReport() {
  StringBackend::EndReport();
  std::string error_message;
  auto file{
      LockedFile::Open(filename_.c_str(), O_CREAT | O_WRONLY | O_APPEND, true, &error_message)};
  if (file.get() == nullptr) {
    LOG(WARNING) << "Could open metrics file '" << filename_ << "': " << error_message;
  } else {
    if (!android::base::WriteStringToFd(StringBackend::GetAndResetBuffer(), file.get()->Fd())) {
      PLOG(WARNING) << "Error writing metrics to file";
    }
  }
}

// Make sure CompilationReasonName and CompilationReasonForName are inverses.
static_assert(CompilationReasonFromName(CompilationReasonName(CompilationReason::kError)) ==
              CompilationReason::kError);
static_assert(CompilationReasonFromName(CompilationReasonName(CompilationReason::kUnknown)) ==
              CompilationReason::kUnknown);
static_assert(CompilationReasonFromName(CompilationReasonName(CompilationReason::kFirstBoot)) ==
              CompilationReason::kFirstBoot);
static_assert(CompilationReasonFromName(CompilationReasonName(CompilationReason::kBootAfterOTA)) ==
              CompilationReason::kBootAfterOTA);
static_assert(CompilationReasonFromName(CompilationReasonName(CompilationReason::kPostBoot)) ==
              CompilationReason::kPostBoot);
static_assert(CompilationReasonFromName(CompilationReasonName(CompilationReason::kInstall)) ==
              CompilationReason::kInstall);
static_assert(CompilationReasonFromName(CompilationReasonName(CompilationReason::kInstallFast)) ==
              CompilationReason::kInstallFast);
static_assert(CompilationReasonFromName(CompilationReasonName(CompilationReason::kInstallBulk)) ==
              CompilationReason::kInstallBulk);
static_assert(
    CompilationReasonFromName(CompilationReasonName(CompilationReason::kInstallBulkSecondary)) ==
    CompilationReason::kInstallBulkSecondary);
static_assert(
    CompilationReasonFromName(CompilationReasonName(CompilationReason::kInstallBulkDowngraded)) ==
    CompilationReason::kInstallBulkDowngraded);
static_assert(CompilationReasonFromName(
                  CompilationReasonName(CompilationReason::kInstallBulkSecondaryDowngraded)) ==
              CompilationReason::kInstallBulkSecondaryDowngraded);
static_assert(CompilationReasonFromName(CompilationReasonName(CompilationReason::kBgDexopt)) ==
              CompilationReason::kBgDexopt);
static_assert(CompilationReasonFromName(CompilationReasonName(CompilationReason::kABOTA)) ==
              CompilationReason::kABOTA);
static_assert(CompilationReasonFromName(CompilationReasonName(CompilationReason::kInactive)) ==
              CompilationReason::kInactive);
static_assert(CompilationReasonFromName(CompilationReasonName(CompilationReason::kShared)) ==
              CompilationReason::kShared);
static_assert(
    CompilationReasonFromName(CompilationReasonName(CompilationReason::kInstallWithDexMetadata)) ==
    CompilationReason::kInstallWithDexMetadata);
static_assert(CompilationReasonFromName(CompilationReasonName(CompilationReason::kPrebuilt)) ==
              CompilationReason::kPrebuilt);
static_assert(CompilationReasonFromName(CompilationReasonName(CompilationReason::kCmdLine)) ==
              CompilationReason::kCmdLine);
static_assert(CompilationReasonFromName(CompilationReasonName(CompilationReason::kVdex)) ==
              CompilationReason::kVdex);
static_assert(
    CompilationReasonFromName(CompilationReasonName(CompilationReason::kBootAfterMainlineUpdate)) ==
    CompilationReason::kBootAfterMainlineUpdate);

}  // namespace metrics
}  // namespace art

#pragma clang diagnostic pop  // -Wconversion
