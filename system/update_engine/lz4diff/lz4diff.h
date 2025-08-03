//
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
//

#ifndef UPDATE_ENGINE_LZ4DIFF_LZ4DIFF_H_
#define UPDATE_ENGINE_LZ4DIFF_LZ4DIFF_H_

#include <vector>
#include <string_view>

#include "lz4diff/lz4diff.pb.h"
#include "update_engine/lz4diff/lz4diff_format.h"
#include "update_engine/update_metadata.pb.h"

namespace chromeos_update_engine {

bool Lz4Diff(std::string_view src,
             std::string_view dst,
             const CompressedFile& src_file_info,
             const CompressedFile& dst_file_info,
             Blob* output,
             InstallOperation::Type* op_type = nullptr) noexcept;

bool Lz4Diff(const Blob& src,
             const Blob& dst,
             const CompressedFile& src_file_info,
             const CompressedFile& dst_file_info,
             Blob* output,
             InstallOperation::Type* op_type = nullptr) noexcept;

}  // namespace chromeos_update_engine

#endif
