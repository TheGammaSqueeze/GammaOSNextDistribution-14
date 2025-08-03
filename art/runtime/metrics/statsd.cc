/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "statsd.h"

#include "arch/instruction_set.h"
#include "base/compiler_filter.h"
#include "base/metrics/metrics.h"
#include "gc/collector/mark_compact.h"
#include "gc/heap.h"
#include "gc/space/image_space.h"
#include "runtime.h"
#include "statslog_art.h"

#pragma clang diagnostic push
#pragma clang diagnostic error "-Wconversion"

namespace art HIDDEN {
namespace metrics {

namespace {

// EncodeDatumId returns a std::optional that provides a enum value from atoms.proto if the datum is
// one that we support logging to statsd. The list of datums that ART collects is a superset of what
// we report to statsd. Therefore, we only have mappings for the DatumIds that statsd recognizes.
// Also it must be noted that histograms are not handled yet by statsd yet.
//
// Other code can use whether the result of this function has a value to decide whether to report
// the atom to statsd.
//
// To report additional measurements to statsd, first add an entry in atoms.proto and then add an
// entry to this function as well.
constexpr std::optional<int32_t> EncodeDatumId(DatumId datum_id) {
  switch (datum_id) {
    case DatumId::kClassVerificationTotalTime:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_CLASS_VERIFICATION_TIME_COUNTER_MICROS);
    case DatumId::kClassVerificationTotalTimeDelta:
      return std::make_optional(
          statsd::ART_DATUM_DELTA_REPORTED__KIND__ART_DATUM_DELTA_CLASS_VERIFICATION_TIME_MICROS);
    case DatumId::kJitMethodCompileTotalTime:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_JIT_METHOD_COMPILE_TIME_MICROS);
    case DatumId::kJitMethodCompileTotalTimeDelta:
      return std::make_optional(
          statsd::ART_DATUM_DELTA_REPORTED__KIND__ART_DATUM_DELTA_JIT_METHOD_COMPILE_TIME_MICROS);
    case DatumId::kClassLoadingTotalTime:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_CLASS_LOADING_TIME_COUNTER_MICROS);
    case DatumId::kClassLoadingTotalTimeDelta:
      return std::make_optional(
          statsd::ART_DATUM_DELTA_REPORTED__KIND__ART_DATUM_DELTA_CLASS_LOADING_TIME_MICROS);
    case DatumId::kClassVerificationCount:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_CLASS_VERIFICATION_COUNT);
    case DatumId::kClassVerificationCountDelta:
      return std::make_optional(
          statsd::ART_DATUM_DELTA_REPORTED__KIND__ART_DATUM_DELTA_CLASS_VERIFICATION_COUNT);
    case DatumId::kWorldStopTimeDuringGCAvg:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_GC_WORLD_STOP_TIME_AVG_MICROS);
    case DatumId::kYoungGcCount:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_GC_YOUNG_GENERATION_COLLECTION_COUNT);
    case DatumId::kYoungGcCountDelta:
      return std::make_optional(
          statsd::
              ART_DATUM_DELTA_REPORTED__KIND__ART_DATUM_DELTA_GC_YOUNG_GENERATION_COLLECTION_COUNT);
    case DatumId::kFullGcCount:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_GC_FULL_HEAP_COLLECTION_COUNT);
    case DatumId::kFullGcCountDelta:
      return std::make_optional(
          statsd::ART_DATUM_DELTA_REPORTED__KIND__ART_DATUM_DELTA_GC_FULL_HEAP_COLLECTION_COUNT);
    case DatumId::kTotalBytesAllocated:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_GC_TOTAL_BYTES_ALLOCATED);
    case DatumId::kTotalBytesAllocatedDelta:
      return std::make_optional(
          statsd::ART_DATUM_DELTA_REPORTED__KIND__ART_DATUM_DELTA_GC_TOTAL_BYTES_ALLOCATED);
    case DatumId::kYoungGcCollectionTime:
      return std::make_optional(
          statsd::
              ART_DATUM_REPORTED__KIND__ART_DATUM_GC_YOUNG_GENERATION_COLLECTION_TIME_HISTO_MILLIS);
    case DatumId::kFullGcCollectionTime:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_GC_FULL_HEAP_COLLECTION_TIME_HISTO_MILLIS);
    case DatumId::kYoungGcThroughput:
      return std::make_optional(
          statsd::
              ART_DATUM_REPORTED__KIND__ART_DATUM_GC_YOUNG_GENERATION_COLLECTION_THROUGHPUT_HISTO_MB_PER_SEC);
    case DatumId::kFullGcThroughput:
      return std::make_optional(
          statsd::
              ART_DATUM_REPORTED__KIND__ART_DATUM_GC_FULL_HEAP_COLLECTION_THROUGHPUT_HISTO_MB_PER_SEC);
    case DatumId::kJitMethodCompileCount:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_JIT_METHOD_COMPILE_COUNT);
    case DatumId::kJitMethodCompileCountDelta:
      return std::make_optional(
          statsd::ART_DATUM_DELTA_REPORTED__KIND__ART_DATUM_DELTA_JIT_METHOD_COMPILE_COUNT);
    case DatumId::kYoungGcTracingThroughput:
      return std::make_optional(
          statsd::
              ART_DATUM_REPORTED__KIND__ART_DATUM_GC_YOUNG_GENERATION_TRACING_THROUGHPUT_HISTO_MB_PER_SEC);
    case DatumId::kFullGcTracingThroughput:
      return std::make_optional(
          statsd::
              ART_DATUM_REPORTED__KIND__ART_DATUM_GC_FULL_HEAP_TRACING_THROUGHPUT_HISTO_MB_PER_SEC);
    case DatumId::kTotalGcCollectionTime:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_GC_TOTAL_COLLECTION_TIME_MS);
    case DatumId::kTotalGcCollectionTimeDelta:
      return std::make_optional(
          statsd::ART_DATUM_DELTA_REPORTED__KIND__ART_DATUM_DELTA_GC_TOTAL_COLLECTION_TIME_MS);
    case DatumId::kYoungGcThroughputAvg:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_GC_YOUNG_GENERATION_COLLECTION_THROUGHPUT_AVG_MB_PER_SEC);
    case DatumId::kFullGcThroughputAvg:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_GC_FULL_HEAP_COLLECTION_THROUGHPUT_AVG_MB_PER_SEC);
    case DatumId::kYoungGcTracingThroughputAvg:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_GC_YOUNG_GENERATION_TRACING_THROUGHPUT_AVG_MB_PER_SEC);
    case DatumId::kFullGcTracingThroughputAvg:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_GC_FULL_HEAP_TRACING_THROUGHPUT_AVG_MB_PER_SEC);
    case DatumId::kGcWorldStopTime:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_GC_WORLD_STOP_TIME_US);
    case DatumId::kGcWorldStopTimeDelta:
      return std::make_optional(
          statsd::ART_DATUM_DELTA_REPORTED__KIND__ART_DATUM_DELTA_GC_WORLD_STOP_TIME_US);
    case DatumId::kGcWorldStopCount:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_GC_WORLD_STOP_COUNT);
    case DatumId::kGcWorldStopCountDelta:
      return std::make_optional(
          statsd::ART_DATUM_DELTA_REPORTED__KIND__ART_DATUM_DELTA_GC_WORLD_STOP_COUNT);
    case DatumId::kYoungGcScannedBytes:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_GC_YOUNG_GENERATION_COLLECTION_SCANNED_BYTES);
    case DatumId::kYoungGcScannedBytesDelta:
      return std::make_optional(
          statsd::
              ART_DATUM_DELTA_REPORTED__KIND__ART_DATUM_DELTA_GC_YOUNG_GENERATION_COLLECTION_SCANNED_BYTES);
    case DatumId::kYoungGcFreedBytes:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_GC_YOUNG_GENERATION_COLLECTION_FREED_BYTES);
    case DatumId::kYoungGcFreedBytesDelta:
      return std::make_optional(
          statsd::
              ART_DATUM_DELTA_REPORTED__KIND__ART_DATUM_DELTA_GC_YOUNG_GENERATION_COLLECTION_FREED_BYTES);
    case DatumId::kYoungGcDuration:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_GC_YOUNG_GENERATION_COLLECTION_DURATION_MS);
    case DatumId::kYoungGcDurationDelta:
      return std::make_optional(
          statsd::
              ART_DATUM_DELTA_REPORTED__KIND__ART_DATUM_DELTA_GC_YOUNG_GENERATION_COLLECTION_DURATION_MS);
    case DatumId::kFullGcScannedBytes:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_GC_FULL_HEAP_COLLECTION_SCANNED_BYTES);
    case DatumId::kFullGcScannedBytesDelta:
      return std::make_optional(
          statsd::
              ART_DATUM_DELTA_REPORTED__KIND__ART_DATUM_DELTA_GC_FULL_HEAP_COLLECTION_SCANNED_BYTES);
    case DatumId::kFullGcFreedBytes:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_GC_FULL_HEAP_COLLECTION_FREED_BYTES);
    case DatumId::kFullGcFreedBytesDelta:
      return std::make_optional(
          statsd::
              ART_DATUM_DELTA_REPORTED__KIND__ART_DATUM_DELTA_GC_FULL_HEAP_COLLECTION_FREED_BYTES);
    case DatumId::kFullGcDuration:
      return std::make_optional(
          statsd::ART_DATUM_REPORTED__KIND__ART_DATUM_GC_FULL_HEAP_COLLECTION_DURATION_MS);
    case DatumId::kFullGcDurationDelta:
      return std::make_optional(
          statsd::
              ART_DATUM_DELTA_REPORTED__KIND__ART_DATUM_DELTA_GC_FULL_HEAP_COLLECTION_DURATION_MS);
    case DatumId::kTimeElapsedDelta:
      return std::make_optional(
          statsd::ART_DATUM_DELTA_REPORTED__KIND__ART_DATUM_DELTA_TIME_ELAPSED_MS);
  }
}

constexpr int32_t EncodeCompileFilter(CompilerFilterReporting filter) {
  switch (filter) {
    case CompilerFilterReporting::kAssumeVerified:
      return statsd::ART_DATUM_REPORTED__COMPILE_FILTER__ART_COMPILATION_FILTER_ASSUMED_VERIFIED;
    case CompilerFilterReporting::kExtract:
      return statsd::ART_DATUM_REPORTED__COMPILE_FILTER__ART_COMPILATION_FILTER_EXTRACT;
    case CompilerFilterReporting::kVerify:
      return statsd::ART_DATUM_REPORTED__COMPILE_FILTER__ART_COMPILATION_FILTER_VERIFY;
    case CompilerFilterReporting::kSpaceProfile:
      return statsd::ART_DATUM_REPORTED__COMPILE_FILTER__ART_COMPILATION_FILTER_SPACE_PROFILE;
    case CompilerFilterReporting::kSpace:
      return statsd::ART_DATUM_REPORTED__COMPILE_FILTER__ART_COMPILATION_FILTER_SPACE;
    case CompilerFilterReporting::kSpeedProfile:
      return statsd::ART_DATUM_REPORTED__COMPILE_FILTER__ART_COMPILATION_FILTER_SPEED_PROFILE;
    case CompilerFilterReporting::kSpeed:
      return statsd::ART_DATUM_REPORTED__COMPILE_FILTER__ART_COMPILATION_FILTER_SPEED;
    case CompilerFilterReporting::kEverythingProfile:
      return statsd::
          ART_DATUM_REPORTED__COMPILE_FILTER__ART_COMPILATION_FILTER_EVERYTHING_PROFILE;
    case CompilerFilterReporting::kEverything:
      return statsd::ART_DATUM_REPORTED__COMPILE_FILTER__ART_COMPILATION_FILTER_EVERYTHING;
    case CompilerFilterReporting::kError:
      return statsd::ART_DATUM_REPORTED__COMPILE_FILTER__ART_COMPILATION_FILTER_ERROR;
    case CompilerFilterReporting::kUnknown:
       return statsd::ART_DATUM_REPORTED__COMPILE_FILTER__ART_COMPILATION_FILTER_UNKNOWN;
    case CompilerFilterReporting::kRunFromApk:
       return statsd::ART_DATUM_REPORTED__COMPILE_FILTER__ART_COMPILATION_FILTER_FAKE_RUN_FROM_APK;
    case CompilerFilterReporting::kRunFromApkFallback:
       return statsd::ART_DATUM_REPORTED__COMPILE_FILTER__ART_COMPILATION_FILTER_FAKE_RUN_FROM_APK_FALLBACK;
  }
}

constexpr int32_t EncodeCompilationReason(CompilationReason reason) {
  switch (reason) {
    case CompilationReason::kUnknown:
      return statsd::ART_DATUM_REPORTED__COMPILATION_REASON__ART_COMPILATION_REASON_UNKNOWN;
    case CompilationReason::kABOTA:
      return statsd::ART_DATUM_REPORTED__COMPILATION_REASON__ART_COMPILATION_REASON_AB_OTA;
    case CompilationReason::kBgDexopt:
      return statsd::ART_DATUM_REPORTED__COMPILATION_REASON__ART_COMPILATION_REASON_BG_DEXOPT;
    case CompilationReason::kError:
      return statsd::ART_DATUM_REPORTED__COMPILATION_REASON__ART_COMPILATION_REASON_ERROR;
    case CompilationReason::kFirstBoot:
      return statsd::ART_DATUM_REPORTED__COMPILATION_REASON__ART_COMPILATION_REASON_FIRST_BOOT;
    case CompilationReason::kInactive:
      return statsd::ART_DATUM_REPORTED__COMPILATION_REASON__ART_COMPILATION_REASON_INACTIVE;
    case CompilationReason::kInstall:
      return statsd::ART_DATUM_REPORTED__COMPILATION_REASON__ART_COMPILATION_REASON_INSTALL;
    case CompilationReason::kInstallWithDexMetadata:
      return statsd::
          ART_DATUM_REPORTED__COMPILATION_REASON__ART_COMPILATION_REASON_INSTALL_WITH_DEX_METADATA;
    case CompilationReason::kShared:
      return statsd::ART_DATUM_REPORTED__COMPILATION_REASON__ART_COMPILATION_REASON_SHARED;
    case CompilationReason::kPostBoot:
      return statsd::ART_DATUM_REPORTED__COMPILATION_REASON__ART_COMPILATION_REASON_POST_BOOT;
    case CompilationReason::kInstallBulk:
      return statsd::ART_DATUM_REPORTED__COMPILATION_REASON__ART_COMPILATION_REASON_INSTALL_BULK;
    case CompilationReason::kInstallBulkSecondary:
      return statsd::
          ART_DATUM_REPORTED__COMPILATION_REASON__ART_COMPILATION_REASON_INSTALL_BULK_SECONDARY;
    case CompilationReason::kInstallBulkDowngraded:
      return statsd::
          ART_DATUM_REPORTED__COMPILATION_REASON__ART_COMPILATION_REASON_INSTALL_BULK_DOWNGRADED;
    case CompilationReason::kInstallBulkSecondaryDowngraded:
      return statsd::
          ART_DATUM_REPORTED__COMPILATION_REASON__ART_COMPILATION_REASON_INSTALL_BULK_SECONDARY_DOWNGRADED;
    case CompilationReason::kBootAfterOTA:
      return statsd::ART_DATUM_REPORTED__COMPILATION_REASON__ART_COMPILATION_REASON_BOOT_AFTER_OTA;
    case CompilationReason::kInstallFast:
      return statsd::ART_DATUM_REPORTED__COMPILATION_REASON__ART_COMPILATION_REASON_INSTALL_FAST;
    case CompilationReason::kPrebuilt:
      return statsd::ART_DATUM_REPORTED__COMPILATION_REASON__ART_COMPILATION_REASON_PREBUILT;
    case CompilationReason::kCmdLine:
      return statsd::ART_DATUM_REPORTED__COMPILATION_REASON__ART_COMPILATION_REASON_CMDLINE;
    case CompilationReason::kVdex:
      return statsd::ART_DATUM_REPORTED__COMPILATION_REASON__ART_COMPILATION_REASON_VDEX;
    case CompilationReason::kBootAfterMainlineUpdate:
      return statsd::
          ART_DATUM_REPORTED__COMPILATION_REASON__ART_COMPILATION_REASON_BOOT_AFTER_MAINLINE_UPDATE;
  }
}

constexpr int32_t EncodeInstructionSet(InstructionSet isa) {
  switch (isa) {
    case InstructionSet::kArm:
      // Fall-through.
    case InstructionSet::kThumb2:
      return statsd::ART_DATUM_REPORTED__ISA__ART_ISA_ARM;
    case InstructionSet::kArm64:
      return statsd::ART_DATUM_REPORTED__ISA__ART_ISA_ARM64;
    case InstructionSet::kRiscv64:
      return statsd::ART_DATUM_REPORTED__ISA__ART_ISA_RISCV64;
    case InstructionSet::kX86:
      return statsd::ART_DATUM_REPORTED__ISA__ART_ISA_X86;
    case InstructionSet::kX86_64:
      return statsd::ART_DATUM_REPORTED__ISA__ART_ISA_X86_64;
    case InstructionSet::kNone:
      return statsd::ART_DATUM_REPORTED__ISA__ART_ISA_UNKNOWN;
  }
}

constexpr int32_t EncodeGcCollectorType(gc::CollectorType collector_type) {
  switch (collector_type) {
    case gc::CollectorType::kCollectorTypeMS:
      return statsd::ART_DATUM_REPORTED__GC__ART_GC_COLLECTOR_TYPE_MARK_SWEEP;
    case gc::CollectorType::kCollectorTypeCMS:
      return statsd::ART_DATUM_REPORTED__GC__ART_GC_COLLECTOR_TYPE_CONCURRENT_MARK_SWEEP;
    case gc::CollectorType::kCollectorTypeCMC:
      return statsd::ART_DATUM_REPORTED__GC__ART_GC_COLLECTOR_TYPE_CONCURRENT_MARK_COMPACT;
    case gc::CollectorType::kCollectorTypeCMCBackground:
      return statsd::
          ART_DATUM_REPORTED__GC__ART_GC_COLLECTOR_TYPE_CONCURRENT_MARK_COMPACT_BACKGROUND;
    case gc::CollectorType::kCollectorTypeSS:
      return statsd::ART_DATUM_REPORTED__GC__ART_GC_COLLECTOR_TYPE_SEMI_SPACE;
    case gc::kCollectorTypeCC:
      return statsd::ART_DATUM_REPORTED__GC__ART_GC_COLLECTOR_TYPE_CONCURRENT_COPYING;
    case gc::kCollectorTypeCCBackground:
      return statsd::ART_DATUM_REPORTED__GC__ART_GC_COLLECTOR_TYPE_CONCURRENT_COPYING_BACKGROUND;
    case gc::kCollectorTypeNone:
    case gc::kCollectorTypeInstrumentation:
    case gc::kCollectorTypeAddRemoveAppImageSpace:
    case gc::kCollectorTypeDebugger:
    case gc::kCollectorTypeHomogeneousSpaceCompact:
    case gc::kCollectorTypeClassLinker:
    case gc::kCollectorTypeJitCodeCache:
    case gc::kCollectorTypeHprof:
    case gc::kCollectorTypeAddRemoveSystemWeakHolder:
    case gc::kCollectorTypeGetObjectsAllocated:
    case gc::kCollectorTypeCriticalSection:
    case gc::kCollectorTypeHeapTrim:
      return statsd::ART_DATUM_REPORTED__GC__ART_GC_COLLECTOR_TYPE_UNKNOWN;
  }
}

int32_t EncodeUffdMinorFaultSupport() {
  auto [uffd_supported, minor_fault_supported] = gc::collector::MarkCompact::GetUffdAndMinorFault();

  if (uffd_supported) {
    if (minor_fault_supported) {
      return statsd::ART_DATUM_REPORTED__UFFD_SUPPORT__ART_UFFD_SUPPORT_MINOR_FAULT_MODE_SUPPORTED;
    } else {
      return statsd::
          ART_DATUM_REPORTED__UFFD_SUPPORT__ART_UFFD_SUPPORT_MINOR_FAULT_MODE_NOT_SUPPORTED;
    }
  } else {
    return statsd::ART_DATUM_REPORTED__UFFD_SUPPORT__ART_UFFD_SUPPORT_UFFD_NOT_SUPPORTED;
  }
}

class StatsdBackend : public MetricsBackend {
 public:
  void BeginOrUpdateSession(const SessionData& session_data) override {
    session_data_ = session_data;
  }

 protected:
  void BeginReport(uint64_t timestamp_since_start_ms) override {
    current_timestamp_ = static_cast<int64_t>(timestamp_since_start_ms);
  }

  void ReportCounter(DatumId counter_type, uint64_t value) override {
    std::optional<int32_t> datum_id = EncodeDatumId(counter_type);
    if (!datum_id.has_value()) {
      return;
    }

    int32_t atom;
    switch (counter_type) {
#define EVENT_METRIC_CASE(name, ...) case DatumId::k##name:
      ART_EVENT_METRICS(EVENT_METRIC_CASE)
#undef EVENT_METRIC_CASE
      atom = statsd::ART_DATUM_REPORTED;
      break;

#define VALUE_METRIC_CASE(name, type, ...) case DatumId::k##name:
      ART_VALUE_METRICS(VALUE_METRIC_CASE)
#undef VALUE_METRIC_CASE
      atom = statsd::ART_DATUM_DELTA_REPORTED;
      break;
    }

    statsd::stats_write(
        atom,
        session_data_.session_id,
        session_data_.uid,
        EncodeCompileFilter(session_data_.compiler_filter),
        EncodeCompilationReason(session_data_.compilation_reason),
        current_timestamp_,
        0,  // deprecated - was ArtThreadType
        datum_id.value(),
        static_cast<int64_t>(value),
        0,  // deprecated - was ArtDexMetadataType
        0,  // deprecated - was ArtApkType
        EncodeInstructionSet(kRuntimeISA),
        EncodeGcCollectorType(Runtime::Current()->GetHeap()->GetForegroundCollectorType()),
        EncodeUffdMinorFaultSupport());
  }

  void ReportHistogram(DatumId /*histogram_type*/,
                       int64_t /*low_value*/,
                       int64_t /*high_value*/,
                       const std::vector<uint32_t>& /*buckets*/) override {
    // TODO: implement this once ArtDatumReported in atoms.proto supports histograms.
    LOG_STREAM(DEBUG) << "Attempting to write histogram to statsd. This is not supported yet.";
  }

  void EndReport() override {}

 private:
  SessionData session_data_;
  // The timestamp provided to the last call to BeginReport
  int64_t current_timestamp_;
};

}  // namespace

std::unique_ptr<MetricsBackend> CreateStatsdBackend() { return std::make_unique<StatsdBackend>(); }

void ReportDeviceMetrics() {
  Runtime* runtime = Runtime::Current();
  int32_t boot_image_status;
  if (runtime->GetHeap()->HasBootImageSpace() && !runtime->HasImageWithProfile()) {
    boot_image_status = statsd::ART_DEVICE_DATUM_REPORTED__BOOT_IMAGE_STATUS__STATUS_FULL;
  } else if (runtime->GetHeap()->HasBootImageSpace() &&
             runtime->GetHeap()->GetBootImageSpaces()[0]->GetProfileFiles().empty()) {
    boot_image_status = statsd::ART_DEVICE_DATUM_REPORTED__BOOT_IMAGE_STATUS__STATUS_MINIMAL;
  } else {
    boot_image_status = statsd::ART_DEVICE_DATUM_REPORTED__BOOT_IMAGE_STATUS__STATUS_NONE;
  }
  statsd::stats_write(statsd::ART_DEVICE_DATUM_REPORTED, boot_image_status);
}

}  // namespace metrics
}  // namespace art

#pragma clang diagnostic pop  // -Wconversion
