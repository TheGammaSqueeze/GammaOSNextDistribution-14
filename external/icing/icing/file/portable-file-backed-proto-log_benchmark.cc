// Copyright (C) 2021 Google LLC
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

#include <cstdint>
#include <random>

#include "testing/base/public/benchmark.h"
#include "gmock/gmock.h"
#include "icing/document-builder.h"
#include "icing/file/filesystem.h"
#include "icing/file/portable-file-backed-proto-log.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/proto/document.pb.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/random-string.h"
#include "icing/testing/tmp-directory.h"

// go/microbenchmarks
//
// To build and run on a local machine:
//   $ blaze build -c opt --dynamic_mode=off --copt=-gmlt
//   icing/file:portable-file-backed-proto-log_benchmark
//
//   $ blaze-bin/icing/file/portable-file-backed-proto-log_benchmark
//   --benchmark_filter=all
//
//
// To build and run on an Android device (must be connected and rooted):
//   $ blaze build --copt="-DGOOGLE_COMMANDLINEFLAGS_FULL_API=1"
//   --config=android_arm64 -c opt --dynamic_mode=off --copt=-gmlt
//   icing/file:portable-file-backed-proto-log_benchmark
//
//   $ adb root
//
//   $ adb push
//   blaze-bin/icing/file/portable-file-backed-proto-log_benchmark
//   /data/local/tmp/
//
//   $ adb shell /data/local/tmp/portable-file-backed-proto-log-benchmark
//   --benchmark_filter=all

namespace icing {
namespace lib {

namespace {

void BM_Write(benchmark::State& state) {
  const Filesystem filesystem;
  int string_length = state.range(0);
  const std::string file_path = IcingStringUtil::StringPrintf(
      "%s%s%d%s", GetTestTempDir().c_str(), "/proto_", string_length, ".log");
  int max_proto_size = (1 << 24) - 1;  // 16 MiB
  bool compress = true;

  // Make sure it doesn't already exist.
  filesystem.DeleteFile(file_path.c_str());

  auto proto_log = PortableFileBackedProtoLog<DocumentProto>::Create(
                       &filesystem, file_path,
                       PortableFileBackedProtoLog<DocumentProto>::Options(
                           compress, max_proto_size))
                       .ValueOrDie()
                       .proto_log;

  DocumentProto document = DocumentBuilder().SetKey("namespace", "uri").Build();

  std::default_random_engine random;
  const std::string rand_str =
      RandomString(kAlNumAlphabet, string_length, &random);

  auto document_properties = document.add_properties();
  document_properties->set_name("string property");
  document_properties->add_string_values(rand_str);

  for (auto _ : state) {
    testing::DoNotOptimize(proto_log->WriteProto(document));
  }
  state.SetBytesProcessed(static_cast<int64_t>(state.iterations()) *
                          string_length);

  // Cleanup after ourselves
  filesystem.DeleteFile(file_path.c_str());
}
BENCHMARK(BM_Write)
    ->Arg(1)
    ->Arg(32)
    ->Arg(512)
    ->Arg(1024)
    ->Arg(4 * 1024)
    ->Arg(8 * 1024)
    ->Arg(16 * 1024)
    ->Arg(32 * 1024)
    ->Arg(256 * 1024)
    ->Arg(2 * 1024 * 1024)
    ->Arg(8 * 1024 * 1024)
    ->Arg(15 * 1024 * 1024);  // We do 15MiB here since our max proto size is
                              // 16MiB, and we need some extra space for the
                              // rest of the document properties

void BM_Read(benchmark::State& state) {
  const Filesystem filesystem;
  int string_length = state.range(0);
  const std::string file_path = IcingStringUtil::StringPrintf(
      "%s%s%d%s", GetTestTempDir().c_str(), "/proto_", string_length, ".log");
  int max_proto_size = (1 << 24) - 1;  // 16 MiB
  bool compress = true;

  // Make sure it doesn't already exist.
  filesystem.DeleteFile(file_path.c_str());

  auto proto_log = PortableFileBackedProtoLog<DocumentProto>::Create(
                       &filesystem, file_path,
                       PortableFileBackedProtoLog<DocumentProto>::Options(
                           compress, max_proto_size))
                       .ValueOrDie()
                       .proto_log;

  DocumentProto document = DocumentBuilder().SetKey("namespace", "uri").Build();

  std::default_random_engine random;
  const std::string rand_str =
      RandomString(kAlNumAlphabet, string_length, &random);

  auto document_properties = document.add_properties();
  document_properties->set_name("string property");
  document_properties->add_string_values(rand_str);

  ICING_ASSERT_OK_AND_ASSIGN(int64_t write_offset,
                             proto_log->WriteProto(document));

  for (auto _ : state) {
    testing::DoNotOptimize(proto_log->ReadProto(write_offset));
  }
  state.SetBytesProcessed(static_cast<int64_t>(state.iterations()) *
                          string_length);

  // Cleanup after ourselves
  filesystem.DeleteFile(file_path.c_str());
}
BENCHMARK(BM_Read)
    ->Arg(1)
    ->Arg(32)
    ->Arg(512)
    ->Arg(1024)
    ->Arg(4 * 1024)
    ->Arg(8 * 1024)
    ->Arg(16 * 1024)
    ->Arg(32 * 1024)
    ->Arg(256 * 1024)
    ->Arg(2 * 1024 * 1024)
    ->Arg(8 * 1024 * 1024)
    ->Arg(15 * 1024 * 1024);  // We do 15MiB here since our max proto size is
                              // 16MiB, and we need some extra space for the
                              // rest of the document properties
                              //
void BM_Erase(benchmark::State& state) {
  const Filesystem filesystem;
  const std::string file_path = IcingStringUtil::StringPrintf(
      "%s%s", GetTestTempDir().c_str(), "/proto.log");
  int max_proto_size = (1 << 24) - 1;  // 16 MiB
  bool compress = true;

  // Make sure it doesn't already exist.
  filesystem.DeleteFile(file_path.c_str());

  auto proto_log = PortableFileBackedProtoLog<DocumentProto>::Create(
                       &filesystem, file_path,
                       PortableFileBackedProtoLog<DocumentProto>::Options(
                           compress, max_proto_size))
                       .ValueOrDie()
                       .proto_log;

  DocumentProto document = DocumentBuilder().SetKey("namespace", "uri").Build();

  std::default_random_engine random;
  const std::string rand_str = RandomString(kAlNumAlphabet, /*len=*/1, &random);

  auto document_properties = document.add_properties();
  document_properties->set_name("string property");
  document_properties->add_string_values(rand_str);

  for (auto _ : state) {
    state.PauseTiming();
    ICING_ASSERT_OK_AND_ASSIGN(int64_t write_offset,
                               proto_log->WriteProto(document));
    state.ResumeTiming();

    testing::DoNotOptimize(proto_log->EraseProto(write_offset));
  }

  // Cleanup after ourselves
  filesystem.DeleteFile(file_path.c_str());
}
BENCHMARK(BM_Erase);

void BM_ComputeChecksum(benchmark::State& state) {
  const Filesystem filesystem;
  const std::string file_path = GetTestTempDir() + "/proto.log";
  int max_proto_size = (1 << 24) - 1;  // 16 MiB
  bool compress = true;

  // Make sure it doesn't already exist.
  filesystem.DeleteFile(file_path.c_str());

  auto proto_log = PortableFileBackedProtoLog<DocumentProto>::Create(
                       &filesystem, file_path,
                       PortableFileBackedProtoLog<DocumentProto>::Options(
                           compress, max_proto_size))
                       .ValueOrDie()
                       .proto_log;

  DocumentProto document = DocumentBuilder().SetKey("namespace", "uri").Build();

  // Make each document 1KiB
  int string_length = 1024;
  std::default_random_engine random;
  const std::string rand_str =
      RandomString(kAlNumAlphabet, string_length, &random);

  auto document_properties = document.add_properties();
  document_properties->set_name("string property");
  document_properties->add_string_values(rand_str);

  int num_docs = state.range(0);
  for (int i = 0; i < num_docs; ++i) {
    ICING_ASSERT_OK(proto_log->WriteProto(document));
  }

  for (auto _ : state) {
    testing::DoNotOptimize(proto_log->ComputeChecksum());
  }

  // Cleanup after ourselves
  filesystem.DeleteFile(file_path.c_str());
}
BENCHMARK(BM_ComputeChecksum)->Range(1024, 1 << 20);

void BM_ComputeChecksumWithCachedChecksum(benchmark::State& state) {
  const Filesystem filesystem;
  const std::string file_path = GetTestTempDir() + "/proto.log";
  int max_proto_size = (1 << 24) - 1;  // 16 MiB
  bool compress = true;

  // Make sure it doesn't already exist.
  filesystem.DeleteFile(file_path.c_str());

  auto proto_log = PortableFileBackedProtoLog<DocumentProto>::Create(
                       &filesystem, file_path,
                       PortableFileBackedProtoLog<DocumentProto>::Options(
                           compress, max_proto_size))
                       .ValueOrDie()
                       .proto_log;

  DocumentProto document = DocumentBuilder().SetKey("namespace", "uri").Build();

  // Make the document 1KiB
  int string_length = 1024;
  std::default_random_engine random;
  const std::string rand_str =
      RandomString(kAlNumAlphabet, string_length, &random);

  auto document_properties = document.add_properties();
  document_properties->set_name("string property");
  document_properties->add_string_values(rand_str);

  // Write some content and persist. This should update our cached checksum to
  // include the document.
  ICING_ASSERT_OK(proto_log->WriteProto(document));
  ICING_ASSERT_OK(proto_log->PersistToDisk());

  // This ComputeChecksum call shouldn't need to do any computation since we can
  // reuse our cached checksum.
  for (auto _ : state) {
    testing::DoNotOptimize(proto_log->ComputeChecksum());
  }

  // Cleanup after ourselves
  filesystem.DeleteFile(file_path.c_str());
}
BENCHMARK(BM_ComputeChecksumWithCachedChecksum);

void BM_ComputeChecksumOnlyForTail(benchmark::State& state) {
  const Filesystem filesystem;
  const std::string file_path = GetTestTempDir() + "/proto.log";
  int max_proto_size = (1 << 24) - 1;  // 16 MiB
  bool compress = true;

  // Make sure it doesn't already exist.
  filesystem.DeleteFile(file_path.c_str());

  auto proto_log = PortableFileBackedProtoLog<DocumentProto>::Create(
                       &filesystem, file_path,
                       PortableFileBackedProtoLog<DocumentProto>::Options(
                           compress, max_proto_size))
                       .ValueOrDie()
                       .proto_log;

  DocumentProto document = DocumentBuilder().SetKey("namespace", "uri").Build();

  // Make the document 1KiB
  int string_length = 1024;
  std::default_random_engine random;
  const std::string rand_str =
      RandomString(kAlNumAlphabet, string_length, &random);

  auto document_properties = document.add_properties();
  document_properties->set_name("string property");
  document_properties->add_string_values(rand_str);

  // Write some content and persist. This should update our cached checksum to
  // include the document.
  ICING_ASSERT_OK(proto_log->WriteProto(document));
  ICING_ASSERT_OK(proto_log->PersistToDisk());

  // Write another proto into the tail, but it's not included in our cached
  // checksum since we didn't call persist.
  ICING_ASSERT_OK(proto_log->WriteProto(document));

  // ComputeChecksum should be calculating the checksum of the tail and adding
  // it to the cached checksum we have.
  for (auto _ : state) {
    testing::DoNotOptimize(proto_log->ComputeChecksum());
  }

  // Cleanup after ourselves
  filesystem.DeleteFile(file_path.c_str());
}
BENCHMARK(BM_ComputeChecksumOnlyForTail);

}  // namespace
}  // namespace lib
}  // namespace icing
