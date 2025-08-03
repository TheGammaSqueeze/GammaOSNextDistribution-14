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

#ifndef ART_RUNTIME_OAT_OAT_FILE_ASSISTANT_CONTEXT_H_
#define ART_RUNTIME_OAT_OAT_FILE_ASSISTANT_CONTEXT_H_

#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

#include "arch/instruction_set.h"
#include "base/macros.h"
#include "runtime.h"

namespace art HIDDEN {

// A helper class for OatFileAssistant that fetches and caches information including boot image
// checksums, bootclasspath checksums, and APEX versions. The same instance can be reused across
// OatFileAssistant calls on different dex files for different instruction sets.
// This class is not thread-safe until `FetchAll` is called.
class OatFileAssistantContext {
 public:
  // Options that a runtime would take.
  // Note that the struct only keeps references, so the caller must keep the objects alive during
  // the lifetime of OatFileAssistant.
  struct RuntimeOptions {
    // Required. See `-Ximage`.
    const std::vector<std::string>& image_locations;
    // Required. See `-Xbootclasspath`.
    const std::vector<std::string>& boot_class_path;
    // Required. See `-Xbootclasspath-locations`.
    const std::vector<std::string>& boot_class_path_locations;
    // Optional. See `-Xbootclasspathfds`.
    std::optional<ArrayRef<File>> boot_class_path_files = {};
    // Optional. See `-Xdeny-art-apex-data-files`.
    const bool deny_art_apex_data_files = false;
  };

  // Information about a boot image.
  struct BootImageInfo {
    // Number of BCP jars covered by the boot image.
    size_t component_count;
    // Checksum of the boot image. The format is "i;<component_count>/<checksum_in_8_digit_hex>"
    std::string checksum;
  };

  // Constructs OatFileAssistantContext from runtime options. Does not fetch information on
  // construction. Information will be fetched from disk when needed.
  EXPORT explicit OatFileAssistantContext(std::unique_ptr<RuntimeOptions> runtime_options);
  // Constructs OatFileAssistantContext from a runtime instance. Fetches as much information as
  // possible from the runtime. The rest information will be fetched from disk when needed.
  explicit OatFileAssistantContext(Runtime* runtime);
  // Returns runtime options.
  const RuntimeOptions& GetRuntimeOptions() const;
  // Fetches all information that hasn't been fetched from disk and caches it. All operations will
  // be read-only after a successful call to this function.
  EXPORT bool FetchAll(std::string* error_msg);
  // Returns information about the boot image of the given instruction set.
  EXPORT const std::vector<BootImageInfo>& GetBootImageInfoList(InstructionSet isa);
  // Returns the checksums of the dex files in the BCP jar at the given index, or nullptr on error.
  // The format of each checksum is "/<checksum_in_8_digit_hex>".
  const std::vector<std::string>* GetBcpChecksums(size_t bcp_index, std::string* error_msg);
  // Returns a string that represents the apex versions of boot classpath jars. See
  // `Runtime::apex_versions_` for the encoding format.
  const std::string& GetApexVersions();

 private:
  std::unique_ptr<RuntimeOptions> runtime_options_;
  std::unordered_map<InstructionSet, std::vector<BootImageInfo>> boot_image_info_list_by_isa_;
  std::unordered_map<size_t, std::vector<std::string>> bcp_checksums_by_index_;
  std::optional<std::string> apex_versions_;
};

}  // namespace art

#endif  // ART_RUNTIME_OAT_OAT_FILE_ASSISTANT_CONTEXT_H_
