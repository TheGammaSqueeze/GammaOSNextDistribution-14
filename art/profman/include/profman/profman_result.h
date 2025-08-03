/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef ART_PROFMAN_INCLUDE_PROFMAN_PROFMAN_RESULT_H_
#define ART_PROFMAN_INCLUDE_PROFMAN_PROFMAN_RESULT_H_

namespace art {

class ProfmanResult {
 public:
  static constexpr int kErrorUsage = 100;

  // The return codes of processing profiles (running profman in normal mode).
  //
  // On a successful run:
  // - If `--force-merge` is specified, the return code can only be `kSuccess`.
  // - If no `--profile-file(-fd)` is specified, the return code can only be
  // `kSkipCompilationSmallDelta` or `kSkipCompilationEmptyProfiles`.
  // - Otherwise, the return code can only be `kCompile`, `kSkipCompilationSmallDelta`, or
  //   `kSkipCompilationEmptyProfiles`.
  //
  // Note that installd consumes the returns codes with its own copy of these values
  // (frameworks/native/cmds/installd/dexopt.cpp).
  enum ProcessingResult {
    // The success code for `--force-merge`.
    // This is also the generic success code for non-analysis runs.
    kSuccess = 0,
    // A merge has been performed, meaning the reference profile has been changed.
    kCompile = 1,
    // One of the following conditions is met:
    // - `--profile-file(-fd)` is not specified.
    // - The specified profiles are outdated (i.e., APK filename or checksum mismatch).
    // - The specified profiles are empty.
    // - The specified profiles don't contain any new class or method.
    // - The specified profiles don't contain enough number of new classes and methods that meets
    //   the threshold to trigger a merge, and `--force-merge-and-analyze` is not set.
    kSkipCompilationSmallDelta = 2,
    // All the input profiles (including the reference profile) are either outdated (i.e., APK
    // filename or checksum mismatch) or empty.
    kSkipCompilationEmptyProfiles = 7,
    // Errors.
    kErrorBadProfiles = 3,
    kErrorIO = 4,
    kErrorCannotLock = 5,
    kErrorDifferentVersions = 6,
  };

  // The return codes of running profman with `--copy-and-update-profile-key`.
  enum CopyAndUpdateResult {
    kCopyAndUpdateSuccess = 0,
    kCopyAndUpdateNoMatch = 21,
    kCopyAndUpdateErrorFailedToUpdateProfile = 22,
    kCopyAndUpdateErrorFailedToSaveProfile = 23,
    kCopyAndUpdateErrorFailedToLoadProfile = 24,
  };
};

}  // namespace art

#endif  // ART_PROFMAN_INCLUDE_PROFMAN_PROFMAN_RESULT_H_
