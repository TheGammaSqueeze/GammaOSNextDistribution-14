/*
 * Copyright 2020 The Android Open Source Project
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

namespace {
constexpr char kDefaultBundleDataFile[] = "bundle_bfbs.bin";
constexpr char kDefaultGeneratedOutputPath[] = ".";
constexpr char kDefaultNamespace[] = "";
constexpr char kDefaultNamespaceDelim[] = "::";
constexpr char kDefaultTitle[] = "Bundled schema tables";
}  // namespace

struct Opts {
  bool verbose{false};
  bool read{false};
  bool write{false};
  const char* filename{kDefaultBundleDataFile};
  const char* gen{kDefaultGeneratedOutputPath};
  const char* main_root_name{nullptr};
  const char* ns_name{kDefaultNamespace};
  const char* title{kDefaultTitle};
  struct {
    int c{0};
    char** v{nullptr};
  } arg;
};
extern Opts opts;

namespace {
namespace helper {  // Part of flatbuffers API
constexpr bool AsBinaryFile = true;
constexpr bool AsTextFile = false;
}  // namespace helper

}  // namespace

/**
 * Read and parse a previously generated bundle data file
 *
 **/
int ReadBundledSchema();

/**
 * Generate a bundle data file from the binary flatbuffer schema
 * files provided as input
 *
 **/
int WriteBundledSchema();

/**
 * Print tool usage options
 */
int Usage(int argc, char** argv);

/**
 * Parse tool usage options
 */
void ParseArgs(int argc, char** argv);
