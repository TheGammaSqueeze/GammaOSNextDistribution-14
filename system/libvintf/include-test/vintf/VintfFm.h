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

#pragma once

#include <functional>
#include <map>
#include <memory>
#include <optional>

#include <vintf/FileSystem.h>
#include <vintf/HalFormat.h>
#include <vintf/Level.h>
#include <vintf/VintfObject.h>

namespace android::vintf {

class VintfFm {
   public:
    // Use default filesystem.
    VintfFm();
    // Exposed for testing.
    VintfFm(std::unique_ptr<WritableFileSystem>&& fs) : mFs(std::move(fs)) {}
    int main(int argc, char** argv);

   private:
    using FrozenMatrices = std::map<Level, CompatibilityMatrix>;
    using FsFactory = std::function<std::unique_ptr<FileSystem>()>;

    std::unique_ptr<WritableFileSystem> mFs;

    // Check framework manifest against frozen text in dir.
    int check(const FsFactory& vintfFsFactory, const std::string& dir);
    // Dump frozen text suing framework manifest
    int update(const FsFactory& vintfFsFactory, const std::string& dir, Level level);
    std::shared_ptr<const HalManifest> getManifestForLevel(const FsFactory& vintfFsFactory,
                                                           Level level);
    bool dumpMatrix(const HalManifest& manifest, const std::string& dir, Level level);
    std::optional<FrozenMatrices> loadMatrices(const std::string& dir);
};

}  // namespace android::vintf
